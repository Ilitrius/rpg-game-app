package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.EquipmentState
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.character.StarterItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ItemGeneratorTest {

    @Test
    fun rarityDistributionMatchesWeights() {
        val random = Random(42)
        val n = 50_000
        val counts = (1..n).groupingBy { ItemGenerator.rollRarity(random) }.eachCount()
        val expected = mapOf(
            Rarity.COMMON to 0.60, Rarity.UNCOMMON to 0.25, Rarity.RARE to 0.10, Rarity.EPIC to 0.04, Rarity.LEGENDARY to 0.01,
        )
        expected.forEach { (rarity, p) ->
            val actual = (counts[rarity] ?: 0).toDouble() / n
            assertEquals("share of $rarity", p, actual, 0.01)
        }
    }

    @Test
    fun boostShiftsTowardsRarer() {
        val random = Random(7)
        val n = 20_000
        val normal = (1..n).count { ItemGenerator.rollRarity(random) >= Rarity.EPIC }
        val boosted = (1..n).count { ItemGenerator.rollRarity(random, rarityBoost = 1.0) >= Rarity.EPIC }
        assertTrue("boosted=$boosted normal=$normal", boosted > normal * 2)
    }

    @Test
    fun minRarityIsRespected() {
        val random = Random(1)
        repeat(2_000) {
            val item = ItemGenerator.roll(5, random, minRarity = Rarity.RARE)
            assertTrue(item.rarity >= Rarity.RARE)
        }
        assertEquals(setOf(Rarity.EPIC, Rarity.LEGENDARY), ItemGenerator.rarityWeights(Rarity.EPIC).keys)
    }

    @Test
    fun allSlotsAndBaseTypesDrop() {
        val random = Random(3)
        val items = (1..3_000).map { ItemGenerator.roll(3, random) }
        assertEquals(EquipmentSlot.entries.toSet(), items.map { it.slot }.toSet())
        assertEquals(ItemBaseTypes.all.map { it.id }.toSet(), items.map { it.baseType }.toSet())
        assertEquals(items.size, items.map { it.id }.toSet().size)
    }

    @Test
    fun generatedItemsAreConsistent() {
        val random = Random(11)
        repeat(3_000) {
            val level = random.nextInt(1, 60)
            val item = ItemGenerator.roll(level, random, rarityBoost = 2.0)
            val base = ItemBaseTypes.byId(item.baseType)!!
            assertEquals(base.slot, item.slot)
            assertEquals(level, item.itemLevel)
            val b = item.bonus
            assertEquals(ItemGenerator.statBudget(level, item.rarity), b.strength + b.dexterity + b.vitality + b.luck)
            assertTrue(b.strength >= 0 && b.dexterity >= 0 && b.vitality >= 0 && b.luck >= 0)
            assertEquals(ItemGenerator.sellPrice(level, item.rarity), item.price)
            if (item.slot == EquipmentSlot.WEAPON) {
                assertTrue(item.damageMin >= 1 && item.damageMax > item.damageMin)
            } else {
                assertEquals(0, item.damageMax)
            }
            assertTrue(item.name.startsWith(base.displayName) || item.name.contains(" ${base.displayName}"))
            assertFalse(item.isNew)
        }
    }

    @Test
    fun scalingIsMonotonicInLevelAndRarity() {
        val sword = ItemBaseTypes.byId(ItemBaseTypes.SHORT_SWORD)!!
        for (rarity in Rarity.entries) {
            for (lvl in 1 until 100) {
                assertTrue(ItemGenerator.statBudget(lvl + 1, rarity) >= ItemGenerator.statBudget(lvl, rarity))
                assertTrue(ItemGenerator.sellPrice(lvl + 1, rarity) > ItemGenerator.sellPrice(lvl, rarity))
                val a = ItemGenerator.damageRange(sword, lvl, rarity)
                val b = ItemGenerator.damageRange(sword, lvl + 1, rarity)
                assertTrue(b.first >= a.first && b.last >= a.last)
            }
        }
        for (lvl in listOf(1, 10, 50)) {
            Rarity.entries.zipWithNext().forEach { (lo, hi) ->
                assertTrue(ItemGenerator.statBudget(lvl, hi) > ItemGenerator.statBudget(lvl, lo))
                assertTrue(ItemGenerator.sellPrice(lvl, hi) > ItemGenerator.sellPrice(lvl, lo))
                assertTrue(ItemGenerator.damageRange(sword, lvl, hi).last >= ItemGenerator.damageRange(sword, lvl, lo).last)
            }
        }
        assertTrue(ItemGenerator.damageRange(sword, 20, Rarity.COMMON).last > ItemGenerator.damageRange(sword, 1, Rarity.COMMON).last)
    }

    @Test
    fun germanNamesUseCorrectAdjectiveEndings() {
        val random = Random(5)
        val names = (1..5_000).map { ItemGenerator.roll(10, random, minRarity = Rarity.RARE).name }
        assertTrue(names.any { it.matches(Regex("\\S+es Kurzschwert .*")) })
        assertTrue(names.any { it.matches(Regex("\\S+e Axt .*")) })
        assertTrue(names.any { it.matches(Regex("\\S+er Dolch .*")) })
        assertTrue(names.any { it.matches(Regex("\\S+e Lederstiefel .*")) })
        assertFalse(names.any { it.contains("es Axt") || it.contains("er Kurzschwert") || it.contains("es Dolch") })
    }

    @Test
    fun legacyItemsMapToBaseTypes() {
        assertEquals(ItemBaseTypes.DAGGER, ItemBaseTypes.of(StarterItems.RUSTY_DAGGER).id)
        val legacy = Item(id = "x", name = "Alt", slot = EquipmentSlot.WEAPON)
        assertEquals(ItemBaseTypes.DAGGER, ItemBaseTypes.of(legacy).id)
        assertEquals(EquipmentSlot.BOOTS, ItemBaseTypes.of(legacy.copy(slot = EquipmentSlot.BOOTS)).slot)
    }

    @Test
    fun newItemFieldsSurviveJsonAndOldSavesStillLoad() {
        val item = ItemGenerator.roll(4, Random(9))
        val state = Inventory.addToInventory(GameState(), item)
        val decoded = GameJson.decode(GameJson.encode(state))
        assertEquals(state, decoded)
        val old = """{"equipment":{"inventory":[{"id":"a","name":"Dolch","slot":"WEAPON"}]}}"""
        val oldItem = GameJson.decode(old).equipment.inventory.single()
        assertEquals("", oldItem.baseType)
        assertEquals(1, oldItem.itemLevel)
        assertFalse(oldItem.isNew)
    }
}

