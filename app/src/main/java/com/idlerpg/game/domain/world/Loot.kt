package com.idlerpg.game.domain.world

import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.equipment.Inventory
import com.idlerpg.game.domain.equipment.ItemGenerator
import kotlin.math.floor
import kotlin.random.Random

/** An item that dropped and where it went. */
data class LootDrop(
    val item: Item,
    /** True if it landed in the inventory; false = inventory was full and it was auto-sold. */
    val added: Boolean,
    val autoSoldGold: Long,
)

/**
 * Item drops from monsters (uses the Ausrüstung API [ItemGenerator] / [Inventory]).
 *
 * - Regular monsters: [dropChance] = 8 % + 0.2 % per luck point, max 16 %; item level = monster level.
 * - Bosses: guaranteed drop, at least [Rarity.RARE] with rarity boost 1.0.
 * - Offline: expected number of drops for the counted kills, at most [MAX_OFFLINE_ITEMS].
 */
object Loot {
    const val BASE_DROP_CHANCE = 0.08
    const val DROP_CHANCE_PER_LUCK = 0.002
    const val MAX_DROP_CHANCE = 0.16
    const val MAX_OFFLINE_ITEMS = 20
    const val BOSS_RARITY_BOOST = 1.0

    fun dropChance(luck: Int): Double =
        (BASE_DROP_CHANCE + luck.coerceAtLeast(0) * DROP_CHANCE_PER_LUCK).coerceAtMost(MAX_DROP_CHANCE)

    /** Rolls the drop of a regular monster of [monsterLevel]; null = nothing dropped. */
    fun rollDrop(monsterLevel: Int, luck: Int, random: Random = Random.Default): Item? =
        if (random.nextDouble() < dropChance(luck)) ItemGenerator.roll(monsterLevel, random) else null

    /** Guaranteed boss drop. */
    fun bossDrop(bossLevel: Int, random: Random = Random.Default): Item =
        ItemGenerator.roll(bossLevel, random, minRarity = Rarity.RARE, rarityBoost = BOSS_RARITY_BOOST)

    /**
     * Number of item drops for [kills] offline kills: exact binomial draw for small kill counts,
     * otherwise the expected value with a random rounding of the fraction. Capped at [MAX_OFFLINE_ITEMS].
     */
    fun offlineDropCount(kills: Long, chance: Double, random: Random = Random.Default): Int {
        if (kills <= 0 || chance <= 0.0) return 0
        val count = if (kills <= 400) {
            var n = 0
            repeat(kills.toInt()) { if (random.nextDouble() < chance) n++ }
            n.toLong()
        } else {
            val expected = kills * chance
            val whole = floor(expected).toLong()
            whole + if (random.nextDouble() < expected - whole) 1 else 0
        }
        return count.coerceAtMost(MAX_OFFLINE_ITEMS.toLong()).toInt()
    }

    /** Puts [item] into the inventory (auto-sold if full). */
    fun grant(state: GameState, item: Item): Pair<GameState, LootDrop> {
        val r = Inventory.tryAdd(state, item)
        return r.state to LootDrop(item, r.added, r.autoSoldGold)
    }
}
