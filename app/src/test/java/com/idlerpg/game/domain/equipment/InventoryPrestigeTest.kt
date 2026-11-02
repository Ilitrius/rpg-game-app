package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.EquipmentState
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.PrestigeState
import com.idlerpg.game.data.Stats
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.prestige.Prestige
import com.idlerpg.game.domain.prestige.Talents
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryPrestigeTest {

    private fun item(id: String) =
        Item(id = id, name = id, slot = EquipmentSlot.WEAPON, price = 10, bonus = Stats(strength = 1), damageMin = 1, damageMax = 3)

    private fun withTalent(level: Int, extra: Int = 0, inv: Int = 0) = GameState(
        inventoryExtraSlots = extra,
        prestige = PrestigeState(talents = mapOf(Talents.SLOTS to level)),
        equipment = EquipmentState(inventory = (1..inv).map { item("i$it") }),
    )

    @Test fun capacityWithAndWithoutTalent() {
        assertEquals(40, Inventory.capacity(GameState()))
        assertEquals(60, Inventory.capacity(GameState(inventoryExtraSlots = 20)))
        assertEquals(50, Inventory.capacity(withTalent(5)))
        assertEquals(110, Inventory.capacity(withTalent(5, extra = 60)))
    }

    @Test fun boughtSlotsStillClamped() {
        assertEquals(100, Inventory.capacity(GameState(inventoryExtraSlots = 999)))
    }

    @Test fun talentSlotsDoNotBlockExpansion() {
        val s = withTalent(10, extra = 40).copy(gold = 10_000_000)
        assertTrue(Inventory.canExpand(s))
        val r = Inventory.expand(s)
        assertEquals(Inventory.ExpandOutcome.OK, r.outcome)
        assertEquals(60, r.state.inventoryExtraSlots)
        assertFalse(Inventory.canExpand(r.state))
        assertNull(Inventory.expansionCost(r.state))
        assertEquals(40 + 60 + 20, Inventory.capacity(r.state))
    }

    @Test fun fillUsesTalentSlots() {
        val s = withTalent(5, inv = 40) // capacity 50, 10 free
        assertEquals(InventoryFill.OK, Inventory.fill(s))
        assertEquals(10, Inventory.freeSlots(s))
        assertEquals(0.8f, Inventory.fillFraction(s), 0.001f)
        assertEquals(InventoryFill.NEARLY_FULL, Inventory.fill(withTalent(5, inv = 45)))
    }

    @Test fun autoSellOnlyWhenFullIncludingTalentSlots() {
        val almost = withTalent(5, inv = 49)
        assertTrue(Inventory.tryAdd(almost, item("x")).added)
        val full = withTalent(5, inv = 50).copy(gold = 5)
        assertTrue(Inventory.isFull(full))
        assertEquals(InventoryFill.FULL, Inventory.fill(full))
        val r = Inventory.tryAdd(full, item("y"))
        assertFalse(r.added)
        assertEquals(10, r.autoSoldGold)
        assertEquals(15, r.state.gold)
        assertEquals(50, r.state.equipment.inventory.size)
    }

    @Test fun rebirthClearsInventoryKeepsBoughtSlots() {
        val zones = Zones.all.take(20).map { it.id }
        val base = StarterItems.ensureStarterWeapon(
            GameState(
                character = CharacterState(level = 80),
                inventoryExtraSlots = 20,
                world = WorldState(currentZoneId = zones.last(), unlockedZoneIds = zones, lastActiveEpochMillis = 1),
            ),
        )
        val loaded = base.copy(equipment = base.equipment.copy(inventory = (1..30).map { item("i$it") }))
        assertTrue(Prestige.canRebirth(loaded))
        val after = Prestige.rebirth(loaded, 1000)
        assertTrue(after.equipment.inventory.isEmpty())
        assertTrue(after.starterWeaponGranted)
        assertEquals(StarterItems.RUSTY_DAGGER.id, after.equipment.equipped[EquipmentSlot.WEAPON]?.id)
        assertEquals(20, after.inventoryExtraSlots)
        assertEquals(60, Inventory.capacity(after))
    }
}
