package com.idlerpg.game.domain.shop

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.data.ShopState
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.equipment.Inventory
import com.idlerpg.game.domain.equipment.ItemGenerator
import com.idlerpg.game.domain.world.Zones
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

/**
 * Merchant economy. Pure logic (no Android); time and randomness are passed in.
 *
 * - **Assortment:** [OFFER_COUNT] items — one per equipment slot plus one highlight offer of at
 *   least [Rarity.RARE]. Item levels spread around the [shopLevel] (−1 … +1).
 * - **Shop level:** hero level, clamped to the current zone's min level and the highest unlocked
 *   zone's max level (so early zones still offer fitting gear, late zones never lag behind).
 * - **Restock (free):** every [RESTOCK_INTERVAL_MILLIS] or as soon as the hero outgrew the stock by
 *   [LEVEL_RESTOCK_GAP] levels. Also the very first time the shop is opened.
 * - **Restock (paid, "Neu auffüllen"):** costs [restockCost]; each paid restock raises the next
 *   price until the next free restock (factor 1, 2, 3 … max [MAX_RESTOCK_FACTOR]).
 * - **Prices:** buy price = sell value ([Item.price]) × [markup]; the markup grows with the item
 *   level because monster gold grows faster than item values (see MonsterScaling.GOLD_PER_LEVEL ×
 *   zone reward multiplier vs. ItemGenerator.sellPrice).
 */
object Shop {

    const val OFFER_COUNT = 8
    const val RESTOCK_INTERVAL_MILLIS = 30L * 60L * 1000L
    const val LEVEL_RESTOCK_GAP = 3

    /** Buy price factor on the sell value at item level 1. */
    const val BASE_MARKUP = 4.0
    /** Extra markup per item level above 1 (level 30 → 4 × 2.45 ≈ 9.8). */
    const val MARKUP_PER_LEVEL = 0.05
    /** Rarity boost of the regular offers (merchants stock slightly better goods than random drops). */
    const val OFFER_RARITY_BOOST = 0.5
    /** Paid restock base price as multiple of a common item's buy price at the shop level. */
    const val RESTOCK_COST_FACTOR = 1.5
    const val MAX_RESTOCK_FACTOR = 5

    enum class BuyOutcome { BOUGHT, NOT_ENOUGH_GOLD, INVENTORY_FULL, SOLD_OUT }

    /** Result of [buy]; [state] is unchanged unless [outcome] is [BuyOutcome.BOUGHT]. */
    data class BuyResult(val state: GameState, val outcome: BuyOutcome, val item: Item?, val price: Long)

    /** Result of [paidRestock]; [state] is unchanged unless [success]. */
    data class RestockResult(val state: GameState, val success: Boolean, val cost: Long)

    /** Result of [sellWhere]: new state, number of sold items and gold earned. */
    data class SellResult(val state: GameState, val count: Int, val gold: Long)

    // ------------------------------------------------------------------ level & prices

    /** Level the shop's goods are generated for (see class doc). */
    fun shopLevel(state: GameState): Int {
        val current = Zones.byId(state.world.currentZoneId)
        val highest = state.world.unlockedZoneIds.map { Zones.byId(it) }.maxByOrNull { it.order } ?: current
        val upper = max(current.maxLevel, highest.maxLevel)
        return state.character.level.coerceAtLeast(1).coerceIn(current.minLevel, upper)
    }

    /** Buy price factor for [itemLevel]: [BASE_MARKUP] × (1 + [MARKUP_PER_LEVEL] × (level − 1)). */
    fun markup(itemLevel: Int): Double {
        val lvl = itemLevel.coerceIn(1, ItemGenerator.MAX_ITEM_LEVEL)
        return BASE_MARKUP * (1.0 + MARKUP_PER_LEVEL * (lvl - 1))
    }

    /** Gold the merchant asks for [item]; always strictly more than its sell value. */
    fun buyPrice(item: Item): Long {
        val sell = item.price.coerceAtLeast(1)
        return max(sell + 1, ceil(sell * markup(item.itemLevel)).toLong())
    }

    /** Price of the next paid restock at the current shop level. */
    fun restockCost(state: GameState): Long {
        val level = shopLevel(state)
        val commonBuy = ceil(ItemGenerator.sellPrice(level, Rarity.COMMON) * markup(level)).toLong()
        val factor = (1 + state.shop.paidRestocks.coerceAtLeast(0)).coerceAtMost(MAX_RESTOCK_FACTOR)
        return ceil(commonBuy * RESTOCK_COST_FACTOR).toLong() * factor
    }

