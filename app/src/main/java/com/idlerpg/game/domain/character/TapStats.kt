package com.idlerpg.game.domain.character

import com.idlerpg.game.data.Stats
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

/**
 * Pure formulas for tap (click) damage on the enemy. No state, no new stat.
 *
 * A tap deals `normal hit × [TAP_DAMAGE_FACTOR] × [clickMultiplier] × [comboMultiplier]`.
 * The base factor keeps tapping a bonus, not a replacement: at [ASSUMED_TAPS_PER_SECOND] with a full combo
 * the tap DPS of the reference hero stays within 2x of the auto DPS (see TapStatsTest).
 *
 * API for the combat loop (spielwelt): keep a combo counter `n` (taps in a row, each tap within
 * [COMBO_WINDOW_MILLIS] of the previous one, reset to 0 otherwise) and call
 * `TapStats.tapHit(combat, n, random)` per tap; it returns a [Hit] like [CombatStats.rollHit].
 */
object TapStats {
    /** Max pause between two taps that still continues the combo. */
    const val COMBO_WINDOW_MILLIS = 600L

    const val CLICK_BASE = 1.0
    const val CLICK_PER_STRENGTH = 0.01
    const val CLICK_PER_DEXTERITY = 0.005
    const val MAX_CLICK_MULTIPLIER = 2.5

    const val COMBO_PER_TAP = 0.02
    const val MAX_COMBO_MULTIPLIER = 1.5

    /** Share of a normal hit that one tap deals before the multipliers. */
    const val TAP_DAMAGE_FACTOR = 0.2

    /** Balance assumption for a fast tapper (used for the max-DPS estimate and tests). */
    const val ASSUMED_TAPS_PER_SECOND = 6.0

    /** 1 + 0.01 x STR + 0.005 x DEX (total stats incl. gear), capped at [MAX_CLICK_MULTIPLIER]. */
    fun clickMultiplier(total: Stats): Double =
        min(
            CLICK_BASE + total.strength.coerceAtLeast(0) * CLICK_PER_STRENGTH +
                total.dexterity.coerceAtLeast(0) * CLICK_PER_DEXTERITY,
            MAX_CLICK_MULTIPLIER,
        )

    /** Combo factor for [n] taps in a row: 1 + 2 % per tap before this one (n <= 0 -> 1.0), capped at 1.5. */
    fun comboMultiplier(n: Int): Double = min(1.0 + n.coerceAtLeast(0) * COMBO_PER_TAP, MAX_COMBO_MULTIPLIER)

    /** Combo count at which [MAX_COMBO_MULTIPLIER] is reached (25). */
    val comboCap: Int get() = Math.round((MAX_COMBO_MULTIPLIER - 1.0) / COMBO_PER_TAP).toInt()

    private fun scale(combat: CombatStats, combo: Int) =
        TAP_DAMAGE_FACTOR * clickMultiplier(combat.totalStats) * comboMultiplier(combo)

    /** Damage of a tap with the given roll [base] damage, min 1. */
    fun tapDamage(combat: CombatStats, base: Long, combo: Int): Long =
        floor(base * scale(combat, combo)).toLong().coerceAtLeast(1L)

    /** Min damage of a non-crit tap at [combo]. */
    fun minTapDamage(combat: CombatStats, combo: Int = 0): Long = tapDamage(combat, combat.damageMin.toLong(), combo)

    /** Max damage of a non-crit tap at [combo]. */
    fun maxTapDamage(combat: CombatStats, combo: Int = 0): Long = tapDamage(combat, combat.damageMax.toLong(), combo)

    /** Rolls one tap (damage roll + crit as for auto attacks, then scaled). Crit flag is kept. */
    fun tapHit(combat: CombatStats, combo: Int, random: Random = Random.Default): Hit {
        val hit = combat.rollHit(random)
        return Hit(tapDamage(combat, hit.damage, combo), hit.critical)
    }

    /** Expected tap DPS (incl. crits) at [tapsPerSecond] with a full combo. */
    fun maxTapDps(combat: CombatStats, tapsPerSecond: Double = ASSUMED_TAPS_PER_SECOND): Double =
        combat.averageHit * (1.0 + combat.critChance * (combat.critMultiplier - 1.0)) *
            scale(combat, comboCap) * tapsPerSecond
}
