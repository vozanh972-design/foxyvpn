package com.example.foxy.engine

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Executors

/**
 * AimCombatEngine — Port logic từ ModuleBridge.smali của STIRLAX MODZ.
 * 
 * Hoạt động độc lập: Bật nút nào chỉ gửi lệnh cho item đó.
 * Không tự động tắt các item khác (giống hệt giao diện gốc trong ảnh).
 */
object AimCombatEngine {

    // ── Hằng bone UI (Giữ nguyên để BoostPanel không lỗi)
    const val HITPART_HEAD = 0
    const val HITPART_NECK = 1
    const val HITPART_CHEST = 2
    const val HITPART_LEFT_FOOT = 12

    const val BONE_HEAD = HITPART_HEAD
    const val BONE_NECK = HITPART_NECK
    const val BONE_BODY = HITPART_CHEST
    const val BONE_FOOT = HITPART_LEFT_FOOT

    // ── Mã Item của Module
    private const val ITEM_AIM_HEAD = 1
    private const val ITEM_DRAG_HS = 5
    private const val ITEM_AIMLOCK_BODY = 8

    // ── Cấu hình HTTP
    private const val BASE_URL = "http://127.0.0.1:7755"
    private const val ORIGIN = "http://127.0.0.1:7755"
    private const val CONNECT_TIMEOUT_MS = 3000
    private const val READ_TIMEOUT_MS = 3000

    // ── Chuỗi ACK (Nguyên văn từ ModuleBridge.smali)
    private const val ACK_HEAD_B64 = "Cz0v2cGajpWTqbxKQGATGywmzML+5/g="
    private const val ACK_NECK_B64 = "Cz0v2cGe5o6JrbpRUXUCGQ=="
    private const val ACK_BODY_B64 = "Cz0v2cGajZWZo7lXQGIEBConzMmW"

    private val ACK_HEAD: ByteArray by lazy { reveal(ACK_HEAD_B64) }
    private val ACK_NECK: ByteArray by lazy { reveal(ACK_NECK_B64) }
    private val ACK_BODY: ByteArray by lazy { reveal(ACK_BODY_B64) }

    // ── Trạng thái công khai
    @Volatile var isAimEnabled: Boolean = false
        private set
    @Volatile var targetBone: Int = HITPART_HEAD
        private set

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AimCombat-http").apply { isDaemon = true }
    }

    /**
     * Cấu hình chế độ ghim.
     * Chỉ bật/tắt đúng item của bone được chọn, không ảnh hưởng các item khác.
     */
    fun configure(enabled: Boolean, bone: Int) {
        targetBone = bone
        isAimEnabled = enabled

        val item = boneToItem(bone)
        executor.execute {
            if (enabled) {
                runCatching { setItem(item, true) }
            } else {
                runCatching { setItem(item, false) }
            }
        }
    }

    /**
     * Reset toàn bộ — port từ ModuleBridge.reset()
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
     * Stub tương thích (không can thiệp UDP)
     */
    fun process(flowKey: String, data: ByteArray, dstPort: Int): ByteArray? = null

    // ═════════════════════════════════════════════════════════════════════
    // Nội bộ
    // ═════════════════════════════════════════════════════════════════════

    private fun boneToItem(bone: Int): Int = when (bone) {
        HITPART_HEAD -> ITEM_AIM_HEAD
        HITPART_NECK -> ITEM_DRAG_HS
        HITPART_CHEST, HITPART_LEFT_FOOT -> ITEM_AIMLOCK_BODY
        else -> ITEM_AIM_HEAD
    }

    private fun setItem(item: Int, enable: Boolean) {
        val path = if (enable) "/item$item" else "/item$item/desativar"
        val expectedAck = when (item) {
            ITEM_AIM_HEAD -> ACK_HEAD
            ITEM_DRAG_HS -> ACK_NECK
            ITEM_AIMLOCK_BODY -> ACK_BODY
            else -> return
        }
        requestWithAck(path, expectedAck)
    }

    private fun command(item: Int, enable: Boolean) {
        val path = if (enable) "/item$item" else "/item$item/desativar"
        request(path)
    }

    private fun request(path: String) {
        val conn = openConnection(path)
        try {
            val code = conn.responseCode
            if (code !in 200 until 300) {
                throw IOException("Modul menolak perintah (HTTP $code).")
            }
            conn.inputStream?.use { drain(it) }
        } finally {
            conn.disconnect()
        }
    }

    private fun requestWithAck(path: String, expectedAck: ByteArray) {
        val conn = openConnection(path)
        try {
            val code = conn.responseCode
            if (code !in 200 until 300) {
                throw IOException("Modul menolak perintah (HTTP $code).")
            }
            val input = conn.inputStream ?: throw IOException("Respuesta tidak valid.")
            input.use { stream ->
                for (b in expectedAck) {
                    val r = stream.read()
                    if (r != (b.toInt() and 0xFF)) {
                        throw IOException("Modul belum mendukung fitur. Inyeccion ulang file.")
                    }
                }
                if (stream.read() != -1) {
                    throw IOException("Respuesta tidak valid.")
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

    private fun reveal(encoded: String): ByteArray {
        val raw = Base64.getDecoder().decode(encoded)
        for (i in raw.indices) {
            val key = ((i * 0x11) + 0x53) and 0xFF
            raw[i] = (raw[i].toInt() xor key).toByte()
        }
        return raw
    }
}
