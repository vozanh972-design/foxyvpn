package com.example.foxy.engine

import kotlin.math.sqrt

/**
 * AimCombatEngine — Xử lý logic Ghim Tâm / Auto Headshot / Bone Targeting theo chuẩn ShinMod (04_aim_headshot_combat).
 *
 * Nguyên lý hoạt động từ ShinMod (libshinmod.so & libstub.so):
 * - Can thiệp gói tin UDP outbound (gửi từ game lên Garena Server).
 * - Sửa trường bone_id tại offset 0x24 (trong struct FF_Entity 108 bytes) thành bone mục tiêu:
 *     Bone 0  = Head (Đầu)   ← HITPART_HEAD (PlayerColliderChecker.HitPart)
 *     Bone 1  = Neck (Cổ)
 *     Bone 2  = Chest (Thân)
 *     Bone 12 = Left Foot (Chân)
 * - headerSize xác định chính xác từ data[0]: < 100 → 10 bytes, ≥ 100 → 8 bytes
 *   (cùng logic với UdpSessionParser.parse() và PoseFreezeEngine.rewritePosition())
 *
 * Điều kiện kích hoạt (theo AIMBOT HEAD logic từ Stirlax Modz):
 *   - isAimEnabled == true (CHẾ ĐỘ GHIM bật)
 *   - Đang bắn: command 104 đã được observe bởi FreezeController (isFiring)
 *     HOẶC NetworkFeatures.magnetActive()
 *   - Không cần localPlayer hay địch trong range → bone rewrite áp dụng ngay
 *     khi detect firing, giống hệt cách AIMBOT HEAD hoạt động qua module 7755.
 */
object AimCombatEngine {

    // ═════════════════════════════════════════════════════════════════════
    // PLAYER COLLIDER CHECKER & HITPART OFFSETS (Từ dump Core_Keo_Tam)
    // ═════════════════════════════════════════════════════════════════════
    // PlayerColliderChecker.HitPart enum (TypeDefIndex: 437):
    const val HITPART_HEAD = 0          // Head (Đầu)
    const val HITPART_NECK = 1          // Neck (Cổ)
    const val HITPART_CHEST = 2         // Chest (Ngực / Thân)
    const val HITPART_HIPS = 3          // Hips (Hông / Bụng)
    const val HITPART_LEFT_ARM = 4      // Cánh tay trái
    const val HITPART_LEFT_FOREARM = 5  // Cẳng tay trái
    const val HITPART_RIGHT_ARM = 6     // Cánh tay phải
    const val HITPART_RIGHT_FOREARM = 7 // Cẳng tay phải
    const val HITPART_LEFT_UP_LEG = 8   // Đùi trái
    const val HITPART_LEFT_LEG = 9      // Cẳng chân trái
    const val HITPART_RIGHT_UP_LEG = 10 // Đùi phải
    const val HITPART_RIGHT_LEG = 11    // Cẳng chân phải
    const val HITPART_LEFT_FOOT = 12    // Bàn chân trái
    const val HITPART_RIGHT_FOOT = 13   // Bàn chân phải
    const val HITPART_WEAKPOINT = 19    // WeakPoint (Điểm yếu chí mạng)

    // Alias tương thích UI BoostPanel
    const val BONE_HEAD = HITPART_HEAD
    const val BONE_NECK = HITPART_NECK
    const val BONE_BODY = HITPART_CHEST
    const val BONE_FOOT = HITPART_LEFT_FOOT

    // Offsets Il2Cpp đã dịch ngược từ COW Assembly-CSharp:
    const val OFFSET_COLLIDER_INFO_HITBOX_TYPE = 0x18       // ColliderInfo.m_hitBoxType
    const val OFFSET_PLAYER_COLLIDER_LIST = 0x20            // PlayerColliderChecker.m_colliderInfoList
    const val OFFSET_CROSSHAIR_LAST_AIM_TARGET = 0x220      // UIHudCrosshairsController.m_LastAimTargetPlayerID
    const val OFFSET_CROSSHAIR_HIT_HEADSHOT = 0x128         // UIHudCrosshairsController.m_HitHintIsHeadShot
    const val OFFSET_CROSSHAIR_RECOIL_BINDING = 0x1B0       // UIHudCrosshairsController.m_RecoilCrosshair
    const val OFFSET_AIM_ASSIST_MAX_DISTANCE = 300f         // WeaponAimAssistAutoAbsorbData.max_distance

    @Volatile var isAimEnabled: Boolean = false
    @Volatile var targetBone: Int = HITPART_HEAD

    /**
     * Cấu hình từ giao diện Chế độ ghim (BoostPanel).
     */
    fun configure(enabled: Boolean, bone: Int) {
        this.isAimEnabled = enabled
        this.targetBone = bone
    }

    /**
     * Xử lý gói tin outbound UDP từ client lên server.
     *
     * Logic theo đúng AIMBOT HEAD (Stirlax Modz / ModuleBridge item1):
     *   1. Guard: tính năng bật + không phải DNS + gói đủ lớn
     *   2. Guard: đang bắn (command 104 observe qua FreezeController) HOẶC magnet active
     *   3. Xác định headerSize đúng chuẩn (data[0] < 100 → 10, ≥ 100 → 8)
     *      — cùng logic UdpSessionParser & PoseFreezeEngine
     *   4. Rewrite byte tại [headerSize + 0x24] = targetBone
     *
     * Không cần localPlayer hay địch trong range: giống AIMBOT HEAD module,
     * bone rewrite áp dụng ngay trên mọi gói fire packet hợp lệ.
     *
     * Trả về ByteArray đã rewrite hoặc null nếu không cần chỉnh sửa.
     */
    fun process(flowKey: String, data: ByteArray, dstPort: Int): ByteArray? {
        // 1. Guard cơ bản: tính năng tắt / DNS passthrough / gói quá ngắn
        if (!isAimEnabled || dstPort == 53 || data.size < 40) return null

        // 2. Guard bắn: chỉ can thiệp khi đang thực sự bắn
        //    FreezeController.isFiring() = true khi đã nhận command 104 (fire start)
        //    và chưa nhận command 105 (fire stop), với hold window 700ms.
        //    NetworkFeatures.magnetActive() = fallback khi magnet bật + đang fire pulse.
        val isFiring = FreezeController.isFiring() || NetworkFeatures.magnetActive()
        if (!isFiring) return null

        // 3. Xác định headerSize theo cùng công thức UdpSessionParser / PoseFreezeEngine:
        //    data[0] in [90,99] → headerSize = 10
        //    data[0] in [100,109] → headerSize = 8
        val headerSize = if ((data[0].toInt() and 0xFF) < 100) 10 else 8
        val boneOffset = headerSize + 0x24
        if (boneOffset >= data.size) return null

        // 4. Rewrite bone_id tại offset 0x24 sau header
        //    (chuẩn ShinMod FF_Entity struct 108 bytes, field bone_id tại 0x24)
        val modified = data.clone()
        modified[boneOffset] = targetBone.toByte()
        return modified
    }

    fun reset() {
        isAimEnabled = false
        targetBone = BONE_HEAD
    }
}
