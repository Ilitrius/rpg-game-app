package com.idlerpg.game.domain.prestige

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.PrestigeState
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.equipment.ItemGenerator
import com.idlerpg.game.domain.shop.Shop
import com.idlerpg.game.domain.world.MonsterScaling
import com.idlerpg.game.domain.world.WorldProgress
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.random.Random

/**
 * Calibration of the prestige talents (PLAN-PRESTIGE.md, tests): simulated run (per kill) of the reference
 * hero (45/30/25 points, gear bought with gold, one new item per purchase) up to the rebirth gate
 * (zone 20 + level 80), with the talents a player could afford after 0/1/2/5 rebirths (souls paid out at
 * the gate, spent by a simple round-robin). Writes `build/prestige-calibration.txt`.
 *
 * Targets: run 2 needs about 50-60 % of the time of run 1, run 6 (after 5 rebirths) about 25 %;
 * the boss of zone 20 must stay a real fight (not trivialized): the auto-boss gate keeps its 25 kills and
 * the hero still fails/waits on a share of bosses in later runs.
 */
class PrestigeCalibrationTest {

    private data class RunResult(
        val seconds: Double,
        val level: Int,
        val kills: Long,
        val souls: Long,
        val goldPerHourAtGate: Double,
        val bossFailures: Int,
        /** Seconds the hero needs to kill the zone-20 boss when first challenging it. */
        val bossFightSeconds: Double,
        val gearLevel: Int,
    )

    private val slots = EquipmentSlot.entries
    private val splitPrio = listOf(Talents.XP, Talents.GOLD, Talents.DAMAGE, Talents.HP, Talents.START_LEVEL, Talents.SPEED)

    /** Round-robin buyer: always the cheapest next level among [splitPrio]. */
    private fun spend(p0: PrestigeState): PrestigeState {
        var state = GameState(prestige = p0)
        while (true) {
            val options = splitPrio.mapNotNull { id ->
                val t = Talents.byId(id)!!
                val lvl = Prestige.level(state.prestige, id)
                if (lvl >= t.maxLevel) null else id to Prestige.soulCost(t, lvl)
            }.filter { it.second <= state.prestige.souls }
            val pick = options.minByOrNull { it.second } ?: break
            state = Prestige.buyTalent(state, pick.first, PayWith.SOULS)
        }
        return state.prestige
    }

    private fun heroState(level: Int, gear: Map<EquipmentSlot, Item>, prestige: PrestigeState): GameState {
        val pts = Progression.STAT_POINTS_PER_LEVEL * (level - 1)
        val str = (pts * 0.45).toInt(); val dex = (pts * 0.30).toInt(); val vit = pts - str - dex
        var s = StarterItems.ensureStarterWeapon(GameState(prestige = prestige))
        s = s.copy(character = s.character.copy(level = level, stats = Stats(5 + str, 5 + dex, 5 + vit, 5)))
        return s.copy(equipment = s.equipment.copy(equipped = s.equipment.equipped + gear))
    }

    private fun simulate(prestige: PrestigeState, seed: Int, targetOrder: Int = 20, targetLevel: Int = 80): RunResult {
        val r = Random(seed)
        val bonuses = PrestigeBonuses.of(prestige)
        var level = Prestige.startLevel(prestige)
        var xp = 0L
        var gold = 0.0
        val gear = HashMap<EquipmentSlot, Item>()
        var state = heroState(level, gear, prestige)
        var hero = CombatStats.of(state)
        var zoneIdx = 0
        var killsInZone = 0L
        var kills = 0L
        var seconds = 0.0
        var bossFails = 0
        var bossFightSeconds = 0.0
        var goldLastHour = 0.0
        var goldWindow = 0.0
        var windowSec = 0.0
        var failCooldown = 0L
        val gearLevels = HashMap<EquipmentSlot, Int>()
        fun refresh() { state = heroState(level, gear, prestige); hero = CombatStats.of(state) }
        fun gearAvg() = slots.map { gearLevels[it] ?: 0 }.average().toInt()
        fun shopStep() {
            // Buy the slot with the lowest item level once affordable (price of an item at hero level).
            val slot = slots.minByOrNull { gearLevels[it] ?: 0 }!!
            val have = gearLevels[slot] ?: 0
            if (have >= level - 2) return
            val item = ItemGenerator.roll(level, r, slot = slot)
            val price = Shop.buyPrice(item).toDouble()
            if (gold >= price) {
                gold -= price
                gear[slot] = item; gearLevels[slot] = level
                refresh()
            }
        }
        while (!(Zones.all[zoneIdx].order >= targetOrder && level >= targetLevel)) {
            val zone = Zones.all[zoneIdx]
            val mLevel = level.coerceIn(zone.minLevel, zone.maxLevel)
            val avg = MonsterScaling.average(zone, mLevel)
            val fightSec = avg.hp / hero.dps
            val dmg = fightSec * avg.dps
            val travel = CombatRules.TRAVEL_MILLIS / 1000.0
            val regen = CombatRules.regen(hero.maxHp, ((fightSec + travel) * 1000).toLong())
            val rest = (dmg - regen).coerceAtLeast(0.0) / (hero.maxHp * CombatRules.HERO_REGEN_PER_SECOND)
            // A hero that dies in an average fight cannot farm here: drop back (not expected for the reference hero).
            val dt = fightSec + travel + rest
            seconds += dt; windowSec += dt
            kills++; killsInZone++
            val g = avg.gold * bonuses.gold
            gold += g; goldWindow += g
            xp += Math.round(avg.xp * bonuses.xp)
            while (level < Progression.MAX_LEVEL && xp >= Progression.xpForNextLevel(level)) {
                xp -= Progression.xpForNextLevel(level); level++
                refresh()
            }
            shopStep()
            if (windowSec >= 3600.0) { goldLastHour = goldWindow; goldWindow = 0.0; windowSec = 0.0 }
            // Boss: auto-boss gate (25 kills, retry penalty 10 kills per failure up to 50), 70 % HP start ignored (rest above).
            if (killsInZone >= WorldProgress.BOSS_KILLS_REQUIRED + failCooldown && zoneIdx < Zones.COUNT - 1) {
                val boss = MonsterScaling.boss(zone)
                val bossSec = boss.maxHp / hero.dps
                val bossDmg = bossSec * boss.dps
                if (zone.order == targetOrder - 1 || zone.order == targetOrder) bossFightSeconds = bossSec
                if (bossSec < WorldProgress.BOSS_TIME_LIMIT_MILLIS / 1000.0 && bossDmg < hero.maxHp * 0.95) {
                    seconds += bossSec + travel
                    gold += boss.gold.toDouble() * bonuses.gold
                    xp += Math.round(boss.xp * bonuses.xp)
                    zoneIdx++; killsInZone = 0; failCooldown = 0
                } else {
                    bossFails++
                    failCooldown = minOf(10L * (failCooldown / 10 + 1), 50L)
                    killsInZone = WorldProgress.BOSS_KILLS_REQUIRED // stays at the gate, but waits [failCooldown] more kills
                    seconds += minOf(bossSec, 30.0)
                }
            }
            if (kills > 5_000_000) error("simulation does not finish")
        }
        return RunResult(
            seconds, level, kills, Prestige.souls(level, Zones.all[zoneIdx].order),
            if (goldLastHour > 0) goldLastHour else goldWindow / maxOf(windowSec, 1.0) * 3600.0, bossFails, bossFightSeconds, gearAvg(),
        )
    }

