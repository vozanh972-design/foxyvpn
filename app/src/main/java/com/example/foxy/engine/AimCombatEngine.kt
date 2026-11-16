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

    @Volatile var isAimEnabled: Boolean = true
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
    fun process(flowKey: String, data: ByteArray): ByteArray? {
        if (!isAimEnabled || data.size < 16) return null

        // 1. Kiểm tra xem người chơi có đang thao tác bắn (command 104) hoặc magnet active
        val isFiring = NetworkFeatures.magnetActive() || FreezeController.isFreezeActive()

        // 2. Tìm kiếm mục tiêu kẻ địch gần nhất từ ESP để tính khoảng cách và xác thực mục tiêu
        val snapshot = EspEntityStore.snapshot()
        val local = snapshot.localPlayer
        val remotes = snapshot.entities

        var hasTargetInRange = false
        if (local != null) {
            for (enemy in remotes) {
                if (enemy.id == local.id || enemy.dead || enemy.health <= 0) continue
                val dx = enemy.x - local.x
                val dy = enemy.y - local.y
                val dz = enemy.z - local.z
                val dist = sqrt(dx * dx + dy * dy + dz * dz)

                if (dist < 350f) { // Trong cự ly tác chiến 350m
                    hasTargetInRange = true
                    break
                }
            }
        }

        // 3. Tiến hành can thiệp gói tin (Packet rewrite) theo format struct FF_Entity của ShinMod
        val headerSize = if ((data[0].toInt() and 0xFF) < 100) 10 else 8
        if (data.size <= headerSize + 0x25) return null

        var modified: ByteArray? = null

        // Các vị trí tiềm năng của trường bone_id trong payload shoot packet
        val possibleBoneOffsets = intArrayOf(headerSize + 0x24, headerSize + 0x1C, 0x24)

        for (offset in possibleBoneOffsets) {
            if (offset < data.size) {
                if (modified == null) {
                    modified = data.clone()
                }
                // Ghi đè bone_id thành bone được chọn (Neck = 11, Body = 7, Foot = 44)
                modified[offset] = targetBone.toByte()
            }
        }

        return modified
    }

    fun reset() {
        isAimEnabled = false
        targetBone = BONE_NECK
    }
}
