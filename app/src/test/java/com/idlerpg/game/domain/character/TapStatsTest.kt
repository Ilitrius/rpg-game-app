package com.idlerpg.game.domain.character

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TapStatsTest {
    private fun refHero(level: Int): CombatStats {
        val pts = Progression.STAT_POINTS_PER_LEVEL * (level - 1)
        val str = (pts * 0.45).toInt()
        val dex = (pts * 0.30).toInt()
        val s = StarterItems.ensureStarterWeapon(GameState())
        return CombatStats.of(
            s.copy(character = s.character.copy(level = level, stats = Stats(5 + str, 5 + dex, 5 + pts - str - dex, 5))),
        )
    }

    @Test fun clickMultiplierBoundsAndFormula() {
        assertEquals(1.0, TapStats.clickMultiplier(Stats(0, 0, 0, 0)), 1e-9)
        assertEquals(1.0, TapStats.clickMultiplier(Stats(-5, -5, 0, 0)), 1e-9)
        assertEquals(1.2, TapStats.clickMultiplier(Stats(10, 20, 0, 0)), 1e-9)
        assertEquals(2.5, TapStats.clickMultiplier(Stats(9999, 9999, 0, 0)), 1e-9)
    }

    @Test fun clickMultiplierIsMonotonic() {
        var prev = 0.0
        for (i in 0..400) {
            val m = TapStats.clickMultiplier(Stats(i, i / 2, 0, 0))
            assertTrue(m >= prev); assertTrue(m <= TapStats.MAX_CLICK_MULTIPLIER); prev = m
        }
    }

    @Test fun comboBoundsMonotonicCap() {
        assertEquals(1.0, TapStats.comboMultiplier(-3), 1e-9)
        assertEquals(1.0, TapStats.comboMultiplier(0), 1e-9)
        assertEquals(1.1, TapStats.comboMultiplier(5), 1e-9)
        assertEquals(1.5, TapStats.comboMultiplier(25), 1e-9)
        assertEquals(1.5, TapStats.comboMultiplier(10_000), 1e-9)
        assertEquals(25, TapStats.comboCap)
        var prev = 0.0
        for (n in 0..100) { val c = TapStats.comboMultiplier(n); assertTrue(c >= prev); prev = c }
    }

    @Test fun tapDamageWithinRangeAndAtLeastOne() {
        for (lvl in listOf(1, 10, 40, 100)) {
            val c = refHero(lvl)
            assertTrue(TapStats.minTapDamage(c) >= 1)
            assertTrue(TapStats.maxTapDamage(c) >= TapStats.minTapDamage(c))
            assertTrue(TapStats.maxTapDamage(c, 25) >= TapStats.maxTapDamage(c, 0))
            val r = Random(1)
            repeat(200) {
                val h = TapStats.tapHit(c, 10, r)
                assertTrue(h.damage >= 1)
            }
        }
    }

    @Test fun tapDpsAtMostDoubleAutoDpsOnReferenceHero() {
        for (lvl in 1..100) {
            val c = refHero(lvl)
            val ratio = TapStats.maxTapDps(c) / c.dps
            assertTrue("level $lvl ratio $ratio", ratio <= 2.0)
        }
        // generous: tapping is clearly worth it at level 1
        assertTrue(TapStats.maxTapDps(refHero(1)) / refHero(1).dps > 1.5)
    }
}
