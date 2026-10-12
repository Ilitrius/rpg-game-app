package com.idlerpg.game.ui.options

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.EquipmentState
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.data.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SaveTransferTest {

    private val sword = Item("sword_1", "Langschwert", EquipmentSlot.WEAPON, Rarity.RARE, Stats(strength = 4), 120)
    private val helm = Item("helm_1", "Eisenhelm", EquipmentSlot.HELMET, bonus = Stats(vitality = 2), price = 40)
    private val ring = Item("ring_1", "Glücksring", EquipmentSlot.RING, Rarity.EPIC, Stats(luck = 3), 300)

    private val sample = GameState(
        character = CharacterState(
            name = "Aria",
            level = 7,
            xp = 1234,
            unspentPoints = 3,
            stats = Stats(strength = 12, dexterity = 9, vitality = 10, luck = 6),
        ),
        equipment = EquipmentState(
            equipped = mapOf(EquipmentSlot.WEAPON to sword, EquipmentSlot.HELMET to helm),
            inventory = listOf(ring),
        ),
        gold = 5678,
    )

    private fun invalid(text: String): SaveError {
        val result = SaveTransfer.parse(text)
        assertTrue("expected Invalid but was $result", result is ImportResult.Invalid)
        return (result as ImportResult.Invalid).error
    }

    private fun withValue(json: String, key: String, value: String): String =
        json.replaceFirst(Regex("\"$key\": [^,\\n]+"), "\"$key\": $value")

    @Test
    fun roundTripKeepsEverything() {
        val result = SaveTransfer.parse(SaveTransfer.encode(sample))
        assertTrue(result is ImportResult.Valid)
        result as ImportResult.Valid
        assertEquals(sample, result.state)
        assertEquals(SaveSummary("Aria", 7, 5678, 3), result.summary)
    }

    @Test
    fun defaultStateRoundTrips() {
        val result = SaveTransfer.parse(SaveTransfer.encode(GameState()))
        assertEquals(GameState(), (result as ImportResult.Valid).state)
    }

    @Test
    fun emptyInputIsRejected() {
        assertEquals(SaveError.Empty, invalid(""))
        assertEquals(SaveError.Empty, invalid("   \n"))
        assertEquals(SaveError.Empty, (SaveTransfer.parse(null) as ImportResult.Invalid).error)
    }

    @Test
    fun invalidJsonIsRejected() {
        assertEquals(SaveError.Malformed, invalid("not json at all"))
        assertEquals(SaveError.Malformed, invalid("{\"gold\": 5"))
        assertEquals(SaveError.Malformed, invalid("[1,2,3]"))
        assertEquals(SaveError.Malformed, invalid("{\"gold\": \"viel\"}"))
    }

    @Test
    fun unknownEnumIsRejected() {
        val json = SaveTransfer.encode(sample).replace("\"EPIC\"", "\"MYTHIC\"")
        assertEquals(SaveError.Malformed, invalid(json))
    }

    @Test
    fun newerSchemaVersionIsRejected() {
        val newer = GameState.SCHEMA_VERSION + 1
        val json = withValue(SaveTransfer.encode(sample), "schemaVersion", newer.toString())
        assertEquals(SaveError.NewerVersion(newer, GameState.SCHEMA_VERSION), invalid(json))
    }

    @Test
    fun zeroSchemaVersionIsRejected() {
        val json = withValue(SaveTransfer.encode(sample), "schemaVersion", "0")
        assertEquals(SaveError.InvalidVersion, invalid(json))
    }

    @Test
    fun negativeValuesAreRejected() {
        val base = SaveTransfer.encode(sample)
        val character = SaveLocation.Character
        assertEquals(SaveError.NegativeValue(SaveField.GOLD, SaveLocation.Root), invalid(withValue(base, "gold", "-1")))
        assertEquals(SaveError.NegativeValue(SaveField.XP, character), invalid(withValue(base, "xp", "-5")))
        assertEquals(SaveError.NegativeValue(SaveField.STRENGTH, character), invalid(withValue(base, "strength", "-3")))
        assertEquals(SaveError.NegativeValue(SaveField.UNSPENT_POINTS, character), invalid(withValue(base, "unspentPoints", "-1")))
        // First "price" in the file belongs to the equipped weapon.
        assertEquals(
            SaveError.NegativeValue(SaveField.PRICE, SaveLocation.EquippedItem(EquipmentSlot.WEAPON, "Langschwert")),
            invalid(withValue(base, "price", "-10")),
        )
    }

    @Test
    fun levelBelowOneIsRejected() {
        val base = SaveTransfer.encode(sample)
        assertEquals(SaveError.LevelTooLow, invalid(withValue(base, "level", "0")))
        assertEquals(SaveError.LevelTooLow, invalid(withValue(base, "level", "-2")))
    }

    @Test
    fun slotMismatchIsRejected() {
        val bad = sample.copy(
            equipment = sample.equipment.copy(equipped = mapOf(EquipmentSlot.BOOTS to sword)),
        )
        assertEquals(SaveError.SlotMismatch("BOOTS", "WEAPON"), invalid(SaveTransfer.encode(bad)))
    }

    @Test
    fun unknownExtraFieldsAreIgnored() {
        val json = SaveTransfer.encode(sample)
            .replaceFirst("{", "{\n  \"futureFeature\": {\"a\": [1, 2]},\n  \"comment\": \"hallo\",")
            .replace("\"name\": \"Aria\"", "\"name\": \"Aria\", \"title\": \"Drachentöterin\"")
        val result = SaveTransfer.parse(json)
        assertEquals(sample, (result as ImportResult.Valid).state)
    }

    @Test
    fun missingFieldsUseDefaults() {
        val result = SaveTransfer.parse("{\"gold\": 42}")
        result as ImportResult.Valid
        assertEquals(42L, result.state.gold)
        assertEquals(CharacterState(), result.state.character)
    }

    @Test
    fun tooLargeInputIsRejected() {
        val huge = "{\"comment\": \"" + "x".repeat(SaveTransfer.MAX_IMPORT_CHARS) + "\"}"
        assertEquals(SaveError.TooLarge, invalid(huge))
    }

    @Test
    fun exportFileNameUsesIsoDate() {
        assertEquals("idlerpg_save_2026-03-05.json", SaveTransfer.exportFileName(LocalDate.of(2026, 3, 5)))
    }
}
