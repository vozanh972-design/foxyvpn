package com.example.foxy.engine

import kotlin.math.*

/**
 * FreezeController — Quyết định khi nào bật freeze (Chế độ Ghim).
 * Ghim = outbound position packets bị rewrite → server nghĩ player đứng yên.
 */
object FreezeController {

    private const val FIRE_HOLD_MS  = 700L
    private const val SCOPE_HOLD_MS = 1800L

    @Volatile var available:    Boolean = false
    @Volatile var manualFreeze: Boolean = false
    @Volatile var autoFreeze:   Boolean = false
    @Volatile var aimLag:       Boolean = false
    @Volatile var scopeFreeze:  Boolean = false

    @Volatile private var firing         = false
    @Volatile private var scoping        = false
    @Volatile private var firePulseUntil = 0L
    @Volatile private var scopePulseUntil= 0L

    fun configure(available: Boolean, manual: Boolean, auto: Boolean, aim: Boolean, scope: Boolean) {
        this.available    = available
        this.manualFreeze = available && manual
        this.autoFreeze   = auto
        this.aimLag       = aim
        this.scopeFreeze  = scope
    }

    fun applyManualFreeze(on: Boolean) {
        if (!available && on) return
        manualFreeze = on
    }

    fun observeCommand(command: Int, outbound: Boolean) {
        if (!outbound) return
        val now = System.currentTimeMillis()
        when (command) {
            103 -> { firing = false; scoping = false; firePulseUntil = 0; scopePulseUntil = 0 }
            104 -> { firing = true;  firePulseUntil = now + FIRE_HOLD_MS }
            105 -> { firing = false; firePulseUntil = 0 }
            137 -> { scoping = true; scopePulseUntil = now + SCOPE_HOLD_MS }
            138 -> { scoping = false; scopePulseUntil = 0 }
        }
    }

    fun isFreezeActive(): Boolean {
        if (!available) return false
        if (manualFreeze) return true
        val now = System.currentTimeMillis()
        if (aimLag && (scoping || now < scopePulseUntil)) return true
        if (scopeFreeze && scoping) return true
        if (autoFreeze && !firing) return true
        return false
    }

    fun resetRuntime() {
        firing = false; scoping = false
        firePulseUntil = 0; scopePulseUntil = 0
    }
}

/**
 * NetworkFeatures — Trạng thái các tính năng network: ghost, wall, under, magnet, teleport.
 * "Magnet" (hút đạn) = chế độ Ghim trong UI.
 */
object NetworkFeatures {

    data class Options(
        val magnet:   Boolean = false,
        val ghost:    Boolean = false,
        val under:    Boolean = false,
        val wall:     Boolean = false,
        val teleport: Boolean = false,
        val speed:    Int     = 3,
    )

    @Volatile var options = Options()
        private set

    @Volatile var ghostGeneration: Long = 0L; private set
    @Volatile var underGeneration: Long = 0L; private set
    @Volatile var wallGeneration:  Long = 0L; private set
    @Volatile var tpGeneration:    Long = 0L; private set
    @Volatile var wallChangedAt:   Long = 0L; private set
    @Volatile var tpState:         Int  = 0;  private set

    @Volatile private var firing  = false
    @Volatile private var scoping = false
    @Volatile private var fireAt  = 0L
    @Volatile private var scopeAt = 0L
    @Volatile private var fireUntil  = 0L
    @Volatile private var scopeUntil = 0L

    private val ignored = BooleanArray(256)

    @Synchronized
    fun configure(magnet: Boolean, ghost: Boolean, under: Boolean, wall: Boolean, teleport: Boolean, speed: Int) {
        val prev = options
        if (prev.ghost != ghost) ghostGeneration++
        if (prev.under != under) underGeneration++
        if (prev.wall  != wall)  { wallGeneration++; wallChangedAt = System.currentTimeMillis() }
        if (prev.teleport && !teleport) { tpState = 0; tpGeneration++ }
        options = Options(magnet, ghost, under, wall, teleport, speed.coerceIn(1, 10))
    }

    @Synchronized
    fun observe(command: Int, outbound: Boolean) {
        if (!outbound) return
        val now = System.currentTimeMillis()
        when (command) {
            103 -> { scoping = false; firing = false; scopeUntil = 0; fireUntil = 0 }
            104 -> { firing = true;  fireAt = now;  fireUntil  = now + 700 }
            105 -> { firing = false; fireUntil = 0 }
            137 -> { scoping = true; scopeAt = now; scopeUntil = now + 1800 }
            138 -> { scoping = false; scopeUntil = 0 }
        }
    }

    fun magnetActive(): Boolean {
        if (!options.magnet) return false
        val now = System.currentTimeMillis()
        if (firing && now - fireAt > 5000) firing = false
        return firing
    }

    fun underActive(): Boolean {
        if (!options.under) return false
        val now = System.currentTimeMillis()
        if (firing  && now - fireAt  >= 2000) firing  = false
        if (scoping && now - scopeAt >= 2000) scoping = false
        return !firing && !scoping && now >= fireUntil && now >= scopeUntil
    }

    @Synchronized
    fun nextTeleport() {
        if (!options.teleport) return
        tpGeneration++
        tpState = when (tpState) { 0 -> 1; 1 -> 2; else -> { tpGeneration++; 0 } }
    }

    @Synchronized
    fun finishTeleport(gen: Long) {
        if (gen == tpGeneration) { tpState = 0; tpGeneration++ }
    }

    fun teleportDistance() = options.speed * 5f

    fun ignore(id: Int, v: Boolean) { if (id in 1..255) ignored[id] = v }
    fun isIgnored(id: Int): Boolean = id in 1..255 && ignored[id]

    @Synchronized
    fun disable() {
        options = Options()
        resetRuntime()
    }

    @Synchronized
    fun resetRuntime() {
        firing = false; scoping = false
        fireUntil = 0; scopeUntil = 0
        tpState = 0; tpGeneration++
        ghostGeneration++; underGeneration++; wallGeneration++
        wallChangedAt = System.currentTimeMillis()
        ignored.fill(false)
    }
}
