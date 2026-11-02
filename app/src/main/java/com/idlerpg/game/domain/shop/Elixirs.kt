package com.idlerpg.game.domain.shop

import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.buffs.Buffs
import com.idlerpg.game.domain.world.MonsterScaling
import com.idlerpg.game.domain.world.Zones
import kotlin.math.ceil

/**
 * Elixirs ("Tränke"): gold sink that sells timed buffs. Pure functions, no Android.
 *
 * - Price is derived from the gold of an average kill in the current zone ([averageKillGold]) times
 *   [Elixir.kills] (10-20 kills), so it scales with the zone and is never stored.
 * - [buy] deducts the gold and adds the buff in ONE state transform (use inside one `repository.update`).
 * - Nothing is lost silently: a buy is refused ([Outcome.STRONGER_ACTIVE], [Outcome.MAX_DURATION])
 *   when the purchase would give nothing or part of the paid time would be cut off.
 */
object Elixirs {
    const val MAGNITUDE = 0.25
    const val DURATION_MILLIS = 15L * 60 * 1000

    enum class Elixir(val type: String, val kills: Int) {
        XP(Buffs.XP, 12),
        GOLD(Buffs.GOLD, 15),
        DAMAGE(Buffs.DAMAGE, 12),
        DROP(Buffs.DROP, 18),
        REGEN(Buffs.REGEN, 10),
    }

    enum class Outcome { BOUGHT, NOT_ENOUGH_GOLD, STRONGER_ACTIVE, MAX_DURATION, BUFFS_FULL }

    data class Result(val state: GameState, val outcome: Outcome, val price: Long)

    /** Expected gold of one regular kill in the current zone at the hero's (zone-clamped) level. */
    fun averageKillGold(state: GameState): Double {
        val zone = Zones.byId(state.world.currentZoneId)
        val level = state.character.level.coerceIn(zone.minLevel, zone.maxLevel)
        val total = zone.monsters.sumOf { it.spawnWeight }.coerceAtLeast(1)
        return zone.monsters.sumOf { MonsterScaling.stats(it, zone, level).gold.toDouble() * it.spawnWeight } / total
    }

    fun price(state: GameState, elixir: Elixir): Long =
        ceil(averageKillGold(state) * elixir.kills).toLong().coerceAtLeast(1)

    /** Why [buy] would refuse (without checking gold), or null. */
    fun blocker(state: GameState, elixir: Elixir): Outcome? {
        val active = state.buffs.active.filter { it.remainingMillis > 0 && Buffs.isKnown(it.type) }
        val old = active.firstOrNull { it.type == elixir.type }
        return when {
            old == null -> if (active.size >= Buffs.MAX_BUFFS) Outcome.BUFFS_FULL else null
            old.magnitude > MAGNITUDE + 1e-9 -> Outcome.STRONGER_ACTIVE
            old.magnitude >= MAGNITUDE - 1e-9 && old.remainingMillis + DURATION_MILLIS > Buffs.MAX_DURATION_MILLIS -> Outcome.MAX_DURATION
            else -> null
        }
    }

    fun buy(state: GameState, elixir: Elixir): Result {
        val price = price(state, elixir)
        blocker(state, elixir)?.let { return Result(state, it, price) }
        if (state.gold < price) return Result(state, Outcome.NOT_ENOUGH_GOLD, price)
        val next = Buffs.add(state.copy(gold = state.gold - price), elixir.type, MAGNITUDE, DURATION_MILLIS, Buffs.SOURCE_ELIXIR)
        return Result(next, Outcome.BOUGHT, price)
    }
}
