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
)

@Serializable
data class EquipmentState(
    val equipped: Map<EquipmentSlot, Item> = emptyMap(),
    val inventory: List<Item> = emptyList(),
)
