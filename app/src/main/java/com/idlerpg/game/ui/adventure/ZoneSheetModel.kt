package com.idlerpg.game.ui.adventure

import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.world.Biome
import com.idlerpg.game.domain.world.WorldProgress
import com.idlerpg.game.domain.world.Zone
import com.idlerpg.game.domain.world.Zones

/** One section of the zone list: the five handmade zones ([biome] == null) or one biome. */
data class ZoneGroup(val biome: Biome?, val zones: List<Zone>, val unlocked: Int) {
    val total: Int get() = zones.size
}

/**
 * Structure of the zone sheet (pure, testable): groups with sticky headers and the position of
 * zones in the flattened lazy list (each group = 1 header item + one item per zone).
 */
data class ZoneSheetModel(val groups: List<ZoneGroup>) {

    /** Index of the header item of the group containing [zone]. */
    fun headerIndexOf(zone: Zone): Int {
        var i = 0
        for (g in groups) {
            if (g.zones.any { it.id == zone.id }) return i
            i += 1 + g.zones.size
        }
        return 0
    }

    /** Index of [zone]'s row in the flattened list. */
    fun indexOf(zone: Zone): Int {
        var i = 0
        for (g in groups) {
            val k = g.zones.indexOfFirst { it.id == zone.id }
            if (k >= 0) return i + 1 + k
            i += 1 + g.zones.size
        }
        return 0
    }

    val itemCount: Int get() = groups.sumOf { 1 + it.zones.size }

    companion object {
        fun build(world: WorldState, all: List<Zone> = Zones.all): ZoneSheetModel {
            val groups = ArrayList<ZoneGroup>()
            var cur = ArrayList<Zone>()
            var curBiome: Biome? = null
            fun flush() { if (cur.isNotEmpty()) groups += ZoneGroup(curBiome, cur, cur.count { WorldProgress.isUnlocked(world, it) }) }
            for (z in all) {
                if (cur.isNotEmpty() && z.biome != curBiome) { flush(); cur = ArrayList() }
                curBiome = z.biome
                cur += z
            }
            flush()
            return ZoneSheetModel(groups)
        }

        /** Zone with the 1-based number typed by the player, or null for empty/invalid/out-of-range input. */
        fun zoneByNumber(text: String, all: List<Zone> = Zones.all): Zone? =
            text.trim().toIntOrNull()?.let { n -> all.firstOrNull { it.order == n } }
    }
}
