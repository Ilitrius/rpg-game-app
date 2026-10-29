package com.idlerpg.game.domain.combat

import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.TapStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ComboTrackerTest {
    @Test
    fun tapsInsideTheWindowBuildUpAndPauseResets() {
        val c = ComboTracker()
        assertEquals(0, c.register(1000))
        assertEquals(1, c.register(1500))
        assertEquals(2, c.register(2100)) // exactly 600 ms still counts
        assertEquals(0, c.register(2701)) // 601 ms: restart
        assertEquals(1, c.register(3000))
        assertEquals(0, c.current(3700))
    }

    @Test
    fun comboRaisesTapDamageUpToCap() {
        val hero = CombatStats.of(GameState().let { it.copy(character = it.character.copy(stats = Stats(30, 20, 10, 5))) })
        val low = TapStats.tapDamage(hero, 100, 0)
        val high = TapStats.tapDamage(hero, 100, TapStats.comboCap)
        val over = TapStats.tapDamage(hero, 100, 500)
        assertTrue(high > low)
        assertEquals(high, over)
        assertEquals(1.5, TapStats.comboMultiplier(500), 1e-9)
    }

    @Test
    fun maxTapDpsStaysWithinTwiceTheAutoDps() {
        for (str in listOf(5, 50, 300, 800)) {
            val hero = CombatStats.of(GameState().let { it.copy(character = it.character.copy(level = 50, stats = Stats(str, str / 2 + 5, str / 3 + 5, 5))) })
            assertTrue("str $str: tap ${TapStats.maxTapDps(hero)} vs auto ${hero.dps}", TapStats.maxTapDps(hero) <= 2.0 * hero.dps)
        }
        val hit = TapStats.tapHit(CombatStats.of(GameState()), 10, Random(1))
        assertTrue(hit.damage >= 1)
    }
}
