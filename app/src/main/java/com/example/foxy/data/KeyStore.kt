package com.example.foxy.data

import android.content.Context

/**
 * Lưu key đã kích hoạt trên máy.
 * Bản mẫu dùng SharedPreferences cho đơn giản. Bản thật nên dùng EncryptedSharedPreferences
 * hoặc DataStore + Android Keystore để key không nằm ở dạng chữ thường.
 */
class KeyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("foxy_prefs", Context.MODE_PRIVATE)

    fun hasKey(): Boolean = !prefs.getString(KEY_LICENSE, null).isNullOrEmpty()

    fun get(): String? = prefs.getString(KEY_LICENSE, null)

    fun save(key: String) {
        prefs.edit().putString(KEY_LICENSE, key).apply()
    }

    /** Gọi khi người dùng bấm "Đăng xuất". */
    fun clear() {
        prefs.edit().remove(KEY_LICENSE).apply()
    }

    companion object {
        private const val KEY_LICENSE = "license_key"

        /** "ABCD1234EFGH5678" -> "ABCD-••••-••••-5678" (dùng cho màn "Gói của tôi"). */
        fun mask(key: String): String =
            if (key.length < 16) key else "${key.take(4)}-••••-••••-${key.takeLast(4)}"
    }
}
