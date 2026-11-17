package com.example.foxy.engine

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AimCombatEngine — Điều khiển module aim native qua cầu HTTP local.
 *
 * Cơ chế mới (khớp ModuleBridge.smali của module STIRLAX / 04_aim_headshot_combat):
 * - Module native lắng nghe HTTP tại http://127.0.0.1:7755 và tự can thiệp vào
 *   quá trình ngắm/bắn bên trong tiến trình game.
 * - Kotlin chỉ cần gửi lệnh GET theo đúng endpoint mà module quy ước:
 *     /item1            → bật  AIMBOT HEAD
 *     /item1/desativar  → tắt  AIMBOT HEAD
 *     /item5            → bật  DRAG HS (kéo đầu)
 *     /item5/desativar  → tắt  DRAG HS
 *     /item8            → bật  AIMLOCK BODY (thân)
 *     /item8/desativar  → tắt  AIMLOCK BODY
 *     /item11           → bật  AIM FOV
 *     /item12           → bật  NO RECOIL
 *     /item13           → bật  HIDE AIM FOV
 *     /item15           → bật  NO SPREAD
 *     /fov/{1..100}     → đặt kích thước FOV
 *     /                 → probe xem module còn sống không
 *
 * Module mới KHÔNG có bone Neck / Foot riêng. Để UI cũ (4 lựa chọn
 * Đầu / Cổ / Thân / Chân) vẫn dùng được mà không phải sửa BoostPanel.kt:
 *   - Đầu  (HITPART_HEAD  = 0)  → item 1  (AIMBOT HEAD)
 *   - Cổ   (HITPART_NECK  = 1)  → item 5  (DRAG HS)
 *   - Thân (HITPART_CHEST = 2)  → item 8  (AIMLOCK BODY)
 *   - Chân (HITPART_LEFT_FOOT = 12) → item 8 (module không có chân → dồn về thân)
 *
 * Lớp này KHÔNG gọi sang bất kỳ file Kotlin nào khác. Chỉ dùng HTTP.
 */
object AimCombatEngine {

    // ═════════════════════════════════════════════════════════════════════
    // HẰNG BONE CŨ — giữ nguyên để BoostPanel.kt không phải sửa
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
    // MÃ ITEM CỦA MODULE MỚI (khớp ModuleBridge.smali)
    // ═════════════════════════════════════════════════════════════════════
    const val ITEM_AIM_HEAD = 1
    const val ITEM_DRAG_HS = 5
    const val ITEM_AIMLOCK_BODY = 8
    const val ITEM_AIM_FOV = 11
    const val ITEM_NO_RECOIL = 12
    const val ITEM_HIDE_AIM_FOV = 13
    const val ITEM_NO_SPREAD = 15

    // ═════════════════════════════════════════════════════════════════════
    // CẤU HÌNH HTTP (khớp smali: Origin header + timeout 3000ms)
    // ═════════════════════════════════════════════════════════════════════
    private const val BASE_URL = "http://127.0.0.1:7755"
    private const val ORIGIN = "http://127.0.0.1:7755"
    private const val CONNECT_TIMEOUT_MS = 3000
    private const val READ_TIMEOUT_MS = 3000
    private const val STATUS_PATH = "/xyz/fov3-status"
    private const val MAX_STATUS_LEN = 128

    /** Regex trạng thái FOV — khớp smali: "XYZFOV3|[0-9]{1,2}|[0-9]{1,4}|..." */
    private val FOV_STATUS_REGEX = Regex("^XYZFOV3\\|[0-9]{1,2}\\|[0-9]{1,4}\\|[0-9]{1,4}\\|[0-9]{1,4}\\|[01]\\|[01]\\|[01]$")

    /** Thông điệp trạng thái FOV, index khớp mã status từ module. */
    private val FOV_STATUS_LABELS = arrayOf(
        "FOV tắt",
        "chọn chế độ aim",
        "chưa có người chơi",
        "chưa có camera",
        "chưa có snapshot",
        "đang tìm mục tiêu",
        "mục tiêu sẵn sàng",
        "đã đọc lệnh bắn",
        "đã gửi lệnh",
        "lỗi module",
    )

    // ═════════════════════════════════════════════════════════════════════
    // TRẠNG THÁI CÔNG KHAI (UI đọc trực tiếp)
    // ═════════════════════════════════════════════════════════════════════
    @Volatile var isAimEnabled: Boolean = false
        private set

    /** Bone "logic" cũ (0/1/2/12) — ánh xạ sang item module khi gửi lệnh. */
    @Volatile var targetBone: Int = HITPART_HEAD
        private set

    /** Module có đang phản hồi không (được cập nhật bởi [check]). */
    @Volatile var moduleLinked: Boolean = false
        private set

