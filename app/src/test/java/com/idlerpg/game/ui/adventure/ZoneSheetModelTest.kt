package com.idlerpg.game.ui.adventure

import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneSheetModelTest {
    @Test
    fun groupsAreHomelandsPlusNineteenBiomes() {
        val m = ZoneSheetModel.build(WorldState())
        assertEquals(20, m.groups.size)
        assertNull(m.groups[0].biome)
        assertEquals(5, m.groups[0].total)
        assertEquals(100, m.groups.sumOf { it.total })
        m.groups.drop(1).forEach { assertEquals(5, it.total) }
        assertEquals(120, m.itemCount)
    }

    @Test
    fun unlockedCountPerGroupAndIndexes() {
        val w = WorldState(defeatedBossIds = Zones.all.take(7).map { it.id })
        val m = ZoneSheetModel.build(w)
        assertEquals(5, m.groups[0].unlocked)
        assertEquals(3, m.groups[1].unlocked) // heide_1..3 (zones 6, 7, 8)
        assertEquals(0, m.groups[2].unlocked)
        assertEquals(0, m.headerIndexOf(Zones.WIESE))
        assertEquals(1, m.indexOf(Zones.WIESE))
        assertEquals(6, m.headerIndexOf(Zones.all[5]))
        assertEquals(7, m.indexOf(Zones.all[5]))
        assertEquals(119, m.indexOf(Zones.last))
    }

    @Test
    fun zoneNumberInput() {
        assertEquals("wiese", ZoneSheetModel.zoneByNumber("1")!!.id)
        assertEquals(Zones.last, ZoneSheetModel.zoneByNumber(" 100 "))
        assertNull(ZoneSheetModel.zoneByNumber(""))
        assertNull(ZoneSheetModel.zoneByNumber("0"))
        assertNull(ZoneSheetModel.zoneByNumber("101"))
        assertNull(ZoneSheetModel.zoneByNumber("abc"))
    }
}
