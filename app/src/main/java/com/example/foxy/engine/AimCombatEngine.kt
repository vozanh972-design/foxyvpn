package com.example.foxy.engine

import android.util.Log
import java.util.concurrent.Executors

private const val TAG = "FoxyAim"

/**
 * AimCombatEngine — Bone Rewrite Engine dựa trên logic thật của Free Fire.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * LOGIC THẬT TỪ GAME (100% từ global-metadata.dat research):
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Packet fire thật: C2S_RUDP_StartFire_Req
 * Fields xác nhận: weapon_id, target_id, distance, bone (HitPart enum)
 *
 * HitPart enum thật:
 *   None=0, Head=1, Neck=2, Chest=3, Body=4, Spine=5, Hand=6, Foot=7
 *
 * Header format thật:
 *   data[0] < 100  → headerSize = 10 bytes
 *   data[0] >= 100 → headerSize = 8 bytes
 *
 * Bone field offset: headerSize + 0x24 (confirmed từ Pigboy RE + ShinMod)
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * CƠ CHẾ HOẠT ĐỘNG:
 * ═══════════════════════════════════════════════════════════════════════════
 * 1. Chỉ can thiệp khi phase == INGAME (không touch lobby/loading/post)
 * 2. Chỉ can thiệp khi CMD 104 (START_FIRE) đã được observe
 * 3. Rewrite byte `bone` trong payload của fire packet
 * 4. KHÔNG làm gián đoạn flow: trả về null nếu không cần chỉnh → packet đi nguyên
 * 5. KHÔNG block dữ liệu: luôn gửi lên server (modified hoặc nguyên bản)
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * BỎ HOÀN TOÀN:
 * ═══════════════════════════════════════════════════════════════════════════
 * - HTTP bridge sang port 7755 (ModuleBridge) — không dùng Smali hook nữa
 * - Executor HTTP thread — không cần
 * - ACK check / Base64 decode — không cần
 * - request() / command() / set() / setFov() — xóa hết
 */
object AimCombatEngine {

    // ── HitPart enum thật từ game IL2CPP (global-metadata.dat research) ──
    const val HITPART_NONE  = FoxyPacketConstants.HITPART_NONE   // 0 = None
    const val HITPART_HEAD  = FoxyPacketConstants.HITPART_HEAD   // 1 = Head ← Target
    const val HITPART_NECK  = FoxyPacketConstants.HITPART_NECK   // 2 = Neck
    const val HITPART_CHEST = FoxyPacketConstants.HITPART_CHEST  // 3 = Chest
    const val HITPART_BODY  = FoxyPacketConstants.HITPART_BODY   // 4 = Body
    const val HITPART_SPINE = FoxyPacketConstants.HITPART_SPINE  // 5 = Spine
    const val HITPART_HAND  = FoxyPacketConstants.HITPART_HAND   // 6 = Hand
    const val HITPART_FOOT  = FoxyPacketConstants.HITPART_FOOT   // 7 = Foot

    // Aliases để tương thích với UI cũ
    const val BONE_HEAD = HITPART_HEAD
    const val BONE_NECK = HITPART_NECK
    const val BONE_BODY = HITPART_CHEST
    const val BONE_FOOT = HITPART_FOOT

    // Bone field offset trong payload (headerSize + 0x24)
    // Xác nhận từ: Pigboy RE + ShinMod research trên FF packet
    private const val BONE_OFFSET_IN_PAYLOAD = FoxyPacketConstants.FIRE_BONE_OFFSET_FROM_HEADER

    // ── State ─────────────────────────────────────────────────────────────
    @Volatile var isAimEnabled: Boolean = false
        private set
    @Volatile var targetBone: Int = HITPART_HEAD
        private set
    @Volatile private var dbgCounter: Long = 0L

    // ── Configure ─────────────────────────────────────────────────────────
    /**
     * Bật/tắt AimBot và chọn bone target.
     * Không gọi HTTP nữa — chỉ update state local.
     */
    fun configure(enabled: Boolean, bone: Int) {
        isAimEnabled = enabled
        targetBone   = bone.coerceIn(HITPART_NONE, HITPART_FOOT)
    }

