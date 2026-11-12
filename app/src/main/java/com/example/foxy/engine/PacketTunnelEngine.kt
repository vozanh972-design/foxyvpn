package com.example.foxy.engine

import android.net.VpnService
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PacketTunnelEngine — từ Pigboy gốc (PacketTunnelEngine.java).
 *
 * Fixes critical:
 *   1. workers = newCachedThreadPool() (gốc dùng thế, không phải 8 threads!)
 *      → Mỗi UDP/TCP flow cần 1 blocking receive thread → 8 threads = exhausted → 999+ ping
 *   2. UDP: protect() đúng cách
 *   3. TCP: gửi SYN-ACK ngay sau khi connect đến server thành công
 */
class PacketTunnelEngine(
    private val vpn:    VpnService,
    private val tunIn:  FileInputStream,
    private val tunOut: FileOutputStream,
) {
    companion object {
        private const val TAG           = "TunnelEngine"
        private const val TCP_FIN       = 0x01
        private const val TCP_SYN       = 0x02
        private const val TCP_RST       = 0x04
        private const val TCP_PSH       = 0x08
        private const val TCP_ACK       = 0x10
        private const val FLOW_IDLE_MS  = 120_000L   // 2 phút idle → xóa flow
        private const val CLEANUP_MS    = 30_000L
    }

    // ─── Giống bản gốc: CachedThreadPool = unlimited threads ────
    private val running    = AtomicBoolean(false)
    private val workers    = Executors.newCachedThreadPool()         // ← KEY FIX
    private val scheduler  = Executors.newSingleThreadScheduledExecutor()
    private val udpFlows   = ConcurrentHashMap<String, UdpFlowState>()
    private val tcpFlows   = ConcurrentHashMap<String, TcpFlowState>()
    private val tunLock    = Any()
    @Volatile private var lastClean = 0L

    // ─── Start ────────────────────────────────────────────────────

    fun start() {
        running.set(true)
        Log.i(TAG, "Tunnel started (CachedThreadPool)")

        // Maintenance: cleanup idle flows mỗi 30s
        scheduler.scheduleAtFixedRate({
            if (running.get()) cleanupFlows()
        }, 30, 30, TimeUnit.SECONDS)

        val buf = ByteArray(65535)
        try {
            while (running.get()) {
                val len = try { tunIn.read(buf) } catch (e: Exception) { break }
                if (len < 20) continue
                if ((buf[0].toInt() and 0xF0) shr 4 != 4) continue   // IPv4 only
                handleIPv4(buf.copyOf(len), len)                       // copy để thread-safe
            }
        } finally {
            Log.i(TAG, "Tunnel loop ended")
        }
    }

    fun stop() {
        running.set(false)
        workers.shutdownNow()
        scheduler.shutdownNow()
        udpFlows.values.forEach { runCatching { it.socket?.close() } }
        tcpFlows.values.forEach { runCatching { it.socket?.close() } }
        udpFlows.clear(); tcpFlows.clear()
        runCatching { tunIn.close() }
        runCatching { tunOut.close() }
    }

    // ─── IPv4 dispatch ─────────────────────────────────────────

    private fun handleIPv4(buf: ByteArray, len: Int) {
        val ihl   = (buf[0].toInt() and 0x0F) * 4
        val ipLen = readU16(buf, 2).coerceAtMost(len)
        if (ihl < 20 || ipLen < ihl) return

        // Fragment check: offset != 0 → skip (giống gốc: reassembleIpv4)
        val fragOff = readU16(buf, 6) and 0x1FFF
        if (fragOff != 0) return

        val proto = buf[9].toInt() and 0xFF
        val srcIp = formatIP(buf, 12)
        val dstIp = formatIP(buf, 16)

        when (proto) {
            6  -> handleTcp(buf, ihl, ipLen, srcIp, dstIp)
            17 -> handleUdp(buf, ihl, ipLen, srcIp, dstIp)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TCP PROXY — full SYN → SYN-ACK → data → FIN state machine
    // ══════════════════════════════════════════════════════════════

    private fun handleTcp(buf: ByteArray, ihl: Int, len: Int, srcIp: String, dstIp: String) {
        if (len < ihl + 20) return
        val srcPort    = readU16(buf, ihl)
        val dstPort    = readU16(buf, ihl + 2)
        val seqNum     = readU32(buf, ihl + 4)
        val dataOffset = ((buf[ihl + 12].toInt() and 0xF0) shr 4) * 4
        val flags      = buf[ihl + 13].toInt() and 0xFF
        val payloadOff = ihl + dataOffset
        val payload    = if (payloadOff < len) buf.copyOfRange(payloadOff, len) else ByteArray(0)
        val flowKey    = "$srcIp:$srcPort→$dstIp:$dstPort"

        // Scan payload để học game encryption key
        if (payload.isNotEmpty()) {
            workers.submit { GameProtocolEngine.scanTcp(flowKey, payload, dstPort) }
        }

        val isSyn = (flags and TCP_SYN) != 0 && (flags and TCP_ACK) == 0
        val isFin = (flags and TCP_FIN) != 0
        val isRst = (flags and TCP_RST) != 0

        if (isRst) {
            tcpFlows.remove(flowKey)?.also { it.isClosed = true; it.socket?.close() }
            return
        }

        if (isSyn) {
            // Đóng flow cũ nếu có
            tcpFlows.remove(flowKey)?.socket?.close()
            val flow = TcpFlowState(srcIp, srcPort, dstIp, dstPort, seqNum)
            tcpFlows[flowKey] = flow

            // Mở kết nối đến real server trong thread riêng
            workers.submit {
                try {
                    val sock = Socket()
                    sock.reuseAddress = true          // ← gốc smali setReuseAddress(true)
                    sock.bind(InetSocketAddress(0))   // ← gốc: bind trước khi protect
                    vpn.protect(sock)   // Bypass VPN loop!
                    sock.keepAlive = true             // ← gốc smali setKeepAlive(true)
                    sock.sendBufferSize    = 262_144  // ← gốc 0x40000
                    sock.receiveBufferSize = 262_144  // ← gốc 0x40000
                    sock.soTimeout = 1_000            // ← gốc 0x3e8=1000ms (KHÔNG PHẢI 30s!)
                    sock.tcpNoDelay = true
                    sock.connect(InetSocketAddress(dstIp, dstPort), 10_000)

                    flow.socket    = sock
                    flow.connected = true

                    // Gửi SYN-ACK về game
                    writeTun(buildTcpPkt(dstIp, dstPort, srcIp, srcPort,
                        seq  = flow.myIsn,
                        ack  = seqNum + 1L,
                        flags = TCP_SYN or TCP_ACK,
                        win  = 65535, data = ByteArray(0)))
                    flow.clientNext = seqNum + 1L
                    flow.serverNext = flow.myIsn + 1L

                    // Thread đọc server → inject vào TUN
                    workers.submit { tcpServerToClient(flow, flowKey, srcIp, srcPort, dstIp, dstPort) }

                } catch (e: Exception) {
                    Log.w(TAG, "TCP connect $dstIp:$dstPort failed: $e")
                    writeTun(buildTcpPkt(dstIp, dstPort, srcIp, srcPort,
                        seq = 0, ack = seqNum + 1L,
                        flags = TCP_RST or TCP_ACK, win = 0, data = ByteArray(0)))
                    tcpFlows.remove(flowKey)
                }
            }
            return
        }

        // Data / FIN
        val flow = tcpFlows[flowKey] ?: return

        // Đợi connected — không block main thread
        if (!flow.connected) return

        flow.lastUsed = System.currentTimeMillis()

        if (payload.isNotEmpty()) {
            // Gốc (TcpFlow line 120): writes: ConcurrentLinkedQueue — writer thread riêng
            // Ta: submit worker, synchronized(flow) để giữ ordering, không block main thread
            val data     = payload          // đã copyOfRange → independent copy
            val fKey     = flowKey
            val fRef     = flow
            val sIp      = srcIp; val sPort = srcPort
            val dIp      = dstIp; val dPort = dstPort
            workers.submit {
                try {
                    synchronized(fRef) {
                        fRef.socket?.outputStream?.write(data)
                        fRef.socket?.outputStream?.flush()
                        fRef.clientNext += data.size.toLong()
                    }
                    // ACK gửi SAU KHI write thành công (giống gốc)
                    writeTun(buildTcpPkt(dIp, dPort, sIp, sPort,
                        seq   = fRef.serverNext,
                        ack   = fRef.clientNext,
                        flags = TCP_ACK, win = 65535, data = ByteArray(0)))
                } catch (e: Exception) {
                    Log.w(TAG, "TCP forward error: $e")
                    tcpFlows.remove(fKey)?.socket?.close()
                }
            }
        }

        if (isFin) {
            flow.isClosed = true
            flow.clientNext++
            runCatching { flow.socket?.shutdownOutput() }
            writeTun(buildTcpPkt(dstIp, dstPort, srcIp, srcPort,
                seq   = flow.serverNext,
                ack   = flow.clientNext,
                flags = TCP_FIN or TCP_ACK, win = 0, data = ByteArray(0)))
            flow.serverNext++
            scheduler.schedule({ tcpFlows.remove(flowKey)?.socket?.close() }, 15, TimeUnit.SECONDS)
        }
    }

    /** Thread riêng: đọc từ real server → inject vào TUN về phía game. */
    private fun tcpServerToClient(
        flow: TcpFlowState,
        flowKey: String,
        clientIp: String, clientPort: Int,
        serverIp: String, serverPort: Int,
    ) {
        val buf = ByteArray(32768)
        try {
            val input = flow.socket?.inputStream ?: return
            // soTimeout=1s → loop check flow còn sống mỗi 1s (giống gốc smali)
            while (running.get() && !flow.isClosed) {
                val n = try {
                    input.read(buf)
                } catch (e: java.net.SocketTimeoutException) {
                    continue   // ← TIMEOUT = tiếp tục check, KHÔNG phải đóng!
                } catch (e: Exception) {
                    break      // Socket thực sự đóng
                }
                if (n <= 0) break
                val data = buf.copyOf(n)
                writeTun(buildTcpPkt(serverIp, serverPort, clientIp, clientPort,
                    seq   = flow.serverNext,
                    ack   = flow.clientNext,
                    flags = TCP_PSH or TCP_ACK,
                    win   = 65535, data = data))
                flow.serverNext += n.toLong()
                flow.lastUsed = System.currentTimeMillis()
            }
        } catch (_: Exception) {}

        // Server đóng → gửi FIN về game
        runCatching {
            writeTun(buildTcpPkt(serverIp, serverPort, clientIp, clientPort,
                seq   = flow.serverNext,
                ack   = flow.clientNext,
                flags = TCP_FIN or TCP_ACK, win = 0, data = ByteArray(0)))
        }
        tcpFlows.remove(flowKey)?.socket?.close()
    }

    // ══════════════════════════════════════════════════════════════
    // UDP PROXY + ESP Detection (game ports 10000-10100)
    // ══════════════════════════════════════════════════════════════

    private fun handleUdp(buf: ByteArray, ihl: Int, len: Int, srcIp: String, dstIp: String) {
        if (len < ihl + 8) return
        val srcPort = readU16(buf, ihl)
        val dstPort = readU16(buf, ihl + 2)
        val udpLen  = readU16(buf, ihl + 4).coerceAtMost(len - ihl)
        if (udpLen < 8) return
        val udpData = buf.copyOfRange(ihl + 8, ihl + udpLen)
        val flowKey = "$srcIp:$srcPort→$dstIp:$dstPort"

        // === ESP detection + Freeze rewrite (outbound: game → server) ===
        try { GameProtocolEngine.processUdp(flowKey, udpData, true) } catch (_: Exception) {}
        val modified = try { PoseFreezeEngine.process(flowKey, udpData, true) } catch (_: Exception) { null }

        // Lấy hoặc tạo UDP flow
        val flow = udpFlows.getOrPut(flowKey) {
            UdpFlowState().also { f ->
                try {
                    val sock = DatagramSocket()
                    vpn.protect(sock)   // Bypass VPN loop!
                    sock.soTimeout = 60_000
                    f.socket = sock
                    // Thread riêng đọc server → inject TUN (unlimited pool → không exhaust)
                    workers.submit { udpReceiveLoop(f, flowKey, srcIp, srcPort) }
                } catch (e: Exception) {
                    Log.w(TAG, "UDP socket create failed: $e")
                }
            }
        }
        flow.lastUsed = System.currentTimeMillis()

        // Forward đến real server
        try {
            val pkt = modified ?: udpData
            flow.socket?.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName(dstIp), dstPort))
        } catch (e: Exception) {
            Log.w(TAG, "UDP send failed: $e")
            udpFlows.remove(flowKey)?.socket?.close()
        }
    }

    /** Thread riêng: đọc UDP response từ server → inject vào TUN về phía game. */
    private fun udpReceiveLoop(flow: UdpFlowState, flowKey: String, clientIp: String, clientPort: Int) {
        val buf = ByteArray(65535)
        val dp  = DatagramPacket(buf, buf.size)
        try {
            while (running.get()) {
                flow.socket?.receive(dp) ?: break
                val n          = dp.length
                val data       = buf.copyOf(n)
                val serverIp   = dp.address?.hostAddress ?: continue
                val serverPort = dp.port

                // === ESP detection (inbound: server → game) ===
                try { GameProtocolEngine.processUdp(flowKey, data, false) } catch (_: Exception) {}
                val modified = try { PoseFreezeEngine.process(flowKey, data, false) } catch (_: Exception) { null }
                val out = modified ?: data

                // Inject về TUN
                writeTun(buildUdpPkt(serverIp, serverPort, clientIp, clientPort, out))
                flow.lastUsed = System.currentTimeMillis()
            }
        } catch (e: SocketTimeoutException) {
            Log.d(TAG, "UDP flow $flowKey timeout")
        } catch (e: Exception) {
            if (running.get()) Log.d(TAG, "UDP receive error $flowKey: ${e.message}")
        } finally {
            udpFlows.remove(flowKey)
            flow.socket?.close()
        }
    }

    // ─── Synchronized TUN write ───────────────────────────────

    private fun writeTun(pkt: ByteArray) {
        synchronized(tunLock) {
            runCatching { tunOut.write(pkt) }
        }
    }

    // ─── Build IP+TCP ─────────────────────────────────────────

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
        pkt[4] = (System.nanoTime() ushr 8).toByte()   // random ID
        pkt[5] = System.nanoTime().toByte()
        pkt[6] = 0x40.toByte()  // DF
        pkt[8] = 64             // TTL
        pkt[9] = 6              // TCP
        writeIPBytes(pkt, 12, srcIp)
        writeIPBytes(pkt, 16, dstIp)
        writeU16(pkt, 10, ipChecksum(pkt, 20))

        // TCP header
        writeU16(pkt, 20, srcPort)
        writeU16(pkt, 22, dstPort)
        writeU32(pkt, 24, seq  and 0xFFFFFFFFL)
        writeU32(pkt, 28, ack  and 0xFFFFFFFFL)
        pkt[32] = 0x50.toByte()   // data offset = 5
        pkt[33] = (flags and 0xFF).toByte()
        writeU16(pkt, 34, win)

        if (data.isNotEmpty()) System.arraycopy(data, 0, pkt, 40, data.size)

        writeU16(pkt, 36, tcpChecksum(pkt, 20, ipToBytes(srcIp), ipToBytes(dstIp), tcpLen))
        return pkt
    }

    // ─── Build IP+UDP ─────────────────────────────────────────

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
        pkt[4] = (System.nanoTime() ushr 8).toByte()
        pkt[5] = System.nanoTime().toByte()
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
        // checksum = 0 (optional for IPv4 UDP)
        if (data.isNotEmpty()) System.arraycopy(data, 0, pkt, 28, data.size)
        return pkt
    }

    // ─── Checksums ────────────────────────────────────────────

    private fun ipChecksum(pkt: ByteArray, ihl: Int): Int {
        var sum = 0L
        for (i in 0 until ihl step 2)
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun tcpChecksum(pkt: ByteArray, tcpOff: Int, srcB: ByteArray, dstB: ByteArray, tcpLen: Int): Int {
        var sum = 0L
        sum += ((srcB[0].toInt() and 0xFF) shl 8) or (srcB[1].toInt() and 0xFF)
        sum += ((srcB[2].toInt() and 0xFF) shl 8) or (srcB[3].toInt() and 0xFF)
        sum += ((dstB[0].toInt() and 0xFF) shl 8) or (dstB[1].toInt() and 0xFF)
        sum += ((dstB[2].toInt() and 0xFF) shl 8) or (dstB[3].toInt() and 0xFF)
        sum += 6; sum += tcpLen
        var i = tcpOff
        while (i + 1 < pkt.size) {
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF); i += 2
        }
        if (i < pkt.size) sum += (pkt[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    // ─── Cleanup ──────────────────────────────────────────────

    private fun cleanupFlows() {
        val now = System.currentTimeMillis()
        udpFlows.entries.removeIf { (_, v) ->
            (now - v.lastUsed > FLOW_IDLE_MS).also { if (it) v.socket?.close() }
        }
        tcpFlows.entries.removeIf { (_, v) ->
            (now - v.lastUsed > FLOW_IDLE_MS * 2).also { if (it) v.socket?.close() }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────

    private fun readU16(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    private fun readU32(b: ByteArray, i: Int) =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
        ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)
    private fun writeU16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v shr 8).toByte(); b[i + 1] = (v and 0xFF).toByte()
    }
    private fun writeU32(b: ByteArray, i: Int, v: Long) {
        b[i]     = (v shr 24).toByte(); b[i + 1] = (v shr 16).toByte()
        b[i + 2] = (v shr 8).toByte();  b[i + 3] = v.toByte()
    }
    private fun writeIPBytes(b: ByteArray, off: Int, ip: String) {
        ip.split(".").forEachIndexed { i, s -> b[off + i] = s.toInt().toByte() }
    }
    private fun ipToBytes(ip: String) = ip.split(".").map { it.toInt().toByte() }.toByteArray()
    private fun formatIP(b: ByteArray, off: Int) =
        "${b[off].toInt() and 0xFF}.${b[off+1].toInt() and 0xFF}" +
        ".${b[off+2].toInt() and 0xFF}.${b[off+3].toInt() and 0xFF}"

    // ─── Flow state ──────────────────────────────────────────

    inner class TcpFlowState(
        val clientIp: String, val clientPort: Int,
        val serverIp: String, val serverPort: Int,
        clientIsn: Long,
    ) {
        var socket: Socket? = null
        @Volatile var connected = false
        @Volatile var isClosed  = false   // ← set true khi nhận RST/FIN từ game
        val myIsn: Long = (System.nanoTime() and 0xFFFFFFFFL)
        @Volatile var clientNext: Long = clientIsn + 1L
        @Volatile var serverNext: Long = myIsn + 1L
        @Volatile var lastUsed = System.currentTimeMillis()
    }

    class UdpFlowState {
        var socket: DatagramSocket? = null
        @Volatile var lastUsed = System.currentTimeMillis()
    }
}
