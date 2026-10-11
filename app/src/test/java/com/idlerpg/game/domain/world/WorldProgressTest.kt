package com.idlerpg.game.domain.world

import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.equipment.Inventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ZoneCatalogTest {

    @Test
    fun fiveZonesInOrderWithContinuousLevels() {
        assertEquals(listOf("wiese", "wald", "sumpf", "gebirge", "hoehle"), Zones.all.map { it.id })
        Zones.all.zipWithNext().forEach { (a, b) ->
            assertEquals(a.order + 1, b.order)
            assertEquals(a.maxLevel, b.minLevel)
            assertEquals(b, Zones.next(a))
            assertEquals(a, Zones.previous(b))
        }
        assertNull(Zones.next(Zones.HOEHLE))
        assertNull(Zones.previous(Zones.WIESE))
    }

    @Test
    fun everyZoneHasThreeOwnMonstersAndABoss() {
        val seen = HashSet<MonsterType>()
        Zones.all.forEach { z ->
            assertEquals(3, z.monsters.size)
            assertTrue(z.boss.isBoss)
            z.monsters.forEach { assertTrue("${it.id} used twice", seen.add(it)) }
            assertTrue(seen.add(z.boss))
        }
        assertEquals(MonsterType.entries.toSet(), seen)
    }

    @Test
    fun bossIsMuchStrongerAndMoreRewardingThanRegularMonsters() {
        Zones.all.forEach { z ->
            val boss = MonsterScaling.boss(z)
            val avg = MonsterScaling.average(z, z.maxLevel)
            assertEquals(z.maxLevel, boss.level)
            assertTrue("${z.id} hp", boss.maxHp > avg.hp * 4)
            assertTrue("${z.id} xp", boss.xp > avg.xp * 5)
        }
    }

    @Test
    fun laterZonesAreTougher() {
        Zones.all.zipWithNext().forEach { (a, b) ->
            val ea = MonsterScaling.average(a, a.minLevel)
            val eb = MonsterScaling.average(b, b.minLevel)
            assertTrue(eb.hp > ea.hp)
            assertTrue(eb.xp > ea.xp)
            assertTrue(eb.dps > ea.dps)
        }
    }
}

class WorldProgressTest {

    private val fresh = StarterItems.ensureStarterWeapon(GameState())

    @Test
    fun onlyFirstZoneOpenAtStart() {
        assertTrue(WorldProgress.isUnlocked(fresh.world, Zones.WIESE))
        Zones.all.drop(1).forEach { assertFalse(WorldProgress.isUnlocked(fresh.world, it)) }
    }

    @Test
    fun bossNeedsKills() {
        val w = WorldState(zoneKills = mapOf("wiese" to 24L))
        assertFalse(WorldProgress.canChallengeBoss(w, Zones.WIESE))
        assertTrue(WorldProgress.canChallengeBoss(w.copy(zoneKills = mapOf("wiese" to 25L)), Zones.WIESE))
        // locked zones can't be challenged even with kills
        assertFalse(WorldProgress.canChallengeBoss(WorldState(zoneKills = mapOf("wald" to 99L)), Zones.WALD))
    }

    @Test
    fun bossVictoryUnlocksNextZoneAndResetsCounter() {
        val s = fresh.copy(world = fresh.world.copy(zoneKills = mapOf("wiese" to 30L), totalKills = 30))
        val won = WorldProgress.applyBossVictory(s, Zones.WIESE, xp = 330, gold = 144, nowMillis = 5L)
        assertTrue("wald" in won.world.unlockedZoneIds)
        assertTrue("wiese" in won.world.defeatedBossIds)
        assertTrue(WorldProgress.isUnlocked(won.world, Zones.WALD))
        assertFalse(WorldProgress.isUnlocked(won.world, Zones.SUMPF))
        assertEquals(144L, won.gold)
        assertEquals(31L, won.world.zoneKills["wiese"])
        assertEquals(0L, WorldProgress.killsTowardsBoss(won.world, Zones.WIESE))
        assertFalse(WorldProgress.canChallengeBoss(won.world, Zones.WIESE))
        // second victory doesn't duplicate ids
        val again = WorldProgress.applyBossVictory(
            won.copy(world = won.world.copy(zoneKills = mapOf("wiese" to 60L))), Zones.WIESE, 1, 1, 6L,
        )
        assertEquals(1, again.world.defeatedBossIds.count { it == "wiese" })
        assertEquals(1, again.world.unlockedZoneIds.count { it == "wald" })
    }