    /** Average of [seeds] simulations. */
    private fun avg(prestige: PrestigeState, seeds: Int = 5): RunResult {
        val rs = (0 until seeds).map { simulate(prestige, it) }
        return RunResult(
            rs.map { it.seconds }.average(), rs.map { it.level }.average().toInt(), rs.map { it.kills }.average().toLong(),
            rs.map { it.souls }.average().toLong(), rs.map { it.goldPerHourAtGate }.average(),
            rs.map { it.bossFailures }.average().toInt(), rs.map { it.bossFightSeconds }.average(), rs.map { it.gearLevel }.average().toInt(),
        )
    }

    /** Talents after [n] rebirths, each paid out at the gate by the previous run (conservative: rebirth as soon as possible). */
    private fun prestigeAfter(n: Int): Pair<PrestigeState, List<RunResult>> {
        var p = PrestigeState()
        val runs = ArrayList<RunResult>()
        repeat(n) { i ->
            val run = avg(p)
            runs += run
            p = p.copy(rebirths = i + 1, souls = p.souls + (run.souls ).toLong())
            p = spend(p)
        }
        return p to runs
    }

    @Test
    fun calibrate() {
        val sb = StringBuilder()
        fun line(label: String, r: RunResult, base: Double, p: PrestigeState) = sb.appendLine(
            String.format(
                Locale.ROOT, "%-9s time %7.0f min (%5.1f%% of run 1) lvl %3d kills %7d souls+ %3d gold/h@gate %12.0f bossFails %2d boss20 %6.1fs gear %3d | talents %s",
                label, r.seconds / 60, r.seconds / base * 100, r.level, r.kills, r.souls, r.goldPerHourAtGate, r.bossFailures, r.bossFightSeconds, r.gearLevel,
                p.talents.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" },
            ),
        )
        val base = avg(PrestigeState())
        line("run 1", base, base.seconds, PrestigeState())
        val results = HashMap<Int, RunResult>()
        for (n in listOf(1, 2, 5)) {
            val (p, _) = prestigeAfter(n)
            val r = avg(p)
            results[n] = r
            line("after $n", r, base.seconds, p)
        }
        // Gold route: price of the cheapest talent levels versus gold/h at the gate.
        sb.appendLine()
        for (rb in listOf(0, 1, 4)) {
            val xp1 = Prestige.goldCost(Talents.byId(Talents.XP)!!, 0, rb)
            val xp5 = Prestige.goldCost(Talents.byId(Talents.XP)!!, 4, rb)
            sb.appendLine(String.format(Locale.ROOT, "rebirths %d: gold price XP lvl1 %d (%.2f h of gate income), lvl5 %d (%.2f h)", rb, xp1, xp1 / base.goldPerHourAtGate, xp5, xp5 / base.goldPerHourAtGate))
        }
        File("build").mkdirs()
        File("build/prestige-calibration.txt").writeText(sb.toString())
        println(sb)
        val r2 = results[1]!!.seconds / base.seconds
        val r6 = results[5]!!.seconds / base.seconds
        assertTrue("run 2 share $r2", r2 in 0.45..0.65)
        assertTrue("run 6 share $r6", r6 in 0.18..0.32)
        // Boss stays a real fight and the gate keeps its 25 kills.
        assertTrue("boss trivialized: ${results[5]!!.bossFightSeconds}", results[5]!!.bossFightSeconds >= 3.0)
        assertTrue(results[5]!!.kills >= (targetZones() * WorldProgress.BOSS_KILLS_REQUIRED))
    }

    private fun targetZones() = 19
}
