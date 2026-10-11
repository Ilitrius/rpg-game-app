package com.idlerpg.game.domain.world

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Monster archetypes with their level-1 base values (before zone multipliers and level scaling).
 * Display names live in `strings_spielwelt.xml` (mapped in the UI by [id]).
 *
 * @property spawnWeight relative chance to appear within a zone
 */
enum class MonsterType(
    val id: String,
    val baseHp: Int,
    val baseDamageMin: Int,
    val baseDamageMax: Int,
    val baseAttackIntervalMillis: Long,
    val baseXp: Int,
    val baseGold: Int,
    val spawnWeight: Int,
    val isBoss: Boolean = false,
) {
    // ---- Wiese
    SLIME("schleim", baseHp = 20, baseDamageMin = 1, baseDamageMax = 3, baseAttackIntervalMillis = 1700, baseXp = 6, baseGold = 2, spawnWeight = 4),
    GIANT_RAT("riesenratte", baseHp = 26, baseDamageMin = 2, baseDamageMax = 4, baseAttackIntervalMillis = 1300, baseXp = 8, baseGold = 3, spawnWeight = 3),
    BOAR("wildschwein", baseHp = 36, baseDamageMin = 3, baseDamageMax = 6, baseAttackIntervalMillis = 2100, baseXp = 12, baseGold = 5, spawnWeight = 2),

    // ---- Wald
    WOLF("wolf", baseHp = 30, baseDamageMin = 2, baseDamageMax = 5, baseAttackIntervalMillis = 1300, baseXp = 10, baseGold = 4, spawnWeight = 3),
    FOREST_SPIDER("waldspinne", baseHp = 24, baseDamageMin = 2, baseDamageMax = 4, baseAttackIntervalMillis = 1100, baseXp = 9, baseGold = 3, spawnWeight = 4),
    MUSHROOM("pilzling", baseHp = 38, baseDamageMin = 3, baseDamageMax = 6, baseAttackIntervalMillis = 2000, baseXp = 12, baseGold = 5, spawnWeight = 2),

    // ---- Sumpf
    TOXIC_SLIME("giftschleim", baseHp = 28, baseDamageMin = 2, baseDamageMax = 5, baseAttackIntervalMillis = 1600, baseXp = 10, baseGold = 4, spawnWeight = 4),
    MIRE_TOAD("moorkroete", baseHp = 40, baseDamageMin = 3, baseDamageMax = 7, baseAttackIntervalMillis = 2100, baseXp = 13, baseGold = 5, spawnWeight = 2),
    WISP("irrlicht", baseHp = 20, baseDamageMin = 3, baseDamageMax = 5, baseAttackIntervalMillis = 1300, baseXp = 11, baseGold = 4, spawnWeight = 3),

    // ---- Gebirge
    STONE_GOLEM("steingolem", baseHp = 48, baseDamageMin = 4, baseDamageMax = 8, baseAttackIntervalMillis = 2400, baseXp = 15, baseGold = 6, spawnWeight = 2),
    SNOW_WOLF("schneewolf", baseHp = 32, baseDamageMin = 3, baseDamageMax = 5, baseAttackIntervalMillis = 1250, baseXp = 11, baseGold = 4, spawnWeight = 3),
    ICE_WISP("eisgeist", baseHp = 24, baseDamageMin = 3, baseDamageMax = 6, baseAttackIntervalMillis = 1350, baseXp = 12, baseGold = 5, spawnWeight = 3),

    // ---- Höhle
    BAT("fledermaus", baseHp = 22, baseDamageMin = 2, baseDamageMax = 4, baseAttackIntervalMillis = 950, baseXp = 9, baseGold = 4, spawnWeight = 4),
    CAVE_SPIDER("hoehlenspinne", baseHp = 30, baseDamageMin = 3, baseDamageMax = 5, baseAttackIntervalMillis = 1100, baseXp = 11, baseGold = 5, spawnWeight = 3),
    CRYSTAL_GOLEM("kristallgolem", baseHp = 52, baseDamageMin = 4, baseDamageMax = 9, baseAttackIntervalMillis = 2300, baseXp = 16, baseGold = 7, spawnWeight = 2),

    // ---- Bosses (one per zone, see Zone.boss)
    SLIME_KING("schleimkoenig", baseHp = 180, baseDamageMin = 3, baseDamageMax = 6, baseAttackIntervalMillis = 1600, baseXp = 110, baseGold = 60, spawnWeight = 0, isBoss = true),
    TREANT("baumhueter", baseHp = 143, baseDamageMin = 2, baseDamageMax = 5, baseAttackIntervalMillis = 1900, baseXp = 130, baseGold = 70, spawnWeight = 0, isBoss = true),
    HYDRA("sumpfhydra", baseHp = 150, baseDamageMin = 2, baseDamageMax = 4, baseAttackIntervalMillis = 1500, baseXp = 140, baseGold = 75, spawnWeight = 0, isBoss = true),
    YETI("yeti", baseHp = 178, baseDamageMin = 3, baseDamageMax = 6, baseAttackIntervalMillis = 1800, baseXp = 150, baseGold = 80, spawnWeight = 0, isBoss = true),
    DRAGON("hoehlendrache", baseHp = 173, baseDamageMin = 3, baseDamageMax = 6, baseAttackIntervalMillis = 1700, baseXp = 170, baseGold = 95, spawnWeight = 0, isBoss = true),
}

