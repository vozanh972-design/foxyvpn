package com.example.foxy.engine

import android.net.VpnService
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PacketTunnelEngine — Đọc packets từ TUN device, route qua real network.
 * Tích hợp GameProtocolEngine để detect ESP.
 */
class PacketTunnelEngine(
    private val vpn:    VpnService,
    private val tunIn:  FileInputStream,
    private val tunOut: FileOutputStream,
) {
    companion object { private const val TAG = "TunnelEngine" }

    private val running   = AtomicBoolean(false)
    private val workers   = Executors.newCachedThreadPool()
    private val udpFlows  = ConcurrentHashMap<String, UdpFlowState>()
    private var lastClean = 0L

    // ─── Start ─────────────────────────────────────────────────

    fun start() {
        running.set(true)
        Log.i(TAG, "Tunnel started")
        val buf = ByteArray(65535)
        try {
            while (running.get()) {
                val len = try { tunIn.read(buf) } catch (e: Exception) { break }
                if (len < 20) continue
                handleIPv4(buf, len)
                val now = System.currentTimeMillis()
                if (now - lastClean > 30_000) { lastClean = now; cleanupFlows() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tunnel error: $e")
        }
        Log.i(TAG, "Tunnel stopped")
    }

    fun stop() {
        running.set(false)
        workers.shutdownNow()
        udpFlows.values.forEach { runCatching { it.socket?.close() } }
        udpFlows.clear()
        runCatching { tunIn.close() }
        runCatching { tunOut.close() }
    }

    // ─── IPv4 dispatch ────────────────────────────────────────

    private fun handleIPv4(buf: ByteArray, len: Int) {
        val ihl     = (buf[0].toInt() and 0xF) * 4
        val proto   = buf[9].toInt() and 0xFF
        val srcIp   = formatIP(buf, 12)
        val dstIp   = formatIP(buf, 16)

        if (ihl >= len) return

        when (proto) {
            6  -> handleTcp(buf, ihl, len, srcIp, dstIp)
            17 -> handleUdp(buf, ihl, len, srcIp, dstIp)
        }
    }

    // ─── TCP ──────────────────────────────────────────────────

    private fun handleTcp(buf: ByteArray, ihl: Int, len: Int, srcIp: String, dstIp: String) {
        if (ihl + 4 >= len) return
        val srcPort = readU16(buf, ihl)
        val dstPort = readU16(buf, ihl + 2)
        val payload = buf.copyOfRange(ihl, len)

        // Scan TCP để học encryption key
        workers.submit {
            GameProtocolEngine.scanTcp("$srcIp:$srcPort-$dstIp:$dstPort", payload, dstPort)
        }
    }

    // ─── UDP ──────────────────────────────────────────────────

    private fun handleUdp(buf: ByteArray, ihl: Int, len: Int, srcIp: String, dstIp: String) {
        if (ihl + 8 >= len) return
        val srcPort  = readU16(buf, ihl)
        val dstPort  = readU16(buf, ihl + 2)
        val udpData  = buf.copyOfRange(ihl + 8, len)
        val flowKey  = "$srcIp:$srcPort-$dstIp:$dstPort"

        // === PROCESS outbound (game → server) ===
        GameProtocolEngine.processUdp(flowKey, udpData, true)
        val modified = PoseFreezeEngine.process(flowKey, udpData, true)

        // Forward đến real server
        val flow = udpFlows.getOrPut(flowKey) {
            UdpFlowState(dstIp, dstPort, srcIp, srcPort).also { f ->
                try {
                    f.socket = DatagramSocket()
                    vpn.protect(f.socket!!)
                    // Start receive loop (inbound)
                    workers.submit { receiveLoop(f, flowKey, srcIp, srcPort) }
                } catch (e: Exception) { Log.e(TAG, "UDP socket: $e") }
            }
        }
        flow.lastUsed = System.currentTimeMillis()

        try {
            val pkt = modified ?: udpData
            flow.socket?.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName(dstIp), dstPort))
        } catch (e: Exception) { /* skip */ }
    }

    private fun receiveLoop(flow: UdpFlowState, flowKey: String, clientIp: String, clientPort: Int) {
        val buf = ByteArray(65535)
        val dp  = DatagramPacket(buf, buf.size)
        while (running.get() && flow.socket?.isClosed == false) {
            try {
                flow.socket!!.soTimeout = 30_000
                flow.socket!!.receive(dp)
                val data = buf.copyOf(dp.length)

                // === PROCESS inbound (server → game) ===
                GameProtocolEngine.processUdp(flowKey, data, false)
                val modified = PoseFreezeEngine.process(flowKey, data, false)
                val out = modified ?: data

                // Forward về game client qua TUN
                sendUdpToTun(clientIp, clientPort, flow.remoteIp, flow.remotePort, out)
                flow.lastUsed = System.currentTimeMillis()
            } catch (e: SocketTimeoutException) { break }
            catch (e: Exception) { break }
        }
        flow.socket?.close()
    }

    private fun sendUdpToTun(srcIp: String, srcPort: Int, dstIp: String, dstPort: Int, data: ByteArray) {
        // Build IP+UDP header để gửi về TUN
        val udpLen  = 8 + data.size
        val ipLen   = 20 + udpLen
        val pkt     = ByteArray(ipLen)
        // IP header
        pkt[0]  = 0x45.toByte()          // Version=4, IHL=5
        pkt[2]  = (ipLen shr 8).toByte()
        pkt[3]  = (ipLen and 0xFF).toByte()
        pkt[8]  = 64                      // TTL
        pkt[9]  = 17                      // Protocol UDP
        writeIPBytes(pkt, 12, dstIp)      // Swap: src=server (dstIp), dst=client (srcIp)
        writeIPBytes(pkt, 16, srcIp)
        // IP checksum
        writeU16(pkt, 10, ipChecksum(pkt, 20))
        // UDP header
        writeU16(pkt, 20, dstPort)        // Swap ports
        writeU16(pkt, 22, srcPort)
        writeU16(pkt, 24, udpLen)
        // Payload
        System.arraycopy(data, 0, pkt, 28, data.size)
        try { tunOut.write(pkt) } catch (e: Exception) { /* skip */ }
    }

    // ─── Cleanup ──────────────────────────────────────────────

    private fun cleanupFlows() {
        val now = System.currentTimeMillis()
        val stale = udpFlows.entries.filter { now - it.value.lastUsed > 120_000 }.map { it.key }
        stale.forEach { key -> udpFlows.remove(key)?.socket?.close() }
    }

    // ─── Helpers ──────────────────────────────────────────────

    private fun formatIP(b: ByteArray, off: Int) =
        "${b[off].toInt() and 0xFF}.${b[off+1].toInt() and 0xFF}.${b[off+2].toInt() and 0xFF}.${b[off+3].toInt() and 0xFF}"

    private fun readU16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i+1].toInt() and 0xFF)
    private fun writeU16(b: ByteArray, i: Int, v: Int) { b[i] = (v shr 8).toByte(); b[i+1] = (v and 0xFF).toByte() }
    private fun writeIPBytes(b: ByteArray, off: Int, ip: String) {
        ip.split(".").forEachIndexed { i, s -> b[off + i] = s.toInt().toByte() }
    }
    private fun ipChecksum(b: ByteArray, len: Int): Int {
        var sum = 0
        for (i in 0 until len step 2) sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i+1].toInt() and 0xFF)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    class UdpFlowState(val remoteIp: String, val remotePort: Int, val clientIp: String, val clientPort: Int) {
        var socket: DatagramSocket? = null
        var lastUsed = System.currentTimeMillis()
    }
}

