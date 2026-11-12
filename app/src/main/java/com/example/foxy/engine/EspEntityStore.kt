package com.example.foxy.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.ConcurrentHashMap

/**
 * EspEntityStore — Copy y chang tu FULL_LOGIC/03_ESP_DETECT.java (phan EspEntityStore)
 * TTL: 15s entities, 60s local player
 */
object EspEntityStore {

    const val ENTITY_TTL_MS = 15_000L   // 15s → stale entity (goc la 15_000)
    const val LOCAL_TTL_MS  = 60_000L   // 60s → stale local

    private val entities    = ConcurrentHashMap<Int, Entity>()
    @Volatile private var localPlayer: Entity? = null
    @Volatile private var direction: Direction  = Direction(0f, 0f, 1f, 0f)

    // ─── Entity record ────────────────────────────────────────
    data class Entity(
        val id:        Int,
        val x:         Float,
        val y:         Float,
        val z:         Float,
        val vx:        Float     = 0f,
        val vy:        Float     = 0f,
        val vz:        Float     = 0f,
        val updatedAt: Long      = 0L,
        val name:      String    = "",
        val team:      Int       = 0,
        val health:    Int       = 200,
        val dead:      Boolean   = false,
    ) {
        // Tinh velocity tu diff position / diff time
        fun withPosition(nx: Float, ny: Float, nz: Float, ts: Long): Entity {
            val dt = maxOf(0.001f, (ts - updatedAt) / 1000f)
            return copy(
                x = nx, y = ny, z = nz,
                vx = (nx - x) / dt,
                vy = (ny - y) / dt,
                vz = (nz - z) / dt,
                updatedAt = ts,
                // Neu truoc do dead → reset HP (respawn)
                health = if (dead) 200 else health,
                dead = false
            )
        }
        fun withTeam(t: Int)   = copy(team = t)
        fun withName(n: String) = copy(name = n)
        fun withDamage(dmg: Int): Entity {
            val hp = maxOf(0, health - dmg)
            return copy(health = hp, dead = hp <= 0)
        }
    }

    // ─── Direction: camera look vector ───────────────────────
    data class Direction(val x: Float, val y: Float, val z: Float, val yaw: Float)

    // ─── Snapshot cho rendering ───────────────────────────────
    data class Snapshot(
        val localPlayer: Entity?,
        val entities:    List<Entity>,
        val yawRadians:  Float,
        val dirX:        Float,
        val dirY:        Float,
        val dirZ:        Float,
    )

    // ─── Update entity remote ─────────────────────────────────
    fun updateEntity(id: Int, x: Float, y: Float, z: Float) {
        if (id <= 0 || !validPos(x, y, z)) return
        val lp = localPlayer
        if (lp != null && lp.id == id) { updateLocal(id, x, y, z); return }
        val ts = System.currentTimeMillis()
        entities.compute(id) { _, existing ->
            existing?.withPosition(x, y, z, ts)
                ?: Entity(id, x, y, z, updatedAt = ts, name = "Entity$id")
        }
    }

    // ─── Update local player ──────────────────────────────────
    fun updateLocal(id: Int, x: Float, y: Float, z: Float) {
        if (!validPos(x, y, z)) return
        val ts   = System.currentTimeMillis()
        val prev = localPlayer
        val dt   = if (prev == null) 0.001f else maxOf(0.001f, (ts - prev.updatedAt) / 1000f)
        localPlayer = Entity(
            id        = id,
            x = x, y = y, z = z,
            vx        = if (prev == null) 0f else (x - prev.x) / dt,
            vy        = if (prev == null) 0f else (y - prev.y) / dt,
            vz        = if (prev == null) 0f else (z - prev.z) / dt,
            updatedAt = ts,
            name      = prev?.name   ?: "Me",
            team      = prev?.team   ?: 0,
            health    = if (prev == null) 200 else if (prev.dead) 200 else prev.health,
            dead      = false
        )
        if (id > 0) entities.remove(id)  // Local player khong hien trong entities
    }

    // ─── Update camera direction ──────────────────────────────
    fun updateDirection(dx: Float, dy: Float, dz: Float) {
        val mag = sqrt(dx * dx + dy * dy + dz * dz)
        if (mag > 0.001f) {
            direction = Direction(dx / mag, dy / mag, dz / mag,
                atan2(dx.toDouble(), dz.toDouble()).toFloat())
        }
    }

    fun updateYaw(yaw: Float) {
        if (!yaw.isFinite()) return
        direction = Direction(sin(yaw).toFloat(), 0f, cos(yaw).toFloat(), yaw)
    }

    fun updateTeam  (id: Int, team: Int)   { entities.computeIfPresent(id) { _, e -> e.withTeam(team) } }
    fun updateName  (id: Int, name: String){ entities.computeIfPresent(id) { _, e -> e.withName(name) } }
    fun applyDamage (id: Int, damage: Int) { entities.computeIfPresent(id) { _, e -> e.withDamage(damage) } }
    fun localEntityId(): Int               = localPlayer?.id ?: -1

    // ─── Snapshot cho rendering ───────────────────────────────
    // Copy y chang tu FULL_LOGIC: loc dead entities, 15s TTL
    fun snapshot(): Snapshot {
        val now  = System.currentTimeMillis()
        val lp   = localPlayer?.takeIf { now - it.updatedAt < LOCAL_TTL_MS }

        val alive = mutableListOf<Entity>()
        val stale = mutableListOf<Int>()
        for ((id, e) in entities) {
            if (now - e.updatedAt > ENTITY_TTL_MS) stale += id
            else if (!e.dead) alive += e  // Chi hien entities con song (goc)
        }
        stale.forEach { entities.remove(it) }

        val dir = direction
        return Snapshot(lp, alive, dir.yaw, dir.x, dir.y, dir.z)
    }

    fun reset() {
        entities.clear()
        localPlayer = null
        direction   = Direction(0f, 0f, 1f, 0f)
    }

    // validPos cua EspEntityStore — copy y chang tu EspEntityStore.java line 132:
    // isNaN/isInfinite checks + abs < 100000 cho CA 3 toa do
    fun validPos(x: Float, y: Float, z: Float): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite()
            && abs(x) < 100000 && abs(y) < 100000 && abs(z) < 100000
    }
}