    @Test
    fun lastBossUnlocksNothingNew() {
        val won = WorldProgress.applyBossVictory(fresh, Zones.HOEHLE, 1, 1, 1L)
        assertEquals(fresh.world.unlockedZoneIds, won.world.unlockedZoneIds)
        assertTrue("hoehle" in won.world.defeatedBossIds)
    }

    @Test
    fun zoneUnlockedByDefeatedPreviousBossEvenWithoutUnlockEntry() {
        val w = WorldState(defeatedBossIds = listOf("wiese", "wald"))
        assertTrue(WorldProgress.isUnlocked(w, Zones.WALD))
        assertTrue(WorldProgress.isUnlocked(w, Zones.SUMPF))
        assertFalse(WorldProgress.isUnlocked(w, Zones.GEBIRGE))
    }

    @Test
    fun travelOnlyToUnlockedZones() {
        assertSame(fresh, WorldProgress.travel(fresh, "wald"))
        assertSame(fresh, WorldProgress.travel(fresh, "atlantis"))
        val open = fresh.copy(world = fresh.world.copy(unlockedZoneIds = listOf("wiese", "wald")))
        assertEquals("wald", WorldProgress.travel(open, "wald").world.currentZoneId)
    }

    @Test
    fun bossTimerOutcomes() {
        val limit = WorldProgress.BOSS_TIME_LIMIT_MILLIS
        assertEquals(BossOutcome.ONGOING, WorldProgress.bossOutcome(100, 50.0, 0))
        assertEquals(BossOutcome.ONGOING, WorldProgress.bossOutcome(100, 50.0, limit - 1))
        assertEquals(BossOutcome.TIMEOUT, WorldProgress.bossOutcome(100, 50.0, limit))
        assertEquals(BossOutcome.DEFEAT, WorldProgress.bossOutcome(100, 0.0, 10))
        // killing blow wins even at the deadline or when the hero falls in the same tick
        assertEquals(BossOutcome.VICTORY, WorldProgress.bossOutcome(0, 50.0, limit + 5))
        assertEquals(BossOutcome.VICTORY, WorldProgress.bossOutcome(0, -3.0, 10))
        assertEquals(limit, WorldProgress.bossTimeLeft(0))
        assertEquals(0L, WorldProgress.bossTimeLeft(limit + 100))
        assertEquals(30_000L, limit)
    }
}

class LootTest {

    private val fresh = StarterItems.ensureStarterWeapon(GameState())

    @Test
    fun dropChanceGrowsWithLuckAndIsCapped() {
        assertEquals(0.08, Loot.dropChance(0), 1e-9)
        assertEquals(0.09, Loot.dropChance(5), 1e-9)
        assertTrue(Loot.dropChance(20) > Loot.dropChance(5))
        assertEquals(Loot.MAX_DROP_CHANCE, Loot.dropChance(1000), 1e-9)
        assertEquals(0.08, Loot.dropChance(-5), 1e-9)
    }

    @Test
    fun seededDropRateMatchesChance() {
        val r = Random(42)
        var drops = 0
        repeat(20_000) { Loot.rollDrop(7, luck = 5, random = r)?.let { drops++; assertEquals(7, it.itemLevel) } }
        assertEquals(0.09, drops / 20_000.0, 0.008)
        // same seed -> same result
        assertEquals(Loot.rollDrop(3, 5, Random(9))?.name, Loot.rollDrop(3, 5, Random(9))?.name)
    }

