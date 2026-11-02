package com.idlerpg.game.domain.combat

import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.buffs.Buffs
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.prestige.Prestige
import com.idlerpg.game.domain.prestige.PrestigeBonuses
import com.idlerpg.game.domain.world.MonsterStats
import kotlin.math.ceil
import com.idlerpg.game.domain.stats.Stats

/** Pure combat helpers used by the adventure loop and offline progress. */
object CombatRules {

    /** Hero regenerates this fraction of max HP per second (in and out of fights). */
    const val HERO_REGEN_PER_SECOND = 0.015

    /** Time for a new monster to walk in from the distance. */
    const val APPROACH_MILLIS = 1100L

    /** Duration of the death animation before the next monster appears. */
    const val DEATH_MILLIS = 700L

    /** How long the "Besiegt" overlay is shown before the hero is back at full HP. */
    const val DEFEAT_MILLIS = 2500L

    /** Non-fighting time per kill (walk-in + death animation). */
    const val TRAVEL_MILLIS = APPROACH_MILLIS + DEATH_MILLIS

    /** HP regenerated over [millis] for a hero with [maxHp] (fractional, caller accumulates). */
    fun regen(maxHp: Int, millis: Long): Double = maxHp * HERO_REGEN_PER_SECOND * millis / 1000.0

    /** Hero HP to use after loading: saved value clamped to 1..[maxHp]; -1 (unknown) = full. */
    fun restoreHeroHp(savedHp: Int, maxHp: Int): Int =
        if (savedHp < 0) maxHp.coerceAtLeast(1) else savedHp.coerceIn(1, maxHp.coerceAtLeast(1))

    /**
     * Heals the saved hero HP for the time since [GameState.world]'s last-active time (normal regen,
     * capped at max HP). Unknown HP (-1) or no last-active time leave [state] unchanged.
     */
    fun healOffline(state: GameState, nowMillis: Long): GameState {
        val w = state.world
        if (w.heroHp < 0 || w.lastActiveEpochMillis <= 0L) return state
        val away = (nowMillis - w.lastActiveEpochMillis).coerceAtLeast(0L)
        val maxHp = CombatStats.of(state).maxHp
        val healed = (restoreHeroHp(w.heroHp, maxHp) + regen(maxHp, away)).coerceAtMost(maxHp.toDouble()).toInt()
        return if (healed == w.heroHp) state else state.copy(world = w.copy(heroHp = healed))
    }

    /** Expected seconds of fighting to kill [monster] with [hero]'s DPS. */
    fun expectedSecondsToKill(hero: CombatStats, monster: MonsterStats): Double =
        monster.maxHp / hero.dps

    /** Minimum number of normal (non-crit) hits to kill [monster] with average hits. */
    fun averageHitsToKill(hero: CombatStats, monster: MonsterStats): Int =
        ceil(monster.maxHp / hero.averageHit).toInt().coerceAtLeast(1)

    /** Expected damage the hero takes while killing [monster] (no regen). */
    fun expectedDamageTaken(hero: CombatStats, monster: MonsterStats): Double =
        expectedSecondsToKill(hero, monster) * monster.dps

    /**
     * Final XP for a base reward of [xp]: base x prestige XP bonus (permanent) x XP buff (temporary, only
     * with [applyBuffs]), rounded once. Both are independent multipliers (never added twice).
     */
    fun rewardXp(state: GameState, xp: Long, applyBuffs: Boolean = false, applyPrestige: Boolean = true): Long =
        Buffs.scale(xp.coerceAtLeast(0), multiplier(state, Buffs.XP, applyBuffs, applyPrestige, PrestigeBonuses::xp))

    /** Final gold for a base reward of [gold], see [rewardXp]. */
    fun rewardGold(state: GameState, gold: Long, applyBuffs: Boolean = false, applyPrestige: Boolean = true): Long =
        Buffs.scale(gold.coerceAtLeast(0), multiplier(state, Buffs.GOLD, applyBuffs, applyPrestige, PrestigeBonuses::gold))

    private fun multiplier(
        state: GameState, buff: String, applyBuffs: Boolean, applyPrestige: Boolean, prestige: (PrestigeBonuses) -> Double,
    ): Double {
        val b = if (applyBuffs) Buffs.bonus(state, buff) else 1.0
        val p = if (applyPrestige) prestige(PrestigeBonuses.of(state)) else 1.0
        return b * p
    }

    /**
     * Grants the reward of [kills] killed monsters in [zoneId]: XP (with level-ups), gold,
     * kill counters, and sets the last-active time to [nowMillis]. [xp]/[gold] are BASE values: the
     * prestige bonus is applied here ([applyPrestige]; offline progress passes its already scaled
     * numbers with false), active XP/gold buffs only with [applyBuffs] (online only).
     */
    fun applyKillReward(
        state: GameState,
        xp: Long,
        gold: Long,
        zoneId: String,
        nowMillis: Long,
        kills: Long = 1,
        applyBuffs: Boolean = false,
        applyPrestige: Boolean = true,
    ): GameState {
        val xp = rewardXp(state, xp, applyBuffs, applyPrestige)
        val gold = rewardGold(state, gold, applyBuffs, applyPrestige)
        val leveled = Progression.applyXp(state, xp)
        val w = state.world
        val rewarded = leveled.copy(
            gold = state.gold + gold,
            stats = Stats.addGold(state.stats, gold),
            world = w.copy(
                totalKills = w.totalKills + kills,
                zoneKills = w.zoneKills + (zoneId to (w.zoneKills[zoneId] ?: 0L) + kills),
                lastActiveEpochMillis = nowMillis,
            ),
        )
        return if (leveled.character.level > state.character.level) Prestige.trackBest(rewarded) else rewarded
    }
}
