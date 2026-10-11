package com.idlerpg.game.domain.character

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Stats
import kotlin.math.floor
import kotlin.random.Random

/**
 * Derived combat values of the hero. Computed by [CombatStats.of]; all fields are plain values,
 * so the combat loop can cache an instance and only recompute when [GameState] changes.
 *
 * @property totalStats base stats + bonuses of all equipped items
 * @property damageMin minimum damage of a normal (non-crit) hit, always >= 1
 * @property damageMax maximum damage of a normal hit, always >= [damageMin]
 * @property attacksPerSecond attack speed (hits per second)
 * @property maxHp maximum hit points
 * @property critChance chance per hit for a critical hit, 0.0..[CombatStats.MAX_CRIT_CHANCE]
 * @property critMultiplier damage factor of a critical hit (e.g. 1.5 = +50 %)
 */
data class CombatStats(
    val totalStats: Stats,
    val damageMin: Int,
    val damageMax: Int,
    val attacksPerSecond: Double,
    val maxHp: Int,
    val critChance: Double,
    val critMultiplier: Double,
) {
    /** Average damage of a normal hit. */
    val averageHit: Double get() = (damageMin + damageMax) / 2.0

    /** Expected damage per second including crits — handy for offline progress estimates. */
    val dps: Double get() = averageHit * attacksPerSecond * (1.0 + critChance * (critMultiplier - 1.0))

    /** Milliseconds between two attacks. */
    val attackIntervalMillis: Long get() = (1000.0 / attacksPerSecond).toLong()

    /**
     * Rolls one hit: uniform damage in [damageMin]..[damageMax], then a crit roll.
     * Pass a seeded [random] in tests.
     */
    fun rollHit(random: Random = Random.Default): Hit {
        val base = random.nextInt(damageMin, damageMax + 1)
        val crit = random.nextDouble() < critChance
        val amount = if (crit) floor(base * critMultiplier).toLong().coerceAtLeast(base + 1L) else base.toLong()
        return Hit(amount, crit)
    }

    companion object {
        /** Damage of an unarmed hero (no weapon equipped). */
        const val FIST_DAMAGE_MIN = 1
        const val FIST_DAMAGE_MAX = 2

        /** Each point of strength adds this much flat damage to min and max. */
        const val DAMAGE_PER_STRENGTH = 0.5

        const val BASE_ATTACKS_PER_SECOND = 0.8
        const val ATTACKS_PER_SECOND_PER_DEX = 0.04
        const val MAX_ATTACKS_PER_SECOND = 5.0

        const val BASE_HP = 50
        const val HP_PER_VITALITY = 10
        const val HP_PER_LEVEL = 5

        const val BASE_CRIT_CHANCE = 0.02
        const val CRIT_CHANCE_PER_LUCK = 0.005
        const val MAX_CRIT_CHANCE = 0.5
        const val BASE_CRIT_MULTIPLIER = 1.5
        const val CRIT_MULTIPLIER_PER_LUCK = 0.01
        const val MAX_CRIT_MULTIPLIER = 3.0

        /** Sum of base stats and the bonuses of every equipped item. */
        fun totalStats(base: Stats, equipped: Collection<Item>): Stats =
            equipped.fold(base) { acc, item -> acc + item.bonus }

        /** Sum of the bonuses of every equipped item. */
        fun equipmentBonus(equipped: Collection<Item>): Stats = totalStats(Stats(), equipped)

        /** Convenience: combat values for the hero in [state]. */
        fun of(state: GameState): CombatStats = compute(
            base = state.character.stats,
            level = state.character.level,
            equipped = state.equipment.equipped.values,
        )

        /**
         * Computes all derived values.
         *
         * - Damage: weapon damage (fists 1–2 if none) + strength × 0.5 (rounded down), min 1.
         * - Attacks/sec: 0.8 + dexterity × 0.04, capped at 5.
         * - Max HP: 50 + vitality × 10 + (level − 1) × 5.
         * - Crit chance: 2 % + luck × 0.5 %, capped at 50 %.
         * - Crit multiplier: 1.5 + luck × 0.01, capped at 3.0.
         */
        fun compute(base: Stats, level: Int, equipped: Collection<Item>): CombatStats {
            val total = totalStats(base, equipped)
            val weapon = equipped.firstOrNull { it.slot == EquipmentSlot.WEAPON && it.damageMax > 0 }
            val weaponMin = weapon?.damageMin?.coerceAtLeast(0) ?: FIST_DAMAGE_MIN
            val weaponMax = weapon?.damageMax ?: FIST_DAMAGE_MAX
            val strengthBonus = floor(total.strength.coerceAtLeast(0) * DAMAGE_PER_STRENGTH).toInt()
            val min = (weaponMin + strengthBonus).coerceAtLeast(1)
            val max = (weaponMax + strengthBonus).coerceAtLeast(min)

            val dex = total.dexterity.coerceAtLeast(0)
            val aps = (BASE_ATTACKS_PER_SECOND + dex * ATTACKS_PER_SECOND_PER_DEX).coerceAtMost(MAX_ATTACKS_PER_SECOND)

            val vit = total.vitality.coerceAtLeast(0)
            val hp = BASE_HP + vit * HP_PER_VITALITY + (level.coerceAtLeast(1) - 1) * HP_PER_LEVEL

            val luck = total.luck.coerceAtLeast(0)
            val critChance = (BASE_CRIT_CHANCE + luck * CRIT_CHANCE_PER_LUCK).coerceAtMost(MAX_CRIT_CHANCE)
            val critMult = (BASE_CRIT_MULTIPLIER + luck * CRIT_MULTIPLIER_PER_LUCK).coerceAtMost(MAX_CRIT_MULTIPLIER)

            return CombatStats(total, min, max, aps, hp, critChance, critMult)
        }
    }
}

/** Result of one attack. */
data class Hit(val damage: Long, val critical: Boolean)
