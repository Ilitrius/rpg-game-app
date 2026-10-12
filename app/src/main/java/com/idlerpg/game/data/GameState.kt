package com.idlerpg.game.data

import kotlinx.serialization.Serializable

/**
 * Complete persistent game state. Shared contract for all feature areas
 * (Charakter, Ausrüstung, Shop, Optionen). Bump [SCHEMA_VERSION] on breaking changes.
 */
@Serializable
data class GameState(
    val schemaVersion: Int = SCHEMA_VERSION,
    val character: CharacterState = CharacterState(),
    val equipment: EquipmentState = EquipmentState(),
    val gold: Long = 0,
    /** True once the starter weapon was handed out (see domain/character/StarterItems). */
    val starterWeaponGranted: Boolean = false,
    /** World/adventure progress (zone, kills, last-active time). Owned by the Spielwelt area. */
    val world: WorldState = WorldState(),
    /** Merchant assortment and restock timer. Owned by the Shop area (domain/shop). */
    val shop: ShopState = ShopState(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class CharacterState(
    val name: String = "Held",
    val level: Int = 1,
    val xp: Long = 0,
    val unspentPoints: Int = 0,
    val stats: Stats = Stats(strength = 5, dexterity = 5, vitality = 5, luck = 5),
)

/** Core attributes. Strength = damage, dexterity = attack speed, vitality = HP, luck = crit/loot. */
@Serializable
data class Stats(
    val strength: Int = 0,
    val dexterity: Int = 0,
    val vitality: Int = 0,
    val luck: Int = 0,
) {
    operator fun plus(other: Stats) = Stats(
        strength + other.strength,
        dexterity + other.dexterity,
        vitality + other.vitality,
        luck + other.luck,
    )
}

@Serializable
enum class EquipmentSlot { WEAPON, HELMET, ARMOR, GLOVES, BOOTS, RING, AMULET }

@Serializable
enum class Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

@Serializable
data class Item(
    val id: String,
    val name: String,
    val slot: EquipmentSlot,
    val rarity: Rarity = Rarity.COMMON,
    val bonus: Stats = Stats(),
    val price: Long = 0,
    /** Weapon base damage per hit (only meaningful for [EquipmentSlot.WEAPON]; 0 = none). */
    val damageMin: Int = 0,
    val damageMax: Int = 0,
    /**
     * Base type id (see domain/equipment/ItemBaseTypes), picks name stem and graphic.
     * "" = legacy/unknown item (a WEAPON without base type is shown as dagger).
     */
    val baseType: String = "",
    /** Level the item was generated for (stats/damage/price scale with it). */
    val itemLevel: Int = 1,
    /** True until the player looked at the item in the Ausrüstung tab ("Neu"-badge). */
    val isNew: Boolean = false,
)

@Serializable
data class EquipmentState(
    val equipped: Map<EquipmentSlot, Item> = emptyMap(),
    val inventory: List<Item> = emptyList(),
)

/**
 * Persistent world progress of the adventure (Spielwelt).
 *
 * @property currentZoneId id of the zone the hero is fighting in (see domain/world/Zones)
 * @property unlockedZoneIds zones the hero may travel to
 * @property totalKills all monsters ever killed (online + offline)
 * @property zoneKills kills per zone id
 * @property lastActiveEpochMillis wall-clock time the adventure was last running (0 = never); base for offline progress
 * @property heroHp hero's current hit points when last saved (-1 = unknown/full)
 * @property defeatedBossIds zone ids whose boss has been defeated at least once
 * @property bossDefeatedAtKills zone kill counter at the last boss victory per zone (next challenge needs more kills)
 */
@Serializable
data class WorldState(
    val currentZoneId: String = "wiese",
    val unlockedZoneIds: List<String> = listOf("wiese"),
    val totalKills: Long = 0,
    val zoneKills: Map<String, Long> = emptyMap(),
    val lastActiveEpochMillis: Long = 0,
    val heroHp: Int = -1,
    val defeatedBossIds: List<String> = emptyList(),
    val bossDefeatedAtKills: Map<String, Long> = emptyMap(),
)

/**
 * Persistent shop state (Shop area, see domain/shop/Shop).
 *
 * @property offers items currently for sale (bought items are removed); buy price is derived from the item
 * @property restockedAtEpochMillis wall-clock time of the last free (timed) restock; 0 = never stocked
 * @property stockLevel shop level the current offers were generated for (0 = never stocked)
 * @property paidRestocks paid "Neu auffüllen" since the last timed restock (raises the next price)
 */
@Serializable
data class ShopState(
    val offers: List<Item> = emptyList(),
    val restockedAtEpochMillis: Long = 0,
    val stockLevel: Int = 0,
    val paidRestocks: Int = 0,
)