class InventoryTest {

    private fun item(id: String, slot: EquipmentSlot = EquipmentSlot.WEAPON, price: Long = 10, str: Int = 1) =
        Item(id = id, name = id, slot = slot, price = price, bonus = Stats(strength = str), damageMin = 1, damageMax = 3)

    @Test
    fun addMarksNewAndAppends() {
        val s = Inventory.addToInventory(GameState(), item("a"))
        assertEquals(listOf("a"), s.equipment.inventory.map { it.id })
        assertTrue(s.equipment.inventory.single().isNew)
    }

    @Test
    fun fullInventoryAutoSellsForGold() {
        val full = GameState(gold = 5, equipment = EquipmentState(inventory = (1..Inventory.CAPACITY).map { item("i$it") }))
        assertTrue(Inventory.isFull(full))
        val result = Inventory.tryAdd(full, item("extra", price = 17))
        assertFalse(result.added)
        assertEquals(17, result.autoSoldGold)
        assertEquals(22, result.state.gold)
        assertEquals(Inventory.CAPACITY, result.state.equipment.inventory.size)
        assertEquals(result.state, Inventory.addToInventory(full, item("extra", price = 17)))
    }

    private fun withItems(n: Int) = GameState(equipment = EquipmentState(inventory = (1..n).map { item("i$it") }))

    @Test
    fun fillLevelThresholds() {
        val nearlyFullFrom = Inventory.CAPACITY - Inventory.NEARLY_FULL_FREE_SLOTS
        assertEquals(InventoryFill.OK, Inventory.fill(withItems(0)))
        assertEquals(InventoryFill.OK, Inventory.fill(withItems(nearlyFullFrom - 1)))
        assertEquals(InventoryFill.NEARLY_FULL, Inventory.fill(withItems(nearlyFullFrom)))
        assertEquals(InventoryFill.NEARLY_FULL, Inventory.fill(withItems(Inventory.CAPACITY - 1)))
        assertEquals(InventoryFill.FULL, Inventory.fill(withItems(Inventory.CAPACITY)))
        assertEquals(Inventory.NEARLY_FULL_FREE_SLOTS, Inventory.freeSlots(withItems(nearlyFullFrom)))
        assertEquals(1, Inventory.freeSlots(withItems(Inventory.CAPACITY - 1)))
    }

    @Test
    fun fullFillMatchesIsFullAndAutoSell() {
        for (n in listOf(0, 20, Inventory.CAPACITY - 1, Inventory.CAPACITY, Inventory.CAPACITY + 3)) {
            val s = withItems(n)
            assertEquals("n=$n", Inventory.isFull(s), Inventory.fill(s) == InventoryFill.FULL)
            // The warning must be shown exactly when the next drop would be auto-sold.
            assertEquals("n=$n", Inventory.fill(s) == InventoryFill.FULL, !Inventory.tryAdd(s, item("drop")).added)
        }
    }

    @Test
    fun overfullSaveClampsFreeSlotsAndFraction() {
        val over = withItems(Inventory.CAPACITY + 5)
        assertEquals(0, Inventory.freeSlots(over))
        assertEquals(1f, Inventory.fillFraction(over))
        assertEquals(InventoryFill.FULL, Inventory.fill(over))
        assertEquals(0f, Inventory.fillFraction(withItems(0)))
        assertEquals(0.5f, Inventory.fillFraction(withItems(Inventory.CAPACITY / 2)))
    }

