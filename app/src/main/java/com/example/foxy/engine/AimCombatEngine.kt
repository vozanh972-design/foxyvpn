package com.example.foxy.engine

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Executors

/**
 * AimCombatEngine — Port 1:1 từ ModuleBridge.smali + AimSync.smali của app STIRLAX MODZ.
 *
 * Toàn bộ giao tiếp với module native đi qua HTTP local:
 *   http://127.0.0.1:7755/item{N}            (bật item N)
 *   http://127.0.0.1:7755/item{N}/desativar  (tắt item N)
 *   http://127.0.0.1:7755/fov/{1..100}       (đặt FOV)
 *   http://127.0.0.1:7755/                   (probe)
 *
 * Không còn can thiệp UDP, không offset 0x24, không EspEntityStore/FreezeController.
 *
 * Ánh xạ bone UI (giữ tương thích BoostPanel.kt) → item module:
 *   HITPART_HEAD  (0)  → item 1  AIMBOT HEAD
 *   HITPART_NECK  (1)  → item 5  DRAG HS
 *   HITPART_CHEST (2)  → item 8  AIMLOCK BODY
 *   HITPART_LEFT_FOOT (12) → item 8 (module không có chân)
 */
object AimCombatEngine {

    // ═════════════════════════════════════════════════════════════════════
    // HẰNG BONE — giữ nguyên để BoostPanel.kt biên dịch được
    // ═════════════════════════════════════════════════════════════════════
    const val HITPART_HEAD = 0
    const val HITPART_NECK = 1
    const val HITPART_CHEST = 2
    const val HITPART_HIPS = 3
    const val HITPART_LEFT_ARM = 4
    const val HITPART_LEFT_FOREARM = 5
    const val HITPART_RIGHT_ARM = 6
    const val HITPART_RIGHT_FOREARM = 7
    const val HITPART_LEFT_UP_LEG = 8
    const val HITPART_LEFT_LEG = 9
    const val HITPART_RIGHT_UP_LEG = 10
    const val HITPART_RIGHT_LEG = 11
    const val HITPART_LEFT_FOOT = 12
    const val HITPART_RIGHT_FOOT = 13
    const val HITPART_WEAKPOINT = 19

    // Alias tương thích UI BoostPanel
    const val BONE_HEAD = HITPART_HEAD
    const val BONE_NECK = HITPART_NECK
    const val BONE_BODY = HITPART_CHEST
    const val BONE_FOOT = HITPART_LEFT_FOOT

    // ═════════════════════════════════════════════════════════════════════
    // MÃ ITEM MODULE (khớp ModuleBridge.set)
    // ═════════════════════════════════════════════════════════════════════
    private const val ITEM_AIM_HEAD = 1
    private const val ITEM_DRAG_HS = 5
    private const val ITEM_AIMLOCK_BODY = 8

    // ── HTTP config (khớp smali: timeout 3000 ms + Origin) ──────────────
    private const val BASE_URL = "http://127.0.0.1:7755"
    private const val ORIGIN = "http://127.0.0.1:7755"
    private const val CONNECT_TIMEOUT_MS = 3000
    private const val READ_TIMEOUT_MS = 3000

    // ── Chuỗi ACK đã mã hóa base64 (nguyên văn từ ModuleBridge.smali) ────
    private const val ACK_HEAD_B64 = "Cz0v2cGajpWTqbxKQGATGywmzML+5/g="
    private const val ACK_NECK_B64 = "Cz0v2cGe5o6JrbpRUXUCGQ=="
    private const val ACK_BODY_B64 = "Cz0v2cGajZWZo7lXQGIEBConzMmW"

    // ACK đã reveal (giải mã sẵn 1 lần lúc class load)
    private val ACK_HEAD: ByteArray by lazy { reveal(ACK_HEAD_B64) }
    private val ACK_NECK: ByteArray by lazy { reveal(ACK_NECK_B64) }
    private val ACK_BODY: ByteArray by lazy { reveal(ACK_BODY_B64) }

    // ── Trạng thái công khai cho UI ──────────────────────────────────────
    @Volatile var isAimEnabled: Boolean = false
        private set
    @Volatile var targetBone: Int = HITPART_HEAD
        private set

