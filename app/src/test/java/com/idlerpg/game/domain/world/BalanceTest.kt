package com.idlerpg.game.domain.world

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.equipment.ItemGenerator
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Balance checks with a reference hero: stat points spread 45 % Stärke / 30 % Geschick / 25 % Vitalität,
 * either with only the starter dagger or with randomly rolled drops (Monte Carlo over many seeds).
 * Also writes a table to `build/balance.txt` for manual tuning.
 */
class BalanceTest {

    private data class Fight(val killSeconds: Double, val damageShare: Double, val sustain: Double)

    private fun hero(level: Int, gearLevel: Int?, slots: Int, seed: Int): CombatStats {
        val pts = Progression.STAT_POINTS_PER_LEVEL * (level - 1)
        val str = (pts * 0.45).toInt()
        val dex = (pts * 0.30).toInt()
        val vit = pts - str - dex
        var s = StarterItems.ensureStarterWeapon(GameState())
        s = s.copy(character = s.character.copy(level = level, stats = Stats(5 + str, 5 + dex, 5 + vit, 5)))
        if (gearLevel != null) {
            val r = Random(seed)
            val order = listOf(EquipmentSlot.WEAPON) + EquipmentSlot.entries.filter { it != EquipmentSlot.WEAPON }.shuffled(r)
            val eq = order.take(slots).associateWith { ItemGenerator.roll(gearLevel, r, slot = it) }
            s = s.copy(equipment = s.equipment.copy(equipped = s.equipment.equipped + eq))
        }
        return CombatStats.of(s)
    }

    private fun fight(h: CombatStats, m: MonsterStats): Fight {
        val secs = m.maxHp / h.dps
        val dmg = secs * m.dps
        val regen = CombatRules.regen(h.maxHp, ((secs * 1000) + CombatRules.TRAVEL_MILLIS).toLong())
        return Fight(secs, dmg / h.maxHp, regen / dmg.coerceAtLeast(1e-9))
    }

    /** Weighted average fight vs. the zone's regular monsters at [level], averaged over seeds. */
    private fun zoneFight(zone: Zone, level: Int, heroLevel: Int, gearLevel: Int?, slots: Int = 7): Fight {
        val seeds = if (gearLevel == null) 1 else 300
        var k = 0.0; var d = 0.0; var su = 0.0
        val total = zone.monsters.sumOf { it.spawnWeight }.toDouble()
        repeat(seeds) { seed ->
            val h = hero(heroLevel, gearLevel, slots, seed)
            zone.monsters.forEach { t ->
                val f = fight(h, MonsterScaling.stats(t, zone, level))
                val w = t.spawnWeight / total / seeds
                k += f.killSeconds * w; d += f.damageShare * w; su += f.sustain * w
            }
        }
        return Fight(k, d, su)
    }

    private fun bossFight(zone: Zone, heroLevel: Int, gearLevel: Int?, slots: Int = 7): Fight {
        val seeds = if (gearLevel == null) 1 else 300
        var k = 0.0; var d = 0.0
        repeat(seeds) { seed ->
            val f = fight(hero(heroLevel, gearLevel, slots, seed), MonsterScaling.boss(zone))
            k += f.killSeconds / seeds; d += f.damageShare / seeds
        }
        return Fight(k, d, 0.0)
    }

    private fun gearOf(zone: Zone) = (zone.minLevel + zone.maxLevel) / 2

    @Test
    fun writeBalanceTable() {
        val sb = StringBuilder()
        fun f(x: Fight) = String.format("%5.1fs %4.0f%%HP sus%4.1f", x.killSeconds, x.damageShare * 100, x.sustain)
        Zones.all.forEach { z ->
            val prev = Zones.previous(z)
            val prevGear = prev?.let { gearOf(it) }
            sb.appendLine("== ${z.id} L${z.minLevel}-${z.maxLevel}")
            for (lvl in listOf(z.minLevel, gearOf(z), z.maxLevel)) {
                sb.appendLine(
                    "  L$lvl starter: ${f(zoneFight(z, lvl, lvl, null))} | prevGear($prevGear,5): " +
                        "${prevGear?.let { f(zoneFight(z, lvl, lvl, it, 5)) } ?: "-"} | zoneGear(${gearOf(z)},7): ${f(zoneFight(z, lvl, lvl, gearOf(z)))}",
                )
            }
            val b = MonsterScaling.boss(z)
            sb.appendLine(
                "  BOSS L${b.level} hp=${b.maxHp} dmg=${b.damageMin}-${b.damageMax}/${b.attackIntervalMillis}ms xp=${b.xp} gold=${b.gold}: " +
                    "starter ${f(bossFight(z, z.maxLevel, null))} | prevGear ${prevGear?.let { f(bossFight(z, z.maxLevel, it, 5)) } ?: "-"} | zoneGear ${f(bossFight(z, z.maxLevel, gearOf(z)))}",
            )
            z.monsters.forEach { t ->
                val m = MonsterScaling.stats(t, z, z.minLevel)
                sb.appendLine("    ${t.id} L${m.level}: hp=${m.maxHp} dmg=${m.damageMin}-${m.damageMax}/${m.attackIntervalMillis}ms xp=${m.xp} gold=${m.gold}")
            }
        }
        File("build").mkdirs()
        File("build/balance.txt").writeText(sb.toString())
    }

    @Test
    fun zoneTwoIsHardWithStarterGearButFineWithZoneOneDrops() {
        val wald = Zones.WALD
        val starter = zoneFight(wald, wald.minLevel, wald.minLevel, null)
        val geared = zoneFight(wald, wald.minLevel, wald.minLevel, gearOf(Zones.WIESE), slots = 5)
        assertTrue("starter $starter", starter.killSeconds > 9.0 && starter.damageShare > 0.45)
        assertTrue("geared $geared", geared.killSeconds in 2.0..7.0 && geared.damageShare < 0.35)
    }

    @Test
    fun everyZoneIsPlayableWithDropsOfThePreviousZone() {
        Zones.all.drop(1).forEach { z ->
            val f = zoneFight(z, z.minLevel, z.minLevel, gearOf(Zones.previous(z)!!), slots = 5)
            assertTrue("${z.id} $f", f.killSeconds < 9.0 && f.damageShare < 0.6)
        }
    }

    @Test
    fun bossesNeedZoneGearToBeBeatenInTime() {
        val limit = WorldProgress.BOSS_TIME_LIMIT_MILLIS / 1000.0
        Zones.all.forEach { z ->
            val geared = bossFight(z, z.maxLevel, gearOf(z))
            assertTrue("${z.id} geared $geared", geared.killSeconds < limit * 0.75 && geared.damageShare < 0.9)
            val starter = bossFight(z, z.maxLevel, null)
            assertTrue("${z.id} starter $starter", starter.killSeconds > limit || starter.damageShare > 1.0)
        }
    }
}