    @Test
    fun bossDropIsGuaranteedAndAtLeastRare() {
        val r = Random(1)
        repeat(300) {
            val item = Loot.bossDrop(12, r)
            assertTrue(item.rarity >= Rarity.RARE)
            assertEquals(12, item.itemLevel)
        }
    }

    @Test
    fun grantAddsToInventoryOrAutoSells() {
        val item = Loot.bossDrop(5, Random(3))
        val (s, drop) = Loot.grant(fresh, item)
        assertTrue(drop.added)
        assertEquals(1, s.equipment.inventory.size)
        assertTrue(s.equipment.inventory.single().isNew)

        val full = fresh.copy(equipment = fresh.equipment.copy(inventory = List(Inventory.CAPACITY) { Item("x$it", "X", EquipmentSlot.RING) }))
        val (s2, drop2) = Loot.grant(full, item)
        assertFalse(drop2.added)
        assertEquals(item.price, drop2.autoSoldGold)
        assertEquals(full.gold + item.price, s2.gold)
        assertEquals(Inventory.CAPACITY, s2.equipment.inventory.size)
    }

    @Test
    fun offlineDropCount() {
        assertEquals(0, Loot.offlineDropCount(0, 0.1, Random(1)))
        assertEquals(0, Loot.offlineDropCount(100, 0.0, Random(1)))
        // small counts: binomial, reproducible
        val a = Loot.offlineDropCount(150, 0.1, Random(5))
        assertEquals(a, Loot.offlineDropCount(150, 0.1, Random(5)))
        assertTrue(a in 5..25)
        // large counts are capped
        assertEquals(Loot.MAX_OFFLINE_ITEMS, Loot.offlineDropCount(100_000, 0.1, Random(1)))
        // medium counts use the expected value: 450 kills * 0.02 = 9
        val m = Loot.offlineDropCount(450, 0.02, Random(2))
        assertTrue(m == 9 || m == 10)
    }
}

class OfflineLootTest {

    private val hour = 3_600_000L
    private val base = StarterItems.ensureStarterWeapon(GameState())
        .copy(world = WorldState(lastActiveEpochMillis = 1_000_000L))

    @Test
    fun offlineGrantsCappedItems() {
        val r = OfflineProgress.calculate(base, 1_000_000L + 8 * hour, Random(7))!!
        assertTrue(r.kills > 300)
        assertEquals(Loot.MAX_OFFLINE_ITEMS, r.items.size)
        assertEquals(0, r.autoSoldItems)
        val s = OfflineProgress.apply(base, r, 1_000_000L + 8 * hour)
        assertEquals(Loot.MAX_OFFLINE_ITEMS, s.equipment.inventory.size)
        assertTrue(s.equipment.inventory.all { it.isNew })
        val lvl = base.character.level.coerceIn(Zones.WIESE.minLevel, Zones.WIESE.maxLevel)
        assertTrue(r.items.all { it.itemLevel == lvl })
    }

    @Test
    fun offlineItemsBeyondCapacityAreSold() {
        val nearlyFull = base.copy(
            equipment = base.equipment.copy(inventory = List(Inventory.CAPACITY - 3) { Item("x$it", "X", EquipmentSlot.RING) }),
        )
        val r = OfflineProgress.calculate(nearlyFull, 1_000_000L + 8 * hour, Random(7))!!
        assertEquals(r.items.size - 3, r.autoSoldItems)
        assertEquals(r.items.drop(3).sumOf { it.price }, r.autoSoldGold)
        val s = OfflineProgress.apply(nearlyFull, r, 1_000_000L + 8 * hour)
        assertEquals(Inventory.CAPACITY, s.equipment.inventory.size)
        assertEquals(r.gold + r.autoSoldGold, s.gold)
    }

    @Test
    fun heroThatCantSurviveEarnsNothingOffline() {
        val hoehle = base.copy(world = base.world.copy(currentZoneId = "hoehle", unlockedZoneIds = Zones.all.map { it.id }))
        val r = OfflineProgress.calculate(hoehle, 1_000_000L + hour, Random(1))!!
        assertEquals(0L, r.kills)
        assertTrue(r.items.isEmpty())
    }
}

