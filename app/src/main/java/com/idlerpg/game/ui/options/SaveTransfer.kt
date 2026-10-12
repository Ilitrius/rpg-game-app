package com.idlerpg.game.ui.options

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.ShopState
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.equipment.ItemGenerator
import com.idlerpg.game.domain.world.Zones
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Short overview of a save shown before it overwrites the current progress. */
data class SaveSummary(
    val name: String,
    val level: Int,
    val gold: Long,
    val itemCount: Int,
)

/**
 * Numeric save values that are checked on import. [key] is the JSON key, shown in error messages
 * so players who edit a save file by hand can find the value. The UI maps each entry to a German label.
 */
enum class SaveField(val key: String) {
    XP("xp"),
    UNSPENT_POINTS("unspentPoints"),
    STRENGTH("strength"),
    DEXTERITY("dexterity"),
    VITALITY("vitality"),
    LUCK("luck"),
    GOLD("gold"),
    PRICE("price"),
    DAMAGE_MIN("damageMin"),
    DAMAGE_MAX("damageMax"),
    ITEM_LEVEL("itemLevel"),
    HERO_HP("heroHp"),
    TOTAL_KILLS("totalKills"),
    ZONE_KILLS("zoneKills"),
    LAST_ACTIVE("lastActiveEpochMillis"),
    BOSS_DEFEATED_AT_KILLS("bossDefeatedAtKills"),
    RESTOCKED_AT("restockedAtEpochMillis"),
    STOCK_LEVEL("stockLevel"),
    PAID_RESTOCKS("paidRestocks"),
}

/** Where a rejected value sits in the save, so the error message can point the player to it. */
sealed interface SaveLocation {
    /** Top level of the save (e.g. gold). */
    data object Root : SaveLocation

    data object Character : SaveLocation

    /** Item equipped in [slot]. */
    data class EquippedItem(val slot: EquipmentSlot, val itemName: String) : SaveLocation

    /** Inventory entry number [position] (1-based, order in the save file). */
    data class InventoryItem(val position: Int, val itemName: String) : SaveLocation

    /** Shop offer number [position] (1-based, order in the save file). */
    data class ShopOffer(val position: Int, val itemName: String) : SaveLocation

    /** World progress (`world`). */
    data object World : SaveLocation

    /** Per-zone entry of a world map (`zoneKills`, `bossDefeatedAtKills`). */
    data class WorldZone(val zoneId: String) : SaveLocation

    /** Merchant state (`shop`). */
    data object Shop : SaveLocation
}

/** Reasons an imported save is rejected. Mapped to German strings in the UI. */
sealed interface SaveError {
    data object Empty : SaveError
    data object TooLarge : SaveError
    data object Malformed : SaveError
    data class NewerVersion(val found: Int, val supported: Int) : SaveError
    data object InvalidVersion : SaveError
    data object LevelTooLow : SaveError
    data class NegativeValue(val field: SaveField, val location: SaveLocation) : SaveError

    /** [field] is below the allowed [min] (e.g. `heroHp` < -1). */
    data class BelowMinimum(val field: SaveField, val location: SaveLocation, val min: Long, val found: Long) : SaveError

    /** [field] lies outside [min]..[max] (e.g. `itemLevel`). */
    data class OutOfRange(val field: SaveField, val location: SaveLocation, val min: Long, val max: Long, val found: Long) : SaveError

    /**
     * Item damage range is reversed (`damageMin` > `damageMax`). Never produced by the game; combat
     * would silently collapse it to the minimum, so the save was edited or is corrupt.
     */
    data class DamageRangeReversed(val location: SaveLocation, val damageMin: Int, val damageMax: Int) : SaveError

    data class SlotMismatch(val slotKey: String, val itemSlot: String) : SaveError
}

/**
 * Harmless oddities of an accepted save, shown in the confirmation dialog so the player knows
 * what the import will change.
 */
sealed interface ImportNote {
    /** `currentZoneId` is unknown to this app version; the hero starts in [fallbackZoneId] instead. */
    data class UnknownCurrentZone(val zoneId: String, val fallbackZoneId: String) : ImportNote

