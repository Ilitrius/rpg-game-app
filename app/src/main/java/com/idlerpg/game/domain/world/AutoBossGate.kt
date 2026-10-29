package com.idlerpg.game.domain.world

import com.idlerpg.game.data.WorldState

/**
 * Decides when the optional auto-boss mode may start the zone boss by itself. Transient (lives in
 * the ViewModel, never saved). Rules:
 * - Only when the kill requirement is met ([WorldProgress.canChallengeBoss]), the boss is not yet
 *   defeated (auto-boss pushes progress, it does not re-farm) and the zone has a successor
 *   (the last zone has nothing to travel to).
 * - Only with at least [MIN_HP_FRACTION] of the hero's HP.
 * - After a lost boss fight (timeout or death) the gate stays closed for
 *   `min(10 x (fails + 1), 50)` further kills in that zone; the next failure widens it.
 * - A boss victory or a level-up (stronger hero) clears the penalty.
 * The manual boss button ignores the gate.
 */
class AutoBossGate {
    private var fails = 0
    private var blockedUntilKills = 0L
    private var lastLevel = -1

    /** Number of failed auto attempts since the last reset. */
    val failCount: Int get() = fails

    /** Kill count (in the zone) before which the gate stays closed. */
    val blockedUntil: Long get() = blockedUntilKills

    /** Lifts the penalty when the hero gained a level since the last call. */
    fun observeLevel(level: Int) {
        if (lastLevel in 0 until level) reset()
        lastLevel = level
    }

    fun onFailure(zoneKillsNow: Long) {
        blockedUntilKills = zoneKillsNow + minOf(PENALTY_STEP * (fails + 1), PENALTY_MAX)
        fails++
    }

    fun onVictory() = reset()

    fun reset() {
        fails = 0
        blockedUntilKills = 0L
    }

    fun shouldStart(enabled: Boolean, world: WorldState, zone: Zone, heroHp: Int, heroMaxHp: Int): Boolean {
        if (!enabled || Zones.next(zone) == null) return false
        if (WorldProgress.isBossDefeated(world, zone) || !WorldProgress.canChallengeBoss(world, zone)) return false
        if (heroMaxHp <= 0 || heroHp < MIN_HP_FRACTION * heroMaxHp) return false
        return (world.zoneKills[zone.id] ?: 0L) >= blockedUntilKills
    }

    companion object {
        const val MIN_HP_FRACTION = 0.7
        const val PENALTY_STEP = 10L
        const val PENALTY_MAX = 50L
    }
}
