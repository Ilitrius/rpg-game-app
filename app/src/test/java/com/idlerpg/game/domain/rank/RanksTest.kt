package com.idlerpg.game.domain.rank

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.PrestigeState
import com.idlerpg.game.data.RankState
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.equipment.Inventory
import com.idlerpg.game.domain.prestige.Prestige
import com.idlerpg.game.domain.prestige.PrestigeBonuses
import com.idlerpg.game.domain.world.Zones
import com.idlerpg.game.ui.options.SaveError
import com.idlerpg.game.ui.options.SaveField
import com.idlerpg.game.ui.options.SaveLocation
import com.idlerpg.game.ui.options.SaveTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RanksTest {
    private fun hero(level: Int = 1, gold: Long = 0, rank: Int = 0, best: Int = 0) = StarterItems.ensureStarterWeapon(
        GameState(
            character = CharacterState(level = level),
            gold = gold,
            rank = RankState(rank),
            prestige = PrestigeState(bestLevel = best),
        ),
    )

    @Test
    fun noRankMeansNeutralBonuses() {
        assertEquals(RankBonuses.NEUTRAL, RankBonuses.of(0))
        assertEquals(RankBonuses.NEUTRAL, RankBonuses.of(-3))
        assertTrue(PrestigeBonuses.of(hero()).isNeutral)
    }

    @Test
    fun bonusesGrowWithRankAndAreClamped() {
        val r1 = RankBonuses.of(1)
        assertEquals(1.06, r1.damage, 1e-9)
        assertEquals(1.06, r1.hp, 1e-9)
        assertEquals(1.02, r1.speed, 1e-9)
        assertEquals(1.0, r1.gold, 1e-9)
        assertEquals(0, r1.extraSlots)
        val r4 = RankBonuses.of(4) // Knight: first gold bonus
        assertEquals(1.02, r4.gold, 1e-9)
        assertEquals(2, r4.extraSlots)
        val top = RankBonuses.of(10)
        assertEquals(1.6, top.damage, 1e-9)
        assertEquals(1.14, top.gold, 1e-9)
        assertEquals(5, top.extraSlots)
        assertEquals(top, RankBonuses.of(999)) // tampered values cannot exceed the catalog
    }

    @Test
    fun levelsAndPricesAscend() {
        for (r in 2..RankConfig.MAX_RANK) {
            assertTrue(Ranks.requiredLevel(r) > Ranks.requiredLevel(r - 1))
            assertTrue("price of rank $r", Ranks.price(r) > Ranks.price(r - 1))
        }
        assertEquals(999, Ranks.requiredLevel(RankConfig.MAX_RANK))
        assertTrue(Ranks.price(1) > 0)
    }

    @Test
    fun cannotBuyBelowLevelOrWithoutGold() {
        val poor = hero(level = 60)
        assertEquals(RankBuyResult.NOT_ENOUGH_GOLD, Ranks.check(poor))
        assertSame(poor, Ranks.buy(poor))
        val low = hero(level = 49, gold = Long.MAX_VALUE)
        assertEquals(RankBuyResult.LEVEL_TOO_LOW, Ranks.check(low))
        assertSame(low, Ranks.buy(low))
    }

    @Test
    fun buyingPaysGoldAndRaisesRankOneAtATime() {
        val rich = hero(level = 100, gold = Ranks.price(1) + Ranks.price(2) + 7)
        val one = Ranks.buy(rich)
        assertEquals(1, one.rank.rank)
        assertEquals(rich.gold - Ranks.price(1), one.gold)
        val two = Ranks.buy(one)
        assertEquals(2, two.rank.rank)
        assertEquals(7, two.gold)
        // rank 3 needs level 175: level 100 is not enough even with plenty of gold
        assertEquals(RankBuyResult.LEVEL_TOO_LOW, Ranks.check(two.copy(gold = Long.MAX_VALUE)))
    }

    @Test
    fun bestLevelOpensTheGateAfterARebirth() {
        val s = hero(level = 1, gold = Ranks.price(1), best = 120)
        assertEquals(RankBuyResult.OK, Ranks.check(s))
    }

    @Test
    fun maxRankStopsBuying() {
        val s = hero(level = 999, gold = Long.MAX_VALUE, rank = RankConfig.MAX_RANK)
        assertEquals(RankBuyResult.MAX_RANK, Ranks.check(s))
        assertSame(s, Ranks.buy(s))
    }

    @Test
    fun rankBoostsCombatValuesAndInventory() {
        val base = hero(level = 100)
        val ranked = base.copy(rank = RankState(10))
        val a = CombatStats.of(base)
        val b = CombatStats.of(ranked)
        assertTrue(b.damageMax > a.damageMax)
        assertTrue(b.maxHp > a.maxHp)
        assertTrue(b.attacksPerSecond > a.attacksPerSecond)
        assertEquals(Inventory.capacity(base) + 5, Inventory.capacity(ranked))
    }

    @Test
    fun rankSurvivesRebirth() {
        val s = hero(level = 100).copy(
            rank = RankState(3),
            world = WorldState(
                currentZoneId = Zones.all[19].id,
                unlockedZoneIds = Zones.all.take(20).map { it.id },
                lastActiveEpochMillis = 1,
            ),
        )
        val reborn = Prestige.rebirth(s, 10)
        assertNotEquals(s.character.level, reborn.character.level)
        assertEquals(3, reborn.rank.rank)
    }

    @Test
    fun savesWithoutRankLoadAndRankRoundTrips() {
        val old = GameJson.decode("""{"gold":5}""")
        assertEquals(0, old.rank.rank)
        val s = hero(rank = 4)
        assertEquals(4, GameJson.decode(GameJson.encode(s)).rank.rank)
    }

    @Test
    fun importRejectsOutOfRangeRank() {
        val bad = hero(rank = RankConfig.MAX_RANK + 1)
        val err = SaveTransfer.validate(bad)
        assertEquals(
            SaveError.OutOfRange(SaveField.RANK, SaveLocation.Root, 0, RankConfig.MAX_RANK.toLong(), (RankConfig.MAX_RANK + 1).toLong()),
            err,
        )
        assertEquals(null, SaveTransfer.validate(hero(rank = RankConfig.MAX_RANK)))
    }
}
