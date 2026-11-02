package com.idlerpg.game.games

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.WorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementTest {
    @Test fun freshGameEarnsNothing() = assertTrue(Achievement.reached(GameState()).isEmpty())

    @Test fun thresholds() {
        val s = GameState(
            character = CharacterState(level = 50),
            world = WorldState(totalKills = 1_000, defeatedBossIds = listOf("wiese")),
        )
        assertEquals(
            setOf(Achievement.KILLS_100, Achievement.KILLS_1000, Achievement.LEVEL_10, Achievement.LEVEL_50, Achievement.BOSS_FIRST),
            Achievement.reached(s),
        )
    }
}
