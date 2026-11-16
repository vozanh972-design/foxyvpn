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

    const val BONE_HEAD = 12
    const val BONE_NECK = 11
    const val BONE_BODY = 7
    const val BONE_FOOT = 44

    @Volatile var isAimEnabled: Boolean = false
    @Volatile var targetBone: Int = BONE_NECK

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
        targetBone = BONE_NECK
    }
}
