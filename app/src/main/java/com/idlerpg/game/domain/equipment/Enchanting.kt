package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Stats
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Gold sinks on items: enchanting (+1..+10, +4 % values per level, no failure risk) and rerolling
 * the stat distribution. Pure functions on [GameState].
 *
 * Stored values ([Item.bonus], [Item.damageMin]/[Item.damageMax]) are the *base* values; the
 * enchant bonus is applied on read via [effectiveBonus]/[effectiveDamageMin]/[effectiveDamageMax].
 * [Item.price] (sell value) grows with the level (+[PRICE_PER_LEVEL] of the base price per level);
 * the base price is recovered by [basePrice], so costs never compound on the enchanted price.
 */
object Enchanting {
    const val MAX_LEVEL = 10
    const val BONUS_PER_LEVEL = 0.04
    const val COST_BASE = 1.6
    const val PRICE_PER_LEVEL = 0.2
    const val REROLL_COST_FACTOR = 2L

    fun level(item: Item): Int = item.enchantLevel.coerceIn(0, MAX_LEVEL)

    /** Multiplier on stat bonuses/weapon damage: 1 + 4 % × level. */
    fun multiplier(item: Item): Double = 1.0 + BONUS_PER_LEVEL * level(item)

    private fun priceFactor(level: Int) = 1.0 + PRICE_PER_LEVEL * level

    /** Sell price of the item without enchant surcharge. */
    fun basePrice(item: Item): Long =
        (item.price.coerceAtLeast(0) / priceFactor(level(item))).roundToLong()

    fun canEnchant(item: Item): Boolean = level(item) < MAX_LEVEL

    /** Gold needed for the next level (`basePrice × 1.6^newLevel`, min 1), null at max level. */
    fun cost(item: Item): Long? {
        if (!canEnchant(item)) return null
        val next = level(item) + 1
        return (basePrice(item).coerceAtLeast(1) * COST_BASE.pow(next)).roundToLong().coerceAtLeast(1)
    }

    private fun scale(v: Int, m: Double) = (v * m).roundToInt()

    /** Stat bonus including the enchant bonus. */
    fun effectiveBonus(item: Item): Stats {
        val m = multiplier(item)
        if (m == 1.0) return item.bonus
        val b = item.bonus
        return Stats(scale(b.strength, m), scale(b.dexterity, m), scale(b.vitality, m), scale(b.luck, m))
    }

    fun effectiveDamageMin(item: Item): Int = scale(item.damageMin, multiplier(item))
    fun effectiveDamageMax(item: Item): Int = scale(item.damageMax, multiplier(item))

    enum class Outcome { OK, NOT_FOUND, MAX_LEVEL, NOT_POSSIBLE, NOT_ENOUGH_GOLD }

    data class Result(val state: GameState, val outcome: Outcome)

    private fun mapItem(state: GameState, itemId: String, f: (Item) -> Item): GameState? {
        val eq = state.equipment
        eq.equipped.entries.firstOrNull { it.value.id == itemId }?.let { (slot, item) ->
            return state.copy(equipment = eq.copy(equipped = eq.equipped + (slot to f(item))))
        }
        if (eq.inventory.none { it.id == itemId }) return null
        return state.copy(equipment = eq.copy(inventory = eq.inventory.map { if (it.id == itemId) f(it) else it }))
    }

    private fun find(state: GameState, itemId: String): Item? =
        state.equipment.equipped.values.firstOrNull { it.id == itemId }
            ?: state.equipment.inventory.firstOrNull { it.id == itemId }

    /** Enchants an equipped or inventory item by one level, paying [cost] (atomic, never fails randomly). */
    fun enchant(state: GameState, itemId: String): Result {
        val item = find(state, itemId) ?: return Result(state, Outcome.NOT_FOUND)
        val cost = cost(item) ?: return Result(state, Outcome.MAX_LEVEL)
        if (state.gold < cost) return Result(state, Outcome.NOT_ENOUGH_GOLD)
        val base = basePrice(item)
        val next = level(item) + 1
        val updated = mapItem(state, itemId) {
            it.copy(enchantLevel = next, price = (base * priceFactor(next)).roundToLong())
        }!!
        return Result(updated.copy(gold = state.gold - cost), Outcome.OK)
    }

    // ---------------------------------------------------------------- reroll

    /** Total base stat strength (sum of absolute values) — preserved by [reroll]. */
    fun statTotal(item: Item): Int = item.bonus.let { Math.abs(it.strength) + Math.abs(it.dexterity) + Math.abs(it.vitality) + Math.abs(it.luck) }

    fun canReroll(item: Item): Boolean = item.bonus.let {
        it.strength >= 0 && it.dexterity >= 0 && it.vitality >= 0 && it.luck >= 0
    } && statTotal(item) >= 2

    /** Gold for a reroll: `basePrice × 2`, min 1. */
    fun rerollCost(item: Item): Long = (basePrice(item) * REROLL_COST_FACTOR).coerceAtLeast(1)

    /**
     * Pure redistribution: same total, same number of stats with a bonus (each >= 1), random
     * stats and shares. Damage, level, rarity and enchant stay untouched.
     */
    fun rerolledBonus(bonus: Stats, random: Random): Stats {
        val values = listOf(bonus.strength, bonus.dexterity, bonus.vitality, bonus.luck)
        val total = values.sum()
        val count = values.count { it > 0 }
        if (count == 0 || total < count) return bonus
        val picked = (0..3).shuffled(random).take(count)
        val shares = MutableList(count) { 1 }
        repeat(total - count) { shares[random.nextInt(count)]++ }
        val out = IntArray(4)
        picked.forEachIndexed { i, stat -> out[stat] = shares[i] }
        return Stats(out[0], out[1], out[2], out[3])
    }

    /** Rerolls the stat distribution of an item for [rerollCost]. */
    fun reroll(state: GameState, itemId: String, random: Random = Random.Default): Result {
        val item = find(state, itemId) ?: return Result(state, Outcome.NOT_FOUND)
        if (!canReroll(item)) return Result(state, Outcome.NOT_POSSIBLE)
        val cost = rerollCost(item)
        if (state.gold < cost) return Result(state, Outcome.NOT_ENOUGH_GOLD)
        val updated = mapItem(state, itemId) { it.copy(bonus = rerolledBonus(it.bonus, random)) }!!
        return Result(updated.copy(gold = state.gold - cost), Outcome.OK)
    }
}

/** Stat bonus including enchant level. */
val Item.effectiveBonus: Stats get() = Enchanting.effectiveBonus(this)
val Item.effectiveDamageMin: Int get() = Enchanting.effectiveDamageMin(this)
val Item.effectiveDamageMax: Int get() = Enchanting.effectiveDamageMax(this)