    // Hàng đợi tuần tự, tránh 2 lệnh HTTP chồng nhau làm module xử lý sai thứ tự.
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AimCombat-http").apply { isDaemon = true }
    }

    private val busy = AtomicBoolean(false)

    // ═════════════════════════════════════════════════════════════════════
    // API CÔNG KHAI — giữ chữ ký cũ để 3 file kia không phải sửa
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Cấu hình chế độ ghim (gọi từ OverlayService.onBoostChange).
     *
     * [enabled] true → bật đúng 1 item aim theo [bone]; false → tắt hết 3 item aim.
     * [bone] nhận HITPART_HEAD / HITPART_NECK / HITPART_CHEST / HITPART_LEFT_FOOT
     *        (các giá trị khác cũng được, tự map về item gần nhất).
     */
    fun configure(enabled: Boolean, bone: Int) {
        targetBone = bone
        isAimEnabled = enabled
        val item = boneToItem(bone)
        executor.execute {
            if (enabled) {
                // Module dùng 3 aim độc quyền — tắt 2 cái còn lại trước rồi mới bật cái mới.
                for (i in intArrayOf(ITEM_AIM_HEAD, ITEM_DRAG_HS, ITEM_AIMLOCK_BODY)) {
                    if (i != item) commandQuiet(i, false)
                }
                commandQuiet(item, true)
            } else {
                for (i in intArrayOf(ITEM_AIM_HEAD, ITEM_DRAG_HS, ITEM_AIMLOCK_BODY)) {
                    commandQuiet(i, false)
                }
            }
        }
    }

    /** Probe module còn sống không (tương đương ModuleBridge.check()). */
    fun check(): Boolean {
        val ok = httpGet("/")
        moduleLinked = ok
        return ok
    }

    /** Bật/tắt một item bất kỳ của module (dùng cho các tính năng phụ nếu cần). */
    fun setItem(item: Int, on: Boolean) {
        executor.execute { commandQuiet(item, on) }
    }

    /** Đặt kích thước FOV 1..100 (tương đương ModuleBridge.setFov()). */
    fun setFov(size: Int) {
        if (size !in 1..100) return
        executor.execute { httpGet("/fov/$size") }
    }

    /**
     * Đọc trạng thái FOV từ module. Trả về null nếu:
     * - HTTP lỗi / module chưa sẵn sàng,
     * - chuỗi trả về không khớp regex,
     * - bất kỳ giá trị nào vượt biên (giống validate trong smali).
     */
    fun fovStatus(): String? {
        val raw = httpGetText(STATUS_PATH, MAX_STATUS_LEN) ?: return null
        val trimmed = raw.trim()
        if (!FOV_STATUS_REGEX.matches(trimmed)) return null
        val parts = trimmed.split('|')
        if (parts.size != 8) return null
        val mode = parts[1].toIntOrNull() ?: return null
        val players = parts[2].toIntOrNull() ?: return null
        val valid = parts[3].toIntOrNull() ?: return null
        val inFov = parts[4].toIntOrNull() ?: return null
        if (mode > 9 || players > 4096 || valid > 4096 || inFov > 4096) return null
        val fired10s = parts[5] == "1"
        val camSent = parts[6] == "1"
        val hasError = parts[7] == "1"
        val modeLabel = FOV_STATUS_LABELS.getOrElse(mode) { "không rõ" }
        return buildString {
            append("FOV: ").append(modeLabel)
            append(" | Người chơi ").append(players)
            append(", hợp lệ ").append(valid)
            append(", trong FOV ").append(inFov)
            append("\nBắn 10 giây: ").append(if (fired10s) "CÓ" else "KHÔNG")
            append(" | Camera đã gửi: ").append(if (camSent) "CÓ" else "KHÔNG")
            append(" | Lỗi: ").append(if (hasError) "CÓ" else "KHÔNG")
        }
    }

    /**
     * Reset toàn bộ module (tương đương ModuleBridge.reset()):
     * tắt item 1..15 rồi đặt FOV về 35.
     */
    fun reset() {
        isAimEnabled = false
        targetBone = HITPART_HEAD
        executor.execute {
            for (i in 1..15) commandQuiet(i, false)
            httpGet("/fov/35")
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // STUB TƯƠNG THÍCH — logic UDP cũ đã bị loại bỏ hoàn toàn.
    // Giữ chữ ký để call-site cũ (VPN loop) vẫn biên dịch được.
    // Luôn trả null = không can thiệp gói tin.
    // ═════════════════════════════════════════════════════════════════════
    fun process(flowKey: String, data: ByteArray, dstPort: Int): ByteArray? = null

    // ═════════════════════════════════════════════════════════════════════
    // NỘI BỘ
    // ═════════════════════════════════════════════════════════════════════

    /** Ánh xạ bone UI cũ sang item module mới. */
    private fun boneToItem(bone: Int): Int = when (bone) {
        HITPART_HEAD -> ITEM_AIM_HEAD
        HITPART_NECK -> ITEM_DRAG_HS
        HITPART_CHEST, HITPART_HIPS,
        HITPART_LEFT_FOOT, HITPART_RIGHT_FOOT,
        HITPART_LEFT_LEG, HITPART_RIGHT_LEG,
        HITPART_LEFT_UP_LEG, HITPART_RIGHT_UP_LEG -> ITEM_AIMLOCK_BODY
        else -> ITEM_AIM_HEAD
    }

    /** Gửi lệnh bật/tắt item, bỏ qua lỗi để không làm chết executor. */
    private fun commandQuiet(item: Int, on: Boolean) {
        val path = if (on) "/item$item" else "/item$item/desativar"
        httpGet(path)
    }

    /** GET đơn giản, trả true nếu HTTP 2xx. */
    private fun httpGet(path: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                doInput = true
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Origin", ORIGIN)
            }
            val code = conn.responseCode
            // Drain body để đóng socket sạch, dù không dùng.
            runCatching { conn.inputStream?.use { it.readBytes() } }
            code in 200..299
        } catch (_: IOException) {
            false
        } catch (_: Exception) {
            false
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** GET đọc text (dùng cho fovStatus), giới hạn [maxBytes] giống smali (128 byte). */
    private fun httpGetText(path: String, maxBytes: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                doInput = true
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Origin", ORIGIN)
            }
            if (conn.responseCode != 200) {
                return null
            }
            val input = conn.inputStream ?: return null
            val buf = ByteArray(maxBytes)
            var len = 0
            input.use { stream ->
                while (len < maxBytes) {
                    val b = stream.read()
                    if (b == -1) break
                    buf[len++] = b.toByte()
                }
            }
            String(buf, 0, len, StandardCharsets.US_ASCII)
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
