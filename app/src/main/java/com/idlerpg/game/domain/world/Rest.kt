package com.idlerpg.game.domain.world

import com.idlerpg.game.data.GameState
import kotlin.math.roundToLong

/**
 * Rest after a defeat: wait [FREE_REST_MILLIS] for free, or heal at once for gold.
 * The price follows the zone's gold ([COST_IN_KILLS] average kills at the hero's level), so it
 * scales with progress like the shop prices instead of being a fixed number.
 */
object Rest {
    const val FREE_REST_MILLIS = 15_000L
    const val COST_IN_KILLS = 5.0
    const val MIN_COST = 5L

    fun healCost(zone: Zone, heroLevel: Int): Long =
        (MonsterScaling.average(zone, heroLevel.coerceAtLeast(1)).gold * COST_IN_KILLS).roundToLong().coerceAtLeast(MIN_COST)

    /** Pays [cost]; null if not enough gold. */
    fun pay(state: GameState, cost: Long): GameState? =
        if (cost < 0 || state.gold < cost) null else state.copy(gold = state.gold - cost)
}