/**
 * PoseFreezeEngine — Rewrite position trong outbound UDP packets khi Ghim bật.
 */
object PoseFreezeEngine {

    private data class FlowState(
        var pinnedX: Long = 0, var pinnedY: Long = 0, var pinnedZ: Long = 0,
        var hasPinned: Boolean = false,
        var freezeActive: Boolean = false,
    )

    private val flows = ConcurrentHashMap<String, FlowState>()

    fun process(flowKey: String, data: ByteArray, outbound: Boolean): ByteArray? {
        if (!outbound) return null  // Inbound: không modify

        val shouldFreeze = FreezeController.isFreezeActive()
        val flow = flows.getOrPut(flowKey) { FlowState() }

        // Parse position từ packet (nếu có)
        // Đây là approximation — position command detection
        val header = GameProtocolEngine.getOrCreate(flowKey).parse(data, 0, data.size)
        if (header == null || !isPositionCommand(header.command)) {
            return null  // Không phải position packet
        }

        // Lấy local player position hiện tại từ EspEntityStore (đã được parse từ inbound)
        val lp = EspEntityStore.snapshot().localPlayer
        if (lp != null && !shouldFreeze) {
            // Update pinned position khi không freeze
            flow.pinnedX = (lp.x * 1000).toLong()
            flow.pinnedY = (lp.y * 1000).toLong()
            flow.pinnedZ = (lp.z * 1000).toLong()
            flow.hasPinned = true
        }

        if (!shouldFreeze || !flow.hasPinned) {
            flow.freezeActive = false
            return null  // Pass through
        }

        // === FREEZE: rewrite position varints ===
        flow.freezeActive = true
        return rewritePositionInPacket(data, header, flow.pinnedX, flow.pinnedY, flow.pinnedZ)
    }

    private fun rewritePositionInPacket(
        data: ByteArray, header: ParsedHeader,
        pinnedX: Long, pinnedY: Long, pinnedZ: Long,
    ): ByteArray? {
        val bodyStart = header.headerSize
        val bodyEnd   = bodyStart + header.bodyLen
        if (bodyEnd > data.size) return null

        // Encode pinned positions as ZigZag varints
        val newX = encodeVarint(zigZagEncode(pinnedX))
        val newY = encodeVarint(zigZagEncode(pinnedY))
        val newZ = encodeVarint(zigZagEncode(pinnedZ))

        // Simple approach: scan body cho 3 consecutive varints ở đầu
        // và replace bằng pinned values
        val body = data.copyOfRange(bodyStart, bodyEnd)
        val cur = intArrayOf(0)
        val x = VerifiedEspParser.readVarint(body, cur, body.size)
        val y = VerifiedEspParser.readVarint(body, cur, body.size)
        val z = VerifiedEspParser.readVarint(body, cur, body.size)
        if (x < 0 || y < 0 || z < 0) return null

        // Rebuild body
        val prefix = byteArrayOf()   // Header intact
        val newBody = newX + newY + newZ + body.copyOfRange(cur[0], body.size)
        if (newBody.size > 65000) return null

        val result = ByteArray(bodyStart + newBody.size)
        System.arraycopy(data, 0, result, 0, bodyStart)
        System.arraycopy(newBody, 0, result, bodyStart, newBody.size)
        return result
    }

    private fun isPositionCommand(cmd: Int) = cmd == 2001 || cmd == 2002 || cmd == 2012

    private fun zigZagEncode(n: Long): Long = if (n >= 0) n * 2 else (-n * 2 - 1)
    private fun encodeVarint(v: Long): ByteArray {
        val out = mutableListOf<Byte>()
        var rem = v
        while (rem > 127) { out += ((rem and 0x7F).toInt() or 0x80).toByte(); rem = rem ushr 7 }
        out += rem.toByte()
        return out.toByteArray()
    }
}
