package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.character.StatType
import com.idlerpg.game.domain.character.get
import com.idlerpg.game.domain.character.plus
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Random item drops. Pure logic (no Android), all randomness comes from the passed [Random].
 *
 * Typical use (Spielwelt, on monster kill):
 * ```
 * val item = ItemGenerator.roll(itemLevel = monsterLevel)                       // normal monster
 * val boss = ItemGenerator.roll(zoneLevel + 2, minRarity = Rarity.RARE, rarityBoost = 1.0)
 * repository.update { Inventory.addToInventory(it, item) }
 * ```
 */
object ItemGenerator {

    /** Base drop weights per rarity (sum 1000): 60 % / 25 % / 10 % / 4 % / 1 %. */
    val BASE_RARITY_WEIGHTS: Map<Rarity, Int> = mapOf(
        Rarity.COMMON to 600,
        Rarity.UNCOMMON to 250,
        Rarity.RARE to 100,
        Rarity.EPIC to 40,
        Rarity.LEGENDARY to 10,
    )

    /** Maximum item level (keeps the formulas in a sane numeric range). */
    const val MAX_ITEM_LEVEL = 999

    private val statMultiplier = mapOf(
        Rarity.COMMON to 1.0, Rarity.UNCOMMON to 1.4, Rarity.RARE to 1.9, Rarity.EPIC to 2.6, Rarity.LEGENDARY to 3.5,
    )
    private val damageMultiplier = mapOf(
        Rarity.COMMON to 1.0, Rarity.UNCOMMON to 1.12, Rarity.RARE to 1.25, Rarity.EPIC to 1.4, Rarity.LEGENDARY to 1.6,
    )
    private val priceMultiplier = mapOf(
        Rarity.COMMON to 1.0, Rarity.UNCOMMON to 2.5, Rarity.RARE to 6.0, Rarity.EPIC to 15.0, Rarity.LEGENDARY to 40.0,
    )
    private val statLines = mapOf(
        Rarity.COMMON to 1, Rarity.UNCOMMON to 2, Rarity.RARE to 2, Rarity.EPIC to 3, Rarity.LEGENDARY to 4,
    )

    /** Adjective stems per rarity; the gender ending (er/e/es) is appended. */
    private val adjectives = mapOf(
        Rarity.UNCOMMON to listOf("Solid", "Gehärtet", "Robust", "Geschärft"),
        Rarity.RARE to listOf("Meisterlich", "Edl", "Gesegnet", "Prächtig"),
        Rarity.EPIC to listOf("Runenverziert", "Verzaubert", "Glühend", "Sternengeschmiedet"),
        Rarity.LEGENDARY to listOf("Mythisch", "Sagenhaft", "Uralt", "Göttlich"),
    )
    private val statSuffix = mapOf(
        StatType.STRENGTH to "der Stärke",
        StatType.DEXTERITY to "der Gewandtheit",
        StatType.VITALITY to "der Lebenskraft",
        StatType.LUCK to "des Glücks",
    )
    private val legendarySuffixes = listOf("der Ahnen", "der Ewigkeit", "des Drachen", "der Sterne", "des Sturmkönigs")

    /**
     * Rolls a complete random item.
     *
     * @param itemLevel level the item is generated for, usually the monster/zone level (min 1)
     * @param random randomness source; pass a seeded `Random(seed)` in tests
     * @param minRarity lowest rarity that may drop (e.g. bosses: [Rarity.RARE])
     * @param rarityBoost >= 0, shifts the rarity table towards rarer results: each tier's weight is
     *   multiplied by `(1 + rarityBoost)^tierIndex`. 0 = normal, 1 = boss-like, 3 = very generous
     * @param slot force a slot (null = random slot, every slot equally likely)
     * @return new item with a fresh UUID id; [Item.isNew] is false (set by [Inventory.addToInventory])
     */
    fun roll(
        itemLevel: Int,
        random: Random = Random.Default,
        minRarity: Rarity = Rarity.COMMON,
        rarityBoost: Double = 0.0,
        slot: EquipmentSlot? = null,
    ): Item {
        val chosenSlot = slot ?: EquipmentSlot.entries[random.nextInt(EquipmentSlot.entries.size)]
        val bases = ItemBaseTypes.forSlot(chosenSlot)
        val base = bases[random.nextInt(bases.size)]
        val rarity = rollRarity(random, minRarity, rarityBoost)
        return create(base, itemLevel, rarity, random)
    }

    /** Effective rarity weights after [minRarity] filter and [rarityBoost] (see [roll]). */
    fun rarityWeights(minRarity: Rarity = Rarity.COMMON, rarityBoost: Double = 0.0): Map<Rarity, Double> {
        val factor = 1.0 + rarityBoost.coerceAtLeast(0.0)
        return Rarity.entries
            .filter { it >= minRarity }
            .associateWith { BASE_RARITY_WEIGHTS.getValue(it) * factor.pow(it.ordinal) }
    }

