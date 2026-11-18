package com.example.foxy.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * WeaponIconCache — Load hình súng thật từ Free Fire game assets.
 *
 * Icons được extract từ split_asset_pack_install_time.apk bằng UnityPy,
 * copy vào assets/weapon_icons/ trong Foxy APK.
 *
 * File naming convention: weapon_<type>.png
 *   weapon_0.png = không cầm gì (empty hand)
 *   weapon_1.png = AR (Assault Rifle)
 *   weapon_2.png = SMG
 *   weapon_3.png = SR (Sniper)
 *   weapon_4.png = SG (Shotgun)
 *   weapon_5.png = P  (Pistol)
 *   weapon_6.png = M  (Melee/nấm đấm)
 *   weapon_7.png = GL (Grenade Launcher)
 *   weapon_8.png = GR (Grenade)
 *
 * Nếu chưa có file icon → trả về null, ESP dùng text label thay thế.
 */
object WeaponIconCache {

    private val cache = HashMap<Int, Bitmap?>()
    private var initialized = false
    private const val TAG = "WeaponIconCache"

    // Icon size khi render trên nameplate (dp → pixels tính khi load)
    const val ICON_SIZE_DP = 14f

    /**
     * Khởi tạo cache: load tất cả weapon icons từ assets.
     * Gọi 1 lần từ FoxyVpnService.onCreate().
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        for (type in 0..8) {
            cache[type] = loadIcon(context, type)
        }
        val loaded = cache.values.count { it != null }
        Log.d(TAG, "Loaded $loaded/${cache.size} weapon icons")
    }

    private fun loadIcon(context: Context, weaponType: Int): Bitmap? {
        return try {
            val assetName = "weapon_icons/weapon_$weaponType.png"
            context.assets.open(assetName).use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            // Icon chưa được pack vào assets → dùng text fallback
            null
        }
    }

    /**
     * Lấy Bitmap icon cho weaponType.
     * Trả về null nếu chưa có icon → caller dùng text label.
     */
    fun get(weaponType: Int): Bitmap? = cache[weaponType]

    /**
     * Scale icon về đúng kích thước dp trên screen.
     * Dùng cached scaled bitmap để tránh tạo object mỗi frame.
     */
    fun getScaled(weaponType: Int, sizePx: Int): Bitmap? {
        val src = cache[weaponType] ?: return null
        return if (src.width == sizePx && src.height == sizePx) src
        else Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
    }
}
