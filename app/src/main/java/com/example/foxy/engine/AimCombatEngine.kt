package com.example.foxy.engine

import java.util.concurrent.Executors

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

        // Guard 2: chỉ hoạt động khi INGAME (phase detector)
        if (!GamePhaseDetector.isIngame()) return null

        // Guard 3: đang bắn — CMD 104 observe (FreezeController) HOẶC magnet
        val isFiring = FreezeController.isFiring() || NetworkFeatures.magnetActive()
        if (!isFiring) return null

        // Guard 4: xác định headerSize từ data[0] thật
        // Logic từ UdpSessionParser + Pigboy original:
        //   data[0] < 100 → headerSize = 10 (long header)
        //   data[0] >= 100 → headerSize = 8 (short header)
        val firstByte = data[0].toInt() and 0xFF
        val headerSize = if (firstByte < 100) FoxyPacketConstants.HEADER_SIZE_LONG
                         else                 FoxyPacketConstants.HEADER_SIZE_SHORT

        // Guard 5: offset bone phải nằm trong bounds của packet
        val boneOffset = headerSize + BONE_OFFSET_IN_PAYLOAD
        if (boneOffset >= data.size) return null

        // Rewrite bone field → targetBone (HitPart enum value thật)
        // Chỉ rewrite nếu giá trị hiện tại khác (tránh rewrite không cần thiết)
        val currentBone = data[boneOffset].toInt() and 0xFF
        if (currentBone == targetBone) return null  // Đã đúng rồi, không cần sửa

        val modified = data.clone()
        modified[boneOffset] = targetBone.toByte()
        return modified
    }

    fun reset() {
        isAimEnabled = false
    }
}
