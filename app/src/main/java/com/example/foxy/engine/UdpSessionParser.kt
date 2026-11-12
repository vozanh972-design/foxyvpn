package com.example.foxy.engine

/**
 * UdpSessionParser — Parse header game UDP packet.
 * Tự học XOR key + field order bằng brute-force.
 */
class UdpSessionParser {

    @Volatile var xorKey: Int = -1
        private set
    @Volatile var order: IntArray? = null
        private set
    var preferredOrder: IntArray? = null

    private val keys = mutableListOf<ByteArray>()
    @Volatile var sessionKey: ByteArray = ByteArray(0)
        private set

    // ─── Parse header ─────────────────────────────────────────

    @Synchronized
    fun parse(data: ByteArray, offset: Int, len: Int): ParsedHeader? {
        if (len < 8 || offset < 0 || offset + len > data.size) return null
        val v = data[offset].toInt() and 0xFF
        if (v < 90 || v >= 110) return null

        val expectedCrc = TeaCipher.headerChecksum(data, offset, len)
        if (data[offset + 1] != expectedCrc) return null

        val headerSize = if (v < 100) 10 else 8
        if (len < headerSize) return null

        if (xorKey < 0 || order == null) discoverHeader(data, offset, len, headerSize)

        val xk = xorKey
        val ord = order ?: return null
        return parseWithOrder(data, offset, len, headerSize, xk, ord)
    }

    // ─── Brute-force discover ─────────────────────────────────

    private fun discoverHeader(data: ByteArray, offset: Int, len: Int, headerSize: Int) {
        val b2 = data[offset + 2].toInt() and 0xFF
        val candidates = LinkedHashSet<Int>()
        for (i in 0..5) candidates += b2 xor i
        synchronized(keys) { keys.forEach { candidates += it[0].toInt() and 0xFF } }
        for (i in 0..255) candidates += i

        val orders = if (preferredOrder != null) arrayOf(preferredOrder!!)
                     else candidateOrders(headerSize)

        for (xk in candidates) {
            for (ord in orders) {
                val h = parseWithOrder(data, offset, len, headerSize, xk, ord) ?: continue
                if (isPriority(h.command)) {
                    xorKey = xk; order = ord.clone(); return
                } else if (xorKey < 0) {
                    xorKey = xk; order = ord.clone()
                }
            }
        }
    }

    private fun parseWithOrder(
        data: ByteArray, offset: Int, len: Int,
        headerSize: Int, xk: Int, ord: IntArray,
    ): ParsedHeader? {
        var pos = offset + 2
        var flags = -1; var command = -1; var seq = -1; var bodyLen = -1

        for (fieldIdx in ord) {
            if (pos >= offset + len) return null
            when (fieldIdx) {
                0 -> { flags   = (data[pos].toInt() and 0xFF) xor xk; pos += 1 }
                1 -> { command = readXorShort(data, pos, xk); pos += 2 }
                2 -> { pos += 2 }  // unknown field
                3 -> { seq     = (data[pos].toInt() and 0xFF) xor xk; pos += 1 }
                4 -> { bodyLen = readXorShort(data, pos, xk); pos += 2 }
                else -> return null
            }
        }
        if (pos != offset + headerSize) return null
        if (flags < 0 || flags >= 6) return null
        if (seq < 0 || seq >= 4) return null
        if (command < 1 || command > 2500) return null
        if (bodyLen != len - headerSize) return null
        return ParsedHeader(command, bodyLen, headerSize, seq, xk, flags)
    }

    // ─── Key management ───────────────────────────────────────

    @Synchronized
    fun addKey(key: ByteArray) {
        val k16 = key.copyOf(16)
        if (keys.any { it.contentEquals(k16) }) return
        keys.add(k16)
        if (keys.size > 32) keys.removeAt(0)
        sessionKey = k16
    }

    // ─── Helpers ──────────────────────────────────────────────

    private fun readXorShort(d: ByteArray, i: Int, xk: Int): Int =
        ((d[i].toInt() and 0xFF) xor xk) or (((d[i+1].toInt() and 0xFF) xor xk) shl 8)

    private fun isPriority(cmd: Int): Boolean =
        (cmd in 101..105) || cmd == 137 || cmd == 138 || cmd == 2001 || cmd == 2002 || cmd == 2012

    private fun candidateOrders(headerSize: Int): Array<IntArray> {
        // headerSize 10 → 5 fields: [0,1,2,3,4], headerSize 8 → 4 fields: [0,1,3,4]
        return if (headerSize == 10) {
            arrayOf(
                intArrayOf(0,1,2,3,4), intArrayOf(0,2,1,3,4), intArrayOf(3,0,1,2,4),
                intArrayOf(3,2,0,1,4), intArrayOf(0,1,3,2,4), intArrayOf(1,0,2,3,4),
            )
        } else {
            arrayOf(
                intArrayOf(0,1,3,4), intArrayOf(3,0,1,4), intArrayOf(0,3,1,4),
                intArrayOf(1,0,3,4), intArrayOf(3,1,0,4), intArrayOf(0,1,4,3),
            )
        }
    }
}
