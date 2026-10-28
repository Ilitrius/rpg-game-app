package com.idlerpg.game.domain.stats

import com.idlerpg.game.data.StatsState
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {
    @Test
    fun highestHitOnlyGrows() {
        var s = Stats.recordHit(StatsState(), 50)
        s = Stats.recordHit(s, 20)
        assertEquals(50, s.highestHit)
        assertEquals(80, Stats.recordHit(s, 80).highestHit)
    }

    @Test
    fun countersAreMonotonicAndIgnoreNegatives() {
        var s = Stats.addGold(Stats.addPlayTime(Stats.recordTap(StatsState()), 1500), 40)
        s = Stats.addGold(Stats.addPlayTime(Stats.recordTap(s, 2), -5), -9)
        assertEquals(StatsState(playTimeMillis = 1500, goldEarned = 40, tapCount = 3), s)
    }

    @Test
    fun overflowSaturates() {
        val s = StatsState(playTimeMillis = Long.MAX_VALUE - 1, goldEarned = Long.MAX_VALUE, tapCount = Long.MAX_VALUE - 1)
        assertEquals(Long.MAX_VALUE, Stats.addPlayTime(s, 100).playTimeMillis)
        assertEquals(Long.MAX_VALUE, Stats.addGold(s, 1).goldEarned)
        assertEquals(Long.MAX_VALUE, Stats.recordTap(s, Long.MAX_VALUE).tapCount)
    }
}