/** Fully scaled values of one monster at a given zone + level. */
data class MonsterStats(
    val type: MonsterType,
    val level: Int,
    val maxHp: Long,
    val damageMin: Int,
    val damageMax: Int,
    val attackIntervalMillis: Long,
    val xp: Long,
    val gold: Long,
) {
    val averageDamage: Double get() = (damageMin + damageMax) / 2.0

    /** Expected damage per second this monster deals. */
    val dps: Double get() = averageDamage * 1000.0 / attackIntervalMillis

    /** Rolls the damage of one monster attack, uniform in [damageMin]..[damageMax]. */
    fun rollDamage(random: Random = Random.Default): Int = random.nextInt(damageMin, damageMax + 1)

    /** Rolls the gold drop: [gold] ±20 %, at least 1. */
    fun rollGold(random: Random = Random.Default): Long =
        (gold * (0.8 + random.nextDouble() * 0.4)).roundToLong().coerceAtLeast(1)
}

/**
 * Scaling rules for monsters by zone and monster level.
 *
 * - HP: base × (1 + 0.30 × (level − 1)) × zone HP multiplier
 * - Damage: base × (1 + 0.20 × (level − 1)) × zone damage multiplier
 * - Attack interval: base × (1 − 0.02 × (level − 1)), never below 60 % of base
 * - XP: base × (1 + 0.50 × (level − 1)) × zone reward multiplier
 * - Gold: base × (1 + 0.35 × (level − 1)) × zone reward multiplier
 */
object MonsterScaling {
    const val HP_PER_LEVEL = 0.30
    const val DAMAGE_PER_LEVEL = 0.20
    const val INTERVAL_REDUCTION_PER_LEVEL = 0.02
    const val MIN_INTERVAL_FACTOR = 0.60
    const val XP_PER_LEVEL = 0.50
    const val GOLD_PER_LEVEL = 0.35

    fun stats(type: MonsterType, zone: Zone, level: Int): MonsterStats {
        val l = (level.coerceAtLeast(1) - 1).toDouble()
        val hp = (type.baseHp * (1 + HP_PER_LEVEL * l) * zone.hpMultiplier).roundToLong().coerceAtLeast(1)
        val dmgFactor = (1 + DAMAGE_PER_LEVEL * l) * zone.damageMultiplier
        val dMin = (type.baseDamageMin * dmgFactor).roundToLong().toInt().coerceAtLeast(1)
        val dMax = (type.baseDamageMax * dmgFactor).roundToLong().toInt().coerceAtLeast(dMin)
        val interval = (type.baseAttackIntervalMillis *
            (1 - INTERVAL_REDUCTION_PER_LEVEL * l).coerceAtLeast(MIN_INTERVAL_FACTOR)).roundToLong()
        val xp = (type.baseXp * (1 + XP_PER_LEVEL * l) * zone.rewardMultiplier).roundToLong().coerceAtLeast(1)
        val gold = (type.baseGold * (1 + GOLD_PER_LEVEL * l) * zone.rewardMultiplier).roundToLong().coerceAtLeast(1)
        return MonsterStats(type, level.coerceAtLeast(1), hp, dMin, dMax, interval, xp, gold)
    }

    /**
     * Level of a newly spawned monster: the hero's level or one below (random),
     * clamped to the zone's level range.
     */
    fun rollLevel(zone: Zone, heroLevel: Int, random: Random = Random.Default): Int =
        (heroLevel - random.nextInt(0, 2)).coerceIn(zone.minLevel, zone.maxLevel)

    /** Picks a monster type of [zone] according to the spawn weights. */
    fun rollType(zone: Zone, random: Random = Random.Default): MonsterType {
        val total = zone.monsters.sumOf { it.spawnWeight }
        var r = random.nextInt(total)
        for (m in zone.monsters) {
            r -= m.spawnWeight
            if (r < 0) return m
        }
        return zone.monsters.last()
    }

    /** The boss of [zone] at its boss level (the zone's max level). */
    fun boss(zone: Zone): MonsterStats = stats(zone.boss, zone, zone.maxLevel)

    /** Rolls a complete new monster for [zone]. */
    fun spawn(zone: Zone, heroLevel: Int, random: Random = Random.Default): MonsterStats =
        stats(rollType(zone, random), zone, rollLevel(zone, heroLevel, random))

    /** Spawn-weighted average of a zone's monsters at [level] (for estimates like offline progress). */
    fun average(zone: Zone, level: Int): AverageMonster {
        val total = zone.monsters.sumOf { it.spawnWeight }.toDouble()
        var hp = 0.0
        var xp = 0.0
        var gold = 0.0
        var dps = 0.0
        zone.monsters.forEach { type ->
            val s = stats(type, zone, level)
            val w = type.spawnWeight / total
            hp += s.maxHp * w
            xp += s.xp * w
            gold += s.gold * w
            dps += s.dps * w
        }
        return AverageMonster(hp, xp, gold, dps)
    }
}

/** Weighted average values of a zone's monsters. */
data class AverageMonster(val hp: Double, val xp: Double, val gold: Double, val dps: Double = 0.0)