    @Test
    fun sellingOneItemFromFullInventoryStopsAutoSell() {
        val full = withItems(Inventory.CAPACITY)
        val afterSell = Inventory.sell(full, "i1")
        assertEquals(InventoryFill.NEARLY_FULL, Inventory.fill(afterSell))
        assertTrue(Inventory.tryAdd(afterSell, item("drop")).added)
    }

    @Test
    fun equipSwapsWithPreviouslyEquipped() {
        val old = item("old")
        val new = item("new", str = 5).copy(isNew = true)
        val s0 = GameState(equipment = EquipmentState(equipped = mapOf(EquipmentSlot.WEAPON to old), inventory = listOf(new)))
        val s1 = Inventory.equip(s0, "new")
        assertEquals("new", s1.equipment.equipped[EquipmentSlot.WEAPON]?.id)
        assertFalse(s1.equipment.equipped[EquipmentSlot.WEAPON]!!.isNew)
        assertEquals(listOf("old"), s1.equipment.inventory.map { it.id })
        // Swap back.
        val s2 = Inventory.equip(s1, "old")
        assertEquals("old", s2.equipment.equipped[EquipmentSlot.WEAPON]?.id)
        assertEquals(listOf("new"), s2.equipment.inventory.map { it.id })
    }

    @Test
    fun equipIntoEmptySlotAndUnknownIdIsNoOp() {
        val ring = item("ring", EquipmentSlot.RING)
        val s0 = GameState(equipment = EquipmentState(inventory = listOf(ring)))
        val s1 = Inventory.equip(s0, "ring")
        assertEquals(ring, s1.equipment.equipped[EquipmentSlot.RING])
        assertTrue(s1.equipment.inventory.isEmpty())
        assertSame(s1, Inventory.equip(s1, "nope"))
    }

    @Test
    fun unequipMovesToInventoryUnlessFull() {
        val s0 = GameState(equipment = EquipmentState(equipped = mapOf(EquipmentSlot.HELMET to item("h", EquipmentSlot.HELMET))))
        val s1 = Inventory.unequip(s0, EquipmentSlot.HELMET)
        assertFalse(s1.equipment.equipped.containsKey(EquipmentSlot.HELMET))
        assertEquals(listOf("h"), s1.equipment.inventory.map { it.id })
        assertSame(s1, Inventory.unequip(s1, EquipmentSlot.HELMET))

        val full = s0.copy(equipment = s0.equipment.copy(inventory = (1..Inventory.CAPACITY).map { item("i$it") }))
        assertFalse(Inventory.canUnequip(full))
        assertSame(full, Inventory.unequip(full, EquipmentSlot.HELMET))
    }

    @Test
    fun sellRemovesItemAndCreditsGold() {
        val s0 = GameState(gold = 3, equipment = EquipmentState(inventory = listOf(item("a", price = 12), item("b"))))
        val s1 = Inventory.sell(s0, "a")
        assertEquals(15, s1.gold)
        assertEquals(listOf("b"), s1.equipment.inventory.map { it.id })
        assertSame(s1, Inventory.sell(s1, "a"))
    }

    @Test
    fun markSeenClearsFlag() {
        val s0 = Inventory.addToInventory(Inventory.addToInventory(GameState(), item("a")), item("b"))
        val s1 = Inventory.markSeen(s0, "a")
        assertEquals(listOf(false, true), s1.equipment.inventory.map { it.isNew })
        assertTrue(Inventory.markAllSeen(s1).equipment.inventory.none { it.isNew })
    }

    @Test
    fun compareComputesDeltas() {
        val a = Item("a", "a", EquipmentSlot.WEAPON, bonus = Stats(strength = 5, luck = 1), damageMin = 4, damageMax = 8)
        val b = Item("b", "b", EquipmentSlot.WEAPON, bonus = Stats(strength = 2, dexterity = 3), damageMin = 2, damageMax = 4)
        val c = Inventory.compare(a, b)
        assertEquals(Stats(strength = 3, dexterity = -3, vitality = 0, luck = 1), c.stats)
        assertEquals(2, c.damageMin)
        assertEquals(4, c.damageMax)
        assertEquals(a.bonus, Inventory.compare(a, null).stats)
    }

    @Test
    fun sortedByRarityThenLevel() {
        val items = listOf(
            item("c1").copy(rarity = Rarity.COMMON, itemLevel = 9),
            item("e1").copy(rarity = Rarity.EPIC, itemLevel = 1),
            item("c2").copy(rarity = Rarity.COMMON, itemLevel = 12),
        )
        assertEquals(listOf("e1", "c2", "c1"), Inventory.sorted(items).map { it.id })
    }

    @Test
    fun combatWithBetterWeaponIncreasesDps() {
        val s = GameState(equipment = EquipmentState(equipped = mapOf(EquipmentSlot.WEAPON to StarterItems.RUSTY_DAGGER)))
        val better = ItemGenerator.create(ItemBaseTypes.byId(ItemBaseTypes.AXE)!!, 10, Rarity.EPIC, Random(1))
        assertNotEquals(0.0, Inventory.combatWith(s, better).dps)
        assertTrue(Inventory.combatWith(s, better).dps > com.idlerpg.game.domain.character.CombatStats.of(s).dps)
    }
}