    // ------------------------------------------------------------------ assortment

    /** Rolls a fresh assortment for [level]: highlight (≥ rare) first, then one item per slot. */
    fun generateOffers(level: Int, random: Random = Random.Default): List<Item> {
        fun lvl() = (level + random.nextInt(-1, 2)).coerceIn(1, ItemGenerator.MAX_ITEM_LEVEL)
        val highlight = ItemGenerator.roll(lvl(), random, minRarity = Rarity.RARE, rarityBoost = OFFER_RARITY_BOOST)
        val perSlot = EquipmentSlot.entries.map { slot ->
            ItemGenerator.roll(lvl(), random, rarityBoost = OFFER_RARITY_BOOST, slot = slot)
        }
        return (listOf(highlight) + perSlot).map { it.copy(isNew = false) }
    }

    /** Wall-clock time of the next free restock (only meaningful once the shop was stocked). */
    fun nextRestockAt(state: GameState): Long = state.shop.restockedAtEpochMillis + RESTOCK_INTERVAL_MILLIS

    /** Milliseconds until the next free restock, never negative. */
    fun millisUntilRestock(state: GameState, nowMillis: Long): Long = (nextRestockAt(state) - nowMillis).coerceAtLeast(0)

    /** True if [ensureStock] would change the state (restock or clock correction). */
    fun isRestockDue(state: GameState, nowMillis: Long): Boolean {
        val shop = state.shop
        return shop.stockLevel <= 0 ||
            shop.restockedAtEpochMillis <= 0 ||
            nowMillis < shop.restockedAtEpochMillis ||
            nowMillis >= nextRestockAt(state) ||
            shopLevel(state) >= shop.stockLevel + LEVEL_RESTOCK_GAP
    }

    /**
     * Free restock if due (never stocked, timer expired, hero outgrew the goods). Returns the same
     * instance if nothing is due. If the clock went backwards (e.g. imported save, changed device
     * time) only the timer is reset to [nowMillis] — no free goods from turning the clock.
     */
    fun ensureStock(state: GameState, nowMillis: Long, random: Random = Random.Default): GameState {
        if (!isRestockDue(state, nowMillis)) return state
        val shop = state.shop
        val neverStocked = shop.stockLevel <= 0 || shop.restockedAtEpochMillis <= 0
        if (!neverStocked && nowMillis < shop.restockedAtEpochMillis &&
            shopLevel(state) < shop.stockLevel + LEVEL_RESTOCK_GAP
        ) {
            return state.copy(shop = shop.copy(restockedAtEpochMillis = nowMillis))
        }
        return restock(state, nowMillis, random, paid = false)
    }

    /** "Neu auffüllen": new goods for [restockCost] gold. Does not reset the free-restock timer. */
    fun paidRestock(state: GameState, nowMillis: Long, random: Random = Random.Default): RestockResult {
        val cost = restockCost(state)
        if (state.gold < cost) return RestockResult(state, success = false, cost = cost)
        val paidState = state.copy(gold = state.gold - cost)
        return RestockResult(restock(paidState, nowMillis, random, paid = true), success = true, cost = cost)
    }

    private fun restock(state: GameState, nowMillis: Long, random: Random, paid: Boolean): GameState {
        val level = shopLevel(state)
        val old = state.shop
        val newShop = if (paid) {
            ShopState(
                offers = generateOffers(level, random),
                // Keep the timer; a never-stocked shop starts it now.
                restockedAtEpochMillis = if (old.restockedAtEpochMillis > 0) old.restockedAtEpochMillis else nowMillis,
                stockLevel = level,
                paidRestocks = old.paidRestocks.coerceAtLeast(0) + 1,
            )
        } else {
            ShopState(offers = generateOffers(level, random), restockedAtEpochMillis = nowMillis, stockLevel = level, paidRestocks = 0)
        }
        return state.copy(shop = newShop)
    }

    // ------------------------------------------------------------------ trading

    /** Why buying [item] is not possible right now, or null if it is. */
    fun buyBlocker(state: GameState, item: Item): BuyOutcome? = when {
        state.shop.offers.none { it.id == item.id } -> BuyOutcome.SOLD_OUT
        Inventory.isFull(state) -> BuyOutcome.INVENTORY_FULL
        state.gold < buyPrice(item) -> BuyOutcome.NOT_ENOUGH_GOLD
        else -> null
    }

