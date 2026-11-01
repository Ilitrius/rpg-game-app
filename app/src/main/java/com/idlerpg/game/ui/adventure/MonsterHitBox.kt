package com.idlerpg.game.ui.adventure

import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Tap target of the monster. Uses exactly the depth the painter uses (approach 0..1 ->
 * depth 0.14..1, size and ground line scale with it) and a minimum radius so the distant,
 * tiny monster during the approach is still easy to hit.
 */
object MonsterHitBox {
    const val MIN_RADIUS_DP = 48f

    /** Base monster height at full size (same formula as the painter). */
    fun baseHeight(width: Float, height: Float, sizeFactor: Float): Float =
        min(width * 0.66f, height * 0.38f) * sizeFactor

    fun depth(approach: Float): Float = lerp(0.14f, 1f, approach.coerceIn(0f, 1f))

    fun contains(
        px: Float, py: Float,
        width: Float, height: Float,
        horizonY: Float, monsterGroundY: Float,
        sizeFactor: Float, approach: Float,
        minRadiusPx: Float,
    ): Boolean {
        val depth = depth(approach)
        val h = baseHeight(width, height, sizeFactor) * depth
        val ground = horizonY + (monsterGroundY - horizonY) * depth
        val top = ground - h * 1.05f
        val bottom = ground + h * 0.08f
        val cy = (top + bottom) / 2f
        val halfH = max((bottom - top) / 2f, minRadiusPx)
        val halfW = max(h * 0.55f, minRadiusPx)
        return abs(px - width / 2f) <= halfW && abs(py - cy) <= halfH
    }
}
