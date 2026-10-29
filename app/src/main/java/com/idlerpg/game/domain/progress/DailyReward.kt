package com.idlerpg.game.domain.progress

import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.ProgressState
import com.idlerpg.game.domain.world.MonsterScaling
import com.idlerpg.game.domain.world.Zones
import kotlin.math.min
import kotlin.math.roundToLong
import com.idlerpg.game.domain.stats.Stats

/**
 * Daily reward (pure, time injected). Honest note: this is a purely local protection. Anyone who edits
 * the save file or the system clock can still cheat; the rules only stop casual clock rewinding.
 *
 * - Claimable at the earliest [MIN_INTERVAL_MILLIS] (20 h) after the last claim.
 * - Not claimable while the clock is more than [CLOCK_TOLERANCE_MILLIS] behind the highest time ever seen.
 * - The streak breaks when more than [STREAK_BREAK_MILLIS] (48 h) passed since the last claim.
 * - Value: gold of about [REFERENCE_KILLS] kills in the current zone, +10 % per streak day up to day 7,
 *   from day 7 on an extra bonus (gold only).
 */
object DailyReward {
    const val MIN_INTERVAL_MILLIS = 20L * 60 * 60 * 1000
    const val STREAK_BREAK_MILLIS = 48L * 60 * 60 * 1000
    const val CLOCK_TOLERANCE_MILLIS = 5L * 60 * 1000

    /** Generous stand-in for ~10 minutes of fighting (about 4 s per kill, rounded up). */
    const val REFERENCE_KILLS = 150
    const val STREAK_BONUS_PER_DAY = 0.10
    const val MAX_STREAK_BONUS_DAY = 7
    /** Extra bonus (share of the base value) from [MAX_STREAK_BONUS_DAY] on. */
    const val WEEK_BONUS = 0.5

    /** [observe] only writes when the time advanced by more than this (spares needless saves). */
    private const val OBSERVE_STEP_MILLIS = 60_000L

    data class Status(
        val canClaim: Boolean,
        /** Time until the reward becomes claimable (0 when claimable now). */
        val millisUntil: Long,
        /** Clock is behind the highest time seen: claiming is blocked. */
        val clockRewound: Boolean,
        /** Streak the claim would result in. */
        val nextStreak: Int,
        val reward: Long,
    )

    /** Average gold of one regular kill in the hero's current zone at a fitting level (>= 1). */
    fun goldPerKill(state: GameState): Double {
        val zone = Zones.byId(state.world.currentZoneId)
        val level = state.character.level.coerceIn(zone.minLevel, zone.maxLevel)
        val total = zone.monsters.sumOf { it.spawnWeight }.coerceAtLeast(1)
        val avg = zone.monsters.sumOf { MonsterScaling.stats(it, zone, level).gold.toDouble() * it.spawnWeight } / total
        return avg.coerceAtLeast(1.0)
    }

    /** Gold for claiming with streak day [streak] (1-based). */
    fun reward(state: GameState, streak: Int): Long {
        val day = streak.coerceAtLeast(1)
        val base = goldPerKill(state) * REFERENCE_KILLS
        var factor = 1.0 + STREAK_BONUS_PER_DAY * (min(day, MAX_STREAK_BONUS_DAY) - 1)
        if (day >= MAX_STREAK_BONUS_DAY) factor += WEEK_BONUS
        return (base * factor).roundToLong().coerceAtLeast(1)
    }

    /** Streak after a claim at [now] (1 = new/broken streak). */
    fun streakAfterClaim(p: ProgressState, now: Long): Int =
        if (p.dailyLastClaimEpochMillis <= 0 || now - p.dailyLastClaimEpochMillis > STREAK_BREAK_MILLIS) 1
        else p.dailyStreak.coerceAtLeast(0) + 1

    /** Raises the clock high-water mark to [now]; returns the same instance when nothing worth saving changed. */
    fun observe(state: GameState, now: Long): GameState {
        val p = state.progress
        if (now <= p.clockHighWaterMillis + OBSERVE_STEP_MILLIS) return state
        return state.copy(progress = p.copy(clockHighWaterMillis = now))
    }

    fun status(state: GameState, now: Long): Status {
        val p = state.progress
        val rewound = now < p.clockHighWaterMillis - CLOCK_TOLERANCE_MILLIS
        val ready = p.dailyLastClaimEpochMillis <= 0 || now - p.dailyLastClaimEpochMillis >= MIN_INTERVAL_MILLIS
        val wait = if (p.dailyLastClaimEpochMillis <= 0) 0L
        else (p.dailyLastClaimEpochMillis + MIN_INTERVAL_MILLIS - now).coerceAtLeast(0)
        val streak = streakAfterClaim(p, now)
        return Status(
            canClaim = ready && !rewound,
            millisUntil = if (rewound) maxOf(wait, p.clockHighWaterMillis - now) else wait,
            clockRewound = rewound,
            nextStreak = streak,
            reward = reward(state, streak),
        )
    }

    /** Claims the reward at [now]; returns the unchanged [state] when not claimable. */
    fun claim(state: GameState, now: Long): GameState {
        val s = status(state, now)
        if (!s.canClaim) return state
        val p = state.progress
        return state.copy(
            gold = state.gold + s.reward,
            stats = Stats.addGold(state.stats, s.reward),
            progress = p.copy(
                dailyLastClaimEpochMillis = now,
                dailyStreak = s.nextStreak,
                clockHighWaterMillis = maxOf(p.clockHighWaterMillis, now),
            ),
        )
    }
}