    // Single-thread executor giống FloatingService.worker — tránh 2 lệnh chồng nhau
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AimCombat-http").apply { isDaemon = true }
    }

    // ═════════════════════════════════════════════════════════════════════
    // API công khai — giữ chữ ký cũ để OverlayService.kt không phải sửa
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Cấu hình chế độ ghim, port từ AimSync.sync + ModuleBridge.set:
     *  - Đảm bảo độc quyền: bật 1 trong 3 aim thì gửi lệnh tắt 2 cái còn lại
     *  - Chỉ gửi lệnh cho item tương ứng với bone
     */
    fun configure(enabled: Boolean, bone: Int) {
        targetBone = bone
        isAimEnabled = enabled

        val item = boneToItem(bone)
        executor.execute {
            if (enabled) {
                // Độc quyền aim — giống AimSync.sync
                for (other in intArrayOf(ITEM_AIM_HEAD, ITEM_DRAG_HS, ITEM_AIMLOCK_BODY)) {
                    if (other != item) {
                        runCatching { setItem(other, false) }
                    }
                }
                runCatching { setItem(item, true) }
            } else {
                // Tắt cả 3 item aim
                for (it in intArrayOf(ITEM_AIM_HEAD, ITEM_DRAG_HS, ITEM_AIMLOCK_BODY)) {
                    runCatching { setItem(it, false) }
                }
            }
        }
    }

    /**
     * Reset toàn bộ — port từ ModuleBridge.reset():
     *  tắt item 1..15 rồi setFov(35).
     */
    fun reset() {
        isAimEnabled = false
        targetBone = HITPART_HEAD
        executor.execute {
            for (i in 1..15) {
                runCatching { command(i, false) }
            }
            runCatching { setFov(35) }
        }
    }

    /**
     * Probe module còn sống không — port từ ModuleBridge.check() (GET "/").
     */
    fun check(): Boolean {
        return runCatching { request("/") }.isSuccess
    }

    /**
     * Đặt FOV 1..100 — port từ ModuleBridge.setFov().
     */
    fun setFov(size: Int) {
        if (size !in 1..100) return
        executor.execute { runCatching { request("/fov/$size") } }
    }

    /**
     * Stub tương thích — logic UDP cũ đã bỏ hoàn toàn.
     */
    fun process(flowKey: String, data: ByteArray, dstPort: Int): ByteArray? = null

    // ═════════════════════════════════════════════════════════════════════
    // Nội bộ — port từ ModuleBridge
    // ═════════════════════════════════════════════════════════════════════

    /** Ánh xạ bone UI → item module. */
    private fun boneToItem(bone: Int): Int = when (bone) {
        HITPART_HEAD -> ITEM_AIM_HEAD
        HITPART_NECK -> ITEM_DRAG_HS
        HITPART_CHEST, HITPART_HIPS,
        HITPART_LEFT_FOOT, HITPART_RIGHT_FOOT,
        HITPART_LEFT_LEG, HITPART_RIGHT_LEG,
        HITPART_LEFT_UP_LEG, HITPART_RIGHT_UP_LEG -> ITEM_AIMLOCK_BODY
        else -> ITEM_AIM_HEAD
    }

    /**
     * Port ModuleBridge.command(item, enable):
     *   enable=true  → GET /itemN
     *   enable=false → GET /itemN/desativar
     * Sau đó kiểm tra ACK tương ứng.
     */
    private fun setItem(item: Int, enable: Boolean) {
        val path = if (enable) "/item$item" else "/item$item/desativar"
        val expectedAck = when (item) {
            ITEM_AIM_HEAD -> ACK_HEAD
            ITEM_DRAG_HS -> ACK_NECK
            ITEM_AIMLOCK_BODY -> ACK_BODY
            else -> return // item khác không kiểm ACK (ESP, FOV...) — app gốc cũng chỉ kiểm ACK cho 3 cái này
        }
        requestWithAck(path, expectedAck)
    }

    /** Wrapper command cho reset() — không kiểm ACK (giống smali chỉ catch IOException). */
    private fun command(item: Int, enable: Boolean) {
        val path = if (enable) "/item$item" else "/item$item/desativar"
        request(path)
    }

    /**
     * Port ModuleBridge.request(path): gửi GET, kiểm HTTP 2xx.
     */
    private fun request(path: String) {
        val conn = openConnection(path)
        try {
            val code = conn.responseCode
            if (code !in 200 until 300) {
                throw IOException("Modul menolak perintah (HTTP $code). Periksa izin modul.")
            }
            // Đọc hết stream để đóng socket sạch
            conn.inputStream?.use { drain(it) }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Port ModuleBridge.request + so khớp ACK byte-by-byte (giống smali).
     */
    private fun requestWithAck(path: String, expectedAck: ByteArray) {
        val conn = openConnection(path)
        try {
            val code = conn.responseCode
            if (code !in 200 until 300) {
                throw IOException("Modul menolak perintah (HTTP $code). Periksa izin modul.")
            }
            val input = conn.inputStream
                ?: throw IOException("Respuesta tidak valid. Inyeccion ulang file y luego mulai ulang game.")
            input.use { stream ->
                // So từng byte theo smali
                for (b in expectedAck) {
                    val r = stream.read()
                    if (r != (b.toInt() and 0xFF)) {
                        throw IOException("Modul belum mendukung fitur. Inyeccion ulang file y luego mulai ulang game.")
                    }
                }
                // Smali còn đọc thêm 1 byte, phải -1 (kết thúc)
                if (stream.read() != -1) {
                    throw IOException("Respuesta tidak valid. Inyeccion ulang file y luego mulai ulang game.")
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(path: String): HttpURLConnection {
        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.useCaches = false
        conn.doInput = true
        conn.instanceFollowRedirects = false
        conn.requestMethod = "GET"
        conn.setRequestProperty("Origin", ORIGIN)
        return conn
    }

    private fun drain(stream: InputStream) {
        val buf = ByteArray(256)
        while (stream.read(buf) != -1) { /* discard */ }
    }

    /**
     * Port ModuleBridge.reveal(): Base64 decode + XOR với (i*0x11 + 0x53) & 0xFF.
     * Dùng java.util.Base64 (API 26+) — nếu minSdk < 26 thay bằng android.util.Base64.
     */
    private fun reveal(encoded: String): ByteArray {
        val raw = Base64.getDecoder().decode(encoded)
        for (i in raw.indices) {
            val key = ((i * 0x11) + 0x53) and 0xFF
            raw[i] = (raw[i].toInt() xor key).toByte()
        }
        return raw
    }
}
