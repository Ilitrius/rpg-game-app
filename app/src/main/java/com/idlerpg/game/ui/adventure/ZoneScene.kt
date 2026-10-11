package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.idlerpg.game.domain.world.Zone
import com.idlerpg.game.domain.world.Zones

/**
 * Painted first-person landscape of one zone. Implementations build all paths/brushes once per
 * size and only animate cheap things (clouds, mist, light, flames) per frame.
 */
interface ZoneScene {
    /** Screen y of the horizon / vanishing line where monsters appear. */
    val horizonY: Float

    /** Screen y where a monster at full size stands. */
    val monsterGroundY: Float

    /**
     * Draws everything behind the monster.
     * @param drift 0..1 over a slow 5-minute cycle (clouds, mist)
     * @param time running seconds 0..60, looping (flicker, shimmer); use whole cycles per 60 s
     */
    fun drawBackground(scope: DrawScope, drift: Float, time: Float)

    /** Draws everything in front of the monster (nearest foliage, vignette). */
    fun drawForeground(scope: DrawScope, time: Float)
}

/** Creates the scene of [zone] for a canvas of [size]. */
fun createZoneScene(zone: Zone, size: Size): ZoneScene = when (zone.id) {
    Zones.WALD_ID -> ForestScene(size)
    Zones.SUMPF_ID -> SwampScene(size)
    Zones.GEBIRGE_ID -> MountainScene(size)
    Zones.HOEHLE_ID -> CaveScene(size)
    else -> MeadowScene(size)
}
