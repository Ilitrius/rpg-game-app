package com.idlerpg.game.domain.progress

import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.GoalState
import kotlin.math.roundToLong

/** Goal counter names (values of [GoalState.type]). Plain strings: unknown ones in a save must not crash. */
object GoalTypes {
    const val KILLS = "kills"
    const val BOSSES = "bosses"
    const val LEVEL = "level"
    const val ZONES = "zones"
    val all = listOf(KILLS, BOSSES, LEVEL, ZONES)

    /** Current value of the counter behind [type], or null for unknown types. */
    fun counter(state: GameState, type: String): Long? = when (type) {
        KILLS -> state.world.totalKills
        BOSSES -> state.world.defeatedBossIds.size.toLong()
        LEVEL -> state.character.level.toLong()
        ZONES -> state.world.unlockedZoneIds.size.toLong()
        else -> null
    }
}

/**
 * Catalog entry. [target] is the gain over the counter at creation time (baseline), so goals are
 * never done instantly for veteran heroes. [rewardKills] = reward in "gold of N kills" of the current zone.
 */
data class GoalDef(val id: String, val type: String, val target: Long, val rewardKills: Double)

/**
 * Goals: always [ACTIVE_COUNT] active goals from the [catalog]; claiming one pulls the next unclaimed
 * entry in. Progress is derived from existing counters (no hooks in the combat loop, offline counts).
 * Claimed goals stay in [com.idlerpg.game.data.ProgressState.goals] as the "done" memory.
 */
object Goals {
    const val ACTIVE_COUNT = 3

    private val killTargets = listOf(25L, 50, 100, 250, 500, 1_000, 2_000, 3_500, 5_000, 10_000, 20_000, 35_000, 50_000, 100_000)
    private val bossTargets = listOf(1L, 2, 3, 5, 8, 12, 20, 30)
    private val levelTargets = listOf(1L, 2, 3, 5, 8, 12, 20, 35, 60, 100)
    private val zoneTargets = listOf(1L, 2, 3, 5, 8, 12)

    private fun killReward(t: Long) = maxOf(15.0, t * 0.4)
    private fun bossReward(t: Long) = 80.0 * t
    private fun levelReward(t: Long) = 40.0 * t
    private fun zoneReward(t: Long) = 120.0 * t

    /** Interleaved by tier (one entry per type per tier), each type strictly ascending. */
    val catalog: List<GoalDef> = buildList {
        val tiers = maxOf(killTargets.size, bossTargets.size, levelTargets.size, zoneTargets.size)
        for (i in 0 until tiers) {
            killTargets.getOrNull(i)?.let { add(GoalDef("kills_$it", GoalTypes.KILLS, it, killReward(it))) }
            bossTargets.getOrNull(i)?.let { add(GoalDef("bosses_$it", GoalTypes.BOSSES, it, bossReward(it))) }
            levelTargets.getOrNull(i)?.let { add(GoalDef("level_$it", GoalTypes.LEVEL, it, levelReward(it))) }
            zoneTargets.getOrNull(i)?.let { add(GoalDef("zones_$it", GoalTypes.ZONES, it, zoneReward(it))) }
        }
    }

    private val byId = catalog.associateBy { it.id }

    /** Catalog entry of [goal]; null for unknown id/type or a type that doesn't match the id. */
    fun defOf(goal: GoalState): GoalDef? = byId[goal.id]?.takeIf { it.type == goal.type }

    /** Progress 0..target, or null when the goal is unknown (skipped). */
    fun progress(state: GameState, goal: GoalState): Long? {
        val def = defOf(goal) ?: return null
        val counter = GoalTypes.counter(state, goal.type) ?: return null
        return (counter - goal.baseline).coerceIn(0, def.target)
    }

    fun isComplete(state: GameState, goal: GoalState): Boolean {
        val def = defOf(goal) ?: return false
        return progress(state, goal) == def.target
    }

    /** Gold reward; scales with the current zone/level via [DailyReward.goldPerKill]. */
    fun reward(state: GameState, goal: GoalState): Long {
        val def = defOf(goal) ?: return 0
        return (DailyReward.goldPerKill(state) * def.rewardKills).roundToLong().coerceAtLeast(1)
    }

    /** Unclaimed, known goals (what the UI shows). */
    fun active(state: GameState): List<GoalState> =
        state.progress.goals.filter { !it.claimed && defOf(it) != null }.take(ACTIVE_COUNT)

    /** Catalog entries not yet in the goal list, in catalog order. */
    private fun pending(state: GameState): List<GoalDef> {
        val known = state.progress.goals.map { it.id }.toSet()
        return catalog.filter { it.id !in known }
    }

    /** True when [ensureActive] would add goals. */
    fun needsRefill(state: GameState): Boolean =
        state.progress.goals.count { !it.claimed && defOf(it) != null } < ACTIVE_COUNT && pending(state).isNotEmpty()

    /** Fills the active goals up to [ACTIVE_COUNT]; the baseline is the counter at that moment. */
    fun ensureActive(state: GameState): GameState {
        if (!needsRefill(state)) return state
        val goals = state.progress.goals.toMutableList()
        var open = goals.count { !it.claimed && defOf(it) != null }
        for (def in pending(state)) {
            if (open >= ACTIVE_COUNT) break
            val counter = GoalTypes.counter(state, def.type) ?: continue
            goals += GoalState(def.id, def.type, def.target, baseline = counter)
            open++
        }
        return state.copy(progress = state.progress.copy(goals = goals))
    }

    /** Claims the finished goal [goalId] (gold + counter) and refills; unchanged state when not claimable. */
    fun claim(state: GameState, goalId: String): GameState {
        val goal = state.progress.goals.firstOrNull { it.id == goalId && !it.claimed } ?: return state
        if (!isComplete(state, goal)) return state
        val gold = reward(state, goal)
        val p = state.progress
        val updated = state.copy(
            gold = state.gold + gold,
            progress = p.copy(
                goals = p.goals.map { if (it.id == goalId) it.copy(claimed = true) else it },
                goalsCompleted = p.goalsCompleted + 1,
            ),
        )
        return ensureActive(updated)
    }
}
