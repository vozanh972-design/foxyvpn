package com.example.foxy.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * VerifiedEspParser — Parse entity positions từ game UDP packets.
 *
 * 4 frame types:
 *   EntityFrame    — vị trí nhiều enemies
 *   LocalKeyframe  — vị trí + hướng nhìn local player
 *   TeamFrame      — team ID
 *   DamageFrame    — damage received
 */
object VerifiedEspParser {

    // ─── Main dispatch ────────────────────────────────────────

    /** Parse packet, cập nhật EspEntityStore. Returns số entities tìm được. */
    fun parseAny(data: ByteArray, outbound: Boolean): Int {
        if (data == null || data.size < 8) return 0
        var hits = 0
        if (outbound) {
            if (parseLocalKeyframe(data)) hits++
        } else {
            hits += parseEntityFrame(data)
            if (parseTeamFrame(data))   hits++
            if (parseDamageFrame(data)) hits++
        }
        return hits
    }

    // ─── Entity Frame — multiple enemy positions ──────────────

    private fun parseEntityFrame(data: ByteArray): Int {
        if (data.size < 15 || data.size > 65535) return 0
        val cur = intArrayOf(8)

        val marker     = readVarint(data, cur, data.size)
        val countField = readVarint(data, cur, data.size)
        if (marker != 0L || countField < 2 || (countField and 1L) != 0L || countField > 254L) return 0

        val entityCount = (countField ushr 1).toInt()
        val localId     = EspEntityStore.localEntityId()
        val seenId      = BooleanArray(128)
        var parsed = 0

        val cur2 = intArrayOf(cur[0])
        for (i in 0 until entityCount) {
            val idV = readVarint(data, cur2, data.size)
            val xV  = readVarint(data, cur2, data.size)
            val yV  = readVarint(data, cur2, data.size)
            val zV  = readVarint(data, cur2, data.size)
            if (idV < 1 || idV > 127 || seenId[idV.toInt()]) break
            if (xV < 0 || yV < 0 || zV < 0) break

            val x = zigZagDecode(xV) / 1000f
            val y = zigZagDecode(yV) / 1000f
            val z = zigZagDecode(zV) / 1000f
            if (!validPos(x, y, z)) break

            seenId[idV.toInt()] = true
            if (idV.toInt() == localId) {
                EspEntityStore.updateLocal(idV.toInt(), x, y, z)
                skipVarints(data, cur2, data.size, 16)
            } else {
                EspEntityStore.updateEntity(idV.toInt(), x, y, z)
                skipVarints(data, cur2, data.size, 3)
                cur2[0] += 3
                if (entityCount > 1 && cur2[0] < data.size) {
                    if (readVarint(data, cur2, data.size) < 0) break
                    if (cur2[0] < data.size) cur2[0]++
                }
            }
            parsed++
        }

        val remaining = data.size - cur2[0]
        if (remaining < 1 || remaining > 3) return 0
        if (readVarint(data, cur2, data.size) < 0) return 0
        return parsed
    }

    // ─── Local Keyframe — local player pos + look dir ─────────

    private fun parseLocalKeyframe(data: ByteArray): Boolean {
        if (data.size < 32 || data.size > 160) return false
        if ((data[8].toInt() and 0x80) == 0) return false
        if ((data[9].toInt() and 0xFF) != 0x80) return false
        if ((data[10].toInt() and 0xFF) != 0x80) return false

        val cur = intArrayOf(8)
        val idV = readVarint(data, cur, data.size)
        if (idV < 0) return false

        val fields = readAllVarints(data, cur[0], data.size) ?: return false
        if (fields.size < 23) return false

        val id = (idV and 0xFFFFFF).toInt()
        if (id < 1 || id > 127) return false

        val x = zigZagDecodeFloat(fields[0])
        val y = zigZagDecodeFloat(fields[1])
        val z = zigZagDecodeFloat(fields[2])
        if (!validPos(x, y, z)) return false

        EspEntityStore.updateLocal(id, x, y, z)

        // Direction fields[13..15]
        if (fields.size >= 16) {
            val dx = fields[13].toInt() / 32767f
            val dy = fields[14].toInt() / 32767f
            val dz = fields[15].toInt() / 32767f
            val mag2 = dx * dx + dy * dy + dz * dz
            if (mag2.isFinite() && mag2 > 0.25f && mag2 < 4f) {
                EspEntityStore.updateDirection(dx, dy, dz)
            }
        }
        return true
    }

    // ─── Team Frame ───────────────────────────────────────────

    private fun parseTeamFrame(data: ByteArray): Boolean {
        if (data.size < 14 || data.size > 20) return false
        val fields = readAllVarints(data, 8, data.size) ?: return false
        if (fields.size < 1 || fields.size > 4) return false
        val v      = fields[0]
        val id     = (v and 0xFFFFFF).toInt()
        val team   = ((v ushr 24) and 0xFF).toInt()
        if (id < 1 || id > 127) return false
        EspEntityStore.updateTeam(id, team)
        return true
    }

    // ─── Damage Frame ─────────────────────────────────────────

    private fun parseDamageFrame(data: ByteArray): Boolean {
        if (data.size < 20 || data.size > 40) return false
        if ((data[8].toInt() and 0xFF) < 128) return false
        if ((data[9].toInt() and 0xFF) != 128 || (data[10].toInt() and 0xFF) != 128) return false
        val fields = readAllVarints(data, 8, data.size) ?: return false
        if (fields.size != 8) return false
        val id     = (fields[0] and 0xFFFFFF).toInt()
        val damage = minOf(200L, fields[5]).toInt()
        if (id < 1 || id > 127 || damage <= 0) return false
        EspEntityStore.applyDamage(id, damage)
        return true
    }

    // ─── Helpers ──────────────────────────────────────────────

    private fun validPos(x: Float, y: Float, z: Float): Boolean =
        x.isFinite() && y.isFinite() && z.isFinite() &&
        abs(x) < 8000 && abs(z) < 8000 && y >= -50 && y <= 500

    fun zigZagDecode(n: Long): Long = -(n and 1L) xor (n ushr 1)
    fun zigZagDecodeFloat(n: Long): Float = zigZagDecode(n) / 1000f

    fun readVarint(data: ByteArray, cur: IntArray, end: Int): Long {
        var result = 0L
        var shift = 0
        while (cur[0] < end && shift < 64) {
            val b = data[cur[0]++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        return -1L
    }

    private fun skipVarints(data: ByteArray, cur: IntArray, end: Int, count: Int) {
        repeat(count) { readVarint(data, cur, end) }
    }

    private fun readAllVarints(data: ByteArray, start: Int, end: Int): LongArray? {
        val result = mutableListOf<Long>()
        val cur = intArrayOf(start)
        while (cur[0] < end) {
            val v = readVarint(data, cur, end)
            if (v < 0) return null
            result += v
        }
        return result.toLongArray()
    }
}
