package com.idlerpg.game.domain.world

import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.equipment.Inventory
import com.idlerpg.game.domain.equipment.ItemGenerator
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.random.Random

/** What the hero achieved while the app was closed. */
data class OfflineReward(
    /** Real time away. */
    val awayMillis: Long,
    /** Time that was actually counted (capped at [OfflineProgress.MAX_OFFLINE_MILLIS]). */
    val countedMillis: Long,
    val kills: Long,
    val xp: Long,
    val gold: Long,
    val levelsGained: Int,
    /** Dropped items (at most [Loot.MAX_OFFLINE_ITEMS]); some may be auto-sold, see [autoSoldItems]. */
    val items: List<Item> = emptyList(),
    /** How many of [items] did not fit into the inventory and were sold automatically. */
    val autoSoldItems: Int = 0,
    /** Gold credited for [autoSoldItems] (on top of [gold]). */
    val autoSoldGold: Long = 0,
    /** True if the hero would not survive an average fight in the current zone (earns nothing). */
    val heroTooWeak: Boolean = false,
) {
    /** True if the time away exceeded [OfflineProgress.MAX_OFFLINE_MILLIS] and was cut off. */
    val capped: Boolean get() = awayMillis > countedMillis

    /** All gold credited: kill gold plus auto-sold items. */
    val totalGold: Long get() = gold + autoSoldGold
}

/**
 * Estimates kills for the time away: average hero DPS vs. average monster HP of the current zone
 * (monster level = hero level clamped to the zone range), plus walk-in/death time per kill,
 * at [EFFICIENCY] of the online rate and capped at [MAX_OFFLINE_MILLIS]. A hero who would not
 * survive a single average fight earns nothing. Item drops follow [Loot.dropChance].
 *
 * Balance (BalanceTest, `build/balance.txt`): online the reference hero earns roughly 2–3.5× the
 * price of the shop's complete assortment per hour (Shop markup on ItemGenerator.sellPrice). A full
 * night offline ([MAX_OFFLINE_MILLIS]) must buy some but not all shop offers of the current level,
 * so offline the hero only gets about one minute of fighting per hour away ([EFFICIENCY] = 1/60):
 * a night ≈ 25–45 % of the assortment, ≈ 1.5–3 levels and 6–14 items. If online gold or shop
 * prices are rebalanced, re-tune [EFFICIENCY] with the BalanceTest band.
 */
object OfflineProgress {
    /** Longest absence that counts (a full night). */
    const val MAX_OFFLINE_MILLIS = 8L * 60 * 60 * 1000
    const val MIN_OFFLINE_MILLIS = 60_000L

    /** Share of the online kill rate achieved while away: about one minute of fighting per hour. */
    const val EFFICIENCY = 1.0 / 60.0

    /** Returns null when there is nothing to grant (first start, clock moved back, < 1 minute). */
    fun calculate(state: GameState, nowMillis: Long, random: Random = Random.Default): OfflineReward? {
        val last = state.world.lastActiveEpochMillis
        if (last <= 0L) return null
        val away = nowMillis - last
        if (away < MIN_OFFLINE_MILLIS) return null
        val counted = away.coerceAtMost(MAX_OFFLINE_MILLIS)

        val hero = CombatStats.of(state)
        val zone = Zones.byId(state.world.currentZoneId)
        val level = state.character.level.coerceIn(zone.minLevel, zone.maxLevel)
        val avg = MonsterScaling.average(zone, level)
        val fightSeconds = avg.hp / hero.dps
        val survives = fightSeconds * avg.dps < hero.maxHp
        if (!survives) return OfflineReward(away, counted, 0, 0, 0, 0, heroTooWeak = true)
        val secondsPerKill = fightSeconds + CombatRules.TRAVEL_MILLIS / 1000.0
        val kills = floor(counted / 1000.0 / secondsPerKill * EFFICIENCY).toLong()
        if (kills <= 0) return OfflineReward(away, counted, 0, 0, 0, 0)

        val xp = (kills * avg.xp).roundToLong()
        val gold = (kills * avg.gold).roundToLong()
        val levels = Progression.applyXp(state.character, xp).levelsGained

        val dropCount = Loot.offlineDropCount(kills, Loot.dropChance(hero.totalStats.luck), random)
        val items = List(dropCount) { ItemGenerator.roll(level, random) }
        val free = (Inventory.CAPACITY - state.equipment.inventory.size).coerceAtLeast(0)
        val sold = items.drop(free)
        return OfflineReward(
            away, counted, kills, xp, gold, levels,
            items = items,
            autoSoldItems = sold.size,
            autoSoldGold = sold.sumOf { it.price.coerceAtLeast(0) },
        )
    }

    /**
     * Applies [reward] (may be null): kill rewards, items (inventory or auto-sold), offline HP regen
     * for the time away, and stamps [nowMillis] as last-active time.
     */
    fun apply(state: GameState, reward: OfflineReward?, nowMillis: Long): GameState {
        val healed = CombatRules.healOffline(state, nowMillis)
        if (reward == null || reward.kills <= 0) {
            return healed.copy(world = healed.world.copy(lastActiveEpochMillis = nowMillis))
        }
        var s = CombatRules.applyKillReward(
            healed, reward.xp, reward.gold, healed.world.currentZoneId, nowMillis, reward.kills,
        )
        reward.items.forEach { s = Inventory.tryAdd(s, it).state }
        return s
    }

}
