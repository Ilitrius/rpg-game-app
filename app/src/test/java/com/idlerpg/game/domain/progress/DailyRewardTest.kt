package com.idlerpg.game.domain.progress

import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.ProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyRewardTest {
    private val h = 3_600_000L
    private val t0 = 1_760_000_000_000L

    private fun state(p: ProgressState = ProgressState()) = GameState(progress = p)

    @Test
    fun firstClaimIsPossibleAndStartsStreak() {
        val s = state()
        assertTrue(DailyReward.status(s, t0).canClaim)
        val c = DailyReward.claim(s, t0)
        assertEquals(1, c.progress.dailyStreak)
        assertEquals(t0, c.progress.dailyLastClaimEpochMillis)
        assertTrue(c.gold > 0)
    }

    @Test
    fun twentyHourWindow() {
        val c = DailyReward.claim(state(), t0)
        assertFalse(DailyReward.status(c, t0 + 20 * h - 1).canClaim)
        assertEquals(1, DailyReward.status(c, t0 + 20 * h - 1).millisUntil)
        assertSame(c, DailyReward.claim(c, t0 + 19 * h))
        assertTrue(DailyReward.status(c, t0 + 20 * h).canClaim)
    }

    @Test
    fun clockRewindBlocksClaiming() {
        var c = DailyReward.claim(state(), t0)
        c = DailyReward.observe(c, t0 + 30 * h)
        assertEquals(t0 + 30 * h, c.progress.clockHighWaterMillis)
        // Clock set back by a day: blocked although 20 h passed since the claim.
        val st = DailyReward.status(c, t0 + 6 * h)
        assertTrue(st.clockRewound && !st.canClaim)
        assertSame(c, DailyReward.claim(c, t0 + 6 * h))
        // Within the 5 minute tolerance it still works.
        assertTrue(DailyReward.status(c, t0 + 30 * h - 4 * 60_000L).canClaim)
        assertFalse(DailyReward.status(c, t0 + 30 * h - 6 * 60_000L).canClaim)
    }

    @Test
    fun observeNeverLowersAndSkipsTinySteps() {
        val s = state(ProgressState(clockHighWaterMillis = t0))
        assertSame(s, DailyReward.observe(s, t0 - h))
        assertSame(s, DailyReward.observe(s, t0 + 30_000))
        assertEquals(t0 + 120_000, DailyReward.observe(s, t0 + 120_000).progress.clockHighWaterMillis)
    }

    @Test
    fun streakGrowsAndBreaksAfter48Hours() {
        var s = DailyReward.claim(state(), t0)
        s = DailyReward.claim(s, t0 + 24 * h)
        s = DailyReward.claim(s, t0 + 48 * h)
        assertEquals(3, s.progress.dailyStreak)
        // exactly 48 h later: streak continues; one millisecond more: breaks.
        assertEquals(4, DailyReward.claim(s, t0 + 96 * h).progress.dailyStreak)
        assertEquals(1, DailyReward.claim(s, t0 + 96 * h + 1).progress.dailyStreak)
    }

    @Test
    fun rewardGrowsTenPercentPerDayUntilDaySevenThenBonus() {
        val s = state()
        val r1 = DailyReward.reward(s, 1).toDouble()
        assertEquals(1.1, DailyReward.reward(s, 2) / r1, 0.01)
        assertEquals(1.5, DailyReward.reward(s, 6) / r1, 0.01)
        assertEquals(1.6 + DailyReward.WEEK_BONUS, DailyReward.reward(s, 7) / r1, 0.01)
        assertEquals(DailyReward.reward(s, 7), DailyReward.reward(s, 30))
    }

    @Test
    fun rewardIsGenerousAndScalesWithZone() {
        val low = DailyReward.reward(state(), 1)
        assertTrue(low >= 150 * 2)
        val high = GameState(
            character = com.idlerpg.game.data.CharacterState(level = 35),
            world = com.idlerpg.game.data.WorldState(currentZoneId = "hoehle"),
        )
        assertTrue(DailyReward.reward(high, 1) > low * 5)
    }
}
