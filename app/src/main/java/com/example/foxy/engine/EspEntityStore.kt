package com.example.foxy.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * EspEntityStore — Lưu trữ vị trí tất cả entities trong game.
 * Cập nhật từ VerifiedEspParser, đọc từ EspHudView.
 */
object EspEntityStore {

    private const val ENTITY_TTL_MS = 15_000L
    private const val LOCAL_TTL_MS  = 60_000L

    data class Entity(
        val id:        Int,
        val x:         Float,   // World X (meters)
        val y:         Float,   // World Y (height, meters)
        val z:         Float,   // World Z (meters)
        val vx:        Float = 0f,  // Velocity X (m/s)
        val vy:        Float = 0f,
        val vz:        Float = 0f,
        val health:    Int   = 200,
        val team:      Int   = 0,
        val name:      String = "",
        val dead:      Boolean = false,
        val updatedAt: Long = System.currentTimeMillis(),
    ) {
        fun withPosition(nx: Float, ny: Float, nz: Float, ts: Long): Entity {
            val dt = ((ts - updatedAt) / 1000f).coerceAtLeast(0.001f)
            return copy(
                x = nx, y = ny, z = nz,
                vx = (nx - x) / dt,
                vy = (ny - y) / dt,
                vz = (nz - z) / dt,
                dead = false,
                updatedAt = ts,
            )
        }
    }

    data class Direction(
        val x: Float = 0f,
        val y: Float = 0f,
        val z: Float = 1f,
        val yaw: Float = 0f,   // atan2(x, z)
    )

    data class Snapshot(
        val localPlayer: Entity?,
        val entities:    List<Entity>,
        val yawRadians:  Float,
        val dirX:        Float,
        val dirY:        Float,
        val dirZ:        Float,
    )

    private val entities  = ConcurrentHashMap<Int, Entity>()
    @Volatile private var localPlayer: Entity? = null
    @Volatile private var direction = Direction()

    // ─── Update ───────────────────────────────────────────────

    fun updateEntity(id: Int, x: Float, y: Float, z: Float) {
        if (id <= 0 || id > 127) return
        val lp = localPlayer
        if (lp != null && lp.id == id) { updateLocal(id, x, y, z); return }
        val ts = System.currentTimeMillis()
        entities.compute(id) { _, existing ->
            if (existing == null) Entity(id, x, y, z, updatedAt = ts)
            else existing.withPosition(x, y, z, ts)
        }
    }

    fun updateLocal(id: Int, x: Float, y: Float, z: Float) {
        val ts = System.currentTimeMillis()
        val prev = localPlayer
        val dt = if (prev == null) 0.001f else ((ts - prev.updatedAt) / 1000f).coerceAtLeast(0.001f)
        localPlayer = Entity(
            id = id, x = x, y = y, z = z,
            vx = if (prev == null) 0f else (x - prev.x) / dt,
            vy = if (prev == null) 0f else (y - prev.y) / dt,
            vz = if (prev == null) 0f else (z - prev.z) / dt,
            health = prev?.health ?: 200,
            team   = prev?.team   ?: 0,
            name   = prev?.name   ?: "Me",
            dead   = false,
            updatedAt = ts,
        )
        if (id > 0) entities.remove(id)
    }

    fun updateDirection(dx: Float, dy: Float, dz: Float) {
        val mag = sqrt(dx * dx + dy * dy + dz * dz)
        if (mag < 1e-4f) return
        direction = Direction(dx / mag, dy / mag, dz / mag, Math.atan2(dx.toDouble(), dz.toDouble()).toFloat())
    }

    fun updateYaw(yaw: Float) {
        if (!yaw.isFinite()) return
        direction = Direction(Math.sin(yaw.toDouble()).toFloat(), 0f, Math.cos(yaw.toDouble()).toFloat(), yaw)
    }

    fun updateTeam(id: Int, team: Int) { entities.computeIfPresent(id) { _, e -> e.copy(team = team) } }
    fun updateName(id: Int, name: String) { entities.computeIfPresent(id) { _, e -> e.copy(name = name) } }
    fun applyDamage(id: Int, damage: Int) {
        entities.computeIfPresent(id) { _, e ->
            val hp = (e.health - damage).coerceAtLeast(0)
            e.copy(health = hp, dead = hp <= 0)
        }
    }

    fun localEntityId(): Int = localPlayer?.id ?: -1

    // ─── Snapshot cho rendering ───────────────────────────────

    fun snapshot(): Snapshot {
        val now = System.currentTimeMillis()
        val lp = localPlayer?.takeIf { now - it.updatedAt < LOCAL_TTL_MS }
        val alive = mutableListOf<Entity>()
        val stale = mutableListOf<Int>()
        for ((id, e) in entities) {
            if (now - e.updatedAt > ENTITY_TTL_MS) stale += id
            else if (!e.dead) alive += e
        }
        stale.forEach { entities.remove(it) }
        val dir = direction
        return Snapshot(lp, alive, dir.yaw, dir.x, dir.y, dir.z)
    }

    fun reset() {
        entities.clear()
        localPlayer = null
        direction = Direction()
    }
}