    /**
     * Buys the offer with [itemId]: gold is deducted, the item goes into the inventory (marked new)
     * and leaves the assortment. A full inventory blocks the purchase (no auto-sell of bought goods).
     */
    fun buy(state: GameState, itemId: String): BuyResult {
        val item = state.shop.offers.firstOrNull { it.id == itemId }
            ?: return BuyResult(state, BuyOutcome.SOLD_OUT, null, 0)
        val price = buyPrice(item)
        buyBlocker(state, item)?.let { return BuyResult(state, it, item, price) }
        val paid = state.copy(
            gold = state.gold - price,
            shop = state.shop.copy(offers = state.shop.offers.filterNot { it.id == itemId }),
        )
        val added = Inventory.tryAdd(paid, item)
        // isFull was checked above, so the item always lands in the inventory.
        if (!added.added) return BuyResult(state, BuyOutcome.INVENTORY_FULL, item, price)
        return BuyResult(added.state, BuyOutcome.BOUGHT, item, price)
    }

    /** Sells one inventory item (delegates to [Inventory.sell]). */
    fun sell(state: GameState, itemId: String): GameState = Inventory.sell(state, itemId)

    /**
     * True if equipping [item] would improve the hero: more damage per second, or more attribute
     * points in total than the item currently in its slot (vitality/luck don't show in DPS).
     */
    fun isUpgrade(state: GameState, item: Item): Boolean {
        val dpsGain = Inventory.combatWith(state, item).dps - CombatStats.of(state).dps
        if (dpsGain > 1e-6) return true
        val diff = Inventory.compare(item, state.equipment.equipped[item.slot]).stats
        return diff.strength + diff.dexterity + diff.vitality + diff.luck > 0
    }

    /** Inventory items "Alle gewöhnlichen verkaufen" sells: common items that are no upgrade. */
    fun bulkSellCandidates(state: GameState): List<Item> = bulkSellCandidates(state, setOf(Rarity.COMMON))

    /**
     * Inventory items of one of [rarities] that are no upgrade. Equipped items are not part of the
     * inventory and therefore never candidates; an empty set yields an empty list.
     */
    fun bulkSellCandidates(state: GameState, rarities: Set<Rarity>): List<Item> =
        if (rarities.isEmpty()) emptyList()
        else state.equipment.inventory.filter { it.rarity in rarities && !isUpgrade(state, it) }

    /** Upgrades in the inventory of one of [rarities] that a bulk sale keeps. */
    fun keptUpgradeCount(state: GameState, rarities: Set<Rarity>): Int =
        state.equipment.inventory.count { it.rarity in rarities && isUpgrade(state, it) }

    /** Sellable amount of one rarity: item [count] and total [gold]. */
    data class RaritySellGroup(val rarity: Rarity, val count: Int, val gold: Long)

    /** One group per rarity that has sellable (non-upgrade) items, worst rarity first. */
    fun sellGroups(state: GameState): List<RaritySellGroup> =
        bulkSellCandidates(state, Rarity.entries.toSet()).groupBy { it.rarity }
            .map { (r, items) -> RaritySellGroup(r, items.size, items.sumOf { it.price.coerceAtLeast(0) }) }
            .sortedBy { it.rarity }

    /** Gold [bulkSell] would earn for [rarities]. */
    fun bulkSellGold(state: GameState, rarities: Set<Rarity>): Long =
        bulkSellCandidates(state, rarities).sumOf { it.price.coerceAtLeast(0) }

    /**
     * Sells all [bulkSellCandidates] of [rarities] in one pass (pure; call inside a single
     * `repository.update`). Upgrades and equipped items stay. Nothing to sell → same [state] instance.
     */
    fun bulkSell(state: GameState, rarities: Set<Rarity>): SellResult {
        val ids = bulkSellCandidates(state, rarities).map { it.id }.toSet()
        if (ids.isEmpty()) return SellResult(state, 0, 0)
        return sellWhere(state) { it.id in ids }
    }

    /** "Alles verkaufen": every rarity, upgrades and equipped items stay. */
    fun sellEverything(state: GameState): SellResult = bulkSell(state, Rarity.entries.toSet())

    /** Sells all [bulkSellCandidates]. */
    fun sellCommonJunk(state: GameState): SellResult = bulkSell(state, setOf(Rarity.COMMON))

    /** Sells every inventory item matching [predicate] (equipped items are never touched). */
    fun sellWhere(state: GameState, predicate: (Item) -> Boolean): SellResult {
        val targets = state.equipment.inventory.filter(predicate)
        var s = state
        targets.forEach { s = Inventory.sell(s, it.id) }
        return SellResult(s, targets.size, s.gold - state.gold)
    }
}