    /** Progress entries for zones this app version doesn't know. They are kept (no effect in the game). */
    data class UnknownZonesIgnored(val zoneIds: List<String>) : ImportNote
}

sealed interface ImportResult {
    data class Valid(
        val state: GameState,
        val summary: SaveSummary,
        val notes: List<ImportNote> = emptyList(),
    ) : ImportResult

    data class Invalid(val error: SaveError) : ImportResult
}

/** Pure (non-UI) logic for save-game export/import. */
object SaveTransfer {

    /** Upper bound for imported text; real saves are a few KB (full inventory + shop ≈ 30 KB). */
    const val MAX_IMPORT_CHARS = 1_000_000

    /** Item names in error messages are cut to this length (a manipulated save could hold huge names). */
    const val MAX_NAME_LENGTH = 40

    /** Lowest valid [WorldState.heroHp]: -1 = unknown/full, 0 = hero was defeated. */
    const val MIN_HERO_HP = -1

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val knownZoneIds: Set<String> = Zones.all.map { it.id }.toSet()

    fun exportFileName(date: LocalDate = LocalDate.now()): String =
        "idlerpg_save_${date.format(DATE_FORMAT)}.json"

    fun encode(state: GameState): String = GameJson.encode(state)

    fun summarize(state: GameState) = SaveSummary(
        name = state.character.name,
        level = state.character.level,
        gold = state.gold,
        itemCount = state.equipment.equipped.size + state.equipment.inventory.size,
    )

    /** Parses, validates and normalizes [text]. Never throws. */
    fun parse(text: String?): ImportResult {
        if (text.isNullOrBlank()) return ImportResult.Invalid(SaveError.Empty)
        if (text.length > MAX_IMPORT_CHARS) return ImportResult.Invalid(SaveError.TooLarge)
        val decoded = try {
            GameJson.decode(text)
        } catch (e: Exception) {
            // SerializationException / IllegalArgumentException (bad JSON, unknown enum, missing fields)
            return ImportResult.Invalid(SaveError.Malformed)
        }
        validate(decoded)?.let { return ImportResult.Invalid(it) }
        val (state, notes) = normalize(decoded)
        return ImportResult.Valid(state, summarize(state), notes)
    }

    /** Returns the first problem found, or null if [state] is acceptable. */
    fun validate(state: GameState): SaveError? {
        if (state.schemaVersion > GameState.SCHEMA_VERSION) {
            return SaveError.NewerVersion(state.schemaVersion, GameState.SCHEMA_VERSION)
        }
        if (state.schemaVersion < 1) return SaveError.InvalidVersion

        val c = state.character
        if (c.level < 1) return SaveError.LevelTooLow
        listOf(
            SaveField.XP to c.xp,
            SaveField.UNSPENT_POINTS to c.unspentPoints.toLong(),
            SaveField.STRENGTH to c.stats.strength.toLong(),
            SaveField.DEXTERITY to c.stats.dexterity.toLong(),
            SaveField.VITALITY to c.stats.vitality.toLong(),
            SaveField.LUCK to c.stats.luck.toLong(),
        ).firstOrNull { it.second < 0 }?.let { return SaveError.NegativeValue(it.first, SaveLocation.Character) }
        if (state.gold < 0) return SaveError.NegativeValue(SaveField.GOLD, SaveLocation.Root)

        // Equipped items in slot order (deterministic, independent of the key order in the file).
        val equipped = state.equipment.equipped
        for (slot in EquipmentSlot.entries) {
            val item = equipped[slot] ?: continue
            if (item.slot != slot) return SaveError.SlotMismatch(slot.name, item.slot.name)
            validateItem(item, SaveLocation.EquippedItem(slot, shortName(item.name)))?.let { return it }
        }
        state.equipment.inventory.forEachIndexed { i, item ->
            validateItem(item, SaveLocation.InventoryItem(i + 1, shortName(item.name)))?.let { return it }
        }

        validateWorld(state.world)?.let { return it }
        validateShop(state.shop)?.let { return it }
        return null
    }

