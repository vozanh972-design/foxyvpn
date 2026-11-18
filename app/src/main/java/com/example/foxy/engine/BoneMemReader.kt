package com.example.foxy.engine

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * BoneMemReader — Đọc bone transform trực tiếp từ /proc/PID/mem
 *
 * Từ memory dump ingame (2026-11-18, PID 10237, map Bermuda):
 *   - Mỗi player có 59 bones, mỗi bone = 40 bytes (pos 12B + rot 16B + scale 12B)
 *   - Player clusters cách nhau 0x2990 (10640) bytes
 *   - bone[0]  = bone_Root   → vị trí player (world X,Y,Z)
 *   - bone[8]  = bone_Head   → đầu
 *   - bone[7]  = bone_Neck
 *   - bone[5]  = bone_Spine1 → ngực
 *   - bone[4]  = bone_Spine  → bụng
 *   - bone[1]  = bone_Hips   → hông/pelvis
 *   - bone[12] = bone_LeftClav  → vai trái
 *   - bone[17] = bone_RightClav → vai phải
 *   - bone[13] = bone_LeftArm   → khuỷu tay trái
 *   - bone[18] = bone_RightArm  → khuỷu tay phải
 *   - bone[14] = bone_LeftForeArm
 *   - bone[19] = bone_RightForeArm
 *   - bone[15] = bone_LeftHand  → tay trái
 *   - bone[20] = bone_RightHand → tay phải
 *   - bone[2]  = bone_LeftLegUpper → đùi trái
 *   - bone[3]  = bone_RightLegUpper → đùi phải
 *   - bone[6]  = bone_LeftLeg   → gối trái
 *   - bone[9]  = bone_RightLeg  → gối phải
 *   - bone[10] = bone_LeftAnkle → mắt cá trái
 *   - bone[11] = bone_RightAnkle → mắt cá phải
 *
 * Struct (40 bytes):
 *   float pos_x  (+0x00)
 *   float pos_y  (+0x04)
 *   float pos_z  (+0x08)
 *   float rot_x  (+0x0C)
 *   float rot_y  (+0x10)
 *   float rot_z  (+0x14)
 *   float rot_w  (+0x18)
 *   float scale_x (+0x1C)
 *   float scale_y (+0x20)
 *   float scale_z (+0x24)
 */
object BoneMemReader {

    // Bone index theo thứ tự trong array (xác nhận từ memory dump)
    const val BONE_ROOT         = 0
    const val BONE_HIPS         = 1
    const val BONE_LEFT_LEG_UP  = 2
    const val BONE_RIGHT_LEG_UP = 3
    const val BONE_SPINE        = 4
    const val BONE_SPINE1       = 5
    const val BONE_LEFT_LEG     = 6
    const val BONE_NECK         = 7
    const val BONE_HEAD         = 8
    const val BONE_RIGHT_LEG    = 9
    const val BONE_LEFT_ANKLE   = 10
    const val BONE_RIGHT_ANKLE  = 11
    const val BONE_LEFT_CLAV    = 12
    const val BONE_LEFT_ARM     = 13
    const val BONE_LEFT_FORE    = 14
    const val BONE_LEFT_HAND    = 15
    const val BONE_RIGHT_CLAV   = 17
    const val BONE_RIGHT_ARM    = 18
    const val BONE_RIGHT_FORE   = 19
    const val BONE_RIGHT_HAND   = 20

    // Struct size
    private const val BONE_STRUCT_SIZE = 40  // bytes per bone transform
    private const val BONES_PER_PLAYER = 59
    private const val BYTES_PER_PLAYER = BONE_STRUCT_SIZE * BONES_PER_PLAYER  // 2360 bytes

    // State
    @Volatile private var gamePid = -1
    @Volatile private var memFile: RandomAccessFile? = null
    private val lastOpenAttempt = AtomicLong(0L)

