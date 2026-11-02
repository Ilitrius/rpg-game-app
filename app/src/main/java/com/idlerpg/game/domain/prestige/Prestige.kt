package com.idlerpg.game.domain.prestige

import com.idlerpg.game.data.BuffState
import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.EquipmentState
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.GoalState
import com.idlerpg.game.data.PrestigeState
import com.idlerpg.game.data.ShopState
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.progress.GoalTypes
import com.idlerpg.game.domain.world.Zones
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/** All prestige numbers in one place (balance knobs). */
object PrestigeConfig {
    /** Card becomes visible at this hero level. */
    const val VISIBLE_LEVEL = 30

    /** Rebirth gate: level AND zone order must both be reached. */
    const val GATE_LEVEL = 80
    const val GATE_ZONE_ORDER = 20

    /**
     * souls = floor(SOUL_SCALE x ((level / LEVEL_DIVISOR)^LEVEL_EXPONENT + zoneOrder^ZONE_EXPONENT / ZONE_DIVISOR)).
     * SOUL_SCALE 8 calibrated with PrestigeCalibrationTest: the gate payout (level ~115, zone 20) is ~120 souls,
     * run 2 then needs ~55 % and run 6 ~28 % of the time of run 1.
     */
    const val SOUL_SCALE = 8.0
    const val LEVEL_DIVISOR = 50.0
    const val LEVEL_EXPONENT = 1.5
    const val ZONE_EXPONENT = 1.3
    const val ZONE_DIVISOR = 4.0

    /** Soul cost of level n+1 of a talent: round(base x (1 + n)^COST_EXPONENT), min 1. */
    const val COST_EXPONENT = 1.4

    /** Gold price = soulCost x GOLD_PER_SOUL x (1 + rebirths). */
    const val GOLD_PER_SOUL = 25_000L

    /** At most this share of a talent's levels may be bought with gold (goldLevels x 2 <= level). */
    const val MAX_GOLD_SHARE = 0.5
}

enum class TalentBranch { PROGRESS, COMBAT, LOOT }

/**
 * One talent. [perLevel] is the effect per level (meaning depends on the talent, see [PrestigeBonuses]);
 * [baseCost] feeds [PrestigeConfig.COST_EXPONENT].
 */
data class Talent(
    val id: String,
    val branch: TalentBranch,
    val maxLevel: Int,
    val perLevel: Double,
    val baseCost: Int,
)

object Talents {
    const val XP = "xp"
    const val GOLD = "gold"
    const val START_LEVEL = "start_level"
    const val REST = "rest"
    const val DAMAGE = "damage"
    const val SPEED = "speed"
    const val HP = "hp"
    const val CRIT = "crit"
    const val DROP = "drop"
    const val SLOTS = "slots"
    const val CLICK = "click"
    const val COMBO = "combo"

    /** 12 talents, 3 branches. Effects: XP/gold/damage/speed/hp/drop/click/combo +x per level, start level +levels, rest -x time, crit +x multiplier, slots +x slots. */
    val catalog: List<Talent> = listOf(
        Talent(XP, TalentBranch.PROGRESS, 20, 0.05, 1),
        Talent(GOLD, TalentBranch.PROGRESS, 20, 0.05, 1),
        Talent(START_LEVEL, TalentBranch.PROGRESS, 10, 2.0, 2),
        Talent(REST, TalentBranch.PROGRESS, 10, 0.05, 1),
        Talent(DAMAGE, TalentBranch.COMBAT, 30, 0.03, 1),
        Talent(SPEED, TalentBranch.COMBAT, 15, 0.02, 2),
        Talent(HP, TalentBranch.COMBAT, 20, 0.04, 1),
        Talent(CRIT, TalentBranch.COMBAT, 20, 0.05, 2),
        Talent(DROP, TalentBranch.LOOT, 10, 0.05, 2),
        Talent(SLOTS, TalentBranch.LOOT, 10, 2.0, 2),
        Talent(CLICK, TalentBranch.LOOT, 20, 0.05, 1),
        Talent(COMBO, TalentBranch.LOOT, 10, 0.05, 1),
    )
    private val byId = catalog.associateBy { it.id }

    fun byId(id: String): Talent? = byId[id]
}

/** Why [Prestige.buyTalent] works or not. */
enum class BuyResult { OK, UNKNOWN, MAX_LEVEL, NOT_ENOUGH_SOULS, NOT_ENOUGH_GOLD, GOLD_SHARE_REACHED }

enum class PayWith { SOULS, GOLD }

