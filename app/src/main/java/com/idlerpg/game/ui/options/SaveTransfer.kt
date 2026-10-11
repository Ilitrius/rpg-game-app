package com.idlerpg.game.ui.options

import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Short overview of a save shown before it overwrites the current progress. */
data class SaveSummary(
    val name: String,
    val level: Int,
    val gold: Long,
    val itemCount: Int,
)

/** Reasons an imported save is rejected. Mapped to German strings in the UI. */
sealed interface SaveError {
    data object Empty : SaveError
    data object TooLarge : SaveError
    data object Malformed : SaveError
    data class NewerVersion(val found: Int, val supported: Int) : SaveError
    data object InvalidVersion : SaveError
    data object LevelTooLow : SaveError
    data class NegativeValue(val field: String) : SaveError
    data class SlotMismatch(val slotKey: String, val itemSlot: String) : SaveError
}

sealed interface ImportResult {
    data class Valid(val state: GameState, val summary: SaveSummary) : ImportResult
    data class Invalid(val error: SaveError) : ImportResult
}

/** Pure (non-UI) logic for save-game export/import. */
object SaveTransfer {

    /** Upper bound for imported text; real saves are a few KB. */
    const val MAX_IMPORT_CHARS = 1_000_000

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun exportFileName(date: LocalDate = LocalDate.now()): String =
        "idlerpg_save_${date.format(DATE_FORMAT)}.json"

    fun encode(state: GameState): String = GameJson.encode(state)

    fun summarize(state: GameState) = SaveSummary(
        name = state.character.name,
        level = state.character.level,
        gold = state.gold,
        itemCount = state.equipment.equipped.size + state.equipment.inventory.size,
    )

    /** Parses and validates [text]. Never throws. */
    fun parse(text: String?): ImportResult {
        if (text.isNullOrBlank()) return ImportResult.Invalid(SaveError.Empty)
        if (text.length > MAX_IMPORT_CHARS) return ImportResult.Invalid(SaveError.TooLarge)
        val state = try {
            GameJson.decode(text)
        } catch (e: Exception) {
            // SerializationException / IllegalArgumentException (bad JSON, unknown enum, missing fields)
            return ImportResult.Invalid(SaveError.Malformed)
        }
        validate(state)?.let { return ImportResult.Invalid(it) }
        return ImportResult.Valid(state, summarize(state))
    }

    /** Returns the first problem found, or null if [state] is acceptable. */
    fun validate(state: GameState): SaveError? {
        if (state.schemaVersion > GameState.SCHEMA_VERSION) {
            return SaveError.NewerVersion(state.schemaVersion, GameState.SCHEMA_VERSION)
        }
        if (state.schemaVersion < 1) return SaveError.InvalidVersion

        val c = state.character
        if (c.level < 1) return SaveError.LevelTooLow
        val numbers = listOf(
            "xp" to c.xp,
            "unspentPoints" to c.unspentPoints.toLong(),
            "strength" to c.stats.strength.toLong(),
            "dexterity" to c.stats.dexterity.toLong(),
            "vitality" to c.stats.vitality.toLong(),
            "luck" to c.stats.luck.toLong(),
            "gold" to state.gold,
        )
        numbers.firstOrNull { it.second < 0 }?.let { return SaveError.NegativeValue(it.first) }

        val allItems = state.equipment.equipped.values + state.equipment.inventory
        allItems.firstOrNull { it.price < 0 }?.let { return SaveError.NegativeValue("price") }

        state.equipment.equipped.entries.firstOrNull { (slot, item) -> item.slot != slot }?.let { (slot, item) ->
            return SaveError.SlotMismatch(slot.name, item.slot.name)
        }
        return null
    }
}
