package com.idlerpg.game.domain.world

import com.idlerpg.game.data.WorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The game goes on after zone 100: every boss victory there raises the endless tier. */
class EndlessTierTest {
    private val last = Zones.last

    @Test
    fun tierZeroAndOtherZonesAreUnchanged() {
        assertSame(last, Zones.effective(last, 0))
        assertSame(Zones.WIESE, Zones.effective(Zones.WIESE, 5))
        assertSame(Zones.all[50], Zones.effective(Zones.all[50], 99))
    }

    @Test
    fun everyTierRaisesLevelsAndDifficulty() {
        var prev = last
        for (tier in 1..30) {
            val z = Zones.effective(last, tier)
            assertEquals("id and order stay", last.id, z.id)
            assertEquals(last.order, z.order)
            assertEquals(last.minLevel + 10 * tier, z.minLevel)
            assertEquals(last.maxLevel + 10 * tier, z.maxLevel)
            assertTrue(z.hpMultiplier > prev.hpMultiplier)
            assertTrue(z.damageMultiplier > prev.damageMultiplier)
            assertTrue(z.rewardMultiplier > prev.rewardMultiplier)
            assertTrue(z.goldMultiplier > prev.goldMultiplier)
            prev = z
        }
    }

    @Test
    fun monstersNeverFallBackBelowTheTierLevels() {
        // The reported loop: at the end monsters dropped from 990 back to 980. Now the range rises per tier.
        val r = Random(1)
        for (tier in 0..5) {
            val z = Zones.effective(last, tier)
            repeat(200) {
                val m = MonsterScaling.spawn(z, heroLevel = 999, r)
                assertTrue("tier $tier level ${m.level}", m.level in z.minLevel..z.maxLevel)
            }
            assertEquals(z.maxLevel, MonsterScaling.boss(z).level)
        }
        assertTrue(MonsterScaling.boss(Zones.effective(last, 1)).level > last.maxLevel)
    }

    @Test
    fun hugeTiersStayInRange() {
        for (tier in listOf(150, 151, 1_000, 100_000, Int.MAX_VALUE)) {
            val z = Zones.effective(last, tier)
            val boss = MonsterScaling.boss(z)
            assertTrue("tier $tier hp", boss.maxHp in 1..Long.MAX_VALUE / 2)
            assertTrue("tier $tier damage", boss.damageMin in 1..boss.damageMax)
            assertTrue("tier $tier xp", boss.xp in 1..Long.MAX_VALUE / 2)
            assertTrue("tier $tier gold", boss.gold in 1..Long.MAX_VALUE / 2)
        }
    }

    @Test
    fun scalingStopsAtTheCapWhileLevelsKeepRising() {
        val a = Zones.effective(last, Zones.ENDLESS_SCALING_CAP)
        val b = Zones.effective(last, Zones.ENDLESS_SCALING_CAP + 20)
        assertEquals(a.hpMultiplier, b.hpMultiplier, 0.0)
        assertEquals(a.rewardMultiplier, b.rewardMultiplier, 0.0)
        assertTrue(b.minLevel > a.minLevel)
    }

    @Test
    fun theLastBossCanBeChallengedAgainAfterEveryVictory() {
        val state = com.idlerpg.game.data.GameState(
            world = WorldState(
                currentZoneId = last.id, zoneKills = mapOf(last.id to 100L), defeatedBossIds = Zones.all.map { it.id },
            ),
        )
        assertTrue(WorldProgress.isUnlocked(state.world, last))
        val won = WorldProgress.applyBossVictory(state, last, 1, 1, 1L)
        assertEquals(1, won.world.endlessTier)
        assertEquals("needs 25 new kills first", 0L, WorldProgress.killsTowardsBoss(won.world, last))
        val ready = won.copy(world = won.world.copy(zoneKills = won.world.zoneKills + (last.id to 126L)))
        assertTrue(WorldProgress.canChallengeBoss(ready.world, last))
    }
}

/** Not an assertion test: writes `build/endless-calibration.txt` (fight time / damage share per tier for a level-999 hero). */
class EndlessCalibrationTest {
    @Test
    fun writeEndlessCalibration() {
        val last = Zones.last
        val sb = StringBuilder("tier monsterLvl | killSec shareOfHeroHp | boss killSec\n")
        for (tier in listOf(0, 1, 2, 3, 5, 10, 20, 30, 50, 100, 150)) {
            val z = Zones.effective(last, tier)
            val lvl = (z.minLevel + z.maxLevel) / 2
            var k = 0.0; var d = 0.0
            val seeds = 40
            val total = z.monsters.sumOf { it.spawnWeight }.toDouble()
            repeat(seeds) { seed ->
                val h = hero999(seed)
                z.monsters.forEach { t ->
                    val w = t.spawnWeight / total / seeds
                    val b = MonsterScaling.stats(t, z, lvl)
                    k += b.maxHp / h.dps * w; d += b.maxHp / h.dps * b.dps / h.maxHp * w
                }
            }
            val boss = MonsterScaling.boss(z); val h0 = hero999(0)
            sb.appendLine(String.format(java.util.Locale.ROOT, "%4d %6d | %9.2f %6.3f | %9.2f", tier, lvl, k, d, boss.maxHp / h0.dps))
        }
        java.io.File("build").mkdirs()
        java.io.File("build/endless-calibration.txt").writeText(sb.toString())
    }

    private fun hero999(seed: Int): com.idlerpg.game.domain.character.CombatStats {
        val level = 999
        val pts = com.idlerpg.game.domain.character.Progression.STAT_POINTS_PER_LEVEL * (level - 1)
        val str = (pts * 0.45).toInt(); val dex = (pts * 0.30).toInt(); val vit = pts - str - dex
        var s = com.idlerpg.game.domain.character.StarterItems.ensureStarterWeapon(com.idlerpg.game.data.GameState())
        s = s.copy(character = s.character.copy(level = level, stats = com.idlerpg.game.data.Stats(5 + str, 5 + dex, 5 + vit, 5)))
        val r = Random(seed)
        val slots = listOf(com.idlerpg.game.data.EquipmentSlot.WEAPON) + com.idlerpg.game.data.EquipmentSlot.entries.filter { it != com.idlerpg.game.data.EquipmentSlot.WEAPON }.shuffled(r)
        val eq = slots.take(7).associateWith { com.idlerpg.game.domain.equipment.ItemGenerator.roll(990, r, slot = it) }
        return com.idlerpg.game.domain.character.CombatStats.of(s.copy(equipment = s.equipment.copy(equipped = s.equipment.equipped + eq)))
    }
}