class HeroHpPersistenceTest {

    private val base = StarterItems.ensureStarterWeapon(GameState())
    private val maxHp = CombatStats.of(base).maxHp

    @Test
    fun restoreClampsAndTreatsUnknownAsFull() {
        assertEquals(maxHp, CombatRules.restoreHeroHp(-1, maxHp))
        assertEquals(40, CombatRules.restoreHeroHp(40, maxHp))
        assertEquals(1, CombatRules.restoreHeroHp(0, maxHp))
        assertEquals(maxHp, CombatRules.restoreHeroHp(maxHp + 500, maxHp))
    }

    @Test
    fun offlineRegenHealsCappedAtMax() {
        val s = base.copy(world = base.world.copy(heroHp = 20, lastActiveEpochMillis = 1_000L))
        // 10 s away: +1.5 % of max per second
        val healed = CombatRules.healOffline(s, 11_000L)
        assertEquals((20 + CombatRules.regen(maxHp, 10_000)).toInt(), healed.world.heroHp)
        assertEquals(maxHp, CombatRules.healOffline(s, 1_000_000L).world.heroHp)
    }

    @Test
    fun unknownHpOrFirstStartUnchanged() {
        val unknown = base.copy(world = base.world.copy(heroHp = -1, lastActiveEpochMillis = 1_000L))
        assertSame(unknown, CombatRules.healOffline(unknown, 99_999L))
        val never = base.copy(world = base.world.copy(heroHp = 10, lastActiveEpochMillis = 0L))
        assertSame(never, CombatRules.healOffline(never, 99_999L))
    }

    @Test
    fun offlineApplyHealsAndStampsTime() {
        val s = base.copy(world = base.world.copy(heroHp = 10, lastActiveEpochMillis = 1_000L))
        val out = OfflineProgress.apply(s, null, 1_000L + 30_000L)
        assertEquals((10 + CombatRules.regen(maxHp, 30_000)).toInt(), out.world.heroHp)
        assertEquals(31_000L, out.world.lastActiveEpochMillis)
    }

    @Test
    fun hpOverMaxAfterUnequipIsClamped() {
        val s = base.copy(world = base.world.copy(heroHp = 999, lastActiveEpochMillis = 1_000L))
        assertEquals(maxHp, CombatRules.healOffline(s, 2_000L).world.heroHp)
    }
}

class NewWorldStateCompatTest {

    @Test
    fun oldWorldJsonGetsDefaultsForNewFields() {
        val old = """{"schemaVersion":1,"world":{"currentZoneId":"wiese","unlockedZoneIds":["wiese"],"totalKills":40,"zoneKills":{"wiese":40},"lastActiveEpochMillis":7}}"""
        val s = GameJson.decode(old)
        assertEquals(-1, s.world.heroHp)
        assertTrue(s.world.defeatedBossIds.isEmpty())
        assertTrue(s.world.bossDefeatedAtKills.isEmpty())
        assertEquals(40L, s.world.zoneKills["wiese"])
        // an old save with 40 kills may challenge the first boss right away
        assertTrue(WorldProgress.canChallengeBoss(s.world, Zones.WIESE))
    }

    @Test
    fun newFieldsRoundTrip() {
        val s = GameState(
            world = WorldState(
                currentZoneId = "wald", unlockedZoneIds = listOf("wiese", "wald"), heroHp = 77,
                defeatedBossIds = listOf("wiese"), bossDefeatedAtKills = mapOf("wiese" to 31L),
            ),
        )
        assertEquals(s, GameJson.decode(GameJson.encode(s)))
    }

    @Test
    fun unknownZoneInSaveFallsBackToWiese() {
        val s = GameJson.decode("""{"world":{"currentZoneId":"vulkan"}}""")
        assertEquals(Zones.WIESE, Zones.byId(s.world.currentZoneId))
        assertNotNull(s)
    }
}
