package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.Item
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.character.StatType

/**
 * Grammatical gender/number of a German noun. Used to decline adjective prefixes in the
 * strong nominative (no article): "Scharfer Dolch", "Scharfe Axt", "Scharfes Kurzschwert",
 * "Scharfe Stiefel".
 */
enum class GrammaticalGender(val adjectiveEnding: String) {
    MASCULINE("er"),
    FEMININE("e"),
    NEUTER("es"),
    PLURAL("e"),
}

/**
 * Template for generated items.
 *
 * @property id stable id stored in [Item.baseType]; also selects the graphic in ui/equipment
 * @property displayName German noun used as name stem
 * @property damageMin weapon base damage at item level 1 (0 for non-weapons)
 * @property statWeights preferred stats for the primary bonus (higher weight = more likely)
 */
data class ItemBaseType(
    val id: String,
    val displayName: String,
    val slot: EquipmentSlot,
    val gender: GrammaticalGender,
    val damageMin: Int = 0,
    val damageMax: Int = 0,
    val statWeights: List<Pair<StatType, Int>>,
)

/** Catalog of all item base types. Ids are persisted — never rename them. */
object ItemBaseTypes {
    const val DAGGER = "dolch"
    const val SHORT_SWORD = "kurzschwert"
    const val MACE = "streitkolben"
    const val AXE = "axt"
    const val LEATHER_CAP = "lederkappe"
    const val IRON_HELM = "eisenhelm"
    const val LEATHER_VEST = "lederwams"
    const val CHAINMAIL = "kettenhemd"
    const val LEATHER_GLOVES = "lederhandschuhe"
    const val GAUNTLETS = "panzerhandschuhe"
    const val LEATHER_BOOTS = "lederstiefel"
    const val PLATE_BOOTS = "plattenstiefel"
    const val SILVER_RING = "silberring"
    const val GOLD_RING = "goldring"
    const val AMULET = "amulett"
    const val TALISMAN = "talisman"

    private val S = StatType.STRENGTH
    private val D = StatType.DEXTERITY
    private val V = StatType.VITALITY
    private val L = StatType.LUCK

    val all: List<ItemBaseType> = listOf(
        ItemBaseType(DAGGER, "Dolch", EquipmentSlot.WEAPON, GrammaticalGender.MASCULINE, 2, 4, listOf(D to 3, L to 2, S to 1)),
        ItemBaseType(SHORT_SWORD, "Kurzschwert", EquipmentSlot.WEAPON, GrammaticalGender.NEUTER, 3, 6, listOf(S to 2, D to 2, L to 1)),
        ItemBaseType(MACE, "Streitkolben", EquipmentSlot.WEAPON, GrammaticalGender.MASCULINE, 4, 7, listOf(S to 3, V to 2)),
        ItemBaseType(AXE, "Axt", EquipmentSlot.WEAPON, GrammaticalGender.FEMININE, 3, 9, listOf(S to 4, V to 1, L to 1)),
        ItemBaseType(LEATHER_CAP, "Lederkappe", EquipmentSlot.HELMET, GrammaticalGender.FEMININE, statWeights = listOf(D to 2, V to 2, L to 1)),
        ItemBaseType(IRON_HELM, "Eisenhelm", EquipmentSlot.HELMET, GrammaticalGender.MASCULINE, statWeights = listOf(V to 3, S to 1)),
        ItemBaseType(LEATHER_VEST, "Lederwams", EquipmentSlot.ARMOR, GrammaticalGender.NEUTER, statWeights = listOf(D to 2, V to 2)),
        ItemBaseType(CHAINMAIL, "Kettenhemd", EquipmentSlot.ARMOR, GrammaticalGender.NEUTER, statWeights = listOf(V to 3, S to 1)),
        ItemBaseType(LEATHER_GLOVES, "Lederhandschuhe", EquipmentSlot.GLOVES, GrammaticalGender.PLURAL, statWeights = listOf(D to 3, L to 1)),
        ItemBaseType(GAUNTLETS, "Panzerhandschuhe", EquipmentSlot.GLOVES, GrammaticalGender.PLURAL, statWeights = listOf(S to 3, V to 1)),
        ItemBaseType(LEATHER_BOOTS, "Lederstiefel", EquipmentSlot.BOOTS, GrammaticalGender.PLURAL, statWeights = listOf(D to 3, L to 1)),
        ItemBaseType(PLATE_BOOTS, "Plattenstiefel", EquipmentSlot.BOOTS, GrammaticalGender.PLURAL, statWeights = listOf(V to 3, S to 1)),
        ItemBaseType(SILVER_RING, "Silberring", EquipmentSlot.RING, GrammaticalGender.MASCULINE, statWeights = listOf(L to 3, D to 1, S to 1, V to 1)),
        ItemBaseType(GOLD_RING, "Goldring", EquipmentSlot.RING, GrammaticalGender.MASCULINE, statWeights = listOf(L to 2, S to 2, V to 1)),
        ItemBaseType(AMULET, "Amulett", EquipmentSlot.AMULET, GrammaticalGender.NEUTER, statWeights = listOf(V to 2, L to 2, S to 1, D to 1)),
        ItemBaseType(TALISMAN, "Talisman", EquipmentSlot.AMULET, GrammaticalGender.MASCULINE, statWeights = listOf(L to 3, D to 1, V to 1)),
    )

    private val byIdMap = all.associateBy { it.id }

    fun byId(id: String): ItemBaseType? = byIdMap[id]

    fun forSlot(slot: EquipmentSlot): List<ItemBaseType> = all.filter { it.slot == slot }

    /**
     * Base type of [item], with fallbacks for legacy items without [Item.baseType]:
     * the starter dagger and any other WEAPON map to [DAGGER]; other slots to their first base type.
     */
    fun of(item: Item): ItemBaseType {
        byIdMap[item.baseType]?.let { return it }
        if (item.id == StarterItems.RUSTY_DAGGER_ID) return byIdMap.getValue(DAGGER)
        return forSlot(item.slot).first()
    }
}
