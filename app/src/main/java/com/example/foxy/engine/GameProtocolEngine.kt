package com.example.foxy.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

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

            // Method 2: binary struct [2B][4B len=16 or 32][16B key] — giống gốc line 618
            for (k in 0..buf.size - 22) {
                val len = readInt32BE(buf, k + 2)
                if (len == 16 && k + 22 <= buf.size) addKey(buf.copyOfRange(k + 6, k + 22))
                // Gốc ALSO checks len==32 (key vẫn 16 bytes tính từ offset +6)
                if (len == 32 && k + 38 <= buf.size) addKey(buf.copyOfRange(k + 6, k + 22))
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

        // ── Case 1: Unencrypted (flags & 3 == 0) hoặc không parse được header ──
        // Logic gốc (GameProtocolEngine.java line 540):
        //   headerSize == 8 → parseAny(rawData)              [entity tại byte 8]
        //   headerSize != 8 → parseAny(withEspHeader(body))  [cắt header + prepend 8 zeros]
        var hits = 0
        if (header == null || (header.flags and 3) == 0) {
            hits = if (header == null || header.headerSize == 8) {
                // Header đúng 8 bytes → parser đọc từ data[8] = byte đầu payload ✓
                VerifiedEspParser.parseAny(data, outbound)
            } else {
                // Header không phải 8 bytes (ví dụ 10 bytes) → tách body rồi prepend 8 null bytes
                val body = data.copyOfRange(header.headerSize, data.size)
                val body2 = if (isGzip(body)) gunzip(body) ?: body else body
                VerifiedEspParser.parseAny(withEspHeader(body2), outbound)
            }
        }

        // ── Case 2: Encrypted → decrypt, gzip?, withEspHeader ──
        // Logic gốc (line 541-547): parseAny(withEspHeader(decryptedBody), z)
        if (hits == 0 && header != null && (header.flags and 3) != 0) {
            val body = decodeBody(header, data)
            if (body != null) {
                val body2 = if (isGzip(body)) gunzip(body) ?: body else body
                hits = VerifiedEspParser.parseAny(withEspHeader(body2), outbound)
            }
        }
    }

    /** Prepend 8 null bytes trước body — giống withEspHeader() trong gốc */
    private fun withEspHeader(body: ByteArray): ByteArray {
        val result = ByteArray(body.size + 8)
        System.arraycopy(body, 0, result, 8, body.size)
        return result
    }

    /** Kiểm tra gzip magic bytes (0x1f, 0x8b) */
    private fun isGzip(data: ByteArray) =
        data.size >= 2 && (data[0].toInt() and 0xFF) == 31 && (data[1].toInt() and 0xFF) == 139

    /** Decompress gzip, giới hạn 1MB như gốc */
    private fun gunzip(data: ByteArray): ByteArray? = try {
        val bos = ByteArrayOutputStream(maxOf(4096, data.size * 3))
        GZIPInputStream(ByteArrayInputStream(data)).use { gz ->
            val buf = ByteArray(2048)
            while (true) {
                val n = gz.read(buf)
                if (n <= 0) break
                bos.write(buf, 0, n)
                if (bos.size() > 1_048_576) return null  // cap 1MB như gốc
            }
        }
        bos.toByteArray()
    } catch (e: Exception) { null }

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
