package com.idlerpg.game.domain.prestige

import com.idlerpg.game.data.ActiveBuff
import com.idlerpg.game.data.BuffState
import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.GoalState
import com.idlerpg.game.data.PrestigeState
import com.idlerpg.game.data.ProgressState
import com.idlerpg.game.data.ShopState
import com.idlerpg.game.data.StatsState
import com.idlerpg.game.data.Stats
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.character.TapStats
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PrestigeTest {
    private val zone20 = Zones.all[19].id

    private fun veteran(level: Int = 80, zoneIdx: Int = 19, prestige: PrestigeState = PrestigeState()): GameState =
        StarterItems.ensureStarterWeapon(
            GameState(
                character = CharacterState(level = level, xp = 123, unspentPoints = 7, stats = Stats(50, 40, 30, 20)),
                gold = 999_999,
                world = WorldState(
                    currentZoneId = Zones.all[zoneIdx].id,
                    unlockedZoneIds = Zones.all.take(zoneIdx + 1).map { it.id },
                    totalKills = 5000,
                    zoneKills = mapOf("wiese" to 100L),
                    defeatedBossIds = listOf("wiese"),
                    lastActiveEpochMillis = 1,
                ),
                shop = ShopState(restockedAtEpochMillis = 5, stockLevel = 3),
                stats = StatsState(playTimeMillis = 10, highestHit = 99, goldEarned = 1234, tapCount = 7),
                buffs = BuffState(listOf(ActiveBuff("damage", 0.2, 1000))),
                inventoryExtraSlots = 10,
                prestige = prestige,
                progress = ProgressState(
                    dailyStreak = 4, clockHighWaterMillis = 77, goalsCompleted = 3,
                    goals = listOf(GoalState("kills_100", "kills", 100, baseline = 4000), GoalState("level_1", "level", 1, baseline = 1, claimed = true)),
                ),
            ),
        )

    @Test fun oldSaveWithoutPrestigeLoads() {
        val json = """{"schemaVersion":1,"gold":5}"""
        val s = GameJson.decode(json)
        assertEquals(PrestigeState(), s.prestige)
        assertTrue(PrestigeBonuses.of(s).isNeutral)
    }

    @Test fun gateNeedsLevelAndZone() {
        assertTrue(Prestige.canRebirth(veteran()))
        assertFalse(Prestige.canRebirth(veteran(level = 79)))
        assertFalse(Prestige.canRebirth(veteran(zoneIdx = 18)))
        assertEquals(0L, Prestige.soulsPreview(veteran(level = 79)))
    }

    @Test fun lockedRebirthReturnsSameInstance() {
        val s = veteran(level = 10)
        assertSame(s, Prestige.rebirth(s, 100))
    }

    @Test fun resetScopeAndKeeps() {
        val s = veteran()
        val souls = Prestige.soulsPreview(s)
        val r = Prestige.rebirth(s, 5000)
        assertEquals(1, r.character.level)
        assertEquals(0L, r.character.xp)
        assertEquals(0, r.character.unspentPoints)
        assertEquals(Stats(5, 5, 5, 5), r.character.stats)
        assertEquals(0L, r.gold)
        assertEquals(ShopState(), r.shop)
        assertEquals("wiese", r.world.currentZoneId)
        assertEquals(listOf("wiese"), r.world.unlockedZoneIds)
        assertEquals(0L, r.world.totalKills)
        assertEquals(5000L, r.world.lastActiveEpochMillis)
        assertTrue(r.buffs.active.isEmpty())
        // fresh starter dagger, nothing else
        assertTrue(r.starterWeaponGranted)
        assertEquals(StarterItems.RUSTY_DAGGER, r.equipment.equipped[EquipmentSlot.WEAPON])
        assertEquals(1, r.equipment.equipped.size)
        assertTrue(r.equipment.inventory.isEmpty())
        // kept
        assertEquals(s.stats, r.stats)
        assertEquals(s.inventoryExtraSlots, r.inventoryExtraSlots)
        assertEquals(s.progress.dailyStreak, r.progress.dailyStreak)
        assertEquals(s.progress.clockHighWaterMillis, r.progress.clockHighWaterMillis)
        assertEquals(s.progress.goalsCompleted, r.progress.goalsCompleted)
        assertEquals(1, r.prestige.rebirths)
        assertEquals(souls, r.prestige.souls)
        assertEquals(80, r.prestige.bestLevel)
        assertEquals(20, r.prestige.bestZoneOrder)
        // goals: unclaimed rebaselined to the new counter, claimed untouched
        assertEquals(0L, r.progress.goals.first { it.id == "kills_100" }.baseline)
        assertTrue(r.progress.goals.first { it.id == "level_1" }.claimed)
        assertEquals(0L, com.idlerpg.game.domain.progress.Goals.progress(r, r.progress.goals.first { it.id == "kills_100" }))
    }

    @Test fun rebirthRightAfterIsLocked() {
        val r = Prestige.rebirth(veteran(), 1)
        assertFalse(Prestige.canRebirth(r))
        assertSame(r, Prestige.rebirth(r, 2))
    }

    @Test fun startLevelTalentGivesLevelAndPoints() {
        val p = PrestigeState(talents = mapOf(Talents.START_LEVEL to 5))
        val r = Prestige.rebirth(veteran(prestige = p), 1)
        assertEquals(11, r.character.level)
        assertEquals(30, r.character.unspentPoints)
        assertFalse(Prestige.canRebirth(r))
    }

    @Test fun soulFormulaMonotoneBoundedAndPreviewEqualsPayout() {
        var prev = -1L
        for (l in 0..2000) { val v = Prestige.souls(l, 50); assertTrue(v >= prev); prev = v }
        prev = -1
        for (z in -5..200) { val v = Prestige.souls(500, z); assertTrue(v >= prev); prev = v }
        assertTrue(Prestige.souls(Int.MAX_VALUE, Int.MAX_VALUE) in 1..2000)
        assertTrue(Prestige.souls(Int.MIN_VALUE, Int.MIN_VALUE) == 0L)
        val s = veteran(level = 150, zoneIdx = 40)
        val before = s.prestige.souls
        assertEquals(Prestige.soulsPreview(s), Prestige.rebirth(s, 1).prestige.souls - before)
        assertEquals(114L, Prestige.souls(80, 20))
    }

    @Test fun soulsSaturate() {
        val s = veteran(prestige = PrestigeState(souls = Long.MAX_VALUE - 1))
        assertEquals(Long.MAX_VALUE, Prestige.rebirth(s, 1).prestige.souls)
    }

    @Test fun trackBest() {
        val s = veteran(level = 30, zoneIdx = 4)
        val t = Prestige.trackBest(s)
        assertEquals(30, t.prestige.bestLevel)
        assertEquals(5, t.prestige.bestZoneOrder)
        assertSame(t, Prestige.trackBest(t))
    }

    @Test fun buyWithSouls() {
        val s = veteran(prestige = PrestigeState(souls = 10))
        val t = Talents.byId(Talents.DAMAGE)!!
        val cost = Prestige.soulCost(t, 0)
        val r = Prestige.buyTalent(s, Talents.DAMAGE, PayWith.SOULS)
        assertEquals(1, Prestige.level(r.prestige, Talents.DAMAGE))
        assertEquals(10 - cost, r.prestige.souls)
        assertEquals(cost, r.prestige.soulsSpentTotal)
        assertEquals(s.gold, r.gold)
        assertEquals(1.03, PrestigeBonuses.of(r).damage, 1e-9)
    }

    @Test fun buyWithGoldAndFiftyPercentRule() {
        val big = PrestigeState(rebirths = 1)
        var s = veteran(prestige = big).copy(gold = Long.MAX_VALUE / 2)
        val t = Talents.byId(Talents.DAMAGE)!!
        // first level by gold is not allowed (1 of 1 levels = 100 %)
        assertEquals(BuyResult.GOLD_SHARE_REACHED, Prestige.check(s, Talents.DAMAGE, PayWith.GOLD))
        s = s.copy(prestige = s.prestige.copy(souls = 1_000_000))
        s = Prestige.buyTalent(s, Talents.DAMAGE, PayWith.SOULS)               // lvl 1 (0 gold)
        val goldBefore = s.gold
        val price = Prestige.goldCost(t, 1, 1)
        assertEquals(Prestige.soulCost(t, 1) * PrestigeConfig.GOLD_PER_SOUL * 2, price)
        s = Prestige.buyTalent(s, Talents.DAMAGE, PayWith.GOLD)                // lvl 2 (1 gold) = 50 %
        assertEquals(goldBefore - price, s.gold)
        assertEquals(2, Prestige.level(s.prestige, Talents.DAMAGE))
        assertEquals(BuyResult.GOLD_SHARE_REACHED, Prestige.check(s, Talents.DAMAGE, PayWith.GOLD))
        assertSame(s, Prestige.buyTalent(s, Talents.DAMAGE, PayWith.GOLD))
        s = Prestige.buyTalent(s, Talents.DAMAGE, PayWith.SOULS)               // lvl 3
        s = Prestige.buyTalent(s, Talents.DAMAGE, PayWith.GOLD)                // lvl 4 (2 gold)
        assertEquals(2, Prestige.goldLevels(s.prestige, Talents.DAMAGE))
        // never more than half of the levels via gold over a long sequence
        repeat(60) {
            s = Prestige.buyTalent(s, Talents.DAMAGE, PayWith.GOLD)
            s = Prestige.buyTalent(s, Talents.DAMAGE, PayWith.SOULS)
            assertTrue(Prestige.goldLevels(s.prestige, Talents.DAMAGE) * 2 <= Prestige.level(s.prestige, Talents.DAMAGE))
        }
        assertEquals(t.maxLevel, Prestige.level(s.prestige, Talents.DAMAGE))
        assertEquals(BuyResult.MAX_LEVEL, Prestige.check(s, Talents.DAMAGE, PayWith.SOULS))
    }

    @Test fun notEnoughResourcesAndUnknown() {
        val s = veteran()
        assertEquals(BuyResult.NOT_ENOUGH_SOULS, Prestige.check(s, Talents.XP, PayWith.SOULS))
        assertEquals(BuyResult.UNKNOWN, Prestige.check(s, "nope", PayWith.SOULS))
        assertSame(s, Prestige.buyTalent(s, "nope", PayWith.SOULS))
    }

    @Test fun respecRefundsSoulsFully() {
        var s = veteran(prestige = PrestigeState(souls = 50))
        s = Prestige.buyTalent(s, Talents.HP, PayWith.SOULS)
        s = Prestige.buyTalent(s, Talents.XP, PayWith.SOULS)
        s = Prestige.buyTalent(s, Talents.XP, PayWith.SOULS)
        assertTrue(s.prestige.souls < 50)
        val r = Prestige.respec(s)
        assertEquals(50L, r.prestige.souls)
        assertEquals(0L, r.prestige.soulsSpentTotal)
        assertTrue(r.prestige.talents.isEmpty() && r.prestige.goldTalentLevels.isEmpty())
        assertSame(r, Prestige.respec(r))
    }

    @Test fun bonusesIgnoreUnknownAndClamp() {
        val p = PrestigeState(talents = mapOf("zzz" to 5, Talents.DAMAGE to 9999, Talents.REST to 9999))
        val b = PrestigeBonuses.of(p)
        assertEquals(1.0 + 30 * 0.03, b.damage, 1e-9)
        assertEquals(0.5, b.restFactor, 1e-9)
    }

    @Test fun combatStatsUseTalentsAndStayNeutralWithout() {
        val plain = veteran()
        val base = CombatStats.of(plain)
        val p = PrestigeState(talents = mapOf(Talents.DAMAGE to 10, Talents.HP to 10, Talents.SPEED to 5, Talents.CRIT to 4))
        val boosted = CombatStats.of(plain.copy(prestige = p))
        assertTrue(boosted.damageMax > base.damageMax)
        assertTrue(boosted.maxHp > base.maxHp)
        assertTrue(boosted.attacksPerSecond >= base.attacksPerSecond)
        assertEquals(base.critMultiplier + 0.2, boosted.critMultiplier, 1e-9)
        assertTrue(boosted.attacksPerSecond <= CombatStats.MAX_ATTACKS_PER_SECOND)
        assertEquals(base, CombatStats.of(plain.copy(prestige = PrestigeState())))
    }

    @Test fun clickBonusCombinesBuffAndTalent() {
        val s = veteran()
        assertEquals(1.0, TapStats.clickBonus(s), 1e-9)
        val t = s.copy(
            prestige = PrestigeState(talents = mapOf(Talents.CLICK to 10)),
            buffs = BuffState(listOf(ActiveBuff("click", 0.5, 1000))),
        )
        assertEquals(1.5 * 1.5, TapStats.clickBonus(t), 1e-9)
        val combat = CombatStats.of(t)
        assertTrue(TapStats.maxTapDamage(combat, 0, TapStats.clickBonus(t)) > TapStats.maxTapDamage(combat))
    }

    @Test fun catalogShape() {
        assertEquals(12, Talents.catalog.size)
        assertEquals(3, Talents.catalog.map { it.branch }.toSet().size)
        assertEquals(12, Talents.catalog.map { it.id }.toSet().size)
        assertNotNull(Talents.byId(Talents.COMBO))
    }
}