    // Cache: entityId → BoneSkeleton
    private val cache = ConcurrentHashMap<Int, BoneSkeleton>()

    // Buffer tái sử dụng (không allocate mỗi frame)
    private val readBuf = ByteArray(BYTES_PER_PLAYER)
    private val bbuf = ByteBuffer.wrap(readBuf).order(ByteOrder.LITTLE_ENDIAN)

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Cập nhật PID game — gọi khi phát hiện PID mới (từ VPN engine).
     * Reset mem handle nếu PID thay đổi.
     */
    fun setGamePid(pid: Int) {
        if (pid == gamePid) return
        gamePid = pid
        closeMemFile()
        cache.clear()
    }

    /**
     * Lấy skeleton của entity theo base address của cluster bone.
     * Trả về null nếu không đọc được (mem không có, PID sai, v.v.)
     *
     * @param clusterBaseAddr  địa chỉ của bone[0] (bone_Root) trong /proc/PID/mem
     */
    fun readSkeleton(clusterBaseAddr: Long): BoneSkeleton? {
        val mem = getMemFile() ?: return null
        return try {
            synchronized(this) {
                mem.seek(clusterBaseAddr)
                val n = mem.read(readBuf, 0, BYTES_PER_PLAYER)
                if (n < BYTES_PER_PLAYER) return null
                parseSkeleton(readBuf)
            }
        } catch (e: Exception) {
            closeMemFile()
            null
        }
    }

    /**
     * Scan vùng heap để tìm player skeleton clusters.
     * Trả về list địa chỉ base của các clusters tìm được.
     *
     * Logic: tìm 59 transforms liên tiếp hợp lệ (pos có world coords Bermuda,
     *        rot là unit quaternion, scale ~1).
     *
     * @param scanStart   địa chỉ bắt đầu (hex long)
     * @param scanSizeBytes  số bytes cần scan
     */
    fun scanForSkeletons(scanStart: Long, scanSizeBytes: Int): List<Long> {
        val mem = getMemFile() ?: return emptyList()
        val results = mutableListOf<Long>()
        val chunkSize = 4096
        val chunk = ByteArray(chunkSize + BYTES_PER_PLAYER)
        val cb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)

