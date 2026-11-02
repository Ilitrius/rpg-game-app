package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.prestige.PrestigeBonuses
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.character.CombatStats

/** Stat/damage difference "candidate − currently equipped" (positive = candidate is better). */
data class ItemComparison(
    val stats: Stats,
    val damageMin: Int,
    val damageMax: Int,
) {
    val hasDamage: Boolean get() = damageMin != 0 || damageMax != 0
}

/**
 * How full the inventory is. Drives the capacity warning in the equipment tab: once the
 * inventory is [FULL], new loot is sold automatically (see [Inventory.tryAdd]).
 */
enum class InventoryFill {
    /** Plenty of room. */
    OK,

    /** At most [Inventory.NEARLY_FULL_FREE_SLOTS] free slots left. */
    NEARLY_FULL,

    /** No free slot: new loot is auto-sold for its [Item.price]. */
    FULL,
}

/**
 * Pure inventory/equipment operations on [GameState]. Every function returns a new state
 * (or the same instance when nothing changes) and never throws for unknown ids.
 */
object Inventory {

    /** Base number of inventory slots (equipped items don't count); see [capacity] for the real limit. */
    const val CAPACITY = 40

    /** Slots added per expansion step (40 -> 60 -> 80 -> 100). */
    const val EXPANSION_STEP = 20

    /**
     * Real capacity: [CAPACITY] + bought extra slots (clamped to 0..[GameState.MAX_EXTRA_SLOTS])
     * + prestige talent slots. Talent slots do not count towards the bought limit ([canExpand]).
     */
    fun capacity(state: GameState): Int =
        CAPACITY + state.inventoryExtraSlots.coerceIn(0, GameState.MAX_EXTRA_SLOTS) +
            PrestigeBonuses.of(state).extraSlots.coerceAtLeast(0)

    /** Whether another expansion step can be bought. */
    fun canExpand(state: GameState): Boolean = state.inventoryExtraSlots < GameState.MAX_EXTRA_SLOTS

    /**
     * Gold price of the next expansion step, null when maxed out. `(300 + 60 × heroLevel) × 3^step`,
     * so it scales with progress (level) and triples per step.
     */
    fun expansionCost(state: GameState): Long? {
        if (!canExpand(state)) return null
        val step = state.inventoryExtraSlots.coerceAtLeast(0) / EXPANSION_STEP
        var cost = 300L + 60L * state.character.level.coerceAtLeast(1)
        repeat(step) { cost *= 3 }
        return cost
    }

    enum class ExpandOutcome { OK, MAX, NOT_ENOUGH_GOLD }

    data class ExpandResult(val state: GameState, val outcome: ExpandOutcome)

    /** Buys the next expansion step (+[EXPANSION_STEP] slots) atomically. */
    fun expand(state: GameState): ExpandResult {
        val cost = expansionCost(state) ?: return ExpandResult(state, ExpandOutcome.MAX)
        if (state.gold < cost) return ExpandResult(state, ExpandOutcome.NOT_ENOUGH_GOLD)
        return ExpandResult(
            state.copy(
                gold = state.gold - cost,
                inventoryExtraSlots = (state.inventoryExtraSlots + EXPANSION_STEP).coerceAtMost(GameState.MAX_EXTRA_SLOTS),
            ),
            ExpandOutcome.OK,
        )
    }

    /**
     * Result of [tryAdd].
     * @property added true if the item landed in the inventory
     * @property autoSoldGold gold credited instead because the inventory was full (0 if added)
     */
    data class AddResult(val state: GameState, val added: Boolean, val autoSoldGold: Long)

    /**
     * Free slots at or below which the inventory counts as [InventoryFill.NEARLY_FULL].
     * With 8–16 % drop chance per kill, 5 slots last a few minutes of fighting — enough time to react.
     */
    const val NEARLY_FULL_FREE_SLOTS = 5

    fun isFull(state: GameState): Boolean = state.equipment.inventory.size >= capacity(state)

    /** Free inventory slots (never negative, even for an over-full imported save). */
    fun freeSlots(state: GameState): Int = (capacity(state) - state.equipment.inventory.size).coerceAtLeast(0)

    /** Fill level for the capacity warning; [InventoryFill.FULL] exactly when [isFull]. */
    fun fill(state: GameState): InventoryFill = when {
        isFull(state) -> InventoryFill.FULL
        freeSlots(state) <= NEARLY_FULL_FREE_SLOTS -> InventoryFill.NEARLY_FULL
        else -> InventoryFill.OK
    }

    /** Occupied share of the inventory in 0..1 (for the capacity bar). */
    fun fillFraction(state: GameState): Float =
        (state.equipment.inventory.size.toFloat() / capacity(state)).coerceIn(0f, 1f)

    /**
     * Adds [item] to the inventory, marked as new. If the inventory is full, the item is sold
     * automatically for [Item.price] gold instead (see [AddResult.autoSoldGold]).
     */
    fun tryAdd(state: GameState, item: Item): AddResult {
        if (isFull(state)) {
            val gold = item.price.coerceAtLeast(0)
            return AddResult(state.copy(gold = state.gold + gold), added = false, autoSoldGold = gold)
        }
        val eq = state.equipment
        return AddResult(
            state.copy(equipment = eq.copy(inventory = eq.inventory + item.copy(isNew = true))),
            added = true,
            autoSoldGold = 0,
        )
    }

    /** Same as [tryAdd] but returns only the new state (full inventory → item auto-sold for gold). */
    fun addToInventory(state: GameState, item: Item): GameState = tryAdd(state, item).state

