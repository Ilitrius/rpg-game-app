package com.idlerpg.game.ui.adventure

import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.progress.DailyReward
import org.junit.Assert.assertEquals
import org.junit.Test

class ClaimHintTest {
    @Test
    fun hintMatchesDailyRewardAndGoals() {
        val s = GameState()
        val now = 1_000_000_000_000L
        val expected = DailyReward.status(s, now).canClaim
        assertEquals(expected, hasClaimable(s, now))
        if (expected) {
            val claimed = DailyReward.claim(s, now)
            assertEquals(false, DailyReward.status(claimed, now).canClaim)
        }
    }
}
