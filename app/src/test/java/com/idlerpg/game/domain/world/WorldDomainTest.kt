package com.idlerpg.game.domain.world

import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.combat.CombatRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MonsterScalingTest {

    private val zone = Zones.WIESE

    @Test
    fun levelOneUsesBaseValues() {
        Zones.WIESE.monsters.forEach { t ->
            val s = MonsterScaling.stats(t, zone, 1)
            assertEquals(t.baseHp.toLong(), s.maxHp)
            assertEquals(t.baseDamageMin, s.damageMin)
            assertEquals(t.baseDamageMax, s.damageMax)
            assertEquals(t.baseAttackIntervalMillis, s.attackIntervalMillis)
            assertEquals(t.baseXp.toLong(), s.xp)
            assertEquals(t.baseGold.toLong(), s.gold)
        }
    }

    @Test
    fun valuesGrowWithLevel() {
        Zones.WIESE.monsters.forEach { t ->
            var prev = MonsterScaling.stats(t, zone, 1)
            for (lvl in 2..20) {
                val s = MonsterScaling.stats(t, zone, lvl)
                assertTrue(s.maxHp > prev.maxHp)
                assertTrue(s.xp >= prev.xp)
                assertTrue(s.gold >= prev.gold)
                assertTrue(s.damageMax >= prev.damageMax)
                assertTrue(s.attackIntervalMillis <= prev.attackIntervalMillis)
                prev = s
            }
        }
    }

    @Test
    fun knownScaledValues() {
        // Slime L5: hp 20 * 2.2 = 44, xp 6 * 3 = 18, gold 2 * 2.4 = 4.8 -> 5
        val s = MonsterScaling.stats(MonsterType.SLIME, zone, 5)
        assertEquals(44L, s.maxHp)
        assertEquals(18L, s.xp)
        assertEquals(5L, s.gold)
        assertEquals(Math.round(1700 * 0.92), s.attackIntervalMillis)
    }

    @Test
    fun attackIntervalNeverBelowFloor() {
        val s = MonsterScaling.stats(MonsterType.GIANT_RAT, zone, 500)
        assertEquals(Math.round(1300 * MonsterScaling.MIN_INTERVAL_FACTOR), s.attackIntervalMillis)
    }

    @Test
    fun zoneMultipliersApply() {
        val hard = zone.copy(id = "test", hpMultiplier = 2.0, damageMultiplier = 3.0, rewardMultiplier = 1.5)
        val s = MonsterScaling.stats(MonsterType.BOAR, hard, 1)
        assertEquals(72L, s.maxHp)
        assertEquals(9, s.damageMin)
        assertEquals(18, s.damageMax)
        assertEquals(18L, s.xp)
    }

    @Test
    fun rolledLevelStaysInZoneRange() {
        val r = Random(1)
        repeat(500) {
            val hero = r.nextInt(1, 40)
            val lvl = MonsterScaling.rollLevel(zone, hero, r)
            assertTrue(lvl in zone.minLevel..zone.maxLevel)
            assertTrue(lvl <= hero.coerceAtLeast(zone.minLevel))
        }
    }

    @Test
    fun rollTypeRespectsWeights() {
        val r = Random(3)
        val counts = Zones.WIESE.monsters.associateWith { 0 }.toMutableMap()
        repeat(9000) { counts.merge(MonsterScaling.rollType(zone, r), 1, Int::plus) }
        // weights 4 : 3 : 2
        assertEquals(4000.0, counts[MonsterType.SLIME]!!.toDouble(), 250.0)
        assertEquals(3000.0, counts[MonsterType.GIANT_RAT]!!.toDouble(), 250.0)
        assertEquals(2000.0, counts[MonsterType.BOAR]!!.toDouble(), 250.0)
    }

    @Test
    fun rollDamageAndGoldInRange() {
        val s = MonsterScaling.stats(MonsterType.BOAR, zone, 3)
        val r = Random(5)
        repeat(1000) {
            assertTrue(s.rollDamage(r) in s.damageMin..s.damageMax)
            val g = s.rollGold(r)
            assertTrue(g >= 1 && g >= (s.gold * 0.8).toLong() && g <= Math.round(s.gold * 1.2))
        }
    }

    @Test
    fun averageIsWeighted() {
        val avg = MonsterScaling.average(zone, 1)
        assertEquals((20 * 4 + 26 * 3 + 36 * 2) / 9.0, avg.hp, 1e-9)
        assertEquals((6 * 4 + 8 * 3 + 12 * 2) / 9.0, avg.xp, 1e-9)
    }

    @Test
    fun unknownZoneFallsBackToWiese() {
        assertEquals(Zones.WIESE, Zones.byId("atlantis"))
        assertEquals(Zones.WIESE, Zones.byId(null))
    }
}

