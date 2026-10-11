package com.idlerpg.game.domain.world

import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.combat.CombatRules

/** Result of a running boss fight, see [WorldProgress.bossOutcome]. */
enum class BossOutcome { ONGOING, VICTORY, DEFEAT, TIMEOUT }

/**
 * Zone unlocking, boss challenges and travelling. Pure functions on [GameState]/[WorldState].
 *
 * - Zone 1 is always open; every other zone opens once the previous zone's boss is defeated.
 * - A boss can be challenged after [BOSS_KILLS_REQUIRED] kills in its zone, counted since the last
 *   victory against that boss (so re-farming a boss needs another [BOSS_KILLS_REQUIRED] kills).
 * - A boss fight lasts at most [BOSS_TIME_LIMIT_MILLIS]; failing (timeout or death) just returns
 *   the hero to normal monsters, the kill counter is kept so the boss can be retried at once.
 */
object WorldProgress {
    const val BOSS_KILLS_REQUIRED = 25L
    const val BOSS_TIME_LIMIT_MILLIS = 30_000L

    /** True if the hero may travel to [zone] (first zone, explicitly unlocked, or previous boss beaten). */
    fun isUnlocked(world: WorldState, zone: Zone): Boolean {
        if (zone.order == 1 || zone.id in world.unlockedZoneIds) return true
        val prev = Zones.previous(zone) ?: return true
        return prev.id in world.defeatedBossIds
    }

    fun isBossDefeated(world: WorldState, zone: Zone): Boolean = zone.id in world.defeatedBossIds

    /** Kills in [zone] that count towards the next boss challenge. */
    fun killsTowardsBoss(world: WorldState, zone: Zone): Long =
        ((world.zoneKills[zone.id] ?: 0L) - (world.bossDefeatedAtKills[zone.id] ?: 0L)).coerceAtLeast(0L)

    fun canChallengeBoss(world: WorldState, zone: Zone): Boolean =
        isUnlocked(world, zone) && killsTowardsBoss(world, zone) >= BOSS_KILLS_REQUIRED

    /** Travels to the zone [zoneId]. Unknown or locked zones leave [state] unchanged. */
    fun travel(state: GameState, zoneId: String): GameState {
        val zone = Zones.all.firstOrNull { it.id == zoneId } ?: return state
        if (!isUnlocked(state.world, zone) || state.world.currentZoneId == zoneId) return state
        return state.copy(world = state.world.copy(currentZoneId = zoneId))
    }

    /**
     * State of a boss fight: a dead boss always wins (even in the last millisecond), then hero death,
     * then the time limit.
     */
    fun bossOutcome(bossHp: Long, heroHp: Double, elapsedMillis: Long, limitMillis: Long = BOSS_TIME_LIMIT_MILLIS): BossOutcome = when {
        bossHp <= 0L -> BossOutcome.VICTORY
        heroHp <= 0.0 -> BossOutcome.DEFEAT
        elapsedMillis >= limitMillis -> BossOutcome.TIMEOUT
        else -> BossOutcome.ONGOING
    }

    /** Remaining boss time in millis (never negative). */
    fun bossTimeLeft(elapsedMillis: Long, limitMillis: Long = BOSS_TIME_LIMIT_MILLIS): Long =
        (limitMillis - elapsedMillis).coerceAtLeast(0L)

    /**
     * Grants a boss victory in [zone]: XP/gold like a kill (counts as one zone kill), marks the boss
     * as defeated, resets the boss kill counter and unlocks the next zone.
     */
    fun applyBossVictory(state: GameState, zone: Zone, xp: Long, gold: Long, nowMillis: Long): GameState {
        val rewarded = CombatRules.applyKillReward(state, xp, gold, zone.id, nowMillis)
        val w = rewarded.world
        val next = Zones.next(zone)
        return rewarded.copy(
            world = w.copy(
                defeatedBossIds = if (zone.id in w.defeatedBossIds) w.defeatedBossIds else w.defeatedBossIds + zone.id,
                unlockedZoneIds = if (next == null || next.id in w.unlockedZoneIds) w.unlockedZoneIds else w.unlockedZoneIds + next.id,
                bossDefeatedAtKills = w.bossDefeatedAtKills + (zone.id to (w.zoneKills[zone.id] ?: 0L)),
            ),
        )
    }
}
