package com.example.foxy.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * PoseFreezeEngine — Rewrite position trong outbound UDP packets khi Ghim bật.
 * Mỗi flow theo dõi vị trí bị "pin" khi freeze active.
 */
object PoseFreezeEngine {

    private data class FlowState(
        @Volatile var pinnedX: Long = 0,
        @Volatile var pinnedY: Long = 0,
        @Volatile var pinnedZ: Long = 0,
        @Volatile var hasPinned: Boolean = false,
    )

    private val flows = ConcurrentHashMap<String, FlowState>()

    /** Returns modified packet hoặc null nếu không cần modify. */
    fun process(flowKey: String, data: ByteArray, outbound: Boolean): ByteArray? {
        if (!outbound) return null  // Inbound: không modify

        val shouldFreeze = FreezeController.isFreezeActive()
        val flow = flows.getOrPut(flowKey) { FlowState() }

        // Lấy vị trí local player để pin
        val lp = EspEntityStore.snapshot().localPlayer
        if (lp != null && !shouldFreeze) {
            // Cập nhật pinned position khi không freeze
            flow.pinnedX = (lp.x * 1000).toLong()
            flow.pinnedY = (lp.y * 1000).toLong()
            flow.pinnedZ = (lp.z * 1000).toLong()
            flow.hasPinned = true
        }

        if (!shouldFreeze || !flow.hasPinned) return null  // Pass through

        // === FREEZE: thử rewrite position varints trong packet ===
        return rewritePosition(data, flow.pinnedX, flow.pinnedY, flow.pinnedZ)
    }

    private fun rewritePosition(data: ByteArray, px: Long, py: Long, pz: Long): ByteArray? {
        if (data.size < 10) return null
        // Parse header để tìm body start
        val headerSize = if ((data[0].toInt() and 0xFF) < 100) 10 else 8
        if (data.size <= headerSize) return null

        val newX = encodeVarint(zigZagEncode(px))
        val newY = encodeVarint(zigZagEncode(py))
        val newZ = encodeVarint(zigZagEncode(pz))

        // Thử scan body cho 3 zigzag varints liên tiếp hợp lệ ở đầu
        val cur = intArrayOf(headerSize)
        val body = data
        val x = VerifiedEspParser.readVarint(body, cur, body.size)
        val y = VerifiedEspParser.readVarint(body, cur, body.size)
        val z = VerifiedEspParser.readVarint(body, cur, body.size)
        if (x < 0 || y < 0 || z < 0) return null

        // Kiểm tra giá trị hợp lý (tránh rewrite packet không phải position)
        val fx = VerifiedEspParser.zigZagDecode(x) / 1000f
        val fy = VerifiedEspParser.zigZagDecode(y) / 1000f
        val fz = VerifiedEspParser.zigZagDecode(z) / 1000f
        if (Math.abs(fx) > 8000 || fy < -50 || fy > 500 || Math.abs(fz) > 8000) return null

        val newBody = newX + newY + newZ + body.copyOfRange(cur[0], body.size)
        if (newBody.size > 65000) return null

        val result = ByteArray(headerSize + newBody.size)
        System.arraycopy(data, 0, result, 0, headerSize)
        System.arraycopy(newBody, 0, result, headerSize, newBody.size)
        return result
    }

    fun reset() { flows.clear() }

    private fun zigZagEncode(n: Long): Long = if (n >= 0) n * 2 else (-n * 2 - 1)

    private fun encodeVarint(v: Long): ByteArray {
        val out = mutableListOf<Byte>()
        var rem = v
        while (rem > 127L) { out += ((rem and 0x7F).toInt() or 0x80).toByte(); rem = rem ushr 7 }
        out += rem.toByte()
        return out.toByteArray()
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}
