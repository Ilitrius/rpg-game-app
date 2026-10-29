package com.idlerpg.game.domain.stats

import com.idlerpg.game.data.StatsState

/** Pure, monotonic updates of [StatsState]. Negative inputs are ignored, sums saturate at Long.MAX_VALUE. */
object Stats {
    private fun add(a: Long, b: Long): Long {
        val base = a.coerceAtLeast(0)
        if (b <= 0) return base
        return if (base > Long.MAX_VALUE - b) Long.MAX_VALUE else base + b
    }

    fun recordHit(s: StatsState, damage: Long): StatsState =
        if (damage > s.highestHit) s.copy(highestHit = damage) else s

    fun addPlayTime(s: StatsState, millis: Long): StatsState =
        if (millis <= 0) s else s.copy(playTimeMillis = add(s.playTimeMillis, millis))

    fun addGold(s: StatsState, gold: Long): StatsState =
        if (gold <= 0) s else s.copy(goldEarned = add(s.goldEarned, gold))

    fun recordTap(s: StatsState, taps: Long = 1): StatsState =
        if (taps <= 0) s else s.copy(tapCount = add(s.tapCount, taps))
}
