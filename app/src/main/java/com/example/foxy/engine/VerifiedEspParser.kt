package com.example.foxy.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * VerifiedEspParser — Port tu bytecode goc (VerifiedEspParser.java jadx + smali)
 *
 * parseEntityFrame: 2 format variant (fmt=0 va fmt=1), collect-then-update
 * Format 0, remote entity: skipVarints(3) + cursor+=3 + skipVarints(4)  [KHONG co if entityCount>1]
 * Format 0, local entity:  skipVarints(16)                               [KHONG co cursor++]
 * Format 1, entityCount<=1: skipVarints(3), i4++
 * Format 1, entityCount>1:  skipVarints(3) + cursor+=5 + readVarint + cursor+=1
 *
 * parseLocalKeyframe: fields.size == 23 (chinh xac tu smali line 496)
 * readAllVarints: break (khong return null) khi readVarint loi — match original readRemainingVarints
 */
object VerifiedEspParser {

    // ─── Dispatch ─────────────────────────────────────────────
    fun parseAny(data: ByteArray, outbound: Boolean): Int {
        if (data.size < 8) return 0
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

    // ─── ENTITY FRAME ─────────────────────────────────────────
    // Source: jadx decompile lines 69-173 + smali lines 115-453
    // Key: 2 format tries, collect-then-update
    private fun parseEntityFrame(data: ByteArray): Int {
        if (data.size < 15 || data.size > 65535) return 0

        val cur = intArrayOf(8)
        val marker     = readVarint(data, cur, data.size)
        val countField = readVarint(data, cur, data.size)
        if (marker != 0L || countField < 2 || (countField and 1L) != 0L || countField > 254L) return 0

        val entityCount = (countField ushr 1).toInt()
        val localId     = EspEntityStore.localEntityId()

        // Collect arrays (khong update store trong loop)
        val ids       = IntArray(entityCount)
        val positions = Array(entityCount) { FloatArray(3) }
        var parsed    = 0

        // Thu 2 formats, fmt=0 truoc
        for (fmt in 0..1) {
            val cur2   = intArrayOf(cur[0])   // Reset cursor cho moi format
            val seenId = BooleanArray(128)
            parsed = 0
            var success = true

            var i4 = 0
            inner@ while (i4 < entityCount) {
                val idV = readVarint(data, cur2, data.size)
                val xV  = readVarint(data, cur2, data.size)
                val yV  = readVarint(data, cur2, data.size)
                val zV  = readVarint(data, cur2, data.size)

                if (idV < 1 || idV > 127 || seenId[idV.toInt()]) { success = false; break@inner }
                if (xV < 0 || yV < 0 || zV < 0) { success = false; break@inner }

                val x = zigZag(xV) / 1000f
                val y = zigZag(yV) / 1000f
                val z = zigZag(zV) / 1000f
                if (!validPosition(x, y, z)) { success = false; break@inner }

                seenId[idV.toInt()] = true
                ids[parsed]        = idV.toInt()
                positions[parsed]  = floatArrayOf(x, y, z)

                // === SKIP EXTRA FIELDS ===
                if (fmt != 0) {
                    // FORMAT 1
                    // Jadx lines 115-132: skipVarints(3), no cursor+=3
                    if (!skipVarints(data, cur2, data.size, 3)) { success = false; break@inner }
                    if (entityCount <= 1) {
                        // Single entity: khong skip them, tang i4 va tiep
                        parsed++; i4++; continue@inner
                    } else {
                        // Multi entity: cursor+=5 + readVarint + cursor+=1
                        if (cur2[0] + 5 > data.size) { success = false; break@inner }
                        cur2[0] += 5
                        if (readVarint(data, cur2, data.size) < 0) break@inner
                        if (cur2[0] >= data.size) break@inner
                        cur2[0] += 1
                    }
                } else if (idV.toInt() == localId) {
                    // FORMAT 0 — LOCAL entity (jadx lines 134-139)
                    // skipVarints(16), KHONG co cursor++
                    if (!skipVarints(data, cur2, data.size, 16)) { success = false; break@inner }
                } else {
                    // FORMAT 0 — REMOTE entity (jadx lines 140-149)
                    // skipVarints(3) + bounds_check + cursor+=3 + skipVarints(4)
                    // KHONG co dieu kien if(entityCount>1) — skipVarints(4) LUON LUON chay
                    if (!skipVarints(data, cur2, data.size, 3)) { success = false; break@inner }
                    if (cur2[0] + 3 > data.size) break@inner
                    cur2[0] += 3
                    if (!skipVarints(data, cur2, data.size, 4)) { success = false; break@inner }
                }
                parsed++
                i4++
            }

            if (!success) continue

            // === Validate remaining bytes (tu smali lines 386-421) ===
            // Special case: format 1, single entity, cursor chinh xac o cuoi
            if (fmt == 1 && entityCount == 1 && cur2[0] == data.size) break  // SUCCESS

            // General case: 1-3 remaining, 1 final varint, cursor o cuoi
            val remaining = data.size - cur2[0]
            if (remaining in 1..3 && readVarint(data, cur2, data.size) >= 0 && cur2[0] == data.size) break  // SUCCESS
        }

        if (parsed == 0) return 0

        // === Update store CHI SAU KHI validate xong ===
        for (i in 0 until parsed) {
            val (x, y, z) = Triple(positions[i][0], positions[i][1], positions[i][2])
            if (ids[i] == localId) EspEntityStore.updateLocal(ids[i], x, y, z)
            else                   EspEntityStore.updateEntity(ids[i], x, y, z)
        }
        return parsed
    }

    // ─── LOCAL KEYFRAME ───────────────────────────────────────
    // Source: jadx lines 175-240 + smali lines 456-563
    private fun parseLocalKeyframe(data: ByteArray): Boolean {
        if (data.size < 32 || data.size > 160) return false
        if ((data[8].toInt() and 0x80) == 0) return false
        if ((data[9].toInt() and 0xFF) != 0x80) return false
        if ((data[10].toInt() and 0xFF) != 0x80) return false

        val cur = intArrayOf(8)
        val idV = readVarint(data, cur, data.size)
        if (idV < 0) return false

        // Doc chinh xac 23 varints (smali line 496: const 23, if-ne → fail)
        // readAllVarints: break (khong return null) khi loi, tra ve partial
        val fields = readAllVarints(data, cur[0], data.size) ?: return false
        if (fields.size != 23) return false

        val id = (idV and 0xFFFFFF).toInt()
        if (id < 1 || id > 127) return false

        val x = zigZagFloat(fields[0])
        val y = zigZagFloat(fields[1])
        val z = zigZagFloat(fields[2])
        if (!validPosition(x, y, z)) return false

        EspEntityStore.updateLocal(id, x, y, z)

        // Direction fields[13,14,15] — IEEE 754 raw int bits → Float.intBitsToFloat
        // Smali chain: ۦۢۦۥ(I)F → ۟۟ۡۤۨ(I)F → ۥۦۤۨ(I)F → java.lang.Float.intBitsToFloat(I)F
        // KHÔNG phải v/32767f (đó là normalized short, sai hoàn toàn!)
        val dx = java.lang.Float.intBitsToFloat(fields[13].toInt())
        val dy = java.lang.Float.intBitsToFloat(fields[14].toInt())
        val dz = java.lang.Float.intBitsToFloat(fields[15].toInt())
        val mag2 = dx * dx + dy * dy + dz * dz
        if (mag2.isFinite() && mag2 > 0.25f && mag2 < 4.0f) {
            EspEntityStore.updateDirection(dx, dy, dz)
        }
        return true
    }

    // ─── TEAM FRAME ───────────────────────────────────────────
    private fun parseTeamFrame(data: ByteArray): Boolean {
        if (data.size < 14 || data.size > 20) return false
        val fields = readAllVarints(data, 8, data.size) ?: return false
        if (fields.isEmpty() || fields.size > 4) return false
        val v        = fields[0]
        val entityId = (v and 0xFFFFFF).toInt()
        val teamId   = ((v ushr 24) and 0xFF).toInt()
        if (entityId < 1 || entityId > 127) return false
        EspEntityStore.updateTeam(entityId, teamId)
        return true
    }

    // ─── DAMAGE FRAME ─────────────────────────────────────────
    private fun parseDamageFrame(data: ByteArray): Boolean {
        if (data.size < 20 || data.size > 40) return false
        if ((data[8].toInt() and 0xFF) < 128) return false
        if ((data[9].toInt() and 0xFF) != 128 || (data[10].toInt() and 0xFF) != 128) return false
        val fields = readAllVarints(data, 8, data.size) ?: return false
        if (fields.size != 8) return false
        val entityId = (fields[0] and 0xFFFFFF).toInt()
        val damage   = minOf(200L, fields[5]).toInt()
        if (entityId < 1 || entityId > 127 || damage <= 0) return false
        EspEntityStore.applyDamage(entityId, damage)
        return true
    }

    // ─── HELPERS ──────────────────────────────────────────────

    fun zigZag(n: Long): Long       = (-(n and 1L)) xor (n ushr 1)
    fun zigZagFloat(n: Long): Float = zigZag(n) / 1000f
    // short2float: khong dung toShort() (copy tu smali: long-to-int → goi ham / 32767f)
    fun short2float(v: Int): Float  = v / 32767f

    fun validPosition(x: Float, y: Float, z: Float): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite()
            && abs(x) < 8000 && abs(z) < 8000
            && y >= -50f && y <= 500f
            && sqrt(x * x + z * z) >= 0.3
    }

    fun readVarint(data: ByteArray, cur: IntArray, end: Int): Long {
        var result = 0L
        var shift  = 0
        while (cur[0] < end && shift < 64) {
            val b = data[cur[0]++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        return -1L
    }

    // skipVarints tra ve boolean (copy tu smali skipVarints([B Cursor I I)Z)
    private fun skipVarints(data: ByteArray, cur: IntArray, end: Int, count: Int): Boolean {
        repeat(count) { if (readVarint(data, cur, end) < 0) return false }
        return true
    }

    // readAllVarints: BREAK (khong return null) khi readVarint loi
    // → tra ve partial result, match voi original readRemainingVarints bytecode
    // Original: khi readVarint < 0 → goto cursor_check → cursor >= end → return current list
    fun readAllVarints(data: ByteArray, start: Int, end: Int): LongArray? {
        if (start >= end) return LongArray(0)
        val result = mutableListOf<Long>()
        val cur    = intArrayOf(start)
        while (cur[0] < end) {
            val v = readVarint(data, cur, end)
            if (v < 0) break  // Break (khong return null) — match original behavior
            result += v
            if (result.size > 2048) break  // Cap nhu original (const 2048)
        }
        return if (result.isEmpty()) null else result.toLongArray()
    }
}
