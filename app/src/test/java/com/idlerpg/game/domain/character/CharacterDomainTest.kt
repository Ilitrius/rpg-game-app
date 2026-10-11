package com.idlerpg.game.domain.character

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.EquipmentState
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CombatStatsTest {

    private val base = Stats(strength = 5, dexterity = 5, vitality = 5, luck = 5)

    @Test
    fun unarmedUsesFistDamagePlusStrength() {
        val c = CombatStats.compute(base, level = 1, equipped = emptyList())
        // 5 str * 0.5 = 2 (floored)
        assertEquals(3, c.damageMin)
        assertEquals(4, c.damageMax)
    }

    @Test
    fun starterDaggerAddsDamageAndStrengthBonus() {
        val c = CombatStats.compute(base, 1, listOf(StarterItems.RUSTY_DAGGER))
        assertEquals(6, c.totalStats.strength)
        assertEquals(2 + 3, c.damageMin)
        assertEquals(4 + 3, c.damageMax)
    }

    @Test
    fun itemBonusesAreSummed() {
        val ring = Item("r", "Ring", EquipmentSlot.RING, bonus = Stats(luck = 4, vitality = 1))
        val boots = Item("b", "Stiefel", EquipmentSlot.BOOTS, bonus = Stats(dexterity = 3))
        val total = CombatStats.totalStats(base, listOf(ring, boots))
        assertEquals(Stats(5, 8, 6, 9), total)
        assertEquals(Stats(0, 3, 1, 4), CombatStats.equipmentBonus(listOf(ring, boots)))
    }

    @Test
    fun attackSpeedScalesWithDexterityAndIsCapped() {
        assertEquals(1.0, CombatStats.compute(base, 1, emptyList()).attacksPerSecond, 1e-9)
        assertEquals(0.8, CombatStats.compute(Stats(), 1, emptyList()).attacksPerSecond, 1e-9)
        assertEquals(5.0, CombatStats.compute(Stats(dexterity = 10_000), 1, emptyList()).attacksPerSecond, 1e-9)
        assertEquals(1000L, CombatStats.compute(base, 1, emptyList()).attackIntervalMillis)
    }

    @Test
    fun hpScalesWithVitalityAndLevel() {
        assertEquals(100, CombatStats.compute(base, 1, emptyList()).maxHp)
        assertEquals(100 + 9 * 5, CombatStats.compute(base, 10, emptyList()).maxHp)
    }

    @Test
    fun critScalesWithLuckAndIsCapped() {
        val c = CombatStats.compute(base, 1, emptyList())
        assertEquals(0.045, c.critChance, 1e-9)
        assertEquals(1.55, c.critMultiplier, 1e-9)
        val lucky = CombatStats.compute(Stats(luck = 10_000), 1, emptyList())
        assertEquals(CombatStats.MAX_CRIT_CHANCE, lucky.critChance, 1e-9)
        assertEquals(CombatStats.MAX_CRIT_MULTIPLIER, lucky.critMultiplier, 1e-9)
    }

    @Test
    fun negativeStatsNeverProduceInvalidValues() {
        val c = CombatStats.compute(Stats(-50, -50, -50, -50), 0, emptyList())
        assertEquals(1, c.damageMin)
        assertTrue(c.damageMax >= c.damageMin)
        assertEquals(CombatStats.BASE_ATTACKS_PER_SECOND, c.attacksPerSecond, 1e-9)
        assertEquals(CombatStats.BASE_HP, c.maxHp)
    }

    @Test
    fun rollHitStaysInRangeAndCritsDealMore() {
        val c = CombatStats.compute(base, 1, listOf(StarterItems.RUSTY_DAGGER))
        val rnd = Random(42)
        var crits = 0
        repeat(5_000) {
            val hit = c.rollHit(rnd)
            if (hit.critical) {
                crits++
                assertTrue(hit.damage > c.damageMin - 1)
                assertTrue(hit.damage <= (c.damageMax * c.critMultiplier).toLong() + 1)
            } else {
                assertTrue(hit.damage in c.damageMin..c.damageMax)
            }
        }
        // ~4.5 % crit chance → 225 expected
        assertTrue("crits=$crits", crits in 150..300)
    }

    @Test
    fun dpsIncludesCrits() {
        val c = CombatStats.compute(base, 1, emptyList())
        assertEquals(3.5 * 1.0 * (1 + 0.045 * 0.55), c.dps, 1e-9)
    }

    @Test
    fun ofUsesGameState() {
        val state = StarterItems.ensureStarterWeapon(GameState())
        assertEquals(CombatStats.compute(base, 1, listOf(StarterItems.RUSTY_DAGGER)), CombatStats.of(state))
    }
}

class ProgressionTest {

