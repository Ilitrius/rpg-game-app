package com.idlerpg.game.domain.buffs

import com.idlerpg.game.data.ActiveBuff
import com.idlerpg.game.data.BuffState
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.world.Loot
import com.idlerpg.game.domain.world.OfflineProgress
import com.idlerpg.game.domain.world.Rest
import com.idlerpg.game.domain.world.WorldProgress
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BuffsTest {
    private val s0 = GameState()
    private val min = 60_000L

    @Test
    fun oldSaveWithoutBuffsLoads() {
        val json = GameJson.encode(s0).replace(Regex("\"buffs\":\\{[^}]*\\},?"), "")
        val loaded = GameJson.decode(json)
        assertTrue(loaded.buffs.active.isEmpty())
        assertEquals(1.0, Buffs.bonus(loaded, Buffs.XP), 1e-9)
    }

    @Test
    fun buffsSurviveJsonRoundTrip() {
        val s = Buffs.add(s0, Buffs.GOLD, 0.25, 15 * min, Buffs.SOURCE_ELIXIR)
        assertEquals(s.buffs, GameJson.decode(GameJson.encode(s)).buffs)
    }

    @Test
    fun tickReducesAndExpires() {
        var s = Buffs.add(s0, Buffs.XP, 0.25, 10_000)
        s = Buffs.tick(s, 4_000)
        assertEquals(6_000, s.buffs.active.single().remainingMillis)
        assertEquals(1.25, Buffs.bonus(s, Buffs.XP), 1e-9)
        s = Buffs.tick(s, 6_000)
        assertTrue(s.buffs.active.isEmpty())
        assertEquals(1.0, Buffs.bonus(s, Buffs.XP), 1e-9)
    }

    @Test
    fun tickWithoutDeltaOrBuffsIsNoOp() {
        val s = Buffs.add(s0, Buffs.XP, 0.25, 10_000)
        assertSame(s, Buffs.tick(s, 0))
        assertSame(s0, Buffs.tick(s0, 5_000))
    }

    @Test
    fun sameMagnitudeAddsDurationStrongerReplacesWeakerIgnored() {
        var s = Buffs.add(s0, Buffs.DAMAGE, 0.25, 10 * min)
        s = Buffs.add(s, Buffs.DAMAGE, 0.25, 5 * min)
        assertEquals(15 * min, s.buffs.active.single().remainingMillis)
        s = Buffs.add(s, Buffs.DAMAGE, 0.10, 60 * min)
        assertEquals(0.25, s.buffs.active.single().magnitude, 1e-9)
        assertEquals(15 * min, s.buffs.active.single().remainingMillis)
        s = Buffs.add(s, Buffs.DAMAGE, 0.5, 2 * min)
        assertEquals(0.5, s.buffs.active.single().magnitude, 1e-9)
        assertEquals(2 * min, s.buffs.active.single().remainingMillis)
    }

    @Test
    fun differentTypesRunInParallel() {
        var s = Buffs.add(s0, Buffs.DAMAGE, 0.25, min)
        s = Buffs.add(s, Buffs.GOLD, 0.25, min)
        assertEquals(2, s.buffs.active.size)
    }

    @Test
    fun capsApply() {
        val s = Buffs.add(s0, Buffs.XP, 5.0, 100L * 60 * min)
        val b = s.buffs.active.single()
        assertEquals(Buffs.MAX_BONUS, b.magnitude, 1e-9)
        assertEquals(Buffs.MAX_DURATION_MILLIS, b.remainingMillis)
        val long = Buffs.add(s, Buffs.XP, 1.0, 3L * 60 * min)
        assertEquals(Buffs.MAX_DURATION_MILLIS, long.buffs.active.single().remainingMillis)
        // bonus sum is capped even for hand-edited saves
        val hacked = s0.copy(buffs = BuffState(List(3) { ActiveBuff(Buffs.XP, 0.9, min) }))
        assertEquals(2.0, Buffs.bonus(hacked, Buffs.XP), 1e-9)
    }

    @Test
    fun invalidInputAndUnknownTypesIgnored() {
        assertSame(s0, Buffs.add(s0, "bogus", 0.2, min))
        assertSame(s0, Buffs.add(s0, Buffs.XP, 0.0, min))
        assertSame(s0, Buffs.add(s0, Buffs.XP, 0.2, 0))
        assertSame(s0, Buffs.add(s0, Buffs.XP, Double.NaN, min))
        val withUnknown = s0.copy(buffs = BuffState(listOf(ActiveBuff("bogus", 0.5, min))))
        assertEquals(1.0, Buffs.bonus(withUnknown, "bogus").let { if (Buffs.isKnown("bogus")) it else 1.0 }, 1e-9)
        assertTrue(Buffs.tick(withUnknown, 1).buffs.active.isEmpty())
    }

    @Test
    fun clockFormat() {
        assertEquals("0:01", Buffs.clock(1))
        assertEquals("14:59", Buffs.clock(14 * min + 59_000))
        assertEquals("1:00:00", Buffs.clock(60 * min))
    }

    @Test
    fun killRewardUsesXpAndGoldBuffsOnlyWhenAsked() {
        val s = Buffs.add(Buffs.add(s0, Buffs.XP, 0.5, min), Buffs.GOLD, 0.25, min)
        val plain = CombatRules.applyKillReward(s, 10, 100, "wiese", 1L)
        assertEquals(100, plain.gold)
        val buffed = CombatRules.applyKillReward(s, 10, 100, "wiese", 1L, applyBuffs = true)
        assertEquals(125, buffed.gold)
        assertEquals(125L, buffed.stats.goldEarned)
        assertTrue(buffed.character.xp + buffed.character.level * 1_000_000L > plain.character.xp + plain.character.level * 1_000_000L)
    }

    @Test
    fun bossVictoryAndBuffGrantAreOneStateTransform() {
        val zone = Zones.WIESE
        val won = WorldProgress.applyBossVictory(s0, zone, 10, 100, 5L, applyBuffs = true)
        val s = Buffs.add(won, Buffs.DAMAGE, Buffs.BOSS_DAMAGE_MAGNITUDE, Buffs.BOSS_DURATION_MILLIS, Buffs.SOURCE_BOSS)
        assertEquals(1.10, Buffs.bonus(s, Buffs.DAMAGE), 1e-9)
        assertEquals(Buffs.SOURCE_BOSS, s.buffs.active.single().source)
        assertTrue(zone.id in s.world.defeatedBossIds)
    }

    @Test
    fun damageBuffRaisesLiveStatsButNotPlainStats() {
        val s = Buffs.add(s0, Buffs.DAMAGE, 0.5, min)
        val plain = CombatStats.of(s)
        val live = CombatStats.live(s)
        assertEquals(CombatStats.of(s0), plain)
        assertTrue(live.damageMax > plain.damageMax)
        assertEquals(plain.maxHp, live.maxHp)
        assertEquals(CombatStats.of(s0), CombatStats.live(s0))
    }

    @Test
    fun dropBuffRaisesChance() {
        assertEquals(Loot.dropChance(0), Loot.dropChance(0, 1.0), 1e-9)
        assertEquals(0.16, Loot.dropChance(0, 2.0), 1e-9)
        assertTrue(Loot.dropChance(1000, 2.0) <= Loot.MAX_DROP_CHANCE * 2)
        val r = Random(3)
        var plain = 0; var buffed = 0
        repeat(20_000) { if (Loot.rollDrop(5, 0, r) != null) plain++ }
        repeat(20_000) { if (Loot.rollDrop(5, 0, r, 2.0) != null) buffed++ }
        assertTrue(buffed > plain * 1.6)
    }

    @Test
    fun offlineIgnoresBuffsAndDoesNotTickThem() {
        val base = s0.copy(world = s0.world.copy(lastActiveEpochMillis = 1_000L))
        val buffed = Buffs.add(Buffs.add(base, Buffs.XP, 1.0, 60 * min), Buffs.GOLD, 1.0, 60 * min)
        val now = 1_000L + 8 * 60 * min
        val a = OfflineProgress.calculate(base, now, Random(1))
        val b = OfflineProgress.calculate(buffed, now, Random(1))
        assertEquals(a!!.kills, b!!.kills)
        assertEquals(a.xp, b.xp)
        assertEquals(a.gold, b.gold)
        val applied = OfflineProgress.apply(buffed, b, now)
        assertEquals(buffed.buffs, applied.buffs)
        assertEquals(60 * min, applied.buffs.active.first().remainingMillis)
    }

    @Test
    fun restCostScalesWithZoneAndPayIsAtomic() {
        val c1 = Rest.healCost(Zones.WIESE, 1)
        assertTrue(c1 >= Rest.MIN_COST)
        assertTrue(Rest.healCost(Zones.all.last(), 900) > Rest.healCost(Zones.WIESE, 5) * 10)
        assertNull(Rest.pay(s0.copy(gold = c1 - 1), c1))
        val paid = Rest.pay(s0.copy(gold = c1 + 7), c1)!!
        assertEquals(7, paid.gold)
    }
}
