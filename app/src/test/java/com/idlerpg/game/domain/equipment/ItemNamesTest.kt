package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.character.StatType
import kotlin.random.Random
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ItemNamesTest {

    private val english = object : ItemNames.Strings {
        override fun baseName(baseTypeId: String) = "Base($baseTypeId)"
        override fun adjective(rarity: Rarity, index: Int) = "Adj$index"
        override fun ending(gender: GrammaticalGender) = ""
        override fun statSuffix(stat: StatType) = "of ${stat.name}"
        override fun legendarySuffix(index: Int) = "of L$index"
        override fun rustyDagger() = "Rusty Dagger"
    }

    @Test
    fun germanDisplayNameEqualsStoredName() {
        val random = Random(42)
        repeat(500) {
            val item = ItemGenerator.roll(1 + it % 30, random, rarityBoost = 2.0)
            assertTrue(item.nameSpec, ItemNames.isValidSpec(item.rarity, item.nameSpec))
            assertEquals(item.name, ItemNames.displayName(item, ItemNames.german))
        }
    }

    @Test
    fun displayNameFollowsLanguageOfStrings() {
        val item = ItemGenerator.create(
            ItemBaseTypes.byId(ItemBaseTypes.LEATHER_BOOTS)!!, 5, Rarity.LEGENDARY, Random(1),
        )
        val name = ItemNames.displayName(item, english)
        assertTrue(name, name.startsWith("Adj") && "Base(lederstiefel)" in name && " of L" in name)
    }

    @Test
    fun legacyItemsKeepStoredName() {
        val legacy = Item(id = "x", name = "Alter Name", slot = EquipmentSlot.HELMET, rarity = Rarity.RARE)
        assertEquals("Alter Name", ItemNames.displayName(legacy, english))
        val legacyBase = legacy.copy(baseType = ItemBaseTypes.IRON_HELM, rarity = Rarity.EPIC)
        assertEquals("Alter Name", ItemNames.displayName(legacyBase, english))
        val invalid = legacyBase.copy(nameSpec = "99;x7")
        assertEquals("Alter Name", ItemNames.displayName(invalid, english))
    }

    @Test
    fun starterDaggerAndCommonItemsAreLocalized() {
        assertEquals("Rusty Dagger", ItemNames.displayName(StarterItems.RUSTY_DAGGER, english))
        val common = Item(id = "c", name = "Eisenhelm", slot = EquipmentSlot.HELMET, baseType = ItemBaseTypes.IRON_HELM)
        assertEquals("Base(eisenhelm)", ItemNames.displayName(common, english))
    }

    @Test
    fun oldSaveWithoutNameSpecDecodes() {
        val json = """{"character":{},"equipment":{"inventory":[{"id":"a","name":"Dolch","slot":"WEAPON"}]}}"""
        val item = GameJson.decode(json).equipment.inventory.first()
        assertEquals("", item.nameSpec)
    }
}