/** Progress towards the rebirth gate (for the locked card). */
data class GateProgress(val level: Int, val zoneOrder: Int) {
    val levelDone: Boolean get() = level >= PrestigeConfig.GATE_LEVEL
    val zoneDone: Boolean get() = zoneOrder >= PrestigeConfig.GATE_ZONE_ORDER
    val open: Boolean get() = levelDone && zoneDone
}

/**
 * Rebirth (prestige): pure functions on [GameState], time injected. Every function returns the same
 * instance when it changes nothing, and the full new state otherwise - call inside ONE
 * `repository.update { ... }` so a rebirth is atomic.
 */
object Prestige {

    /** Highest zone order the hero reached in this run (highest unlocked zone, current zone included). */
    fun runZoneOrder(state: GameState): Int {
        val ids = state.world.unlockedZoneIds + state.world.currentZoneId
        return ids.maxOf { id -> Zones.all.firstOrNull { it.id == id }?.order ?: 1 }
    }

    fun gate(state: GameState) = GateProgress(state.character.level, runZoneOrder(state))

    fun isVisible(state: GameState): Boolean =
        state.prestige.rebirths > 0 || maxOf(state.character.level, state.prestige.bestLevel) >= PrestigeConfig.VISIBLE_LEVEL

    fun canRebirth(state: GameState): Boolean = gate(state).open

    /** Pure soul formula; monotone in both arguments, bounded (level <= 999, zone <= 100). */
    fun souls(level: Int, zoneOrder: Int): Long {
        val l = level.coerceIn(0, Progression.MAX_LEVEL).toDouble()
        val z = zoneOrder.coerceIn(0, Zones.COUNT).toDouble()
        val v = (l / PrestigeConfig.LEVEL_DIVISOR).pow(PrestigeConfig.LEVEL_EXPONENT) +
            z.pow(PrestigeConfig.ZONE_EXPONENT) / PrestigeConfig.ZONE_DIVISOR
        return floor(v * PrestigeConfig.SOUL_SCALE).toLong()
    }

    /** Souls the rebirth would pay out right now (0 when locked). Equals the payout of [rebirth]. */
    fun soulsPreview(state: GameState): Long =
        if (canRebirth(state)) souls(state.character.level, runZoneOrder(state)) else 0L

    /** Level the next run starts at (1 + start-level talent). */
    fun startLevel(prestige: PrestigeState): Int =
        (1 + PrestigeBonuses.startLevelBonus(prestige)).coerceAtMost(Progression.MAX_LEVEL)

    /** Keeps [PrestigeState.bestZoneOrder]/[PrestigeState.bestLevel] up to date. Call after level-ups / zone unlocks. */
    fun trackBest(state: GameState): GameState {
        val p = state.prestige
        val lvl = maxOf(p.bestLevel, state.character.level.coerceIn(0, Progression.MAX_LEVEL))
        val zone = maxOf(p.bestZoneOrder, runZoneOrder(state).coerceIn(0, Zones.COUNT))
        if (lvl == p.bestLevel && zone == p.bestZoneOrder) return state
        return state.copy(prestige = p.copy(bestLevel = lvl, bestZoneOrder = zone))
    }

    /**
     * Rebirth. Resets character/equipment/gold/shop/world/buffs, hands out a fresh starter dagger, starts at
     * the talent start level, restarts goal baselines. Keeps progress (daily/goals), stats, prestige (+souls),
     * [GameState.inventoryExtraSlots]. [nowEpochMillis] becomes the new last-active time. Locked -> same instance.
     */
    fun rebirth(state: GameState, nowEpochMillis: Long): GameState {
        if (!canRebirth(state)) return state
        val tracked = trackBest(state)
        val gained = souls(state.character.level, runZoneOrder(state))
        val p = tracked.prestige
        val prestige = p.copy(rebirths = (p.rebirths + 1).coerceAtMost(Int.MAX_VALUE), souls = saturatingAdd(p.souls, gained))
        val level = startLevel(prestige)
        val character = CharacterState(
            level = level,
            unspentPoints = (level - 1) * Progression.STAT_POINTS_PER_LEVEL,
        )
        val fresh = tracked.copy(
            character = character,
            equipment = EquipmentState(),
            gold = 0,
            starterWeaponGranted = false,
            world = WorldState(lastActiveEpochMillis = nowEpochMillis),
            shop = ShopState(),
            buffs = BuffState(),
            prestige = prestige,
        )
        return rebaselineGoals(StarterItems.ensureStarterWeapon(fresh))
    }

