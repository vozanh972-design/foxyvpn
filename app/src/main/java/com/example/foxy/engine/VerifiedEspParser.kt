package com.example.foxy.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * VerifiedEspParser — Copy y chang từ FULL_LOGIC/03_ESP_DETECT.java
 * Chỉ thay đổi tên package và syntax Java → Kotlin.
 */
object VerifiedEspParser {

    // ─── Dispatch: thử tất cả frame types ────────────────────
    fun parseAny(data: ByteArray, outbound: Boolean): Int {
        if (data.size < 8) return 0
        var hits = 0
        if (outbound) {
            // Chỉ parse LOCAL frame khi outbound (packet từ game client)
            if (parseLocalKeyframe(data)) hits++
        } else {
            // Inbound: parse entity frame, team, damage
            hits += parseEntityFrame(data)
            if (parseTeamFrame(data))   hits++
            if (parseDamageFrame(data)) hits++
        }
        return hits
    }

    // ─── ENTITY FRAME — Vị trí nhiều entities ────────────────
    // Nhận dạng: byte[8] = 0x00, entityCount >= 2 và chẵn
    // BUG FIX: Collect positions TRUOC → validate day du → THEN update store
    private fun parseEntityFrame(data: ByteArray): Int {
        if (data.size < 15 || data.size > 65535) return 0

        val cur = intArrayOf(8)
        val marker     = readVarint(data, cur, data.size)
        val countField = readVarint(data, cur, data.size)
        // marker phai = 0, countField = entityCount * 2 (chan, 2-254)
        if (marker != 0L || countField < 2 || (countField and 1L) != 0L || countField > 254L) return 0

        val entityCount = (countField ushr 1).toInt()
        val localId     = EspEntityStore.localEntityId()
        val seenId      = BooleanArray(128)

        // Collect truoc (khong update store trong loop)
        val ids       = IntArray(entityCount)
        val positions = Array(entityCount) { FloatArray(3) }
        var parsed    = 0

        val cur2 = intArrayOf(cur[0])
        for (i in 0 until entityCount) {
            val idV = readVarint(data, cur2, data.size)
            val xV  = readVarint(data, cur2, data.size)
            val yV  = readVarint(data, cur2, data.size)
            val zV  = readVarint(data, cur2, data.size)

            if (idV < 1 || idV > 127 || seenId[idV.toInt()]) break
            if (xV < 0 || yV < 0 || zV < 0) break

            // Convert: zigzag decode → millimeters → meters
            val x = zigZag(xV) / 1000f
            val y = zigZag(yV) / 1000f
            val z = zigZag(zV) / 1000f
            if (!validPosition(x, y, z)) break

            seenId[idV.toInt()] = true
            ids[parsed]        = idV.toInt()
            positions[parsed]  = floatArrayOf(x, y, z)

            // Skip extra fields theo format variant
            if (idV.toInt() == localId) {
                skipVarints(data, cur2, data.size, 16)  // Local: 16 extra varints
            } else {
                skipVarints(data, cur2, data.size, 3)   // Remote: 3 extra varints
                cur2[0] += 3                              // 3 bytes padding
                if (entityCount > 1) {
                    if (readVarint(data, cur2, data.size) < 0) break
                    cur2[0]++
                }
            }
            parsed++
        }

        // === Full validation TRUOC KHI update store ===
        val remaining = data.size - cur2[0]
        if (remaining < 1 || remaining > 3) return 0
        if (readVarint(data, cur2, data.size) < 0) return 0
        if (cur2[0] != data.size) return 0  // Extra check tu FULL_LOGIC

        // Chi update store khi da validate xong
        for (i in 0 until parsed) {
            val (x, y, z) = Triple(positions[i][0], positions[i][1], positions[i][2])
            if (ids[i] == localId) EspEntityStore.updateLocal(ids[i], x, y, z)
            else                   EspEntityStore.updateEntity(ids[i], x, y, z)
        }
        return parsed
    }

    // ─── LOCAL KEYFRAME — Vi tri + huong nhin local player ──
    // Nhan dang: byte[8] bit7=1, byte[9]=0x80, byte[10]=0x80
    private fun parseLocalKeyframe(data: ByteArray): Boolean {
        if (data.size < 32 || data.size > 160) return false
        if ((data[8].toInt() and 0x80) == 0) return false
        if ((data[9].toInt() and 0xFF) != 0x80) return false
        if ((data[10].toInt() and 0xFF) != 0x80) return false

        val cur = intArrayOf(8)
        val idV = readVarint(data, cur, data.size)
        if (idV < 0) return false

        // Doc 23 varints
        val fields = readAllVarints(data, cur[0], data.size) ?: return false
        if (fields.size != 23) return false

        val id = (idV and 0xFFFFFF).toInt()
        if (id < 1 || id > 127) return false

        // fields[0,1,2] = x, y, z (millimeters → meters)
        val x = zigZagFloat(fields[0])
        val y = zigZagFloat(fields[1])
        val z = zigZagFloat(fields[2])
        if (!validPosition(x, y, z)) return false

        EspEntityStore.updateLocal(id, x, y, z)

        // fields[13,14,15] = look direction vector
        val dx = short2float(fields[13].toInt())
        val dy = short2float(fields[14].toInt())
        val dz = short2float(fields[15].toInt())
        val mag2 = dx * dx + dy * dy + dz * dz
        // Validate: magnitude phai 0.25 < |v|^2 < 4
        if (mag2.isFinite() && mag2 > 0.25f && mag2 < 4.0f) {
            EspEntityStore.updateDirection(dx, dy, dz)
        }
        return true
    }

    // ─── TEAM FRAME — Team ID cua entity ─────────────────────
    // Nhan dang: packet length 14-20 bytes
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

    // ─── DAMAGE FRAME — Sat thuong nhan vao ──────────────────
    // Nhan dang: byte[8]>=0x80, byte[9]=0x80, byte[10]=0x80, length 20-40
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

    // ─── HELPERS (copy tu FULL_LOGIC) ─────────────────────────

    // ZigZag decode: (-(n&1)) ^ (n>>>1)
    fun zigZag(n: Long): Long = (-(n and 1L)) xor (n ushr 1)
    fun zigZagFloat(n: Long): Float = zigZag(n) / 1000f

    // Normalize 16-bit unsigned → [-1, 1]
    // Khong dung toShort() — copy y chang tu FULL_LOGIC short2float
    fun short2float(v: Int): Float = v / 32767f

    // Validate position: XZ [-8000, 8000], Y [-50, 500], dist >= 0.3
    fun validPosition(x: Float, y: Float, z: Float): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite()
            && abs(x) < 8000 && abs(z) < 8000
            && y >= -50f && y <= 500f
            && sqrt(x * x + z * z) >= 0.3
    }

    // Varint reader (Protobuf-style, 7 bits per byte, MSB=continue)
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

    private fun skipVarints(data: ByteArray, cur: IntArray, end: Int, count: Int) {
        repeat(count) { readVarint(data, cur, end) }
    }

    fun readAllVarints(data: ByteArray, start: Int, end: Int): LongArray? {
        val result = mutableListOf<Long>()
        val cur    = intArrayOf(start)
        while (cur[0] < end) {
            val v = readVarint(data, cur, end)
            if (v < 0) return null
            result += v
        }
        return result.toLongArray()
    }
}
