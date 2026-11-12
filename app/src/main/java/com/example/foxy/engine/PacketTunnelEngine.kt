package com.example.foxy.engine

import android.net.VpnService
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PacketTunnelEngine — Tunnel hoàn chỉnh với TCP proxy + UDP ESP detection.
 *
 * Fix critical: TCP packets trước đây bị drop hoàn toàn → game mất kết nối server.
 * Giờ implement full TCP proxy với SYN/ACK handshake + bi-directional forwarding.
 */
class PacketTunnelEngine(
    private val vpn:    VpnService,
    private val tunIn:  FileInputStream,
    private val tunOut: FileOutputStream,
) {
    companion object {
        private const val TAG = "TunnelEngine"
        private const val TCP_FIN = 0x01
        private const val TCP_SYN = 0x02
        private const val TCP_RST = 0x04
        private const val TCP_PSH = 0x08
        private const val TCP_ACK = 0x10
    }

    private val running = AtomicBoolean(false)
    private val workers = Executors.newScheduledThreadPool(8)
    private val udpFlows = ConcurrentHashMap<String, UdpFlowState>()
    private val tcpFlows = ConcurrentHashMap<String, TcpFlowState>()
    @Volatile private var lastClean = 0L
    private val tunLock = Any()   // Serialize writes to TUN

    // ─── Start ────────────────────────────────────────────────

    fun start() {
        running.set(true)
        Log.i(TAG, "Tunnel started")
        val buf = ByteArray(65535)
        try {
            while (running.get()) {
                val len = try { tunIn.read(buf) } catch (e: Exception) { -1 }
                if (len < 0) break
                if (len < 20) continue
                if ((buf[0].toInt() and 0xF0) shr 4 != 4) continue  // IPv4 only
                handleIPv4(buf, len)
                val now = System.currentTimeMillis()
                if (now - lastClean > 30_000) { lastClean = now; cleanupFlows() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tunnel loop error: $e")
        } finally {
            Log.i(TAG, "Tunnel stopped")
        }
    }

    fun stop() {
        running.set(false)
        workers.shutdownNow()
        udpFlows.values.forEach { runCatching { it.socket?.close() } }
        tcpFlows.values.forEach { runCatching { it.socket?.close() } }
        udpFlows.clear(); tcpFlows.clear()
        runCatching { tunIn.close() }
        runCatching { tunOut.close() }
    }

    // ─── IPv4 dispatch ────────────────────────────────────────

    private fun handleIPv4(buf: ByteArray, len: Int) {
        val ihl   = (buf[0].toInt() and 0x0F) * 4
        if (ihl < 20 || ihl >= len) return
        val proto = buf[9].toInt() and 0xFF
        val srcIp = formatIP(buf, 12)
        val dstIp = formatIP(buf, 16)
        when (proto) {
            6  -> handleTcp(buf, ihl, len, srcIp, dstIp)
            17 -> handleUdp(buf, ihl, len, srcIp, dstIp)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TCP PROXY — Quan trọng nhất! Fix kết nối game bị văng.
    // Implement full SYN→SYN-ACK→data→FIN state machine.
    // ═══════════════════════════════════════════════════════════

    private fun handleTcp(buf: ByteArray, ihl: Int, len: Int, srcIp: String, dstIp: String) {
        if (ihl + 20 > len) return
        val srcPort    = readU16(buf, ihl)
        val dstPort    = readU16(buf, ihl + 2)
        val seqNum     = readU32(buf, ihl + 4)
        val ackNum     = readU32(buf, ihl + 8)
        val dataOffset = ((buf[ihl + 12].toInt() and 0xF0) shr 4) * 4
        val flags      = buf[ihl + 13].toInt() and 0xFF
        val payloadOff = ihl + dataOffset
        val payload    = if (payloadOff < len) buf.copyOfRange(payloadOff, len) else ByteArray(0)

        val flowKey = "$srcIp:$srcPort→$dstIp:$dstPort"

        // Scan TCP payload để học encryption key
        if (payload.isNotEmpty()) {
            workers.submit { GameProtocolEngine.scanTcp(flowKey, payload, dstPort) }
        }

        val isSyn = (flags and TCP_SYN) != 0 && (flags and TCP_ACK) == 0
        val isFin = (flags and TCP_FIN) != 0
        val isRst = (flags and TCP_RST) != 0

        // RST: đóng flow ngay
        if (isRst) { tcpFlows.remove(flowKey)?.socket?.close(); return }

        // ─── SYN: tạo kết nối mới ───────────────────────────
        if (isSyn) {
            tcpFlows.remove(flowKey)?.socket?.close()
            val flow = TcpFlowState(dstIp, dstPort, srcIp, srcPort, seqNum)
            tcpFlows[flowKey] = flow

            workers.submit {
                try {
                    val sock = Socket()
                    vpn.protect(sock)   // Bypass VPN loop!
                    sock.connect(InetSocketAddress(dstIp, dstPort), 10_000)
                    sock.tcpNoDelay = true
                    flow.socket    = sock
                    flow.connected = true

                    // Gửi SYN-ACK về game client
                    writeTun(buildTcpPkt(dstIp, dstPort, srcIp, srcPort,
                        seq = flow.myIsn, ack = seqNum + 1L,
                        flags = TCP_SYN or TCP_ACK, win = 65535, data = ByteArray(0)))
                    flow.clientNext = seqNum + 1L
                    flow.serverNext = flow.myIsn + 1L

                    // Thread đọc từ server → ghi vào TUN (inbound)
                    workers.submit { tcpServerToClient(flow, flowKey, srcIp, srcPort, dstIp, dstPort) }

                } catch (e: Exception) {
                    Log.w(TAG, "TCP connect $dstIp:$dstPort failed: $e")
                    // RST về client
                    writeTun(buildTcpPkt(dstIp, dstPort, srcIp, srcPort,
                        seq = 0, ack = seqNum + 1L, flags = TCP_RST or TCP_ACK, win = 0, data = ByteArray(0)))
                    tcpFlows.remove(flowKey)
                }
            }
            return
        }

        // ─── Các packet tiếp theo: forward data ─────────────
        val flow = tcpFlows[flowKey] ?: return
        if (!flow.connected) return
        flow.lastUsed = System.currentTimeMillis()

        if (payload.isNotEmpty()) {
            try {
                flow.socket?.outputStream?.let { out ->
                    out.write(payload)
                    out.flush()
                }
                flow.clientNext = seqNum + payload.size.toLong()
                // ACK data từ client
                writeTun(buildTcpPkt(dstIp, dstPort, srcIp, srcPort,
                    seq = flow.serverNext, ack = flow.clientNext,
                    flags = TCP_ACK, win = 65535, data = ByteArray(0)))
            } catch (e: Exception) {
                Log.w(TAG, "TCP forward error: $e")
                tcpFlows.remove(flowKey)?.socket?.close()
            }
        }

        if (isFin) {
            flow.clientNext++
            try { flow.socket?.shutdownOutput() } catch (_: Exception) {}
            writeTun(buildTcpPkt(dstIp, dstPort, srcIp, srcPort,
                seq = flow.serverNext, ack = flow.clientNext,
                flags = TCP_FIN or TCP_ACK, win = 0, data = ByteArray(0)))
            flow.serverNext++
            workers.schedule({ tcpFlows.remove(flowKey)?.socket?.close() }, 15, TimeUnit.SECONDS)
        }
    }

    /** Thread đọc data từ real server → inject vào TUN về phía client. */
    private fun tcpServerToClient(
        flow: TcpFlowState,
        flowKey: String,
        clientIp: String, clientPort: Int,
        serverIp: String, serverPort: Int,
    ) {
        val buf = ByteArray(32768)
        try {
            while (running.get()) {
                val n = flow.socket?.inputStream?.read(buf) ?: break
                if (n <= 0) break
                val data = buf.copyOf(n)
                writeTun(buildTcpPkt(serverIp, serverPort, clientIp, clientPort,
                    seq = flow.serverNext, ack = flow.clientNext,
                    flags = TCP_PSH or TCP_ACK, win = 65535, data = data))
                flow.serverNext += n.toLong()
                flow.lastUsed = System.currentTimeMillis()
            }
        } catch (_: Exception) {}
        // Server đóng → gửi FIN về client
        try {
            writeTun(buildTcpPkt(serverIp, serverPort, clientIp, clientPort,
                seq = flow.serverNext, ack = flow.clientNext,
                flags = TCP_FIN or TCP_ACK, win = 0, data = ByteArray(0)))
        } catch (_: Exception) {}
        tcpFlows.remove(flowKey)?.socket?.close()
    }

    // ═══════════════════════════════════════════════════════════
    // UDP PROXY + ESP Detection
    // ═══════════════════════════════════════════════════════════

    private fun handleUdp(buf: ByteArray, ihl: Int, len: Int, srcIp: String, dstIp: String) {
        if (ihl + 8 >= len) return
        val srcPort = readU16(buf, ihl)
        val dstPort = readU16(buf, ihl + 2)
        val udpData = buf.copyOfRange(ihl + 8, len)
        val flowKey = "$srcIp:$srcPort→$dstIp:$dstPort"

        // === ESP detection + Freeze (outbound: game → server) ===
        GameProtocolEngine.processUdp(flowKey, udpData, true)
        val modified = PoseFreezeEngine.process(flowKey, udpData, true)

        // Forward đến real server
        val flow = udpFlows.getOrPut(flowKey) {
            UdpFlowState(dstIp, dstPort).also { f ->
                runCatching {
                    val sock = DatagramSocket()
                    vpn.protect(sock)
                    f.socket = sock
                    workers.submit { udpReceiveLoop(f, flowKey, srcIp, srcPort) }
                }
            }
        }
        flow.lastUsed = System.currentTimeMillis()

        try {
            val pkt = modified ?: udpData
            flow.socket?.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName(dstIp), dstPort))
        } catch (_: Exception) {}
    }

    private fun udpReceiveLoop(flow: UdpFlowState, flowKey: String, clientIp: String, clientPort: Int) {
        val buf = ByteArray(65535)
        val dp  = DatagramPacket(buf, buf.size)
        while (running.get()) {
            try {
                flow.socket?.soTimeout = 60_000
                flow.socket!!.receive(dp)
                val data = buf.copyOf(dp.length)
                val serverIp   = dp.address.hostAddress ?: continue
                val serverPort = dp.port

                // === ESP detection (inbound: server → game) ===
                GameProtocolEngine.processUdp(flowKey, data, false)
                val modified = PoseFreezeEngine.process(flowKey, data, false)
                val out = modified ?: data

                // Inject về TUN về phía game client
                writeTun(buildUdpPkt(serverIp, serverPort, clientIp, clientPort, out))
                flow.lastUsed = System.currentTimeMillis()

            } catch (e: SocketTimeoutException) { break }
            catch (e: Exception) { break }
        }
        flow.socket?.close()
    }

    // ─── Synchronized TUN write ───────────────────────────────

    private fun writeTun(pkt: ByteArray) {
        synchronized(tunLock) {
            runCatching { tunOut.write(pkt) }
        }
    }

    // ─── Build IP+TCP packet ──────────────────────────────────

    private fun buildTcpPkt(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        seq: Long, ack: Long,
        flags: Int, win: Int,
        data: ByteArray,
    ): ByteArray {
        val tcpLen = 20 + data.size
        val ipLen  = 20 + tcpLen
        val pkt    = ByteArray(ipLen)

        // IP header
        pkt[0] = 0x45.toByte()
        writeU16(pkt, 2, ipLen)
        pkt[6] = 0x40.toByte()  // Don't fragment
        pkt[8] = 64             // TTL
        pkt[9] = 6              // TCP
        writeIPBytes(pkt, 12, srcIp)
        writeIPBytes(pkt, 16, dstIp)
        writeU16(pkt, 10, ipChecksum(pkt, 20))

        // TCP header
        writeU16(pkt, 20, srcPort)
        writeU16(pkt, 22, dstPort)
        writeU32(pkt, 24, seq and 0xFFFFFFFFL)
        writeU32(pkt, 28, ack and 0xFFFFFFFFL)
        pkt[32] = 0x50.toByte()   // data offset = 5 × 4 = 20
        pkt[33] = (flags and 0xFF).toByte()
        writeU16(pkt, 34, win)
        // urgent = 0

        if (data.isNotEmpty()) System.arraycopy(data, 0, pkt, 40, data.size)

        writeU16(pkt, 36, tcpChecksum(pkt, 20, ipToBytes(srcIp), ipToBytes(dstIp), tcpLen))
        return pkt
    }

    // ─── Build IP+UDP packet ──────────────────────────────────

    private fun buildUdpPkt(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        data: ByteArray,
    ): ByteArray {
        val udpLen = 8 + data.size
        val ipLen  = 20 + udpLen
        val pkt    = ByteArray(ipLen)

        // IP
        pkt[0] = 0x45.toByte()
        writeU16(pkt, 2, ipLen)
        pkt[6] = 0x40.toByte()
        pkt[8] = 64
        pkt[9] = 17  // UDP
        writeIPBytes(pkt, 12, srcIp)
        writeIPBytes(pkt, 16, dstIp)
        writeU16(pkt, 10, ipChecksum(pkt, 20))

        // UDP
        writeU16(pkt, 20, srcPort)
        writeU16(pkt, 22, dstPort)
        writeU16(pkt, 24, udpLen)
        writeU16(pkt, 26, 0)  // checksum = 0 (optional for IPv4 UDP)
        if (data.isNotEmpty()) System.arraycopy(data, 0, pkt, 28, data.size)
        return pkt
    }

    // ─── Checksums ────────────────────────────────────────────

    private fun ipChecksum(pkt: ByteArray, ihl: Int): Int {
        var sum = 0
        for (i in 0 until ihl step 2)
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i+1].toInt() and 0xFF)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun tcpChecksum(pkt: ByteArray, tcpOff: Int, srcB: ByteArray, dstB: ByteArray, tcpLen: Int): Int {
        var sum = 0
        // Pseudo-header
        sum += ((srcB[0].toInt() and 0xFF) shl 8) or (srcB[1].toInt() and 0xFF)
        sum += ((srcB[2].toInt() and 0xFF) shl 8) or (srcB[3].toInt() and 0xFF)
        sum += ((dstB[0].toInt() and 0xFF) shl 8) or (dstB[1].toInt() and 0xFF)
        sum += ((dstB[2].toInt() and 0xFF) shl 8) or (dstB[3].toInt() and 0xFF)
        sum += 6; sum += tcpLen
        // TCP header + data
        var i = tcpOff
        while (i + 1 < pkt.size) {
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i+1].toInt() and 0xFF); i += 2
        }
        if (i < pkt.size) sum += (pkt[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    // ─── Cleanup ──────────────────────────────────────────────

    private fun cleanupFlows() {
        val now = System.currentTimeMillis()
        udpFlows.entries.filter { now - it.value.lastUsed > 120_000 }.forEach { (k, v) ->
            udpFlows.remove(k); v.socket?.close()
        }
        tcpFlows.entries.filter { now - it.value.lastUsed > 300_000 }.forEach { (k, v) ->
            tcpFlows.remove(k); v.socket?.close()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────

    private fun readU16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i+1].toInt() and 0xFF)
    private fun readU32(b: ByteArray, i: Int) =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i+1].toLong() and 0xFF) shl 16) or
        ((b[i+2].toLong() and 0xFF) shl 8) or (b[i+3].toLong() and 0xFF)
    private fun writeU16(b: ByteArray, i: Int, v: Int) { b[i] = (v shr 8).toByte(); b[i+1] = (v and 0xFF).toByte() }
    private fun writeU32(b: ByteArray, i: Int, v: Long) {
        b[i] = (v shr 24).toByte(); b[i+1] = (v shr 16).toByte()
        b[i+2] = (v shr 8).toByte(); b[i+3] = v.toByte()
    }
    private fun writeIPBytes(b: ByteArray, off: Int, ip: String) {
        ip.split(".").forEachIndexed { i, s -> b[off + i] = s.toInt().toByte() }
    }
    private fun ipToBytes(ip: String): ByteArray = ip.split(".").map { it.toInt().toByte() }.toByteArray()
    private fun formatIP(b: ByteArray, off: Int) =
        "${b[off].toInt() and 0xFF}.${b[off+1].toInt() and 0xFF}.${b[off+2].toInt() and 0xFF}.${b[off+3].toInt() and 0xFF}"

    // ─── Flow state classes ───────────────────────────────────

    inner class TcpFlowState(
        val remoteIp: String, val remotePort: Int,
        val clientIp: String, val clientPort: Int,
        clientIsn: Long,
    ) {
        var socket: Socket? = null
        @Volatile var connected = false
        val myIsn: Long = (System.nanoTime() and 0xFFFFFFFFL)
        @Volatile var clientNext: Long = clientIsn + 1L
        @Volatile var serverNext: Long = myIsn + 1L
        @Volatile var lastUsed = System.currentTimeMillis()
    }

    class UdpFlowState(val remoteIp: String, val remotePort: Int) {
        var socket: DatagramSocket? = null
        @Volatile var lastUsed = System.currentTimeMillis()
    }
}
