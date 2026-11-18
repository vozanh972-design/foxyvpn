package com.example.foxy.engine

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.Executors

/**
<<<<<<< HEAD
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
=======
 * Dịch 1:1 từ ModuleBridge.smali.
 * Không thêm bớt logic, không chế lại.
>>>>>>> 21efc3d4548df609773273e217983335c9b984a6
 */
object AimCombatEngine {

    // ── Khai báo hằng số cho UI (Để BoostPanel không báo lỗi)
    const val HITPART_HEAD = 0
    const val HITPART_NECK = 1
    const val HITPART_CHEST = 2
    const val HITPART_LEFT_FOOT = 12
    const val BONE_HEAD = HITPART_HEAD
    const val BONE_NECK = HITPART_NECK
    const val BONE_BODY = HITPART_CHEST
    const val BONE_FOOT = HITPART_LEFT_FOOT

<<<<<<< HEAD
    // Offsets Il2Cpp đã dịch ngược từ COW Assembly-CSharp:
    const val OFFSET_COLLIDER_INFO_HITBOX_TYPE = 0x18       // ColliderInfo.m_hitBoxType
    const val OFFSET_PLAYER_COLLIDER_LIST = 0x20            // PlayerColliderChecker.m_colliderInfoList
    const val OFFSET_CROSSHAIR_LAST_AIM_TARGET = 0x220      // UIHudCrosshairsController.m_LastAimTargetPlayerID
    const val OFFSET_CROSSHAIR_HIT_HEADSHOT = 0x128         // UIHudCrosshairsController.m_HitHintIsHeadShot
    const val OFFSET_CROSSHAIR_RECOIL_BINDING = 0x1B0       // UIHudCrosshairsController.m_RecoilCrosshair
    const val OFFSET_AIM_ASSIST_MAX_DISTANCE = 300f         // WeaponAimAssistAutoAbsorbData.max_distance
=======
    // ── URL cơ sở
    private const val BASE_URL = "http://127.0.0.1:7755"
    private const val ORIGIN = "http://127.0.0.1:7755"
    
    // ── Hàng đợi luồng (giống Executor trong FloatingService)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AimCombat-http").apply { isDaemon = true }
    }
>>>>>>> 21efc3d4548df609773273e217983335c9b984a6

    // ── Trạng thái lưu cho UI
    @Volatile var isAimEnabled: Boolean = false
        private set
    @Volatile var targetBone: Int = HITPART_HEAD
        private set

    /**
     * Hàm này gọi tương đương hàm ModuleBridge.set("AIMBOT HEAD", enabled)
     */
    fun configure(enabled: Boolean, bone: Int) {
        targetBone = bone
        isAimEnabled = enabled

        executor.execute {
            // Map bone UI sang Tên chức năng của Smali
            val featureName = when (bone) {
                HITPART_HEAD -> "AIMBOT HEAD"
                HITPART_NECK -> "DRAG HS"
                HITPART_CHEST, HITPART_LEFT_FOOT -> "AIMLOCK BODY"
                else -> "AIMBOT HEAD"
            }
            try {
                // Gọi thẳng hàm set của Smali
                set(featureName, enabled)
            } catch (e: Exception) {
                // Bỏ qua lỗi mạng nếu module chưa bật
            }
        }
    }

    /**
<<<<<<< HEAD
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

=======
     * Tương đương ModuleBridge.reset()
     */
