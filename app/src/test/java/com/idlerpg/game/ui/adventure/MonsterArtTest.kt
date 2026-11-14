package com.idlerpg.game.ui.adventure

import com.idlerpg.game.domain.world.MonsterType
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterArtTest {

    private val generated = Zones.all.filter { it.biome != null }

    @Test
    fun handmadeZonesHaveNoArtName() {
        Zones.all.filter { it.biome == null }.forEach { z ->
            (z.monsters + z.boss).forEach { assertNull("${z.id} $it", MonsterArt.name(z, it)) }
        }
    }

    @Test
    fun regularMonstersShareNamesAcrossStagesAndBossesDoNot() {
        val heide = generated.filter { it.biome!!.id == "heide" }
        assertEquals(5, heide.size)
        heide.forEach { z ->
            assertEquals(listOf("mob_heide_1", "mob_heide_2", "mob_heide_3"), z.monsters.map { MonsterArt.name(z, it) })
        }
        assertEquals((1..5).map { "mob_heide_boss_$it" }, heide.map { MonsterArt.name(it, it.boss) })
    }

    @Test
    fun namesAreUniqueAndCountMatches() {
        val names = generated.flatMap { z -> (z.monsters + z.boss).map { MonsterArt.name(z, it)!! } }.toSet()
        // 19 biomes x (3 regular + 5 stage bosses)
        assertEquals(19 * 8, names.size)
        assertTrue(names.all { it.startsWith("mob_") && it == it.lowercase() })
        assertEquals(MonsterType.GEN_BOSS, generated.first().boss)
    }
}
