package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.character.StatType

/**
 * Language-independent item names. A generated item stores a [Item.nameSpec] ("<adj>;<suffix>")
 * plus [Item.baseType]; the display name is composed at display time from resources
 * (see ui/equipment/ItemNamesAndroid), so it follows the app language. [Item.name] keeps the
 * German name as fallback for legacy items and for code that has no resources.
 *
 * Spec grammar: `adj` = index into the rarity's adjective list (empty = none);
 * `suffix` = "s<StatType.ordinal>" (stat suffix, "of Strength") or "l<index>" (legendary suffix).
 * Name order is always: adjective, base noun, suffix.
 */
object ItemNames {

    /** Resource lookups needed to compose a name. Implemented with Android resources and in tests. */
    interface Strings {
        fun baseName(baseTypeId: String): String?

        /** Adjective stem [index] of [rarity] (German: without gender ending). */
        fun adjective(rarity: Rarity, index: Int): String?

        /** German adjective ending for the noun's gender ("" in languages without declension). */
        fun ending(gender: GrammaticalGender): String
        fun statSuffix(stat: StatType): String
        fun legendarySuffix(index: Int): String?
        fun rustyDagger(): String
    }

    // German source tables (stored fallback name and test oracle). Order = resource array order.
    val adjectivesDe: Map<Rarity, List<String>> = mapOf(
        Rarity.UNCOMMON to listOf("Solid", "Gehärtet", "Robust", "Geschärft"),
        Rarity.RARE to listOf("Meisterlich", "Edl", "Gesegnet", "Prächtig"),
        Rarity.EPIC to listOf("Runenverziert", "Verzaubert", "Glühend", "Sternengeschmiedet"),
        Rarity.LEGENDARY to listOf("Mythisch", "Sagenhaft", "Uralt", "Göttlich"),
    )
    val statSuffixDe: Map<StatType, String> = mapOf(
        StatType.STRENGTH to "der Stärke",
        StatType.DEXTERITY to "der Gewandtheit",
        StatType.VITALITY to "der Lebenskraft",
        StatType.LUCK to "des Glücks",
    )
    val legendarySuffixesDe: List<String> = listOf("der Ahnen", "der Ewigkeit", "des Drachen", "der Sterne", "des Sturmkönigs")

    /** Strings implementation backed by the German tables (no resources needed). */
    val german: Strings = object : Strings {
        override fun baseName(baseTypeId: String) = ItemBaseTypes.byId(baseTypeId)?.displayName
        override fun adjective(rarity: Rarity, index: Int) = adjectivesDe[rarity]?.getOrNull(index)
        override fun ending(gender: GrammaticalGender) = gender.adjectiveEnding
        override fun statSuffix(stat: StatType) = statSuffixDe.getValue(stat)
        override fun legendarySuffix(index: Int) = legendarySuffixesDe.getOrNull(index)
        override fun rustyDagger() = "Rostiger Dolch"
    }

    fun encode(adjective: Int?, suffix: String?): String = "${adjective ?: ""};${suffix ?: ""}"

    fun statSuffixCode(stat: StatType) = "s${stat.ordinal}"
    fun legendarySuffixCode(index: Int) = "l$index"

    /** German name; used for [Item.name] at generation time. */
    fun germanName(baseTypeId: String, rarity: Rarity, spec: String): String =
        compose(german, baseTypeId, rarity, spec) ?: ItemBaseTypes.byId(baseTypeId)?.displayName.orEmpty()

    /**
     * Display name of [item] in the language of [strings]. Falls back to the stored [Item.name]
     * for legacy items (empty spec and not a plain common base item) or invalid specs.
     */
    fun displayName(item: Item, strings: Strings): String {
        if (item.id == StarterItems.RUSTY_DAGGER_ID) return strings.rustyDagger()
        if (item.baseType.isEmpty() || ItemBaseTypes.byId(item.baseType) == null) return item.name
        if (item.nameSpec.isEmpty()) {
            // Common generated items were named exactly like their base type.
            return if (item.rarity == Rarity.COMMON) strings.baseName(item.baseType) ?: item.name else item.name
        }
        return compose(strings, item.baseType, item.rarity, item.nameSpec) ?: item.name
    }

    private fun compose(s: Strings, baseTypeId: String, rarity: Rarity, spec: String): String? {
        val base = ItemBaseTypes.byId(baseTypeId) ?: return null
        val noun = s.baseName(baseTypeId) ?: return null
        val parts = spec.split(';')
        if (parts.size != 2) return null
        val adjIdx = parts[0]
        val suffixCode = parts[1]
        val out = ArrayList<String>(3)
        if (adjIdx.isNotEmpty()) {
            val stem = s.adjective(rarity, adjIdx.toIntOrNull() ?: return null) ?: return null
            out += stem + s.ending(base.gender)
        }
        out += noun
        if (suffixCode.isNotEmpty()) {
            val n = suffixCode.drop(1).toIntOrNull() ?: return null
            out += when (suffixCode.first()) {
                's' -> s.statSuffix(StatType.entries.getOrNull(n) ?: return null)
                'l' -> s.legendarySuffix(n) ?: return null
                else -> return null
            }
        }
        return out.joinToString(" ")
    }

    /** True if [spec] is empty or well-formed for [rarity] (used by import validation). */
    fun isValidSpec(rarity: Rarity, spec: String): Boolean {
        if (spec.isEmpty()) return true
        val parts = spec.split(';')
        if (parts.size != 2) return false
        val a = parts[0]
        if (a.isNotEmpty()) {
            val i = a.toIntOrNull() ?: return false
            if (i !in (adjectivesDe[rarity]?.indices ?: IntRange.EMPTY)) return false
        }
        val c = parts[1]
        if (c.isEmpty()) return true
        val n = c.drop(1).toIntOrNull() ?: return false
        return when (c.first()) {
            's' -> n in StatType.entries.indices
            'l' -> n in legendarySuffixesDe.indices
            else -> false
        }
    }
}