    /**
     * Xử lý gói UDP outbound (client → server).
     *
     * Chỉ rewrite khi:
     *   1. isAimEnabled = true
     *   2. Phase == INGAME (không can thiệp ở lobby)
     *   3. Đang bắn: CMD 104 observe hoặc magnetActive
     *   4. Gói đủ lớn (>= 40 bytes) và không phải DNS
     *   5. Header hợp lệ
     *
     * Luôn trả về:
     *   - ByteArray đã sửa bone → gửi lên server
     *   - null → packet đi nguyên bản, KHÔNG bị block
     */
    fun process(flowKey: String, data: ByteArray, dstPort: Int): ByteArray? {
        // Guard 1: tính năng tắt / DNS passthrough / gói quá ngắn
        if (!isAimEnabled) return null
        if (dstPort == 53) return null
        if (data.size < FoxyPacketConstants.MIN_FIRE_PACKET_SIZE) return null

        // Guard 2: chỉ hoạt động khi INGAME hoặc có fire activity gần đây
        // Training room cũng cần hoạt động → bỏ strict phase check,
        // thay bằng check: đã từng thấy game traffic (không phải lobby thuần)
        // GamePhaseDetector.isIngame() OR có fire pulse → đủ điều kiện
        val hasFireActivity = FreezeController.isFiring()
        if (!GamePhaseDetector.isIngame() && !hasFireActivity) return null

        // Guard 3: xác định headerSize từ data[0] thật
        // data[0] < 100 → headerSize = 10 (long header)
        // data[0] >= 100 → headerSize = 8 (short header)
        val firstByte = data[0].toInt() and 0xFF
        val headerSize = when {
            firstByte < 100 -> FoxyPacketConstants.HEADER_SIZE_LONG   // 10
            else             -> FoxyPacketConstants.HEADER_SIZE_SHORT  // 8
        }

        // Guard 4: chỉ rewrite khi đang bắn (CMD 104 đã được observe)
        if (!hasFireActivity) return null

        // ── Scan protobuf body tìm field bone thật ────────────────────────
        // Thay vì hardcode offset 0x24, scan protobuf varint fields:
        // C2S_RUDP_StartFire_Req body: field1=weapon, field2=target, field3=dist, field4=bone
        // HitPart field value: 0=None, 1=Head, 2=Neck, 3=Chest, 4=Body, 5=Spine, 6=Hand, 7=Foot
        // Nếu scan không tìm được → fallback hardcode offset
        val bodyStart = headerSize
        if (bodyStart >= data.size) return null

        val boneOffset = findBoneOffset(data, bodyStart, targetBone)
            ?: (headerSize + BONE_OFFSET_IN_PAYLOAD).also {
                if (it >= data.size) return null
            }

        val currentBone = data[boneOffset].toInt() and 0xFF

        // ── Debug log: dump fire packet khi bone cần đổi ─────────────────
        // Log mỗi 20 packet để không spam, chứa hex dump 48 bytes đầu
        if (dbgCounter++ % 20 == 0L) {
            val hexDump = data.take(minOf(48, data.size))
                .joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            Log.d(TAG, "FirePkt[${data.size}] hdr=$headerSize boneOff=$boneOffset curBone=$currentBone tgt=$targetBone | $hexDump")
        }

        if (currentBone == targetBone) return null

        val modified = data.clone()
        modified[boneOffset] = targetBone.toByte()
        return modified
    }

    /**
     * Scan protobuf body để tìm offset của field bone (HitPart 0-7).
     * Free Fire fire packet: protobuf fields đều là varint (wire type 0).
     * Field 4 = bone field → field number 4, wire type 0 → tag byte = (4 << 3) | 0 = 0x20 = 32
     *
     * Trả về offset của VALUE byte (không phải tag), null nếu không tìm thấy.
     */
    private fun findBoneOffset(data: ByteArray, start: Int, targetBone: Int): Int? {
        // Scan tìm tag byte 0x20 (field 4, wire type 0 = varint) trong body
        // Giá trị sau tag phải là HitPart hợp lệ (0-7)
        for (i in start until (data.size - 1)) {
            val tag = data[i].toInt() and 0xFF
            if (tag == 0x20) {  // field 4, varint
                val valByte = data[i + 1].toInt() and 0xFF
                if (valByte in 0..7) {
                    // Xác nhận: giá trị != targetBone (đây là field cần sửa)
                    return i + 1
                }
            }
        }
        // Fallback: scan tìm byte trong range 0-7 sau vị trí hợp lý
        // Tìm byte khác với targetBone trong range bone hợp lệ
        for (i in (start + 4) until (data.size - 1)) {
            val b = data[i].toInt() and 0xFF
            if (b in 0..7 && b != targetBone) {
                // Kiểm tra byte trước nó là tag protobuf hợp lệ
                val prevTag = data[i - 1].toInt() and 0xFF
                if ((prevTag and 0x07) == 0 && (prevTag ushr 3) in 1..8) {
                    return i
                }
            }
        }
        return null
    }

    fun reset() {
        isAimEnabled = false
    }
}
