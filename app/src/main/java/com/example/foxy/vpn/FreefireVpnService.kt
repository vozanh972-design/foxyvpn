package com.example.foxy.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FreefireVpnService — VPN Service thực, dựa theo nguyên lý PacketTunnelEngine của Pigboy:
 *
 *  Pigboy PacketTunnelEngine:
 *    ┌─ TUN interface (VpnService) ──────────────────────────────────┐
 *    │  PacketTunnelEngine                                            │
 *    │    ├─ TcpFlow: nhận TCP packet → forward qua socket thật      │
 *    │    ├─ UdpFlow: nhận UDP packet → forward qua DatagramSocket   │
 *    │    └─ FragmentBuffer: reassemble gói bị phân mảnh             │
 *    └───────────────────────────────────────────────────────────────┘
 *
 *  Cơ chế:
 *    1. Tạo TUN interface ảo (VpnService.Builder)
 *    2. Đọc từng gói IP từ TUN fd
 *    3. Phân loại TCP (proto=6) / UDP (proto=17)
 *    4. Forward gói đến đích thật qua socket được protect() (bypass VPN loop)
 *    5. Nhận phản hồi → ghi ngược lại TUN fd
 */
class FreefireVpnService : VpnService() {

    companion object {
        private const val TAG = "FreefireVPN"
        const val ACTION_START = "com.example.foxy.vpn.START"
        const val ACTION_STOP  = "com.example.foxy.vpn.STOP"

        // Địa chỉ TUN interface ảo
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE   = "0.0.0.0"
        private const val VPN_DNS     = "8.8.8.8"
        private const val MTU         = 1500

        // Thông tin callback gửi broadcast về MainActivity
        const val BROADCAST_STATE  = "com.example.foxy.vpn.STATE"
        const val EXTRA_CONNECTED  = "connected"
        const val EXTRA_DOWN_MBPS  = "down_mbps"
        const val EXTRA_UP_MBPS    = "up_mbps"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Giống TcpFlow/UdpFlow map của PacketTunnelEngine
    private val tcpFlows = ConcurrentHashMap<String, SocketChannel>()
    private val udpFlows = ConcurrentHashMap<String, DatagramChannel>()

    // Stats counter (giống Pigboy ghi down/up bytes)
    private var bytesDown = 0L
    private var bytesUp   = 0L
    private var statsJob: kotlinx.coroutines.Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> { stopVpn(); START_NOT_STICKY }
            else        -> { startVpn(); START_STICKY }
        }
    }

    // ─── START VPN ─────────────────────────────────────────────────────────────

    private fun startVpn() {
        if (running.getAndSet(true)) return
        Log.i(TAG, "startVpn()")

        try {
            val builder = Builder()
                .setSession("Free Fire VPN")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer(VPN_DNS)
                .setMtu(MTU)
                .setBlocking(false)

            // Không tunnel chính game Free Fire — bypass để không loop
            try {
                builder.addDisallowedApplication("com.dts.freefireth")
            } catch (_: Exception) {}

            vpnInterface = builder.establish()
            Log.i(TAG, "TUN interface established: fd=${vpnInterface?.fd}")

            // Khởi động PacketTunnelEngine — đọc/ghi TUN fd
            startPacketTunnel()
            startStatsReporter()

            broadcastState(connected = true)
        } catch (e: Exception) {
            Log.e(TAG, "startVpn error: $e")
            running.set(false)
        }
    }

    // ─── PACKET TUNNEL ENGINE (giống PacketTunnelEngine của Pigboy) ────────────

    /**
     * Vòng lặp chính đọc gói IP từ TUN fd:
     *   - Proto 6  → TcpFlow.handle()
     *   - Proto 17 → UdpFlow.handle()
     */
    private fun startPacketTunnel() {
        val pfd = vpnInterface ?: return

        scope.launch {
            val input  = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val packet = ByteBuffer.allocate(MTU)

            Log.i(TAG, "PacketTunnelEngine started")
            while (running.get()) {
                packet.clear()
                val len = input.read(packet.array())
                if (len <= 0) {
                    Thread.sleep(1)
                    continue
                }
                packet.limit(len)
                bytesUp += len

                // Parse IP header (giống TcpSegment parser của Pigboy)
                val ipVersion = (packet.get(0).toInt() ushr 4) and 0xF
                if (ipVersion != 4) continue   // chỉ xử lý IPv4

                val ihl      = (packet.get(0).toInt() and 0xF) * 4
                val protocol = packet.get(9).toInt() and 0xFF
                val srcIp    = readIp(packet, 12)
                val dstIp    = readIp(packet, 16)
                val srcPort  = readPort(packet, ihl)
                val dstPort  = readPort(packet, ihl + 2)

                when (protocol) {
                    6  -> handleTcp(packet, len, srcIp, dstIp, srcPort, dstPort, ihl, output)
                    17 -> handleUdp(packet, len, srcIp, dstIp, srcPort, dstPort, ihl, output)
                }
            }
            Log.i(TAG, "PacketTunnelEngine stopped")
        }
    }

    // ─── TcpFlow (giống PacketTunnelEngine$TcpFlow) ────────────────────────────

    private suspend fun handleTcp(
        packet: ByteBuffer,
        len: Int,
        srcIp: String, dstIp: String,
        srcPort: Int,  dstPort: Int,
        ihl: Int,
        output: FileOutputStream,
    ) {
        val flowKey = "$srcIp:$srcPort→$dstIp:$dstPort"

        try {
            val ch = tcpFlows.getOrPut(flowKey) {
                val c = SocketChannel.open()
                protect(c.socket())     // Bypass VPN loop — giống Pigboy protect()
                c.configureBlocking(false)
                c.connect(InetSocketAddress(InetAddress.getByName(dstIp), dstPort))
                c
            }

            // Forward payload (giống TcpSegment.forward)
            val payloadOff = ihl + 20   // TCP header = 20 bytes min
            if (payloadOff < len) {
                val payload = ByteBuffer.wrap(packet.array(), payloadOff, len - payloadOff)
                while (payload.hasRemaining()) {
                    ch.write(payload)
                }
            }

            // Đọc phản hồi từ server → ghi ngược TUN (giống connectAndReceive)
            val respBuf = ByteBuffer.allocate(MTU)
            val read = ch.read(respBuf)
            if (read > 0) {
                bytesDown += read
                output.write(respBuf.array(), 0, read)
            }
        } catch (e: Exception) {
            tcpFlows.remove(flowKey)?.close()
        }
    }

    // ─── UdpFlow (giống PacketTunnelEngine$UdpFlow) ────────────────────────────

    private suspend fun handleUdp(
        packet: ByteBuffer,
        len: Int,
        srcIp: String, dstIp: String,
        srcPort: Int,  dstPort: Int,
        ihl: Int,
        output: FileOutputStream,
    ) {
        val flowKey = "udp:$srcIp:$srcPort→$dstIp:$dstPort"

        try {
            val ch = udpFlows.getOrPut(flowKey) {
                val c = DatagramChannel.open()
                protect(c.socket())     // Bypass VPN loop
                c.configureBlocking(false)
                c.connect(InetSocketAddress(InetAddress.getByName(dstIp), dstPort))
                c
            }

            // Forward UDP payload (giống UdpFlow.send)
            val payloadOff = ihl + 8    // UDP header = 8 bytes
            if (payloadOff < len) {
                val payload = ByteBuffer.wrap(packet.array(), payloadOff, len - payloadOff)
                ch.write(payload)
            }

            // Đọc phản hồi (giống UdpFlow.receive)
            val respBuf = ByteBuffer.allocate(MTU)
            val read = ch.read(respBuf)
            if (read > 0) {
                bytesDown += read
                output.write(respBuf.array(), 0, read)
            }
        } catch (e: Exception) {
            udpFlows.remove(flowKey)?.close()
        }
    }

    // ─── Stats reporter (gửi down/up Mbps về ViewModel) ───────────────────────

    private fun startStatsReporter() {
        statsJob = scope.launch {
            var prevDown = 0L
            var prevUp   = 0L
            while (running.get()) {
                kotlinx.coroutines.delay(1000)
                val downDelta = bytesDown - prevDown
                val upDelta   = bytesUp   - prevUp
                prevDown = bytesDown
                prevUp   = bytesUp

                val downMbps = downDelta * 8.0 / 1_000_000
                val upMbps   = upDelta   * 8.0 / 1_000_000

                broadcastState(connected = true, downMbps = downMbps, upMbps = upMbps)
            }
        }
    }

    // ─── STOP VPN ──────────────────────────────────────────────────────────────

    private fun stopVpn() {
        Log.i(TAG, "stopVpn()")
        running.set(false)
        statsJob?.cancel()
        tcpFlows.values.forEach { runCatching { it.close() } }
        udpFlows.values.forEach { runCatching { it.close() } }
        tcpFlows.clear()
        udpFlows.clear()
        vpnInterface?.close()
        vpnInterface = null
        broadcastState(connected = false)
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

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun broadcastState(connected: Boolean, downMbps: Double = 0.0, upMbps: Double = 0.0) {
        sendBroadcast(Intent(BROADCAST_STATE).apply {
            putExtra(EXTRA_CONNECTED, connected)
            putExtra(EXTRA_DOWN_MBPS, downMbps)
            putExtra(EXTRA_UP_MBPS,   upMbps)
        })
    }

    private fun readIp(buf: ByteBuffer, offset: Int): String {
        return "${buf.get(offset).toInt() and 0xFF}.${buf.get(offset+1).toInt() and 0xFF}" +
               ".${buf.get(offset+2).toInt() and 0xFF}.${buf.get(offset+3).toInt() and 0xFF}"
    }

    private fun readPort(buf: ByteBuffer, offset: Int): Int {
        return ((buf.get(offset).toInt() and 0xFF) shl 8) or (buf.get(offset+1).toInt() and 0xFF)
    }
}
