package com.example.foxy.engine

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * AimCombatEngine — Xử lý logic Ghim Tâm / Auto Headshot / Bone Targeting.
 * 
 * Ánh xạ chính xác từ ModuleBridge.smali:
 * - Item 1: AIMBOT HEAD  -> Đầu (Head)
 * - Item 5: DRAG HS      -> Cổ (Neck)
 * - Item 8: AIMLOCK BODY -> Thân/Ngực (Body/Chest)
 * 
 * (Module không có chức năng riêng cho Chân, nên nút "Chân" sẽ được map về Item 8)
 */
object AimCombatEngine {

    // ═════════════════════════════════════════════════════════════════════
    // HẰNG SỐ BONE (Giữ nguyên để BoostPanel.kt không bị lỗi)
    // ═════════════════════════════════════════════════════════════════════
    const val HITPART_HEAD = 0
    const val HITPART_NECK = 1
    const val HITPART_CHEST = 2
    const val HITPART_LEFT_FOOT = 12

    const val BONE_HEAD = HITPART_HEAD
    const val BONE_NECK = HITPART_NECK
    const val BONE_BODY = HITPART_CHEST
    const val BONE_FOOT = HITPART_LEFT_FOOT

    // ═════════════════════════════════════════════════════════════════════
    // MÃ ITEM CỦA MODULE (Khớp ModuleBridge.smali)
    // ═════════════════════════════════════════════════════════════════════
    private const val ITEM_AIM_HEAD = 1
    private const val ITEM_DRAG_HS = 5
    private const val ITEM_AIMLOCK_BODY = 8

    // Cấu hình HTTP
    private const val BASE_URL = "http://127.0.0.1:7755"
    private const val ORIGIN = "http://127.0.0.1:7755"

    // Trạng thái
    @Volatile var isAimEnabled: Boolean = false
        private set
    @Volatile var targetBone: Int = HITPART_HEAD
        private set

    // Hàng đợi luồng để gửi lệnh không bị nghẽn
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AimCombat-http").apply { isDaemon = true }
    }

    /**
     * Cấu hình chức năng Ghim (Gọi từ OverlayService).
     */
    fun configure(enabled: Boolean, bone: Int) {
        targetBone = bone
        isAimEnabled = enabled

        // Chuyển đổi Bone ID từ UI sang Item ID của Module
        val itemId = when (bone) {
            HITPART_HEAD -> ITEM_AIM_HEAD
            HITPART_NECK -> ITEM_DRAG_HS
            HITPART_CHEST -> ITEM_AIMLOCK_BODY
            HITPART_LEFT_FOOT -> ITEM_AIMLOCK_BODY // Module không có chân, dồn về bắn thân
            else -> ITEM_AIM_HEAD
        }

        executor.execute {
            if (enabled) {
                // Bật item được chọn
                httpGet("/item$itemId")
                
                // Tự động tắt các item aim khác để tránh xung đột
                if (itemId != ITEM_AIM_HEAD) httpGet("/item$ITEM_AIM_HEAD/desativar")
                if (itemId != ITEM_DRAG_HS) httpGet("/item$ITEM_DRAG_HS/desativar")
                if (itemId != ITEM_AIMLOCK_BODY) httpGet("/item$ITEM_AIMLOCK_BODY/desativar")
            } else {
                // Tắt tất cả các item aim
                httpGet("/item$ITEM_AIM_HEAD/desativar")
                httpGet("/item$ITEM_DRAG_HS/desativar")
                httpGet("/item$ITEM_AIMLOCK_BODY/desativar")
            }
        }
    }

    /**
     * Reset toàn bộ (Gọi khi tắt Service).
     */
    fun reset() {
        isAimEnabled = false
        targetBone = HITPART_HEAD
        executor.execute {
            httpGet("/item$ITEM_AIM_HEAD/desativar")
            httpGet("/item$ITEM_DRAG_HS/desativar")
            httpGet("/item$ITEM_AIMLOCK_BODY/desativar")
        }
    }

    /**
     * Hàm stub (giữ để không lỗi code cũ).
     */
    fun process(flowKey: String, data: ByteArray, dstPort: Int): ByteArray? = null

    /**
     * Hàm gửi HTTP GET.
     */
    private fun httpGet(path: String) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(BASE_URL + path)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Origin", ORIGIN)
            
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                conn.inputStream?.close()
            }
        } catch (e: Exception) {
            // Bỏ qua lỗi mạng
        } finally {
            conn?.disconnect()
        }
    }
}
