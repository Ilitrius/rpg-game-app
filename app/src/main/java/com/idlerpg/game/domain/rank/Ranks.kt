package com.idlerpg.game.domain.rank

import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.progress.DailyReward
import com.idlerpg.game.domain.world.Zones
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Hero ranks (Squire ... Demigod): bought with gold once the hero (or his best level) reached the rank's level.
 * Every rank permanently raises damage/HP/speed; from rank 4 on also gold, every 2nd rank an inventory slot.
 * Ranks survive a rebirth (a permanent investment, like prestige).
 */
object RankConfig {
    /** Number of ranks; rank 0 = no rank yet. */
    const val MAX_RANK = 10

    /** Hero level needed to buy rank `i + 1` (index `i`). */
    val LEVELS: IntArray = intArrayOf(50, 100, 175, 250, 350, 450, 575, 700, 850, 999)

    const val DAMAGE_PER_RANK = 0.06
    const val HP_PER_RANK = 0.06
    const val SPEED_PER_RANK = 0.02

    /** Gold bonus per rank above [GOLD_FROM_RANK] - 1 (Knight and up). */
    const val GOLD_PER_RANK = 0.02
    const val GOLD_FROM_RANK = 4

    /** Price of rank 1 in regular kills of the zone where its level is reached; grows by [KILLS_GROWTH] per rank. */
    const val BASE_KILLS = 1500.0
    const val KILLS_GROWTH = 1.25
}

/** Permanent bonuses of a rank as multipliers (neutral = 1.0 / 0). Merged into `PrestigeBonuses.of(state)`. */
data class RankBonuses(
    val damage: Double = 1.0,
    val hp: Double = 1.0,
    val speed: Double = 1.0,
    val gold: Double = 1.0,
    val extraSlots: Int = 0,
) {
    companion object {
        val NEUTRAL = RankBonuses()

        fun of(rank: Int): RankBonuses {
            val r = rank.coerceIn(0, RankConfig.MAX_RANK)
            if (r == 0) return NEUTRAL
            return RankBonuses(
                damage = 1.0 + r * RankConfig.DAMAGE_PER_RANK,
                hp = 1.0 + r * RankConfig.HP_PER_RANK,
                speed = 1.0 + r * RankConfig.SPEED_PER_RANK,
                gold = 1.0 + (r - RankConfig.GOLD_FROM_RANK + 1).coerceAtLeast(0) * RankConfig.GOLD_PER_RANK,
                extraSlots = r / 2,
            )
        }

        fun of(state: GameState): RankBonuses = of(state.rank.rank)
    }
}

enum class RankBuyResult { OK, MAX_RANK, LEVEL_TOO_LOW, NOT_ENOUGH_GOLD }

object Ranks {
    /** Current rank, clamped into the valid range (tampered saves cannot exceed the catalog). */
    fun rank(state: GameState): Int = state.rank.rank.coerceIn(0, RankConfig.MAX_RANK)

    /** Hero level that counts for rank gates: the best level ever reached (survives a rebirth). */
    fun heroLevel(state: GameState): Int = maxOf(state.character.level, state.prestige.bestLevel)

    /** Level needed for rank [rank] (1..MAX_RANK). */
    fun requiredLevel(rank: Int): Int = RankConfig.LEVELS[rank.coerceIn(1, RankConfig.MAX_RANK) - 1]

    /** Gold price of rank [rank] (1..MAX_RANK): [RankConfig.BASE_KILLS] x growth^(rank-1) kills at its reference zone. */
    fun price(rank: Int): Long = PRICES[rank.coerceIn(1, RankConfig.MAX_RANK) - 1]

    private val PRICES: LongArray by lazy {
        LongArray(RankConfig.MAX_RANK) { i ->
            val level = RankConfig.LEVELS[i]
            val zone = Zones.all.firstOrNull { it.maxLevel >= level } ?: Zones.all.last()
            val perKill = DailyReward.goldPerKillAt(zone, level)
            (RankConfig.BASE_KILLS * RankConfig.KILLS_GROWTH.pow(i) * perKill).roundToLong().coerceAtLeast(1)
        }
    }

    fun check(state: GameState): RankBuyResult {
        val current = rank(state)
        if (current >= RankConfig.MAX_RANK) return RankBuyResult.MAX_RANK
        val next = current + 1
        return when {
            heroLevel(state) < requiredLevel(next) -> RankBuyResult.LEVEL_TOO_LOW
            state.gold < price(next) -> RankBuyResult.NOT_ENOUGH_GOLD
            else -> RankBuyResult.OK
        }
    }

    /** Buys the next rank; unchanged state (same instance) when [check] is not OK. */
    fun buy(state: GameState): GameState {
        if (check(state) != RankBuyResult.OK) return state
        val next = rank(state) + 1
        return state.copy(gold = state.gold - price(next), rank = state.rank.copy(rank = next))
    }
}
