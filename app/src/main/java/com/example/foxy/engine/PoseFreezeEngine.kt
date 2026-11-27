package com.example.foxy.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * PoseFreezeEngine — Xử lý outbound position packets cho toàn bộ Pigboy Features:
 * Freeze / AutoFreeze / AimLag / ScopeFreeze, Ghost, Under, Wall, Teleport.
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
        if (!outbound || data.size < 28) return null  // Inbound hoặc gói tin ngắn: pass through

        // Ở sảnh (chưa có localPlayer trong trận) -> 100% Passthrough
        val snap = EspEntityStore.snapshot()
        val lp = snap.localPlayer ?: return null

        val freezeOrGhost = FreezeController.isFreezeActive() || NetworkFeatures.options.ghost
        val under = NetworkFeatures.underActive()
        val wall = NetworkFeatures.options.wall
        val tpState = NetworkFeatures.tpState
        val tpGen = NetworkFeatures.tpGeneration

        val flow = flows.getOrPut(flowKey) { FlowState() }

        if (!freezeOrGhost) {
            flow.pinnedX = (lp.x * 1000).toLong()
            flow.pinnedY = (lp.y * 1000).toLong()
            flow.pinnedZ = (lp.z * 1000).toLong()
            flow.hasPinned = true
        }

        if (!freezeOrGhost && !under && !wall && tpState == 0) {
            return null
        }

        var targetX = if (freezeOrGhost && flow.hasPinned) flow.pinnedX else (lp.x * 1000).toLong()
        var targetY = if (freezeOrGhost && flow.hasPinned) flow.pinnedY else (lp.y * 1000).toLong()
        var targetZ = if (freezeOrGhost && flow.hasPinned) flow.pinnedZ else (lp.z * 1000).toLong()

        // Under: hạ trục Y xuống 1.2m
        if (under) {
            targetY -= 1200L
        }

        // Wall: đẩy tọa độ tiến theo hướng camera 2.5m
        if (wall) {
            targetX += (snap.dirX * 2500f).toLong()
            targetZ += (snap.dirZ * 2500f).toLong()
        }

        // Teleport: dịch chuyển theo speed * 5m dọc hướng nhìn
        if (tpState > 0) {
            val distMm = NetworkFeatures.teleportDistance() * 1000f
            targetX += (snap.dirX * distMm).toLong()
            targetZ += (snap.dirZ * distMm).toLong()
            NetworkFeatures.finishTeleport(tpGen)
        }

        return rewritePosition(data, targetX, targetY, targetZ)
    }

    private fun rewritePosition(data: ByteArray, px: Long, py: Long, pz: Long): ByteArray? {
        if (data.size < 10) return null
        val headerSize = if ((data[0].toInt() and 0xFF) < 100) 10 else 8
        if (data.size <= headerSize) return null

        val newX = encodeVarint(zigZagEncode(px))
        val newY = encodeVarint(zigZagEncode(py))
        val newZ = encodeVarint(zigZagEncode(pz))

        val cur = intArrayOf(headerSize)
        val x = VerifiedEspParser.readVarint(data, cur, data.size)
        val y = VerifiedEspParser.readVarint(data, cur, data.size)
        val z = VerifiedEspParser.readVarint(data, cur, data.size)
        if (x < 0 || y < 0 || z < 0) return null

        // Kiểm tra giá trị hợp lý để tránh rewrite nhầm gói không phải position
        val fx = VerifiedEspParser.zigZag(x) / 1000f
        val fy = VerifiedEspParser.zigZag(y) / 1000f
        val fz = VerifiedEspParser.zigZag(z) / 1000f
        if (Math.abs(fx) > 8000 || fy < -50 || fy > 500 || Math.abs(fz) > 8000) return null

        val newBody = newX + newY + newZ + data.copyOfRange(cur[0], data.size)
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
