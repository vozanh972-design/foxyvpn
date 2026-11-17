package com.example.foxy.engine

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.Executors

/**
 * Dịch 1:1 từ ModuleBridge.smali.
 * Không thêm bớt logic, không chế lại.
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

    // ── URL cơ sở
    private const val BASE_URL = "http://127.0.0.1:7755"
    private const val ORIGIN = "http://127.0.0.1:7755"
    
    // ── Hàng đợi luồng (giống Executor trong FloatingService)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AimCombat-http").apply { isDaemon = true }
    }

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
     * Tương đương ModuleBridge.reset()
     */
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
