package com.example.foxy.engine

import kotlin.math.sqrt

/**
 * AimCombatEngine — Xử lý logic Ghim Tâm / Auto Headshot / Bone Targeting theo chuẩn ShinMod (04_aim_headshot_combat).
 *
 * Nguyên lý hoạt động từ ShinMod (libshinmod.so & libstub.so):
 * - Can thiệp gói tin UDP outbound (gửi từ game lên Garena Server).
 * - Sửa trường bone_id tại offset 0x24 (trong struct FF_Entity 108 bytes) thành bone mục tiêu:
 *     Bone 12 = Head (Đầu)
 *     Bone 11 = Neck (Cổ)
 *     Bone 7  = Spine Upper / Chest (Thân)
 *     Bone 44 = Left Foot (Chân) / Bone 48 = Right Foot
 * - Kết hợp định vị tọa độ mục tiêu gần nhất từ EspEntityStore để tối ưu hóa góc bắn.
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
    const val OFFSET_PLAYER_COLLIDER_LIST = 0x20           // PlayerColliderChecker.m_colliderInfoList
    const val OFFSET_CROSSHAIR_LAST_AIM_TARGET = 0x220     // UIHudCrosshairsController.m_LastAimTargetPlayerID
    const val OFFSET_CROSSHAIR_HIT_HEADSHOT = 0x128        // UIHudCrosshairsController.m_HitHintIsHeadShot
    const val OFFSET_CROSSHAIR_RECOIL_BINDING = 0x1B0      // UIHudCrosshairsController.m_RecoilCrosshair
    const val OFFSET_AIM_ASSIST_MAX_DISTANCE = 300f        // WeaponAimAssistAutoAbsorbData.max_distance

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
     * Trả về ByteArray đã được rewrite bone_id hoặc null nếu không cần chỉnh sửa.
     */
    fun process(flowKey: String, data: ByteArray, dstPort: Int): ByteArray? {
        // 1. Kiểm tra trạng thái kích hoạt, cổng và kích thước gói tin tối thiểu
        if (!isAimEnabled || dstPort == 53 || data.size < 40) return null

        // 2. Bảo vệ tuyệt đối sảnh chờ / loading: chỉ kích hoạt khi đã ở trong trận (ESP xác nhận localPlayer)
        val snapshot = EspEntityStore.snapshot()
        val local = snapshot.localPlayer ?: return null

        // 3. Chỉ can thiệp khi đang trong trạng thái giao tranh (nhận lệnh bắn command 104 hoặc magnet)
        val isFiring = FreezeController.isFiring() || NetworkFeatures.magnetActive()
        if (!isFiring) return null

        // 4. Tìm kiếm mục tiêu kẻ địch hợp lệ trong cự ly tác chiến (< 350m)
        val remotes = snapshot.entities
        var hasTargetInRange = false
        for (enemy in remotes) {
            if (enemy.id == local.id || enemy.dead || enemy.health <= 0) continue
            val dx = enemy.x - local.x
            val dy = enemy.y - local.y
            val dz = enemy.z - local.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)

            if (dist < 350f) {
                hasTargetInRange = true
                break
            }
        }
        if (!hasTargetInRange) return null

        // 5. Chuẩn ShinMod (04_aim_headshot_combat / FF_Entity struct 108 bytes):
        // Chỉ can thiệp đúng 1 trường bone_id tại offset 0x24 trong payload thực thể
        val headerSize = if ((data[0].toInt() and 0xFF) < 100) 10 else 8
        val boneOffset = headerSize + 0x24
        if (boneOffset >= data.size) return null

        val modified = data.clone()
        modified[boneOffset] = targetBone.toByte()
        return modified
    }

    fun reset() {
        isAimEnabled = false
        targetBone = BONE_HEAD
    }
}
