package com.idlerpg.game.domain.character

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.data.Stats

/** Catalog of items every hero starts with. */
object StarterItems {

    /** Stable id of the starter dagger (used e.g. to pick its graphic). */
    const val RUSTY_DAGGER_ID = "starter_rusty_dagger"

    /** "Rostiger Dolch": weak common dagger, 2–4 damage, +1 Stärke. */
    val RUSTY_DAGGER = Item(
        id = RUSTY_DAGGER_ID,
        name = "Rostiger Dolch",
        slot = EquipmentSlot.WEAPON,
        rarity = Rarity.COMMON,
        bonus = Stats(strength = 1),
        price = 5,
        damageMin = 2,
        damageMax = 4,
    )

    /**
     * Hands out the starter dagger exactly once per save: if it was not granted yet and the
     * WEAPON slot is empty, the dagger is equipped. The [GameState.starterWeaponGranted] flag is
     * always set afterwards, so a player who later unequips/sells the weapon does not get a new one.
     * Returns [state] unchanged (same instance) when nothing needs to happen.
     */
    fun ensureStarterWeapon(state: GameState): GameState {
        if (state.starterWeaponGranted) return state
        val equipped = state.equipment.equipped
        val newEquipped = if (equipped.containsKey(EquipmentSlot.WEAPON)) {
            equipped
        } else {
            equipped + (EquipmentSlot.WEAPON to RUSTY_DAGGER)
        }
        return state.copy(
            equipment = state.equipment.copy(equipped = newEquipped),
            starterWeaponGranted = true,
        )
    }
}