class CombatBalanceTest {

    private val starter = StarterItems.ensureStarterWeapon(GameState())
    private val hero = CombatStats.of(starter)

    @Test
    fun starterHeroKillsZoneOneMonstersInThreeToSixSeconds() {
        Zones.WIESE.monsters.forEach { t ->
            val m = MonsterScaling.stats(t, Zones.WIESE, 1)
            val secs = CombatRules.expectedSecondsToKill(hero, m)
            assertTrue("$t takes $secs s", secs in 3.0..6.0)
        }
    }

    @Test
    fun starterHeroSurvivesEveryZoneOneFight() {
        Zones.WIESE.monsters.forEach { t ->
            val m = MonsterScaling.stats(t, Zones.WIESE, 1)
            val taken = CombatRules.expectedDamageTaken(hero, m)
            assertTrue("$t deals $taken", taken < hero.maxHp * 0.2)
        }
    }

    @Test
    fun regenOutpacesAverageDamageOverAFullCycle() {
        // Over one average kill cycle (fight + walk-in + death) the hero should not lose HP on average.
        val avgDmgTaken = Zones.WIESE.monsters.sumOf {
            CombatRules.expectedDamageTaken(hero, MonsterScaling.stats(it, Zones.WIESE, 1)) * it.spawnWeight
        } / Zones.WIESE.monsters.sumOf { it.spawnWeight }
        val avgFightMs = Zones.WIESE.monsters.sumOf {
            CombatRules.expectedSecondsToKill(hero, MonsterScaling.stats(it, Zones.WIESE, 1)) * 1000 * it.spawnWeight
        } / Zones.WIESE.monsters.sumOf { it.spawnWeight }
        val regen = CombatRules.regen(hero.maxHp, (avgFightMs + CombatRules.TRAVEL_MILLIS).toLong())
        assertTrue("regen $regen vs dmg $avgDmgTaken", regen >= avgDmgTaken)
    }

    @Test
    fun regenIsProportional() {
        assertEquals(1.5, CombatRules.regen(100, 1000), 1e-9)
        assertEquals(7.5, CombatRules.regen(250, 2000), 1e-9)
        assertEquals(0.0, CombatRules.regen(100, 0), 1e-9)
    }

    @Test
    fun hitsToKill() {
        val m = MonsterScaling.stats(MonsterType.SLIME, Zones.WIESE, 1)
        // starter: 5–7 damage, avg 6 -> ceil(20/6) = 4
        assertEquals(4, CombatRules.averageHitsToKill(hero, m))
    }

    @Test
    fun killRewardAddsXpGoldAndCounters() {
        val s = CombatRules.applyKillReward(starter, xp = 8, gold = 3, zoneId = "wiese", nowMillis = 1234L)
        assertEquals(8L, s.character.xp)
        assertEquals(3L, s.gold)
        assertEquals(1L, s.world.totalKills)
        assertEquals(1L, s.world.zoneKills["wiese"])
        assertEquals(1234L, s.world.lastActiveEpochMillis)
        val s2 = CombatRules.applyKillReward(s, xp = 50, gold = 2, zoneId = "wiese", nowMillis = 2000L)
        assertEquals(2, s2.character.level)
        assertEquals(8L, s2.character.xp)
        assertEquals(Progression.STAT_POINTS_PER_LEVEL, s2.character.unspentPoints)
        assertEquals(5L, s2.gold)
        assertEquals(2L, s2.world.zoneKills["wiese"])
    }