    /** Rolls a rarity according to [rarityWeights]. */
    fun rollRarity(random: Random = Random.Default, minRarity: Rarity = Rarity.COMMON, rarityBoost: Double = 0.0): Rarity {
        val weights = rarityWeights(minRarity, rarityBoost)
        var r = random.nextDouble() * weights.values.sum()
        for ((rarity, w) in weights) {
            if (r < w) return rarity
            r -= w
        }
        return weights.keys.last()
    }

    /** Sum of all stat bonus points of an item: ceil((1 + 0.6 × level) × rarityFactor). */
    fun statBudget(itemLevel: Int, rarity: Rarity): Int {
        val lvl = itemLevel.coerceIn(1, MAX_ITEM_LEVEL)
        return ceil((1.0 + 0.6 * lvl) * statMultiplier.getValue(rarity)).toInt()
    }

    /**
     * Weapon damage range: base damage × (1 + 0.25 × (level − 1)) × rarityFactor;
     * max is always > min. Returns 0..0 for non-weapon base types.
     */
    fun damageRange(base: ItemBaseType, itemLevel: Int, rarity: Rarity): IntRange {
        if (base.damageMax <= 0) return 0..0
        val lvl = itemLevel.coerceIn(1, MAX_ITEM_LEVEL)
        val scale = (1.0 + 0.25 * (lvl - 1)) * damageMultiplier.getValue(rarity)
        val min = max(1, (base.damageMin * scale).roundToInt())
        val maxDmg = max(min + 1, (base.damageMax * scale).roundToInt())
        return min..maxDmg
    }

    /** Sell value in gold: 4 × (1 + 0.4 × (level − 1)) × rarityFactor (1 / 2.5 / 6 / 15 / 40), min 1. */
    fun sellPrice(itemLevel: Int, rarity: Rarity): Long {
        val lvl = itemLevel.coerceIn(1, MAX_ITEM_LEVEL)
        return max(1L, (4.0 * (1.0 + 0.4 * (lvl - 1)) * priceMultiplier.getValue(rarity)).roundToLong())
    }

    /** Builds an item of the given [base] type and [rarity]; stat distribution and name affixes are random. */
    fun create(base: ItemBaseType, itemLevel: Int, rarity: Rarity, random: Random = Random.Default): Item {
        val lvl = itemLevel.coerceIn(1, MAX_ITEM_LEVEL)
        val bonus = rollStats(base, lvl, rarity, random)
        val damage = damageRange(base, lvl, rarity)
        return Item(
            id = UUID.randomUUID().toString(),
            name = buildName(base, rarity, bonus, random),
            slot = base.slot,
            rarity = rarity,
            bonus = bonus,
            price = sellPrice(lvl, rarity),
            damageMin = damage.first,
            damageMax = damage.last,
            baseType = base.id,
            itemLevel = lvl,
        )
    }

    /** Distributes exactly [statBudget] points over 1–4 stats; the primary stat (from the base type) gets the most weight. */
    private fun rollStats(base: ItemBaseType, lvl: Int, rarity: Rarity, random: Random): Stats {
        val budget = statBudget(lvl, rarity)
        val primary = weightedPick(base.statWeights, random)
        val others = StatType.entries.filter { it != primary }.shuffled(random)
        val count = statLines.getValue(rarity).coerceAtMost(budget)
        val chosen = listOf(primary) + others.take(count - 1)
        val weights = chosen.map { if (it == primary) 2.5 else 1.0 }

        var stats = Stats()
        chosen.forEach { stats = stats.plus(it, 1) }
        repeat(budget - chosen.size) {
            var r = random.nextDouble() * weights.sum()
            var idx = 0
            while (idx < weights.lastIndex && r >= weights[idx]) {
                r -= weights[idx]
                idx++
            }
            stats = stats.plus(chosen[idx], 1)
        }
        return stats
    }

    private fun buildName(base: ItemBaseType, rarity: Rarity, bonus: Stats, random: Random): String {
        val top = StatType.entries.maxBy { bonus[it] }
        val adjective = adjectives[rarity]?.let { it[random.nextInt(it.size)] + base.gender.adjectiveEnding }
        return when (rarity) {
            Rarity.COMMON -> base.displayName
            Rarity.UNCOMMON ->
                if (random.nextBoolean()) "$adjective ${base.displayName}" else "${base.displayName} ${statSuffix.getValue(top)}"
            Rarity.RARE, Rarity.EPIC -> "$adjective ${base.displayName} ${statSuffix.getValue(top)}"
            Rarity.LEGENDARY -> "$adjective ${base.displayName} ${legendarySuffixes[random.nextInt(legendarySuffixes.size)]}"
        }
    }

    private fun <T> weightedPick(options: List<Pair<T, Int>>, random: Random): T {
        var r = random.nextInt(options.sumOf { it.second })
        for ((value, w) in options) {
            if (r < w) return value
            r -= w
        }
        return options.last().first
    }
}
