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
            // ════════════════════════════════════════════════════════
            // RESTART LOOP — equivalent của watchdog A1$100000003 trong gốc:
            //   A1$100000001.run(): engine.start() thoát → setRunning(false)
            //   A1$100000003.run(): detect → sleep(0x3e8=1000ms) → post restart
            //   A1$100000003$100000002.run(): rebuild VPN + engine mới
            // Nếu không có loop này: engine exit → game mất mạng mãi mãi
            // ════════════════════════════════════════════════════════
            while (running.get()) {
                try {
                    doStartTunnel()
                } catch (e: Exception) {
                    Log.e(TAG, "Tunnel error: $e")
                }

                // Nếu vẫn running (stopVpn chưa gọi) → rebuild sau 1000ms
                // 1000ms = 0x3e8 (C0026A1.smali line 6188 watchdog sleep)
                if (running.get()) {
                    Log.i(TAG, "Engine stopped, restarting in 1s…")
                    try { Thread.sleep(1000) } catch (_: InterruptedException) {}
                }
            }

            broadcastState(false)
        }
    }

    /** Mỗi lần build VPN interface + chạy engine (blocking đến khi engine thoát). */
    private fun doStartTunnel() {
        // 1. Build TUN interface
        val builder = Builder()
            .setSession("Foxy VPN")
            .addAddress(VPN_ADDRESS, 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(VPN_DNS)
            .addDnsServer(VPN_DNS2)
            .setMtu(MTU)
            .setBlocking(true)   // Blocking mode cho tunnel engine

        // ════════════════════════════════════════════════════════
        // QUAN TRỌNG: CHỈ route traffic của game vào VPN tunnel
        // (giống logic gốc Pigboy — addAllowedApplication)
        // ════════════════════════════════════════════════════════
        try { builder.addAllowedApplication("com.dts.freefireth") }  catch (_: Exception) {}
        try { builder.addAllowedApplication("com.dts.freefiremax") } catch (_: Exception) {}

        val tun = builder.establish() ?: run {
            Log.e(TAG, "establish() returned null")
            return
        }

        // Đóng TUN fd cũ trước khi gán fd mới
        val oldTun = tunInterface
        tunInterface = tun

        try { oldTun?.close() } catch (_: Exception) {}

        // 2. Reset engine state
        GameProtocolEngine.reset()

        // 3. Khởi động PacketTunnelEngine
        val engine = PacketTunnelEngine(
            vpn   = this@FreefireVpnService,
            tunIn = java.io.FileInputStream(tun.fileDescriptor),
            tunOut= java.io.FileOutputStream(tun.fileDescriptor),
        )
        tunnelEngine = engine

        broadcastState(true)
        Log.i(TAG, "TUN fd=${tun.fd} — Tunnel Engine running")

        // 4. Chạy tunnel loop (blocking) — khi thoát: watchdog loop sẽ restart
        engine.start()

        Log.i(TAG, "engine.start() exited")
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