    /** Unclaimed goals count from the new counters (kills/zones/... were reset). */
    private fun rebaselineGoals(state: GameState): GameState {
        val goals = state.progress.goals.map { g: GoalState ->
            if (g.claimed) g else GoalTypes.counter(state, g.type)?.let { g.copy(baseline = it) } ?: g
        }
        return state.copy(progress = state.progress.copy(goals = goals))
    }

    // --- Talents ---

    fun level(prestige: PrestigeState, id: String): Int =
        Talents.byId(id)?.let { (prestige.talents[id] ?: 0).coerceIn(0, it.maxLevel) } ?: 0

    fun goldLevels(prestige: PrestigeState, id: String): Int = (prestige.goldTalentLevels[id] ?: 0).coerceAtLeast(0)

    /** Soul cost of the next level of [talent] at current [level]. */
    fun soulCost(talent: Talent, level: Int): Long =
        (talent.baseCost * (1.0 + level.coerceAtLeast(0)).pow(PrestigeConfig.COST_EXPONENT)).roundToLong().coerceAtLeast(1L)

    /** Gold price of the next level: soulCost x GOLD_PER_SOUL x (1 + rebirths). */
    fun goldCost(talent: Talent, level: Int, rebirths: Int): Long {
        val per = soulCost(talent, level) * PrestigeConfig.GOLD_PER_SOUL
        val mult = 1L + rebirths.coerceAtLeast(0)
        return if (per > Long.MAX_VALUE / mult) Long.MAX_VALUE else per * mult
    }

    /** Whether one more level of [id] may be paid with gold (50 % rule: goldLevels x 2 <= level after purchase). */
    fun goldShareAllows(prestige: PrestigeState, id: String): Boolean {
        val after = level(prestige, id) + 1
        return (goldLevels(prestige, id) + 1).toDouble() <= after * PrestigeConfig.MAX_GOLD_SHARE
    }

    fun check(state: GameState, id: String, with: PayWith): BuyResult {
        val t = Talents.byId(id) ?: return BuyResult.UNKNOWN
        val p = state.prestige
        val lvl = level(p, id)
        if (lvl >= t.maxLevel) return BuyResult.MAX_LEVEL
        return when (with) {
            PayWith.SOULS -> if (p.souls >= soulCost(t, lvl)) BuyResult.OK else BuyResult.NOT_ENOUGH_SOULS
            PayWith.GOLD -> when {
                !goldShareAllows(p, id) -> BuyResult.GOLD_SHARE_REACHED
                state.gold < goldCost(t, lvl, p.rebirths) -> BuyResult.NOT_ENOUGH_GOLD
                else -> BuyResult.OK
            }
        }
    }

    /** Buys one level of talent [id]; unchanged state (same instance) when [check] is not OK. */
    fun buyTalent(state: GameState, id: String, with: PayWith): GameState {
        if (check(state, id, with) != BuyResult.OK) return state
        val t = Talents.byId(id)!!
        val p = state.prestige
        val lvl = level(p, id)
        val talents = p.talents + (id to lvl + 1)
        return when (with) {
            PayWith.SOULS -> {
                val cost = soulCost(t, lvl)
                state.copy(prestige = p.copy(souls = p.souls - cost, soulsSpentTotal = saturatingAdd(p.soulsSpentTotal, cost), talents = talents))
            }
            PayWith.GOLD -> state.copy(
                gold = state.gold - goldCost(t, lvl, p.rebirths),
                prestige = p.copy(talents = talents, goldTalentLevels = p.goldTalentLevels + (id to goldLevels(p, id) + 1)),
            )
        }
    }

    /** Free respec: all talents cleared, all spent souls refunded (gold paid is not refunded). */
    fun respec(state: GameState): GameState {
        val p = state.prestige
        if (p.talents.isEmpty() && p.goldTalentLevels.isEmpty() && p.soulsSpentTotal == 0L) return state
        return state.copy(
            prestige = p.copy(
                souls = saturatingAdd(p.souls, p.soulsSpentTotal),
                soulsSpentTotal = 0,
                talents = emptyMap(),
                goldTalentLevels = emptyMap(),
            ),
        )
    }

    /** True when at least one talent can be bought with souls (HUD hint dot). */
    fun hasSpendableSouls(state: GameState): Boolean =
        Talents.catalog.any { check(state, it.id, PayWith.SOULS) == BuyResult.OK }

    private fun saturatingAdd(a: Long, b: Long): Long {
        val r = a + b
        return if (r < a) Long.MAX_VALUE else r
    }
}