    @Test
    fun killRewardIgnoresNegativeValues() {
        val s = CombatRules.applyKillReward(starter, xp = -5, gold = -5, zoneId = "wiese", nowMillis = 1L)
        assertEquals(0L, s.character.xp)
        assertEquals(0L, s.gold)
    }
}

class OfflineProgressTest {

    private val hour = 3_600_000L
    private val base = StarterItems.ensureStarterWeapon(GameState())
        .copy(world = WorldState(lastActiveEpochMillis = 1_000_000L))

    @Test
    fun firstStartGivesNothing() {
        assertNull(OfflineProgress.calculate(GameState(), 5_000_000L))
    }

    @Test
    fun shortAbsenceOrClockBackwardsGivesNothing() {
        assertNull(OfflineProgress.calculate(base, 1_000_000L + 30_000L))
        assertNull(OfflineProgress.calculate(base, 500_000L))
    }

    @Test
    fun oneHourMatchesEstimate() {
        val r = OfflineProgress.calculate(base, 1_000_000L + hour)!!
        val hero = CombatStats.of(base)
        val avg = MonsterScaling.average(Zones.WIESE, 1)
        val perKill = avg.hp / hero.dps + CombatRules.TRAVEL_MILLIS / 1000.0
        val expected = (3600.0 / perKill * OfflineProgress.EFFICIENCY).toLong()
        assertEquals(expected, r.kills)
        assertEquals(hour, r.awayMillis)
        assertEquals(hour, r.countedMillis)
        assertEquals(Math.round(expected * avg.xp), r.xp)
        assertEquals(Math.round(expected * avg.gold), r.gold)
        // sanity: roughly 250–350 kills per hour for the starter hero
        assertTrue(r.kills in 250..350)
    }

    @Test
    fun absenceIsCappedAtEightHours() {
        val eight = OfflineProgress.calculate(base, 1_000_000L + 8 * hour)!!
        val twenty = OfflineProgress.calculate(base, 1_000_000L + 20 * hour)!!
        assertEquals(eight.kills, twenty.kills)
        assertEquals(20 * hour, twenty.awayMillis)
        assertEquals(OfflineProgress.MAX_OFFLINE_MILLIS, twenty.countedMillis)
    }

    @Test
    fun strongerHeroKillsMore() {
        val strong = base.copy(character = base.character.copy(stats = base.character.stats.copy(strength = 40)))
        val a = OfflineProgress.calculate(base, 1_000_000L + hour)!!
        val b = OfflineProgress.calculate(strong, 1_000_000L + hour)!!
        assertTrue(b.kills > a.kills)
    }

    @Test
    fun applyGrantsRewardAndStampsTime() {
        val now = 1_000_000L + 2 * hour
        val r = OfflineProgress.calculate(base, now)!!
        val s = OfflineProgress.apply(base, r, now)
        assertEquals(r.gold, s.gold)
        assertEquals(r.kills, s.world.totalKills)
        assertEquals(now, s.world.lastActiveEpochMillis)
        assertEquals(r.levelsGained, s.character.level - 1)
        assertTrue(r.levelsGained > 0)
    }

    @Test
    fun applyWithoutRewardOnlyStampsTime() {
        val s = OfflineProgress.apply(base, null, 42L)
        assertEquals(base.copy(world = base.world.copy(lastActiveEpochMillis = 42L)), s)
    }
}

class WorldStateCompatTest {

    @Test
    fun oldSaveWithoutWorldDecodesWithDefaults() {
        val old = """{"schemaVersion":1,"gold":12,"character":{"name":"Alt","level":3}}"""
        val s = GameJson.decode(old)
        assertEquals(WorldState(), s.world)
        assertEquals(12L, s.gold)
    }

    @Test
    fun worldRoundTrips() {
        val s = GameState(world = WorldState(totalKills = 77, zoneKills = mapOf("wiese" to 77L), lastActiveEpochMillis = 99L))
        assertEquals(s, GameJson.decode(GameJson.encode(s)))
    }
}