        try {
            var offset = 0
            while (offset < scanSizeBytes) {
                val readLen = minOf(chunkSize, scanSizeBytes - offset)
                synchronized(this) {
                    mem.seek(scanStart + offset)
                    mem.read(chunk, 0, readLen + BYTES_PER_PLAYER.coerceAtMost(scanSizeBytes - offset - readLen))
                }
                // Scan từng float offset trong chunk
                var i = 0
                while (i + BYTES_PER_PLAYER <= readLen) {
                    cb.position(i)
                    if (isValidPlayerSkeleton(cb)) {
                        results += scanStart + offset + i
                        i += BYTES_PER_PLAYER  // skip toàn bộ cluster
                    } else {
                        i += 4  // step 4 bytes (float alignment)
                    }
                }
                offset += readLen
            }
        } catch (e: Exception) {
            closeMemFile()
        }
        return results
    }

    // ─── Skeleton parse ───────────────────────────────────────────────────────

    private fun parseSkeleton(buf: ByteArray): BoneSkeleton {
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val positions = Array(BONES_PER_PLAYER) { i ->
            val off = i * BONE_STRUCT_SIZE
            bb.position(off)
            floatArrayOf(bb.float, bb.float, bb.float)  // X, Y, Z
        }
        return BoneSkeleton(positions)
    }

    private fun isValidPlayerSkeleton(bb: ByteBuffer): Boolean {
        // Kiểm tra bone[0] (bone_Root) có world coords hợp lệ
        val startPos = bb.position()
        val x = bb.float; val y = bb.float; val z = bb.float
        // World coords Bermuda: X/Z trong [-2000..2000], Y trong [-10..200]
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return false
        if (x < -2000f || x > 2000f || z < -2000f || z > 2000f) return false
        if (y < -10f || y > 200f) return false

        // Kiểm tra rot là unit quaternion (tổng bình phương ~1)
        val rx = bb.float; val ry = bb.float; val rz = bb.float; val rw = bb.float
        val rotLen = rx*rx + ry*ry + rz*rz + rw*rw
        if (rotLen < 0.5f || rotLen > 1.5f) return false

        // Kiểm tra scale ~1 (0.01..10)
        val sx = bb.float; val sy = bb.float; val sz = bb.float
        if (sx < 0.01f || sx > 10f || sy < 0.01f || sy > 10f || sz < 0.01f || sz > 10f) return false

        // Kiểm tra ít nhất 5 bones tiếp theo đều hợp lệ
        for (i in 1..5) {
            val off = startPos + i * BONE_STRUCT_SIZE
            if (off + 12 > bb.capacity()) return false
            bb.position(off)
            val bx = bb.float; val by = bb.float; val bz = bb.float
            if (!bx.isFinite() || !by.isFinite() || !bz.isFinite()) return false
        }
        return true
    }

    // ─── Mem file ─────────────────────────────────────────────────────────────

    private fun getMemFile(): RandomAccessFile? {
        memFile?.let { return it }
        val pid = gamePid
        if (pid <= 0) return null
        val now = System.currentTimeMillis()
        // Không thử mở lại quá thường xuyên (tránh spam)
        if (now - lastOpenAttempt.get() < 2000L) return null
        lastOpenAttempt.set(now)
        return try {
            RandomAccessFile("/proc/$pid/mem", "r").also { memFile = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun closeMemFile() {
        try { memFile?.close() } catch (_: Exception) {}
        memFile = null
    }

    fun reset() {
        closeMemFile()
        cache.clear()
        gamePid = -1
    }
}

/**
 * BoneSkeleton — Snapshot của 59 bone positions cho 1 player.
 * Sử dụng trong EspHudView để project các điểm skeleton lên màn hình.
 */
data class BoneSkeleton(
    val positions: Array<FloatArray>  // [boneIndex][0=X, 1=Y, 2=Z]
) {
    fun bonePos(idx: Int): FloatArray? =
        positions.getOrNull(idx)?.takeIf { it[0].isFinite() && it[1].isFinite() && it[2].isFinite() }

    // Convenience getters cho các bone quan trọng
    val head:       FloatArray? get() = bonePos(BoneMemReader.BONE_HEAD)
    val neck:       FloatArray? get() = bonePos(BoneMemReader.BONE_NECK)
    val spine1:     FloatArray? get() = bonePos(BoneMemReader.BONE_SPINE1)   // ngực
    val spine:      FloatArray? get() = bonePos(BoneMemReader.BONE_SPINE)    // bụng
    val hips:       FloatArray? get() = bonePos(BoneMemReader.BONE_HIPS)     // hông
    val leftClav:   FloatArray? get() = bonePos(BoneMemReader.BONE_LEFT_CLAV)
    val rightClav:  FloatArray? get() = bonePos(BoneMemReader.BONE_RIGHT_CLAV)
    val leftHand:   FloatArray? get() = bonePos(BoneMemReader.BONE_LEFT_HAND)
    val rightHand:  FloatArray? get() = bonePos(BoneMemReader.BONE_RIGHT_HAND)
    val leftAnkle:  FloatArray? get() = bonePos(BoneMemReader.BONE_LEFT_ANKLE)
    val rightAnkle: FloatArray? get() = bonePos(BoneMemReader.BONE_RIGHT_ANKLE)
    val root:       FloatArray? get() = bonePos(BoneMemReader.BONE_ROOT)

    // Equality check theo nội dung array
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BoneSkeleton) return false
        return positions.contentDeepEquals(other.positions)
    }
    override fun hashCode() = positions.contentDeepHashCode()
}
