package com.idlerpg.game.domain.world

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.equipment.ItemGenerator
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.shop.Shop
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Not an assertion test: writes `build/zone-calibration.txt` with, per zone, the fight time and damage
 * share of the reference hero (zone gear, mid level) against monsters with multipliers 1.0, plus the
 * multipliers that would give the target values. Used to derive [ZoneScaling].
 */
class ZoneCalibrationTest {
    private fun hero(level: Int, gear: Int, seed: Int): CombatStats {
        val pts = Progression.STAT_POINTS_PER_LEVEL * (level - 1)
        val str = (pts * 0.45).toInt(); val dex = (pts * 0.30).toInt(); val vit = pts - str - dex
        var s = StarterItems.ensureStarterWeapon(GameState())
        s = s.copy(character = s.character.copy(level = level, stats = Stats(5 + str, 5 + dex, 5 + vit, 5)))
        val r = Random(seed)
        val order = listOf(EquipmentSlot.WEAPON) + EquipmentSlot.entries.filter { it != EquipmentSlot.WEAPON }.shuffled(r)
        val eq = order.take(7).associateWith { ItemGenerator.roll(gear, r, slot = it) }
        return CombatStats.of(s.copy(equipment = s.equipment.copy(equipped = s.equipment.equipped + eq)))
    }

    @Test
    fun writeCalibration() {
        val sb = StringBuilder("order id level | kill@1 share@1 | actual kill share | needHp needDmg\n")
        for (z in Zones.all) {
            val lvl = (z.minLevel + z.maxLevel) / 2
            val unit = z.copy(hpMultiplier = 1.0, damageMultiplier = 1.0, rewardMultiplier = 1.0)
            var k1 = 0.0; var d1 = 0.0; var ka = 0.0; var da = 0.0
            val seeds = 60
            val total = z.monsters.sumOf { it.spawnWeight }.toDouble()
            repeat(seeds) { seed ->
                val h = hero(lvl, lvl, seed)
                z.monsters.forEach { t ->
                    val w = t.spawnWeight / total / seeds
                    val a = MonsterScaling.stats(t, unit, lvl); val b = MonsterScaling.stats(t, z, lvl)
                    k1 += a.maxHp / h.dps * w; d1 += a.maxHp / h.dps * a.dps / h.maxHp * w
                    ka += b.maxHp / h.dps * w; da += b.maxHp / h.dps * b.dps / h.maxHp * w
                }
            }
            sb.appendLine(String.format(java.util.Locale.ROOT, "%3d %-12s %3d | %7.2f %6.3f | %6.2f %6.3f | %6.3f %6.3f",
                z.order, z.id, lvl, k1, d1, ka, da, 3.5 / k1, 0.10 / d1 * (3.5 / k1)))
        }
        File("build").mkdirs()
        File("build/zone-calibration.txt").writeText(sb.toString())
    }

    @Test
    fun writeEconomyCalibration() {
        val t0 = 1_000_000_000L
        val night = 8L * 3_600_000L
        val sb = StringBuilder("order id level | reward | night/shop levels items | nightXp/need\n")
        for (z in Zones.all) {
            val lvl = (z.minLevel + z.maxLevel) / 2
            var gold = 0.0; var lv = 0.0; var items = 0.0; var shop = 0.0; var xp = 0.0
            val seeds = 40
            repeat(seeds) { seed ->
                val h = hero(lvl, lvl, seed)
                var st = StarterItems.ensureStarterWeapon(GameState())
                val pts = Progression.STAT_POINTS_PER_LEVEL * (lvl - 1)
                val str = (pts * 0.45).toInt(); val dex = (pts * 0.30).toInt(); val vit = pts - str - dex
                st = st.copy(character = st.character.copy(level = lvl, stats = Stats(5 + str, 5 + dex, 5 + vit, 5)))
                val r = Random(seed)
                val order = listOf(EquipmentSlot.WEAPON) + EquipmentSlot.entries.filter { it != EquipmentSlot.WEAPON }.shuffled(r)
                st = st.copy(equipment = st.equipment.copy(equipped = st.equipment.equipped + order.take(7).associateWith { ItemGenerator.roll(lvl, r, slot = it) }))
                st = st.copy(world = WorldState(currentZoneId = z.id, unlockedZoneIds = Zones.all.filter { it.order <= z.order }.map { it.id }, lastActiveEpochMillis = t0))
                val rew = OfflineProgress.calculate(st, t0 + night, Random(seed))
                if (rew != null) { gold += rew.totalGold.toDouble() / seeds; lv += rew.levelsGained.toDouble() / seeds; items += rew.items.size.toDouble() / seeds; xp += rew.xp.toDouble() / seeds }
                shop += Shop.generateOffers(Shop.shopLevel(st), Random(seed)).sumOf { Shop.buyPrice(it).toDouble() } / seeds
            }
            sb.appendLine(String.format(java.util.Locale.ROOT, "%3d %-12s %3d | %5.2f | %6.3f %5.2f %5.1f | %.3f", z.order, z.id, lvl, z.rewardMultiplier, gold / shop, lv, items, xp / Progression.xpForNextLevel(lvl)))
        }
        File("build").mkdirs()
        File("build/zone-economy.txt").writeText(sb.toString())
    }
}
