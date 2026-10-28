package com.idlerpg.game.domain.world

import kotlin.math.exp
import kotlin.math.pow

/**
 * Multipliers of the generated zones (orders 6..100), smooth in the zone order. Fitted with
 * `ZoneCalibrationTest` (build/zone-calibration.txt, zone-economy.txt) against the targets the handmade
 * zones were tuned to: reference hero with the zone's own drops needs ~3.5 s per kill and loses
 * ~10 % HP per fight; a night offline buys ~38 % of the shop assortment and gives ~2 levels.
 *
 * - HP rises from 5.5 and saturates near 8.3 (hero power grows about as fast as per-level monster HP).
 * - Damage is flat 0.75 (like the last handmade zone).
 * - XP grows with order^0.647, gold linearly with the order (shop prices grow steeply with item level);
 *   both are divided by the kill rate, which falls with the HP multiplier, hence larger than the raw fit.
 */
internal object ZoneScaling {
    fun hp(order: Int): Double = 8.3 - 2.8 * exp(-(order - 6) / 7.0)
    fun damage(@Suppress("UNUSED_PARAMETER") order: Int): Double = 0.75
    fun reward(order: Int): Double = 0.6 * order.toDouble().pow(0.647)
    fun gold(order: Int): Double = 0.32 * order
}
