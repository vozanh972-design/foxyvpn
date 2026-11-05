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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FreefireVpnService — dựa đúng logic từ Pigboy DEX (classes2.dex):
 *
 * Từ DEX thực đọc được:
 *   class A1 { startOrRestartTunnel(), stopTunnel() }   ← wrapper
 *   class PacketTunnelEngine {                           ← engine chính
 *       TcpFlow, UdpFlow, FragmentBuffer, TcpSegment
 *   }
 *   class FirewallRules { blocksDomain(), blocksIp(), denyDns(), learn(), observeDns() }
 *
 * Logic thực của Pigboy:
 *   1. VpnService.Builder.establish() → tạo TUN interface (Android tự route)
 *   2. FirewallRules — block domain/IP qua DNS monitoring
 *   3. PacketTunnelEngine quản lý TCP/UDP flows cho firewall
 *   4. KHÔNG forward gói thủ công — Android OS tự làm khi TUN up
 *
 * Cái tôi implement đúng theo đó:
 *   - establish() TUN interface
 *   - Foreground notification (bắt buộc Android 8+)
 *   - Broadcast state về MainActivity
 */
class FreefireVpnService : VpnService() {

    companion object {
        private const val TAG = "FreefireVPN"
        const val ACTION_START = "com.example.foxy.vpn.START"
        const val ACTION_STOP  = "com.example.foxy.vpn.STOP"

        private const val CHANNEL_ID   = "foxy_vpn_channel"
        private const val NOTIF_ID     = 1001

        // TUN interface config
        private const val VPN_ADDRESS  = "10.8.0.2"
        private const val VPN_GATEWAY  = "10.8.0.1"
        private const val VPN_DNS      = "8.8.8.8"
        private const val VPN_DNS2     = "8.8.4.4"
        private const val MTU          = 1500

        // Broadcast — gửi về ViewModel
        const val BROADCAST_STATE  = "com.example.foxy.vpn.STATE"
        const val EXTRA_CONNECTED  = "connected"
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> { stopVpn(); START_NOT_STICKY }
            else        -> { startVpn(); START_STICKY }
        }
    }

    // ─── FOREGROUND NOTIFICATION (bắt buộc, giống Pigboy) ─────────────────────

    private fun showNotification() {
        // Tạo channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Free Fire VPN",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "VPN đang hoạt động" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Free Fire VPN")
                .setContentText("🔥 Đang bảo vệ kết nối Free Fire")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Free Fire VPN")
                .setContentText("🔥 Đang bảo vệ kết nối Free Fire")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        }

        startForeground(NOTIF_ID, notif)
    }

    // ─── START VPN — theo đúng Pigboy startOrRestartTunnel() ──────────────────

    private fun startVpn() {
        if (running.getAndSet(true)) return
        Log.i(TAG, "startOrRestartTunnel()")

        // 1. Foreground notification trước (bắt buộc Android 9+)
        showNotification()

        scope.launch {
            try {
                // 2. Tạo TUN interface — giống Pigboy PacketTunnelEngine.establish()
                val builder = Builder()
                    .setSession("Free Fire VPN")
                    .addAddress(VPN_ADDRESS, 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(VPN_DNS)
                    .addDnsServer(VPN_DNS2)
                    .setMtu(MTU)
                    .setBlocking(false)

                // Bypass chính game Free Fire khỏi VPN loop (Pigboy làm điều này)
                try { builder.addDisallowedApplication("com.dts.freefireth") } catch (_: Exception) {}

                tunInterface = builder.establish()
                Log.i(TAG, "TUN established: fd=${tunInterface?.fd}")

                // 3. Broadcast Connected về ViewModel
                broadcastState(connected = true)

            } catch (e: Exception) {
                Log.e(TAG, "startVpn error: $e")
                running.set(false)
                broadcastState(connected = false)
            }
        }
    }

    // ─── STOP VPN — theo đúng Pigboy stopTunnel() ─────────────────────────────

    private fun stopVpn() {
        Log.i(TAG, "stopTunnel()")
        running.set(false)
        tunInterface?.close()
        tunInterface = null
        broadcastState(connected = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        scope.cancel()
    }

    override fun onRevoke() {
        stopVpn()
    }

    // ─── Broadcast state về ViewModel ─────────────────────────────────────────

    private fun broadcastState(connected: Boolean) {
        sendBroadcast(Intent(BROADCAST_STATE).apply {
            `package` = packageName  // restricted broadcast — chỉ trong app
            putExtra(EXTRA_CONNECTED, connected)
        })
    }
}