>>>>>>> 21efc3d4548df609773273e217983335c9b984a6
    fun reset() {
        isAimEnabled = false
        executor.execute {
            try {
                resetInternal()
            } catch (e: Exception) {
                // Bỏ qua
            }
        }
    }

    /**
     * Tương đương ModuleBridge.process() (can thiệp UDP cũ - đã bỏ)
     */
    fun process(flowKey: String, data: ByteArray, dstPort: Int): ByteArray? = null

    // ═════════════════════════════════════════════════════════════════════
    // PHẦN DỊCH 1:1 TỪ ModuleBridge.smali BẮT ĐẦU TỪ ĐÂY
    // ═════════════════════════════════════════════════════════════════════

    // Tương đương ModuleBridge.set(String, boolean)
    @Throws(IOException::class)
    private fun set(feature: String, enable: Boolean) {
        when (feature) {
            "GHS", "AIMBOT HEAD" -> command(1, enable)
            "DRAG HS" -> command(5, enable)
            "AIMLOCK BODY" -> command(8, enable)
            "ESP LINE" -> command(2, enable)
            "ESP BOX" -> command(3, enable)
            "ESP NAME" -> command(6, enable)
            "ESP HEALTH" -> command(7, enable)
            "ESP SKELETON" -> command(9, enable)
            "ESP DISTANCE" -> command(10, enable)
            "AIM FOV" -> command(11, enable)
            "NO RECOIL" -> command(12, enable)
            "HIDE AIM FOV" -> command(13, enable)
            "FAST SWITCH" -> command(14, enable)
            "NO SPREAD" -> command(15, enable)
            else -> throw IOException("Fitur tidak dikenal: $feature")
        }
    }

    // Tương đương ModuleBridge.reset()
    @Throws(IOException::class)
    private fun resetInternal() {
        var firstException: IOException? = null
        for (i in 1..15) {
            try {
                command(i, false)
            } catch (e: IOException) {
                if (firstException == null) firstException = e
            }
        }
        try {
            setFov(35)
        } catch (e: IOException) {
            if (firstException == null) firstException = e
        }
        if (firstException != null) {
            throw firstException
        }
    }

    // Tương đương ModuleBridge.setFov(int)
    @Throws(IOException::class)
    private fun setFov(size: Int) {
        if (size in 1..100) {
            request("/fov/$size")
        } else {
            throw IOException("AIM FOV SIZE harus 1–100.")
        }
    }

    // Tương đương ModuleBridge.command(int, boolean)
    @Throws(IOException::class)
    private fun command(item: Int, enable: Boolean) {
        val path = if (enable) "/item$item" else "/item$item/desativar"
        request(path)
    }

    // Tương đương ModuleBridge.request(String)
    @Throws(IOException::class)
    private fun request(path: String) {
        val url = URL(BASE_URL + path)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.useCaches = false
        conn.doInput = true
        conn.instanceFollowRedirects = false
        conn.requestMethod = "GET"
        conn.setRequestProperty("Origin", ORIGIN)

        try {
            val responseCode = conn.responseCode
            if (responseCode in 200 until 300) {
                // Đọc dữ liệu ACK (giống smali đọc InputStream)
                val expectedAck = getAckForPath(path)
                if (expectedAck != null) {
                    val input = conn.inputStream
                    for (b in expectedAck) {
                        val r = input.read()
                        if (r != (b.toInt() and 0xFF)) {
                            throw IOException("Modul belum mendukung fitur ini.")
                        }
                    }
                    if (input.read() != -1) {
                        throw IOException("Respuesta tidak valid.")
                    }
                }
            } else {
                throw IOException("Modul menolak perintah (HTTP $responseCode).")
            }
        } finally {
            conn.disconnect()
        }
    }

    // Hàm phụ lấy ACK tương ứng với đường dẫn (dịch từ việc so sánh chuỗi trong smali)
    private fun getAckForPath(path: String): ByteArray? {
        return when {
            path.contains("/item1") -> reveal("Cz0v2cGajpWTqbxKQGATGywmzML+5/g=")
            path.contains("/item5") -> reveal("Cz0v2cGe5o6JrbpRUXUCGQ==")
            path.contains("/item8") -> reveal("Cz0v2cGajZWZo7lXQGIEBConzMmW")
            else -> null // Các item khác smali không check ACK chặt chẽ
        }
    }

    // Tương đương ModuleBridge.reveal(String)
    private fun reveal(encoded: String): ByteArray {
        val raw = Base64.getDecoder().decode(encoded)
        for (i in raw.indices) {
            val key = ((i * 0x11) + 0x53) and 0xFF
            raw[i] = (raw[i].toInt() xor key).toByte()
        }
        return raw
    }
}
