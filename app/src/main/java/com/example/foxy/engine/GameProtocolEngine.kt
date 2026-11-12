package com.example.foxy.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * GameProtocolEngine — Điều phối xử lý packet:
 *   - scanTcp: học AES/TEA key từ TCP stream
 *   - processUdp: detect + parse ESP từ UDP packet
 */
object GameProtocolEngine {

    private val sessions    = ConcurrentHashMap<String, UdpSessionParser>()
    private val globalKeys  = CopyOnWriteArrayList<ByteArray>()
    private val tcpBuffers  = ConcurrentHashMap<String, ByteArray>()

    @Volatile var learnedOrder: IntArray? = null
        private set

    // ─── Scan TCP cho encryption keys ────────────────────────

    fun scanTcp(flowKey: String, data: ByteArray, port: Int) {
        if (port == 80 || port == 443 || port == 853) return

        synchronized(this) {
            // Sliding buffer 4096 bytes
            val prev = tcpBuffers[flowKey] ?: ByteArray(0)
            val total = prev.size + data.size
            val bufLen = minOf(4096, total)
            val buf = ByteArray(bufLen)
            val skip = total - bufLen
            for (i in 0 until bufLen) {
                val idx = skip + i
                buf[i] = if (idx < prev.size) prev[idx] else data[idx - prev.size]
            }
            if (tcpBuffers.size > 128) tcpBuffers.clear()
            tcpBuffers[flowKey] = buf

            // Method 1: 32-char hex string → 16-byte key
            val str = String(buf, Charsets.ISO_8859_1)
            var i = 0
            while (i + 32 <= str.length) {
                val leftOk  = i == 0 || str[i - 1].digitToIntOrNull(16) == null
                val rightOk = i + 32 >= str.length || str[i + 32].digitToIntOrNull(16) == null
                if (leftOk && rightOk && str.substring(i, i + 32).all { it.digitToIntOrNull(16) != null }) {
                    val key = ByteArray(16) { j ->
                        ((str[i + j * 2].digitToInt(16) shl 4) or str[i + j * 2 + 1].digitToInt(16)).toByte()
                    }
                    addKey(key)
                }
                i++
            }

            // Method 2: binary struct [2B][4B len=16][16B key]
            for (k in 0..buf.size - 22) {
                val len = readInt32BE(buf, k + 2)
                if (len == 16 && k + 22 <= buf.size) addKey(buf.copyOfRange(k + 6, k + 22))
            }

            // Method 3: UDP header field order learning
            for (k in 0..buf.size - 7) {
                if ((buf[k].toInt() and 0xFF) == 5) {
                    val perm = IntArray(5)
                    val used = BooleanArray(5)
                    var valid = true
                    for (j in 0..4) {
                        val v = (buf[k + 1 + j].toInt() and 0xFF) - 48
                        if (v < 0 || v > 4 || used[v]) { valid = false; break }
                        used[v] = true; perm[j] = v
                    }
                    if (valid) learnedOrder = perm
                }
            }
        }
    }

    private fun addKey(raw: ByteArray) {
        val key = raw.copyOf(16)
        synchronized(globalKeys) {
            if (globalKeys.any { it.contentEquals(key) }) return
            globalKeys.add(key)
            if (globalKeys.size > 32) globalKeys.removeAt(0)
            sessions.values.forEach { it.addKey(key) }
        }
    }

    // ─── Process UDP ──────────────────────────────────────────

    fun processUdp(flowKey: String, data: ByteArray, outbound: Boolean) {
        if (data.size < 8) return
        val parser = getOrCreate(flowKey)

        val header = parser.parse(data, 0, data.size)
        if (header != null) {
            FreezeController.observeCommand(header.command, outbound)
            NetworkFeatures.observe(header.command, outbound)
        }

        // Direct ESP parse
        var hits = VerifiedEspParser.parseAny(data, outbound)

        // Try decode + parse if encrypted
        if (hits == 0 && header != null && (header.flags and 3) != 0) {
            val body = decodeBody(header, data)
            if (body != null) hits = VerifiedEspParser.parseAny(body, outbound)
        }
    }

    private fun decodeBody(header: ParsedHeader, data: ByteArray): ByteArray? {
        val bodyStart = header.headerSize
        val bodyEnd   = bodyStart + header.bodyLen
        if (bodyEnd > data.size) return null
        val body = data.copyOfRange(bodyStart, bodyEnd)
        if ((header.flags and 1) == 0) return body          // Not encrypted
        // Try all known keys
        synchronized(globalKeys) {
            for (k in globalKeys.indices.reversed()) {
                val dec = TeaCipher.decryptCbc(body, globalKeys[k])
                if (dec != null) return dec
            }
        }
        return null
    }

    fun getOrCreate(flowKey: String): UdpSessionParser {
        return sessions.getOrPut(flowKey) {
            UdpSessionParser().also { p ->
                learnedOrder?.let { p.preferredOrder = it }
                synchronized(globalKeys) { globalKeys.forEach { p.addKey(it) } }
            }
        }
    }

    fun reset() {
        sessions.clear()
        synchronized(globalKeys) { globalKeys.clear() }
        tcpBuffers.clear()
        learnedOrder = null
        EspEntityStore.reset()
    }

    private fun readInt32BE(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 24) or
        ((b[i+1].toInt() and 0xFF) shl 16) or
        ((b[i+2].toInt() and 0xFF) shl 8) or
        (b[i+3].toInt() and 0xFF)
}

/** Result of parsing one UDP header */
data class ParsedHeader(
    val command:    Int,
    val bodyLen:    Int,
    val headerSize: Int,
    val sequence:   Int,
    val xorKey:     Int,
    val flags:      Int,
)