    /**
     * Repairs harmless problems of a valid save and reports them as [ImportNote]s:
     * - Unknown `currentZoneId` (typo, or a zone from a newer app version) → first zone, the same
     *   fallback the game itself uses ([Zones.byId]); the first zone is always unlocked and safe
     *   for every hero level.
     * - Unknown zone ids in the progress lists/maps are kept unchanged: the game only looks up
     *   known zones, so they have no effect, and progress of zones from a newer app version is not lost.
     */
    fun normalize(state: GameState): Pair<GameState, List<ImportNote>> {
        val world = state.world
        val notes = mutableListOf<ImportNote>()
        var result = state
        if (world.currentZoneId !in knownZoneIds) {
            val fallback = Zones.all.first().id
            notes += ImportNote.UnknownCurrentZone(world.currentZoneId, fallback)
            result = state.copy(world = world.copy(currentZoneId = fallback))
        }
        val unknown = (world.unlockedZoneIds + world.defeatedBossIds + world.zoneKills.keys + world.bossDefeatedAtKills.keys)
            .filter { it !in knownZoneIds }
            .distinct()
        if (unknown.isNotEmpty()) notes += ImportNote.UnknownZonesIgnored(unknown)
        return result to notes
    }

    /** [name] cut to [MAX_NAME_LENGTH] characters (with "…") for error messages. */
    fun shortName(name: String): String {
        val trimmed = name.trim()
        return if (trimmed.length <= MAX_NAME_LENGTH) trimmed else trimmed.take(MAX_NAME_LENGTH - 1).trimEnd() + "…"
    }

    private fun validateItem(item: Item, location: SaveLocation): SaveError? {
        if (item.price < 0) return SaveError.NegativeValue(SaveField.PRICE, location)
        if (item.damageMin < 0) return SaveError.NegativeValue(SaveField.DAMAGE_MIN, location)
        if (item.damageMax < 0) return SaveError.NegativeValue(SaveField.DAMAGE_MAX, location)
        // 0..0 = no weapon damage (legacy items, armor); min == max is a fixed-damage weapon.
        if (item.damageMin > item.damageMax) return SaveError.DamageRangeReversed(location, item.damageMin, item.damageMax)
        if (item.itemLevel !in 1..ItemGenerator.MAX_ITEM_LEVEL) {
            return SaveError.OutOfRange(
                SaveField.ITEM_LEVEL, location, 1, ItemGenerator.MAX_ITEM_LEVEL.toLong(), item.itemLevel.toLong(),
            )
        }
        return null
    }

    private fun validateWorld(world: WorldState): SaveError? {
        val loc = SaveLocation.World
        // 0 is a real value: the hero was defeated when the game was saved.
        if (world.heroHp < MIN_HERO_HP) {
            return SaveError.BelowMinimum(SaveField.HERO_HP, loc, MIN_HERO_HP.toLong(), world.heroHp.toLong())
        }
        if (world.totalKills < 0) return SaveError.NegativeValue(SaveField.TOTAL_KILLS, loc)
        if (world.lastActiveEpochMillis < 0) return SaveError.NegativeValue(SaveField.LAST_ACTIVE, loc)
        world.zoneKills.entries.firstOrNull { it.value < 0 }?.let {
            return SaveError.NegativeValue(SaveField.ZONE_KILLS, SaveLocation.WorldZone(shortName(it.key)))
        }
        world.bossDefeatedAtKills.entries.firstOrNull { it.value < 0 }?.let {
            return SaveError.NegativeValue(SaveField.BOSS_DEFEATED_AT_KILLS, SaveLocation.WorldZone(shortName(it.key)))
        }
        return null
    }

    private fun validateShop(shop: ShopState): SaveError? {
        val loc = SaveLocation.Shop
        if (shop.restockedAtEpochMillis < 0) return SaveError.NegativeValue(SaveField.RESTOCKED_AT, loc)
        if (shop.stockLevel < 0) return SaveError.NegativeValue(SaveField.STOCK_LEVEL, loc)
        if (shop.paidRestocks < 0) return SaveError.NegativeValue(SaveField.PAID_RESTOCKS, loc)
        shop.offers.forEachIndexed { i, item ->
            validateItem(item, SaveLocation.ShopOffer(i + 1, shortName(item.name)))?.let { return it }
        }
        return null
    }
}
