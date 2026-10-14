package com.idlerpg.game.ui.equipment

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.idlerpg.game.R
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.character.StatType
import com.idlerpg.game.domain.equipment.GrammaticalGender
import com.idlerpg.game.domain.equipment.ItemBaseTypes
import com.idlerpg.game.domain.equipment.ItemNames

/** [ItemNames.Strings] backed by Android resources (`strings_ausruestung.xml`, de/en). */
class ResourceItemNames(private val res: Resources) : ItemNames.Strings {

    override fun baseName(baseTypeId: String): String? {
        val id = when (baseTypeId) {
            ItemBaseTypes.DAGGER -> R.string.item_base_dolch
            ItemBaseTypes.SHORT_SWORD -> R.string.item_base_kurzschwert
            ItemBaseTypes.MACE -> R.string.item_base_streitkolben
            ItemBaseTypes.AXE -> R.string.item_base_axt
            ItemBaseTypes.LEATHER_CAP -> R.string.item_base_lederkappe
            ItemBaseTypes.IRON_HELM -> R.string.item_base_eisenhelm
            ItemBaseTypes.LEATHER_VEST -> R.string.item_base_lederwams
            ItemBaseTypes.CHAINMAIL -> R.string.item_base_kettenhemd
            ItemBaseTypes.LEATHER_GLOVES -> R.string.item_base_lederhandschuhe
            ItemBaseTypes.GAUNTLETS -> R.string.item_base_panzerhandschuhe
            ItemBaseTypes.LEATHER_BOOTS -> R.string.item_base_lederstiefel
            ItemBaseTypes.PLATE_BOOTS -> R.string.item_base_plattenstiefel
            ItemBaseTypes.SILVER_RING -> R.string.item_base_silberring
            ItemBaseTypes.GOLD_RING -> R.string.item_base_goldring
            ItemBaseTypes.AMULET -> R.string.item_base_amulett
            ItemBaseTypes.TALISMAN -> R.string.item_base_talisman
            else -> return null
        }
        return res.getString(id)
    }

    override fun adjective(rarity: Rarity, index: Int): String? {
        val arr = when (rarity) {
            Rarity.UNCOMMON -> R.array.item_adj_uncommon
            Rarity.RARE -> R.array.item_adj_rare
            Rarity.EPIC -> R.array.item_adj_epic
            Rarity.LEGENDARY -> R.array.item_adj_legendary
            Rarity.COMMON -> return null
        }
        return res.getStringArray(arr).getOrNull(index)
    }

    override fun ending(gender: GrammaticalGender): String = res.getString(
        when (gender) {
            GrammaticalGender.MASCULINE -> R.string.item_ending_masculine
            GrammaticalGender.FEMININE -> R.string.item_ending_feminine
            GrammaticalGender.NEUTER -> R.string.item_ending_neuter
            GrammaticalGender.PLURAL -> R.string.item_ending_plural
        },
    )

    override fun statSuffix(stat: StatType): String = res.getString(
        when (stat) {
            StatType.STRENGTH -> R.string.item_suffix_strength
            StatType.DEXTERITY -> R.string.item_suffix_dexterity
            StatType.VITALITY -> R.string.item_suffix_vitality
            StatType.LUCK -> R.string.item_suffix_luck
        },
    )

    override fun legendarySuffix(index: Int): String? = res.getStringArray(R.array.item_suffix_legendary).getOrNull(index)

    override fun rustyDagger(): String = res.getString(R.string.item_name_rusty_dagger)
}

/** Name of [item] in the current app language (legacy items fall back to the stored name). */
fun Item.localizedName(resources: Resources): String = ItemNames.displayName(this, ResourceItemNames(resources))

/**
 * Name of [item] in the current app language. Use this instead of `item.name` in all UI.
 * Recomposes when the configuration (language) changes.
 */
@Composable
fun itemDisplayName(item: Item): String {
    val config = LocalConfiguration.current
    val context = LocalContext.current
    return remember(item.id, item.name, item.nameSpec, item.baseType, item.rarity, config) {
        item.localizedName(context.resources)
    }
}
