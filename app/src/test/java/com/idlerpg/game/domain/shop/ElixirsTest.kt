package com.idlerpg.game.domain.shop

import com.idlerpg.game.data.ActiveBuff
import com.idlerpg.game.data.BuffState
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.buffs.Buffs
import com.idlerpg.game.domain.shop.Elixirs.Elixir
import com.idlerpg.game.domain.shop.Elixirs.Outcome
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElixirsTest {
    private fun inZone(id: String, gold: Long = 1_000_000_000L, buffs: List<ActiveBuff> = emptyList()): GameState {
        val zone = Zones.byId(id)
        val base = GameState()
        return base.copy(
            gold = gold,
            character = base.character.copy(level = zone.minLevel),
            world = base.world.copy(currentZoneId = id),
            buffs = BuffState(buffs),
        )
    }

    @Test fun priceIsTenToTwentyAverageKills() {
        val s = inZone("wald")
        val kill = Elixirs.averageKillGold(s)
        Elixir.entries.forEach {
            val ratio = Elixirs.price(s, it) / kill
            assertTrue("$it $ratio", ratio in 9.99..20.5)
        }
    }

    @Test fun priceRisesWithZone() {
        val ids = listOf("wiese", "wald", "sumpf", "gebirge", "hoehle")
        val prices = ids.map { Elixirs.price(inZone(it), Elixir.XP) }
        assertEquals(prices.sorted(), prices)
        assertTrue(Elixirs.price(inZone("ende_5"), Elixir.XP) > prices.last())
    }

    @Test fun buyDeductsGoldAndAddsBuffAtomically() {
        val s = inZone("wiese", gold = 100_000)
        val r = Elixirs.buy(s, Elixir.DAMAGE)
        assertEquals(Outcome.BOUGHT, r.outcome)
        assertEquals(s.gold - r.price, r.state.gold)
        val b = r.state.buffs.active.single()
        assertEquals(Buffs.DAMAGE, b.type)
        assertEquals(Buffs.SOURCE_ELIXIR, b.source)
        assertEquals(Elixirs.DURATION_MILLIS, b.remainingMillis)
        assertEquals(1.25, Buffs.bonus(r.state, Buffs.DAMAGE), 1e-9)
    }

    @Test fun withoutGoldNothingChanges() {
        val s = inZone("wiese", gold = 0)
        val r = Elixirs.buy(s, Elixir.XP)
        assertEquals(Outcome.NOT_ENOUGH_GOLD, r.outcome)
        assertEquals(s, r.state)
    }

    @Test fun sameElixirExtendsDuration() {
        val s = inZone("wiese")
        val twice = Elixirs.buy(Elixirs.buy(s, Elixir.GOLD).state, Elixir.GOLD)
        assertEquals(Outcome.BOUGHT, twice.outcome)
        assertEquals(2 * Elixirs.DURATION_MILLIS, twice.state.buffs.active.single().remainingMillis)
    }

    @Test fun strongerRunningBuffBlocksWithoutLosingGold() {
        val s = inZone("wiese", buffs = listOf(ActiveBuff(Buffs.XP, 0.5, 60_000, "goal")))
        val r = Elixirs.buy(s, Elixir.XP)
        assertEquals(Outcome.STRONGER_ACTIVE, r.outcome)
        assertEquals(s, r.state)
    }

    @Test fun weakerBuffIsReplaced() {
        val s = inZone("wiese", buffs = listOf(ActiveBuff(Buffs.DAMAGE, 0.10, 60_000, Buffs.SOURCE_BOSS)))
        val r = Elixirs.buy(s, Elixir.DAMAGE)
        assertEquals(Outcome.BOUGHT, r.outcome)
        assertEquals(0.25, r.state.buffs.active.single().magnitude, 1e-9)
    }

    @Test fun durationCapBlocksInsteadOfWastingTime() {
        val s = inZone("wiese", buffs = listOf(ActiveBuff(Buffs.DROP, 0.25, Buffs.MAX_DURATION_MILLIS - 1000, "elixir")))
        val r = Elixirs.buy(s, Elixir.DROP)
        assertEquals(Outcome.MAX_DURATION, r.outcome)
        assertEquals(s, r.state)
    }
}
