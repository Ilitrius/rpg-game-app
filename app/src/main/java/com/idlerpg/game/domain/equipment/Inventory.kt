package com.idlerpg.game.domain.equipment

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
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

    /** Maximum number of items in the inventory (equipped items don't count). */
    const val CAPACITY = 40

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

    fun isFull(state: GameState): Boolean = state.equipment.inventory.size >= CAPACITY

    /** Free inventory slots (never negative, even for an over-full imported save). */
    fun freeSlots(state: GameState): Int = (CAPACITY - state.equipment.inventory.size).coerceAtLeast(0)

    /** Fill level for the capacity warning; [InventoryFill.FULL] exactly when [isFull]. */
    fun fill(state: GameState): InventoryFill = when {
        isFull(state) -> InventoryFill.FULL
        freeSlots(state) <= NEARLY_FULL_FREE_SLOTS -> InventoryFill.NEARLY_FULL
        else -> InventoryFill.OK
    }

    /** Occupied share of the inventory in 0..1 (for the capacity bar). */
    fun fillFraction(state: GameState): Float =
        (state.equipment.inventory.size.toFloat() / CAPACITY).coerceIn(0f, 1f)

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
        val other = equipped?.bonus ?: Stats()
        return ItemComparison(
            stats = Stats(
                strength = item.bonus.strength - other.strength,
                dexterity = item.bonus.dexterity - other.dexterity,
                vitality = item.bonus.vitality - other.vitality,
                luck = item.bonus.luck - other.luck,
            ),
            damageMin = item.damageMin - (equipped?.damageMin ?: 0),
            damageMax = item.damageMax - (equipped?.damageMax ?: 0),
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
