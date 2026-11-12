package com.example.foxy.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.foxy.MainActivity
import com.example.foxy.R
import com.example.foxy.engine.GameProtocolEngine
import com.example.foxy.engine.PacketTunnelEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FreefireVpnService — VPN service tích hợp PacketTunnelEngine.
 * ESP detection chạy trong tunnel (GameProtocolEngine).
 * Freeze/Ghim rewrite position packet (PoseFreezeEngine).
 */
class FreefireVpnService : VpnService() {

    companion object {
        private const val TAG          = "FreefireVPN"
        const val ACTION_START         = "com.example.foxy.vpn.START"
        const val ACTION_STOP          = "com.example.foxy.vpn.STOP"
        private const val CHANNEL_ID   = "foxy_vpn_channel"
        private const val NOTIF_ID     = 1001

        // TUN config
        private const val VPN_ADDRESS  = "10.8.0.2"
        private const val VPN_DNS      = "8.8.8.8"
        private const val VPN_DNS2     = "8.8.4.4"
        private const val MTU          = 1500

        // Broadcast
        const val BROADCAST_STATE  = "com.example.foxy.vpn.STATE"
        const val EXTRA_CONNECTED  = "connected"
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelEngine: PacketTunnelEngine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> { stopVpn(); START_NOT_STICKY }
            else        -> { startVpn(); START_STICKY }
        }
    }

    private fun startVpn() {
        if (running.getAndSet(true)) return
        Log.i(TAG, "Starting VPN + Tunnel Engine")
        showNotification()

        scope.launch {
            try {
                // 1. Build TUN interface
                val builder = Builder()
                    .setSession("Foxy VPN")
                    .addAddress(VPN_ADDRESS, 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(VPN_DNS)
                    .addDnsServer(VPN_DNS2)
                    .setMtu(MTU)
                    .setBlocking(true)   // Blocking mode cho tunnel engine

                // KHÔNG bypass game — packet game phải đi qua tunnel để ESP hoạt động
                // (chỉ bypass app VPN của chính mình nếu có vòng lặp)

                val tun = builder.establish() ?: run {
                    Log.e(TAG, "establish() returned null")
                    running.set(false)
                    broadcastState(false)
                    return@launch
                }
                tunInterface = tun

                // 2. Reset engine state
                GameProtocolEngine.reset()

                // 3. Khởi động PacketTunnelEngine
                val engine = PacketTunnelEngine(
                    vpn   = this@FreefireVpnService,
                    tunIn = FileInputStream(tun.fileDescriptor),
                    tunOut= FileOutputStream(tun.fileDescriptor),
                )
                tunnelEngine = engine

                broadcastState(true)
                Log.i(TAG, "TUN fd=${tun.fd} — Tunnel Engine running")

                // 4. Chạy tunnel loop (blocking)
                engine.start()

            } catch (e: Exception) {
                Log.e(TAG, "startVpn error: $e")
                running.set(false)
                broadcastState(false)
            }
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN")
        running.set(false)
        tunnelEngine?.stop()
        tunnelEngine = null
        tunInterface?.close()
        tunInterface = null
        GameProtocolEngine.reset()
        broadcastState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Foxy VPN", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "VPN + ESP đang hoạt động" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Foxy VPN đang chạy")
                .setContentText("Mắt thần đang theo dõi")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openIntent).setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Foxy VPN đang chạy")
                .setContentText("Mắt thần đang theo dõi")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openIntent).setOngoing(true).build()
        }
        startForeground(NOTIF_ID, notif)
    }

    private fun broadcastState(connected: Boolean) {
        sendBroadcast(Intent(BROADCAST_STATE).apply {
            `package` = packageName
            putExtra(EXTRA_CONNECTED, connected)
        })
    }

    override fun onRevoke()  { stopVpn() }
    override fun onDestroy() { super.onDestroy(); stopVpn(); scope.cancel() }
}
