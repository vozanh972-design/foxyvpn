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
        val id:          Int,
        val x:           Float,
        val y:           Float,
        val z:           Float,
        val vx:          Float   = 0f,
        val vy:          Float   = 0f,
        val vz:          Float   = 0f,
        val updatedAt:   Long    = 0L,
        val name:        String  = "",
        val team:        Int     = 0,
        val health:      Int     = 200,
        val maxHealth:   Int     = 200,
        val dead:        Boolean = false,
        val knocked:     Boolean = false,   // bị gục (knocked down, chưa chết hẳn)
        val weaponType:  Int     = 0,       // 0=unknown,1=AR,2=SMG,3=Sniper,4=SG,5=Pistol,6=Melee,7=Launcher,8=Grenade
        val factionId:   Int     = -1,      // factionId từ game (-1 = chưa biết)
        // ── State từ action_flag packet (S2C_UDP_Optimize_PlayerState_Res) ──
        // Bit flags: 0=fire, 1=aim/scope, 2=crouch, 3=prone, 4=sprint, 5=moving
        val actionFlag:  Int     = 0,
        val moveSpeed:   Float   = 0f,      // velocity magnitude từ packet
    ) {
        // ── Action flag helpers ──────────────────────────────────
        val isFiring:   Boolean get() = (actionFlag and 0x01) != 0
        val isAiming:   Boolean get() = (actionFlag and 0x02) != 0
        val isCrouching:Boolean get() = (actionFlag and 0x04) != 0
        val isProne:    Boolean get() = (actionFlag and 0x08) != 0
        val isSprinting:Boolean get() = (actionFlag and 0x10) != 0
        val isMoving:   Boolean get() = (actionFlag and 0x20) != 0 || moveSpeed > 0.5f

        /** State text ngắn để hiện trên ESP */
        val stateLabel: String get() = when {
            knocked     -> "KNOCKED"
            isProne     -> "PRONE"
            isCrouching -> "CROUCH"
            isFiring && isAiming -> "SNIPE"
            isFiring    -> "FIRE"
            isAiming    -> "AIM"
            isSprinting -> "SPRINT"
            isMoving    -> "MOVE"
            else        -> ""
        }
        // Tinh velocity tu diff position / diff time
        fun withPosition(nx: Float, ny: Float, nz: Float, ts: Long): Entity {
            val dt = maxOf(0.001f, (ts - updatedAt) / 1000f)
            val dvx = (nx - x) / dt
            val dvz = (nz - z) / dt
            val spd = kotlin.math.sqrt(dvx * dvx + dvz * dvz)
            return copy(
                x = nx, y = ny, z = nz,
                vx = dvx,
                vy = (ny - y) / dt,
                vz = dvz,
                updatedAt = ts,
                // speed từ position diff nếu chưa có từ packet
                moveSpeed = if (moveSpeed == 0f && spd < 50f) spd else moveSpeed,
                // Neu truoc do dead → reset HP (respawn)
                health = if (dead) 200 else health,
                dead   = false,
                knocked = false,
            )
        }
        fun withTeam(t: Int)             = copy(team = t)
        fun withName(n: String)          = copy(name = n)
        fun withFaction(f: Int)          = copy(factionId = f)
        fun withWeapon(wt: Int)          = copy(weaponType = wt)
        fun withMaxHp(mhp: Int)          = copy(maxHealth = mhp.coerceIn(1, 500))
        fun withKnocked(k: Boolean)      = copy(knocked = k, dead = false)
        fun withActionFlag(flag: Int, spd: Float = moveSpeed) = copy(actionFlag = flag, moveSpeed = spd)
        fun withDamage(dmg: Int): Entity {
            val hp = maxOf(0, health - dmg)
            val nowKnocked = hp <= 0 && !dead
            return copy(health = hp, knocked = nowKnocked, dead = false)
        }

        /** HP % [0.0 .. 1.0] */
        val hpRatio: Float get() = (health.coerceIn(0, maxHealth).toFloat() / maxHealth.toFloat()).coerceIn(0f, 1f)

        /** Label ngắn loại vũ khí để hiển thị trên ESP (kèm icon unicode) */
        val weaponLabel: String get() = when (weaponType) {
            1 -> "AR"    // Assault Rifle
            2 -> "SMG"   // Sub Machine Gun
            3 -> "SR"    // Sniper Rifle
            4 -> "SG"    // Shotgun
            5 -> "P"     // Pistol
            6 -> "M"     // Melee / Nấm đấm
            7 -> "GL"    // Grenade Launcher
            8 -> "GR"    // Grenade / Lựu đạn
            else -> ""   // Chưa biết / không cầm gì
        }

        /**
         * Icon Unicode nhỏ hiện trên nameplate thay chỗ khoảng cách.
         * Dùng emoji đơn giản để hiện loại súng / trạng thái cầm vũ khí.
         *   weaponType=0 (không cầm gì) → không có icon
         *   weaponType=6 (melee/nấm đấm) → "✊"
         *   Còn lại → icon tương ứng
         */
        val weaponIcon: String get() = when (weaponType) {
            1 -> "\uD83D\uDD2B"   // 🔫 AR
            2 -> "\uD83D\uDD2B"   // 🔫 SMG
            3 -> "\uD83D\uDD2B"   // 🔫 SR
            4 -> "\uD83D\uDD2B"   // 🔫 SG
            5 -> "\uD83D\uDD2B"   // 🔫 Pistol
            6 -> "\u270A"          // ✊ Melee/nấm đấm
            7 -> "\uD83D\uDCA5"   // 💥 Launcher
            8 -> "\uD83D\uDCA3"   // 💣 Grenade
            else -> ""
        }

        /** Height offset thật dựa trên posture (ảnh hưởng đến vị trí skeleton) */
        val poseHeightMult: Float get() = when {
            isProne     -> 0.3f   // Nằm: chiều cao ~30% standing
            isCrouching -> 0.65f  // Ngồi: chiều cao ~65% standing
            else        -> 1.0f   // Đứng/chạy: 100%
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
            name      = prev?.name      ?: "Me",
            team      = prev?.team      ?: 0,
            health    = if (prev == null) 200 else if (prev.dead) 200 else prev.health,
            maxHealth = prev?.maxHealth ?: 200,
            weaponType= prev?.weaponType?: 0,
            factionId = prev?.factionId ?: -1,
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

    fun updateTeam      (id: Int, team: Int)    { entities.computeIfPresent(id) { _, e -> e.withTeam(team) } }
    fun updateName      (id: Int, name: String) { entities.computeIfPresent(id) { _, e -> e.withName(name) } }
    fun updateFaction   (id: Int, fac: Int)     { entities.computeIfPresent(id) { _, e -> e.withFaction(fac) } }
    fun updateWeapon    (id: Int, wt: Int)      { entities.computeIfPresent(id) { _, e -> e.withWeapon(wt) } }
    fun updateMaxHp     (id: Int, mhp: Int)     { entities.computeIfPresent(id) { _, e -> e.withMaxHp(mhp) } }
    fun setKnocked      (id: Int, k: Boolean)   { entities.computeIfPresent(id) { _, e -> e.withKnocked(k) } }
    fun applyDamage     (id: Int, damage: Int)  { entities.computeIfPresent(id) { _, e -> e.withDamage(damage) } }
    fun updateActionFlag(id: Int, flag: Int, speed: Float = 0f) {
        entities.computeIfPresent(id) { _, e -> e.withActionFlag(flag, speed) }
    }

    /**
     * Upsert entity với tên thật từ visibility packet.
     * Nếu entity chưa tồn tại → tạo mới với position (0,0,0) và tên đã biết.
     * Khi parseEntityFrame sau đó cập nhật vị trí → entity sẽ có tên thật.
     */
    fun upsertName(id: Int, name: String, team: Int = 0, weaponType: Int = 0) {
        if (id <= 0 || name.isBlank()) return
        val lp = localPlayer
        if (lp != null && lp.id == id) return  // Không update local player qua đây
        val ts = System.currentTimeMillis()
        entities.compute(id) { _, existing ->
            existing?.copy(
                name = name,
                team = if (team > 0) team else existing.team,
                weaponType = if (weaponType > 0) weaponType else existing.weaponType,
            ) ?: Entity(id = id, x = 0f, y = 0f, z = 0f,
                updatedAt = ts, name = name, team = team, weaponType = weaponType)
        }
    }

    fun localEntityId(): Int                    = localPlayer?.id ?: -1

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
