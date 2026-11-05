package com.example.foxy.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Lưu key đã kích hoạt và quản lý chữ ký thiết bị duy nhất trọn đời (Hardware Fingerprint).
 * Chữ ký này sinh tự động 1 lần duy nhất và không bao giờ bị ghi đè, dùng để định danh thiết bị khi kích hoạt key.
 */
class KeyStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("foxy_prefs", Context.MODE_PRIVATE)

    fun hasKey(): Boolean = !prefs.getString(KEY_LICENSE, null).isNullOrEmpty()

    fun get(): String? = prefs.getString(KEY_LICENSE, null)

    fun save(key: String) {
        val signature = getOrCreateDeviceSignature()
        prefs.edit()
            .putString(KEY_LICENSE, key)
            .putString(KEY_ACTIVATED_SIGNATURE, signature)
            .apply()
    }

    /**
     * Sinh chữ ký thiết bị 1 lần duy nhất trọn đời nếu chưa có.
     * Chữ ký được bảo vệ tuyệt đối: không bao giờ bị xóa hay ghi đè ngay cả khi đăng xuất.
     */
    fun getOrCreateDeviceSignature(): String {
        val existing = prefs.getString(KEY_DEVICE_SIGNATURE, null)
        if (!existing.isNullOrEmpty()) {
            return existing
        }

        // Tạo chữ ký độc nhất dựa trên Android ID + Thông tin phần cứng + UUID
        val androidId = try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Exception) {
            ""
        }
        val rawSeed = "$androidId-${Build.MANUFACTURER}-${Build.MODEL}-${UUID.randomUUID()}"
        val signature = hashToSignature(rawSeed)

        prefs.edit().putString(KEY_DEVICE_SIGNATURE, signature).apply()
        return signature
    }

    /** Gọi khi người dùng bấm "Đăng xuất" (chỉ xóa key, chữ ký thiết bị vẫn giữ nguyên). */
    fun clear() {
        prefs.edit()
            .remove(KEY_LICENSE)
            .remove(KEY_ACTIVATED_SIGNATURE)
            .apply()
    }

    private fun hashToSignature(seed: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        val hex = bytes.take(8).joinToString("") { "%02X".format(it) } // 16 ký tự hex
        return "FOXY-${hex.take(4)}-${hex.drop(4).take(4)}-${hex.drop(8).take(4)}-${hex.takeLast(4)}"
    }

    companion object {
        private const val KEY_LICENSE = "license_key"
        private const val KEY_DEVICE_SIGNATURE = "lifetime_device_signature"
        private const val KEY_ACTIVATED_SIGNATURE = "activated_with_signature"

        /** "ABCD1234EFGH5678" -> "ABCD-••••-••••-5678" (dùng cho màn "Gói của tôi"). */
        fun mask(key: String): String =
            if (key.length < 16) key else "${key.take(4)}-••••-••••-${key.takeLast(4)}"
    }
}
