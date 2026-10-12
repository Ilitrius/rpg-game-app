package com.idlerpg.game.domain.character

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.equipment.ItemBaseTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeaponActionTest {

    private fun weapon(baseType: String, id: String = "w1") =
        Item(id = id, name = "Waffe", slot = EquipmentSlot.WEAPON, baseType = baseType, damageMin = 10, damageMax = 20)

    // ---------------------------------------------------------------- type mapping

    @Test
    fun typeMappingIncludesFistAndLegacy() {
        assertEquals(WeaponType.FIST, weaponTypeOf(null))
        assertEquals(WeaponType.FIST, weaponTypeOf(Item("h", "Helm", EquipmentSlot.HELMET, baseType = ItemBaseTypes.IRON_HELM)))
        assertEquals(WeaponType.DAGGER, weaponTypeOf(StarterItems.RUSTY_DAGGER))
        assertEquals(WeaponType.DAGGER, weaponTypeOf(weapon("")))
        assertEquals(WeaponType.DAGGER, weaponTypeOf(weapon(ItemBaseTypes.AXE, id = StarterItems.RUSTY_DAGGER_ID)))
        assertEquals(WeaponType.DAGGER, weaponTypeOf(weapon(ItemBaseTypes.DAGGER)))
        assertEquals(WeaponType.SHORT_SWORD, weaponTypeOf(weapon(ItemBaseTypes.SHORT_SWORD)))
        assertEquals(WeaponType.MACE, weaponTypeOf(weapon(ItemBaseTypes.MACE)))
        assertEquals(WeaponType.AXE, weaponTypeOf(weapon(ItemBaseTypes.AXE)))
        assertEquals(WeaponType.DAGGER, weaponTypeOf(weapon("zweihaender")))
    }

    @Test
    fun everyWeaponBaseTypeIsARealWeaponType() {
        ItemBaseTypes.forSlot(EquipmentSlot.WEAPON).forEach { bt ->
            assertTrue(bt.id, weaponTypeOf(weapon(bt.id)) != WeaponType.FIST)
        }
    }

    @Test
    fun combatStatsKnowTheWeaponType() {
        val base = Stats(5, 5, 5, 5)
        assertEquals(WeaponType.FIST, CombatStats.compute(base, 1, emptyList()).weaponType)
        assertEquals(WeaponType.MACE, CombatStats.compute(base, 1, listOf(weapon(ItemBaseTypes.MACE))).weaponType)
    }

    // ---------------------------------------------------------------- modifiers

    private fun dmg(t: WeaponType, a: WeaponAction) = WeaponActions.modifier(t, a).damageMultiplier

    private val attacks = listOf(WeaponAction.SWING, WeaponAction.STAB, WeaponAction.STRIKE)

    @Test
    fun parryNeverDealsDamage() {
        WeaponType.entries.forEach { assertEquals(0.0, dmg(it, WeaponAction.PARRY), 0.0) }
    }

    @Test
    fun affinitiesMatchDesign() {
        assertEquals(WeaponAction.STAB, bestAttack(WeaponType.DAGGER))
        assertEquals(WeaponAction.SWING, bestAttack(WeaponType.AXE))
        assertEquals(WeaponAction.STRIKE, bestAttack(WeaponType.MACE))
        // Short sword is balanced.
        attacks.forEach { assertEquals(1.0, dmg(WeaponType.SHORT_SWORD, it), 0.051) }
        // Fist is weak at every attack.
        attacks.forEach { assertTrue(dmg(WeaponType.FIST, it) < 1.0) }
        // Dagger stab carries the highest crit bonus of all.
        val daggerStab = WeaponActions.modifier(WeaponType.DAGGER, WeaponAction.STAB)
        WeaponType.entries.forEach { t ->
            WeaponAction.entries.forEach { a ->
                assertTrue(WeaponActions.modifier(t, a).critChanceBonus <= daggerStab.critChanceBonus)
            }
        }
        assertEquals(1.15, daggerStab.damageMultiplier, 1e-9)
        assertEquals(0.12, daggerStab.critChanceBonus, 1e-9)
        assertEquals(0.30, daggerStab.critMultiplierBonus, 1e-9)
        assertEquals(1.25, dmg(WeaponType.AXE, WeaponAction.SWING), 1e-9)
        assertEquals(1.30, dmg(WeaponType.MACE, WeaponAction.STRIKE), 1e-9)
        // Blunt/heavy weapons are bad at stabbing.
        assertTrue(dmg(WeaponType.MACE, WeaponAction.STAB) < 0.8)
        assertTrue(dmg(WeaponType.AXE, WeaponAction.STAB) < 0.8)
    }

    private fun bestAttack(t: WeaponType) =
        attacks.maxBy { WeaponActions.modifier(t, it).expectedFactor(0.05, 1.5) }

    @Test
    fun signatureActions() {
        assertEquals(WeaponAction.STAB, WeaponActions.signatureAction(WeaponType.DAGGER))
        assertEquals(WeaponAction.STRIKE, WeaponActions.signatureAction(WeaponType.MACE))
        assertEquals(WeaponAction.SWING, WeaponActions.signatureAction(WeaponType.AXE))
        assertEquals(WeaponAction.SWING, WeaponActions.signatureAction(WeaponType.FIST))
    }

    // ---------------------------------------------------------------- parry chance

    @Test
    fun parryChanceFormula() {
        // 35 % + 10 dex × 1 % + 4 luck × 0.25 % + dagger 5 % = 51 %
        assertEquals(0.51, WeaponActions.parryChance(Stats(dexterity = 10, luck = 4), WeaponType.DAGGER), 1e-9)
        assertEquals(0.30, WeaponActions.parryChance(Stats(), WeaponType.AXE), 1e-9)
        assertEquals(0.35, WeaponActions.parryChance(Stats(dexterity = -10), WeaponType.FIST), 1e-9)
    }

    @Test
    fun parryChanceIsBoundedAndMonotonic() {
        WeaponType.entries.forEach { t ->
            var last = 0.0
            for (dex in -5..300 step 5) {
                val c = WeaponActions.parryChance(Stats(dexterity = dex, luck = 10), t)
                assertTrue(c in WeaponActions.MIN_PARRY_CHANCE..WeaponActions.MAX_PARRY_CHANCE)
                assertTrue(c >= last)
                last = c
            }
            assertEquals(WeaponActions.MAX_PARRY_CHANCE, last, 0.0)
            assertTrue(WeaponActions.parryChance(Stats(luck = 20), t) > WeaponActions.parryChance(Stats(), t))
        }
        val s = Stats(dexterity = 10)
        assertTrue(WeaponActions.parryChance(s, WeaponType.SHORT_SWORD) > WeaponActions.parryChance(s, WeaponType.MACE))
        assertTrue(WeaponActions.parryChance(s, WeaponType.DAGGER) > WeaponActions.parryChance(s, WeaponType.AXE))
    }

    // ---------------------------------------------------------------- rollHit with action

    private fun stats(type: WeaponType, critChance: Double = 0.0) =
        CombatStats(Stats(dexterity = 10), 10, 20, 1.0, 100, critChance, 1.5, type)

    @Test
    fun rollHitAppliesDamageMultiplier() {
        val rnd = Random(7)
        val mace = stats(WeaponType.MACE)
        repeat(500) {
            val h = mace.rollHit(WeaponAction.STRIKE, rnd)
            if (!h.critical) assertTrue(h.damage in 13L..26L) // floor(10..20 × 1.3)
            val s = mace.rollHit(WeaponAction.STAB, rnd)
            assertFalse(s.critical)
            assertTrue(s.damage in 7L..14L) // floor(10..20 × 0.7)
        }
    }

    @Test
    fun rollHitCritBonusAndMultiplier() {
        val rnd = Random(3)
        val dagger = stats(WeaponType.DAGGER)
        var crits = 0
        val n = 4000
        repeat(n) {
            val h = dagger.rollHit(WeaponAction.STAB, rnd)
            if (h.critical) {
                crits++
                // base floor(10..20 × 1.15) = 11..23, crit × (1.5 + 0.3)
                assertTrue(h.damage in 19L..41L)
            }
        }
        assertEquals(0.12, crits.toDouble() / n, 0.02)
        repeat(500) { assertFalse(dagger.rollHit(WeaponAction.SWING, rnd).critical) }
    }

    @Test
    fun rollHitCritChanceIsCapped() {
        val c = stats(WeaponType.DAGGER, critChance = 0.7)
        val rnd = Random(11)
        val crits = (1..4000).count { c.rollHit(WeaponAction.STAB, rnd).critical }
        assertEquals(WeaponActions.MAX_ACTION_CRIT_CHANCE, crits / 4000.0, 0.03)
    }

    @Test
    fun parryDealsNothingAndRollParryUsesChance() {
        val c = stats(WeaponType.SHORT_SWORD)
        assertEquals(Hit(0, false), c.rollHit(WeaponAction.PARRY))
        assertEquals(0.50, c.parryChance, 1e-9)
        val rnd = Random(5)
        val ok = (1..4000).count { c.rollParry(rnd) }
        assertEquals(0.50, ok / 4000.0, 0.03)
    }

    @Test
    fun minimumDamageIsOne() {
        val weak = CombatStats(Stats(), 1, 1, 1.0, 10, 0.0, 1.5, WeaponType.MACE)
        repeat(50) { assertEquals(1L, weak.rollHit(WeaponAction.STAB, Random(it)).damage) }
    }

    @Test
    fun oldRollHitUnchanged() {
        val a = stats(WeaponType.MACE).rollHit(Random(42))
        val b = CombatStats(Stats(dexterity = 10), 10, 20, 1.0, 100, 0.0, 1.5).rollHit(Random(42))
        assertEquals(b, a)
    }
}