    /**
     * Equips the inventory item with [itemId] into its slot. A previously equipped item of that
     * slot goes back into the inventory (inventory size stays the same, so the cap can't be exceeded).
     */
    fun equip(state: GameState, itemId: String): GameState {
        val eq = state.equipment
        val item = eq.inventory.firstOrNull { it.id == itemId } ?: return state
        val previous = eq.equipped[item.slot]
        val newInventory = eq.inventory.filterNot { it.id == itemId } + listOfNotNull(previous)
        return state.copy(
            equipment = eq.copy(
                equipped = eq.equipped + (item.slot to item.copy(isNew = false)),
                inventory = newInventory,
            ),
        )
    }

    /**
     * Equips the best item per slot from the inventory ("beste Ausrüstung"). Ranked by expected damage per
     * second with the item in place, then by summed stat bonus, then item level; an item only replaces the
     * current one when it is strictly better, so repeated calls change nothing. Swapped-out items return to
     * the inventory. Runs a few passes because stat bonuses of one slot can change the value of another.
     */
    fun equipBest(state: GameState): GameState {
        var current = state
        repeat(3) {
            val next = EquipmentSlot.entries.fold(current) { s, slot -> equipBestInSlot(s, slot) }
            if (next == current) return next
            current = next
        }
        return current
    }

    /** Number of slots [equipBest] would change (0 = nothing to do); drives the button's enabled state. */
    fun equipBestChanges(state: GameState): Int {
        val after = equipBest(state).equipment.equipped
        return EquipmentSlot.entries.count { after[it]?.id != state.equipment.equipped[it]?.id }
    }

    private fun equipBestInSlot(state: GameState, slot: EquipmentSlot): GameState {
        val eq = state.equipment
        val equipped = eq.equipped[slot]
        val best = eq.inventory.filter { it.slot == slot }.maxWithOrNull(compareBy<Item> { score(state, it).first }
            .thenBy { score(state, it).second }.thenBy { it.itemLevel }) ?: return state
        if (equipped != null) {
            val b = score(state, best)
            val e = score(state, equipped)
            val better = b.first > e.first + 1e-9 || (kotlin.math.abs(b.first - e.first) <= 1e-9 && b.second > e.second)
            if (!better) return state
        }
        return equip(state, best.id)
    }

    /** (dps with the item equipped, summed stat bonus). */
    private fun score(state: GameState, item: Item): Pair<Double, Int> {
        val bonus = item.effectiveBonus
        return combatWith(state, item).dps to (bonus.strength + bonus.dexterity + bonus.vitality + bonus.luck)
    }

    /** True if an equipped item can be moved back into the inventory. */
    fun canUnequip(state: GameState): Boolean = !isFull(state)

    /** Moves the item in [slot] into the inventory. No-op if the slot is empty or the inventory is full. */
    fun unequip(state: GameState, slot: EquipmentSlot): GameState {
        val eq = state.equipment
        val item = eq.equipped[slot] ?: return state
        if (!canUnequip(state)) return state
        return state.copy(equipment = eq.copy(equipped = eq.equipped - slot, inventory = eq.inventory + item))
    }

    /** Sells the inventory item with [itemId] for [Item.price] gold. Equipped items must be unequipped first. */
    fun sell(state: GameState, itemId: String): GameState {
        val eq = state.equipment
        val item = eq.inventory.firstOrNull { it.id == itemId } ?: return state
        return state.copy(
            gold = state.gold + item.price.coerceAtLeast(0),
            equipment = eq.copy(inventory = eq.inventory.filterNot { it.id == itemId }),
        )
    }

    /** Clears the "Neu" flag of one inventory item. */
    fun markSeen(state: GameState, itemId: String): GameState {
        val eq = state.equipment
        if (eq.inventory.none { it.id == itemId && it.isNew }) return state
        return state.copy(equipment = eq.copy(inventory = eq.inventory.map { if (it.id == itemId) it.copy(isNew = false) else it }))
    }

    /** Clears the "Neu" flag of all inventory items. */
    fun markAllSeen(state: GameState): GameState {
        val eq = state.equipment
        if (eq.inventory.none { it.isNew }) return state
        return state.copy(equipment = eq.copy(inventory = eq.inventory.map { it.copy(isNew = false) }))
    }

    /** Display order: rarity (best first), then item level (highest first), then slot, then name. */
    fun sorted(items: List<Item>): List<Item> = items.sortedWith(
        compareByDescending<Item> { it.rarity }
            .thenByDescending { it.itemLevel }
            .thenBy { it.slot }
            .thenBy { it.name },
    )

    /** Differences [item] − [equipped] (an empty slot counts as all zeros). */
    fun compare(item: Item, equipped: Item?): ItemComparison {
        val other = equipped?.effectiveBonus ?: Stats()
        val mine = item.effectiveBonus
        return ItemComparison(
            stats = Stats(
                strength = mine.strength - other.strength,
                dexterity = mine.dexterity - other.dexterity,
                vitality = mine.vitality - other.vitality,
                luck = mine.luck - other.luck,
            ),
            damageMin = item.effectiveDamageMin - (equipped?.effectiveDamageMin ?: 0),
            damageMax = item.effectiveDamageMax - (equipped?.effectiveDamageMax ?: 0),
        )
    }

    /** Combat values the hero would have with [item] equipped instead of the current item of its slot. */
    fun combatWith(state: GameState, item: Item): CombatStats {
        val equipped = state.equipment.equipped + (item.slot to item)
        return CombatStats.compute(state.character.stats, state.character.level, equipped.values)
    }

    /** Combat values the hero would have without anything in [slot]. */
    fun combatWithout(state: GameState, slot: EquipmentSlot): CombatStats {
        val equipped = state.equipment.equipped - slot
        return CombatStats.compute(state.character.stats, state.character.level, equipped.values)
    }
}