    @Test
    fun xpCurve() {
        assertEquals(50L, Progression.xpForNextLevel(1))
        assertEquals(152L, Progression.xpForNextLevel(2))
        assertEquals(1991L, Progression.xpForNextLevel(10))
        assertEquals(50L, Progression.xpForNextLevel(0))
        for (l in 1 until 200) assertTrue(Progression.xpForNextLevel(l + 1) > Progression.xpForNextLevel(l))
    }

    @Test
    fun xpBelowThresholdOnlyAccumulates() {
        val r = Progression.applyXp(CharacterState(), 30)
        assertEquals(0, r.levelsGained)
        assertEquals(1, r.character.level)
        assertEquals(30L, r.character.xp)
        assertEquals(0, r.character.unspentPoints)
    }

    @Test
    fun levelUpCarriesOverflowAndGrantsPoints() {
        val r = Progression.applyXp(CharacterState(xp = 40), 20)
        assertEquals(1, r.levelsGained)
        assertEquals(2, r.character.level)
        assertEquals(10L, r.character.xp)
        assertEquals(3, r.character.unspentPoints)
    }

    @Test
    fun multipleLevelUpsAtOnce() {
        val r = Progression.applyXp(CharacterState(unspentPoints = 1), 50 + 152 + 5)
        assertEquals(2, r.levelsGained)
        assertEquals(3, r.character.level)
        assertEquals(5L, r.character.xp)
        assertEquals(7, r.character.unspentPoints)
    }

    @Test
    fun zeroOrNegativeXpDoesNothing() {
        val c = CharacterState(xp = 10)
        assertSame(c, Progression.applyXp(c, 0).character)
        assertSame(c, Progression.applyXp(c, -5).character)
    }

    @Test
    fun hugeXpStopsAtMaxLevel() {
        val r = Progression.applyXp(CharacterState(), Long.MAX_VALUE)
        assertEquals(Progression.MAX_LEVEL, r.character.level)
        assertEquals(0L, r.character.xp)
        assertEquals(1f, Progression.levelProgress(r.character))
    }

    @Test
    fun levelProgress() {
        assertEquals(0.5f, Progression.levelProgress(CharacterState(xp = 25)), 1e-6f)
    }

    @Test
    fun spendPoint() {
        val state = GameState(character = CharacterState(unspentPoints = 2))
        val s1 = Progression.spendPoint(state, StatType.DEXTERITY)
        assertEquals(1, s1.character.unspentPoints)
        assertEquals(6, s1.character.stats.dexterity)
        val s2 = Progression.spendPoint(s1, StatType.LUCK)
        assertEquals(6, s2.character.stats[StatType.LUCK])
        assertSame(s2, Progression.spendPoint(s2, StatType.STRENGTH))
    }

    @Test
    fun applyXpOnGameState() {
        val s = Progression.applyXp(GameState(gold = 9), 50)
        assertEquals(2, s.character.level)
        assertEquals(9L, s.gold)
    }
}

class StarterItemsTest {

    @Test
    fun newGameGetsDaggerOnce() {
        val s = StarterItems.ensureStarterWeapon(GameState())
        assertEquals(StarterItems.RUSTY_DAGGER, s.equipment.equipped[EquipmentSlot.WEAPON])
        assertTrue(s.starterWeaponGranted)
        assertSame(s, StarterItems.ensureStarterWeapon(s))
    }

    @Test
    fun existingWeaponIsKept() {
        val sword = Item("s", "Schwert", EquipmentSlot.WEAPON, damageMin = 5, damageMax = 9)
        val s = StarterItems.ensureStarterWeapon(
            GameState(equipment = EquipmentState(equipped = mapOf(EquipmentSlot.WEAPON to sword))),
        )
        assertEquals(sword, s.equipment.equipped[EquipmentSlot.WEAPON])
        assertTrue(s.starterWeaponGranted)
    }

    @Test
    fun unequippedWeaponIsNotReplacedAfterGrant() {
        val s = GameState(starterWeaponGranted = true)
        assertSame(s, StarterItems.ensureStarterWeapon(s))
        assertFalse(s.equipment.equipped.containsKey(EquipmentSlot.WEAPON))
    }

    @Test
    fun oldSaveWithoutNewFieldsGetsDagger() {
        val old = """{"schemaVersion":1,"gold":3,"equipment":{"equipped":{},"inventory":[]}}"""
        val decoded = GameJson.decode(old)
        assertFalse(decoded.starterWeaponGranted)
        val s = StarterItems.ensureStarterWeapon(decoded)
        assertEquals(StarterItems.RUSTY_DAGGER, s.equipment.equipped[EquipmentSlot.WEAPON])
        // and it round-trips with the new damage fields
        assertEquals(s, GameJson.decode(GameJson.encode(s)))
    }
}
