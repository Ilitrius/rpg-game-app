package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.EquipmentState
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.character.CombatStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EnchantingTest {
    private val sword = Item(
        id = "w", name = "Schwert", slot = EquipmentSlot.WEAPON, bonus = Stats(strength = 10, luck = 5),
        price = 100, damageMin = 10, damageMax = 20,
    )

    private fun state(gold: Long = 1_000_000, extra: Int = 0, level: Int = 1) = GameState(
        character = CharacterState(level = level),
        gold = gold,
        inventoryExtraSlots = extra,
        equipment = EquipmentState(inventory = listOf(sword)),
    )

    // --- capacity

    @Test fun capacityGrowsWithExtraSlots() {
        assertEquals(40, Inventory.capacity(state()))
        assertEquals(100, Inventory.capacity(state(extra = 60)))
        assertEquals(100, Inventory.capacity(state(extra = 999))) // clamped
        assertEquals(40, Inventory.capacity(state(extra = -5)))
    }

    @Test fun fillFollowsCapacity() {
        val items = List(40) { sword.copy(id = "i$it") }
        val full = state().copy(equipment = EquipmentState(inventory = items))
        assertEquals(InventoryFill.FULL, Inventory.fill(full))
        val bigger = full.copy(inventoryExtraSlots = 20)
        assertEquals(InventoryFill.OK, Inventory.fill(bigger))
        assertEquals(20, Inventory.freeSlots(bigger))
        assertEquals(40f / 60f, Inventory.fillFraction(bigger), 0.01f)
        assertTrue(Inventory.tryAdd(bigger, sword.copy(id = "n")).added)
    }

    @Test fun expansionStepsTripleAndStopAtMax() {
        val c0 = Inventory.expansionCost(state())!!
        val c1 = Inventory.expansionCost(state(extra = 20))!!
        val c2 = Inventory.expansionCost(state(extra = 40))!!
        assertEquals(c0 * 3, c1)
        assertEquals(c0 * 9, c2)
        assertNull(Inventory.expansionCost(state(extra = 60)))
        assertTrue(Inventory.expansionCost(state(level = 50))!! > c0) // scales with progress
    }

    @Test fun expandPaysAndIsBlockedWithoutGoldOrAtMax() {
        val cost = Inventory.expansionCost(state())!!
        val r = Inventory.expand(state(gold = cost + 7))
        assertEquals(Inventory.ExpandOutcome.OK, r.outcome)
        assertEquals(20, r.state.inventoryExtraSlots)
        assertEquals(7L, r.state.gold)
        val poor = state(gold = cost - 1)
        assertSame(poor, Inventory.expand(poor).state)
        assertEquals(Inventory.ExpandOutcome.MAX, Inventory.expand(state(extra = 60)).outcome)
    }

    // --- enchanting

    @Test fun costGrowsBy1_6PerLevelFromBasePrice() {
        assertEquals(160L, Enchanting.cost(sword)) // 100 * 1.6
        var s = state()
        var last = 0L
        repeat(10) {
            val item = s.equipment.inventory.first()
            val cost = Enchanting.cost(item)!!
            assertTrue(cost > last)
            last = cost
            s = Enchanting.enchant(s, "w").state
        }
        val maxed = s.equipment.inventory.first()
        assertEquals(10, maxed.enchantLevel)
        assertNull(Enchanting.cost(maxed))
        assertEquals(Enchanting.Outcome.MAX_LEVEL, Enchanting.enchant(s, "w").outcome)
    }

    @Test fun basePriceIsStableAcrossLevels() {
        var s = state()
        repeat(10) { s = Enchanting.enchant(s, "w").state }
        assertEquals(100L, Enchanting.basePrice(s.equipment.inventory.first()))
        assertEquals(300L, s.equipment.inventory.first().price) // sell value rises
    }

    @Test fun enchantChargesGoldAndNeverFails() {
        val r = Enchanting.enchant(state(gold = 200), "w")
        assertEquals(Enchanting.Outcome.OK, r.outcome)
        assertEquals(40L, r.state.gold)
        assertEquals(1, r.state.equipment.inventory.first().enchantLevel)
        val poor = state(gold = 159)
        val p = Enchanting.enchant(poor, "w")
        assertEquals(Enchanting.Outcome.NOT_ENOUGH_GOLD, p.outcome)
        assertSame(poor, p.state)
        assertEquals(Enchanting.Outcome.NOT_FOUND, Enchanting.enchant(poor, "x").outcome)
    }

    @Test fun enchantWorksOnEquippedItems() {
        val s = state().copy(equipment = EquipmentState(equipped = mapOf(EquipmentSlot.WEAPON to sword)))
        val r = Enchanting.enchant(s, "w")
        assertEquals(1, r.state.equipment.equipped.getValue(EquipmentSlot.WEAPON).enchantLevel)
    }

    @Test fun effectiveValuesGet4PercentPerLevel() {
        val e = sword.copy(enchantLevel = 10) // +40 %
        assertEquals(Stats(strength = 14, luck = 7), e.effectiveBonus)
        assertEquals(14, e.effectiveDamageMin)
        assertEquals(28, e.effectiveDamageMax)
        assertEquals(sword.bonus, sword.effectiveBonus)
    }

    @Test fun combatUsesEnchantedValues() {
        val base = CombatStats.compute(Stats(), 1, listOf(sword))
        val ench = CombatStats.compute(Stats(), 1, listOf(sword.copy(enchantLevel = 10)))
        assertTrue(ench.damageMax > base.damageMax)
        assertTrue(ench.damageMin > base.damageMin)
    }

    @Test fun compareUsesEnchantedValues() {
        val diff = Inventory.compare(sword.copy(enchantLevel = 10), sword)
        assertEquals(4, diff.stats.strength)
        assertEquals(4, diff.damageMin)
    }

    // --- reroll

    @Test fun rerollKeepsInvariants() {
        val item = sword.copy(bonus = Stats(strength = 7, dexterity = 4, vitality = 0, luck = 9), enchantLevel = 3, price = 120)
        repeat(200) { seed ->
            val out = Enchanting.rerolledBonus(item.bonus, Random(seed))
            assertEquals(20, out.strength + out.dexterity + out.vitality + out.luck)
            val nonZero = listOf(out.strength, out.dexterity, out.vitality, out.luck).count { it > 0 }
            assertEquals(3, nonZero)
            assertTrue(listOf(out.strength, out.dexterity, out.vitality, out.luck).all { it >= 0 })
        }
    }

    @Test fun rerollIsDeterministicAndChargesTwiceBasePrice() {
        val s = state(gold = 1000)
        val a = Enchanting.reroll(s, "w", Random(5))
        val b = Enchanting.reroll(s, "w", Random(5))
        assertEquals(a.state, b.state)
        assertEquals(800L, a.state.gold) // 100 * 2
        val item = a.state.equipment.inventory.first()
        assertEquals(sword.damageMin, item.damageMin)
        assertEquals(sword.price, item.price)
        assertEquals(15, item.bonus.strength + item.bonus.dexterity + item.bonus.vitality + item.bonus.luck)
    }

    @Test fun rerollBlockedWithoutGoldOrBonus() {
        assertEquals(Enchanting.Outcome.NOT_ENOUGH_GOLD, Enchanting.reroll(state(gold = 199), "w").outcome)
        val plain = state().copy(equipment = EquipmentState(inventory = listOf(sword.copy(bonus = Stats()))))
        assertEquals(Enchanting.Outcome.NOT_POSSIBLE, Enchanting.reroll(plain, "w").outcome)
        assertFalse(Enchanting.canReroll(sword.copy(bonus = Stats(strength = 5, luck = -2))))
    }

    // --- old saves

    @Test fun oldSaveWithoutNewFieldsLoads() {
        val json = """{"gold":5,"equipment":{"inventory":[{"id":"a","name":"X","slot":"WEAPON","price":10}]}}"""
        val s = GameJson.decode(json)
        assertEquals(0, s.inventoryExtraSlots)
        assertEquals(40, Inventory.capacity(s))
        val item = s.equipment.inventory.first()
        assertEquals(0, item.enchantLevel)
        assertNotNull(Enchanting.cost(item))
    }
}
