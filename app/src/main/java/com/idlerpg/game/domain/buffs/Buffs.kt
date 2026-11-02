package com.idlerpg.game.domain.buffs

import com.idlerpg.game.data.ActiveBuff
import com.idlerpg.game.data.BuffState
import com.idlerpg.game.data.GameState
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Timed buffs (pure functions on [GameState]).
 *
 * - One entry per type ([TYPES]); unknown types are ignored/dropped.
 * - Runtime counts ACTIVE play time only: [tick] is fed with measured deltas by the adventure loop;
 *   wall-clock, background and offline never reduce it (and offline progress ignores buffs).
 * - Stacking in [add]: same type + same magnitude = durations add; stronger magnitude replaces the entry
 *   (new duration); weaker magnitude is ignored. Magnitude is capped at [MAX_BONUS], duration at
 *   [MAX_DURATION_MILLIS], at most [MAX_BUFFS] entries.
 * - [bonus] returns a multiplier `1 + magnitude` (1.0 = no buff).
 *
 * Public API for the shop: `Buffs.add(state, type, magnitude, durationMillis, source)`.
 */
object Buffs {
    const val DAMAGE = "damage"
    const val XP = "xp"
    const val GOLD = "gold"
    const val DROP = "drop"
    const val REGEN = "regen"
    const val CLICK = "click"

    val TYPES: List<String> = listOf(DAMAGE, XP, GOLD, DROP, REGEN, CLICK)

    const val SOURCE_ELIXIR = "elixir"
    const val SOURCE_DAILY = "daily"
    const val SOURCE_GOAL = "goal"
    const val SOURCE_BOSS = "boss"

    /** Maximum bonus per type (+100 %). */
    const val MAX_BONUS = 1.0
    const val MAX_BUFFS = 6
    const val MAX_DURATION_MILLIS = 4L * 60 * 60 * 1000

    /** Boss victory reward: +10 % damage for 5 minutes. */
    const val BOSS_DAMAGE_MAGNITUDE = 0.10
    const val BOSS_DURATION_MILLIS = 5L * 60 * 1000

    fun isKnown(type: String): Boolean = type in TYPES

    /** Adds a buff (see class doc for the stacking rules). Invalid input returns [state] unchanged. */
    fun add(state: GameState, type: String, magnitude: Double, durationMillis: Long, source: String = ""): GameState {
        if (!isKnown(type) || !magnitude.isFinite() || magnitude <= 0.0 || durationMillis <= 0L) return state
        val mag = magnitude.coerceAtMost(MAX_BONUS)
        val dur = durationMillis.coerceAtMost(MAX_DURATION_MILLIS)
        val list = state.buffs.active.filter { isKnown(it.type) && it.remainingMillis > 0 }
        val old = list.firstOrNull { it.type == type }
        val merged = when {
            old == null -> ActiveBuff(type, mag, dur, source)
            mag > old.magnitude + EPS -> ActiveBuff(type, mag, dur, source)
            mag < old.magnitude - EPS -> old
            else -> old.copy(remainingMillis = (old.remainingMillis + dur).coerceAtMost(MAX_DURATION_MILLIS))
        }
        if (old == null && list.size >= MAX_BUFFS) return state
        val next = if (old == null) list + merged else list.map { if (it.type == type) merged else it }
        return state.copy(buffs = BuffState(next))
    }

    /** Reduces all runtimes by [deltaMillis] of active play time and removes expired/unknown buffs. */
    fun tick(state: GameState, deltaMillis: Long): GameState {
        if (state.buffs.active.isEmpty() || deltaMillis <= 0L) return state
        val next = state.buffs.active.mapNotNull {
            val left = it.remainingMillis - deltaMillis
            if (left > 0 && isKnown(it.type)) it.copy(remainingMillis = left) else null
        }
        return state.copy(buffs = BuffState(next))
    }

    /** Damage/XP/gold/... multiplier for [type]: 1 + sum of its magnitudes, capped at 1 + [MAX_BONUS]. */
    fun bonus(state: GameState, type: String): Double = bonus(state.buffs, type)

    fun bonus(buffs: BuffState, type: String): Double =
        1.0 + buffs.active.filter { it.type == type && it.remainingMillis > 0 && it.magnitude > 0 }
            .sumOf { it.magnitude }.coerceAtMost(MAX_BONUS)

    /** [value] times [multiplier], rounded; never below [value] for multipliers >= 1. */
    fun scale(value: Long, multiplier: Double): Long =
        if (multiplier == 1.0 || value <= 0) value else (value * multiplier).roundToLong()

    /** Remaining time as `m:ss` or `h:mm:ss`. */
    fun clock(millis: Long): String {
        val total = ((millis.coerceAtLeast(0) + 999) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    private const val EPS = 1e-9
}
