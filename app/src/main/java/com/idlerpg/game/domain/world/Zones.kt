package com.idlerpg.game.domain.world

/**
 * One region of the world. Zones are ordered by [order]; later zones scale monsters up
 * via the multipliers. Display names live in `strings_spielwelt.xml` (mapped by [id]).
 * The next zone is unlocked by defeating this zone's [boss] (see [WorldProgress]).
 *
 * @property minLevel lowest monster level in this zone
 * @property maxLevel highest monster level in this zone (also the boss level)
 * @property monsters regular monsters spawning here
 * @property boss boss monster, challengeable after [WorldProgress.BOSS_KILLS_REQUIRED] kills
 */
data class Zone(
    val id: String,
    val order: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val monsters: List<MonsterType>,
    val boss: MonsterType,
    val hpMultiplier: Double = 1.0,
    val damageMultiplier: Double = 1.0,
    val rewardMultiplier: Double = 1.0,
) {
    init {
        require(monsters.isNotEmpty()) { "zone $id needs monsters" }
        require(monsters.none { it.isBoss }) { "zone $id: bosses can't spawn as regular monsters" }
        require(boss.isBoss) { "zone $id: ${boss.id} is no boss" }
        require(minLevel in 1..maxLevel) { "invalid level range in $id" }
    }
}

/**
 * Zone catalog in progression order.
 *
 * Balance (see BalanceTest / build/balance.txt): with the drops of the previous zone a hero at the
 * zone's min level needs ~6 s per kill and loses ~25 % HP per fight; with the zone's own drops ~3.5 s
 * and ~10 %. Monster damage already grows per level (base damage + faster attacks), so the damage
 * multipliers of later zones are deliberately ≤ 1 — hero HP only grows linearly.
 */
object Zones {
    const val WIESE_ID = "wiese"
    const val WALD_ID = "wald"
    const val SUMPF_ID = "sumpf"
    const val GEBIRGE_ID = "gebirge"
    const val HOEHLE_ID = "hoehle"

    val WIESE = Zone(
        id = WIESE_ID, order = 1, minLevel = 1, maxLevel = 5,
        monsters = listOf(MonsterType.SLIME, MonsterType.GIANT_RAT, MonsterType.BOAR),
        boss = MonsterType.SLIME_KING,
    )

    val WALD = Zone(
        id = WALD_ID, order = 2, minLevel = 5, maxLevel = 12,
        monsters = listOf(MonsterType.WOLF, MonsterType.FOREST_SPIDER, MonsterType.MUSHROOM),
        boss = MonsterType.TREANT,
        hpMultiplier = 1.9, damageMultiplier = 1.5, rewardMultiplier = 1.25,
    )

    val SUMPF = Zone(
        id = SUMPF_ID, order = 3, minLevel = 12, maxLevel = 20,
        monsters = listOf(MonsterType.TOXIC_SLIME, MonsterType.MIRE_TOAD, MonsterType.WISP),
        boss = MonsterType.HYDRA,
        hpMultiplier = 2.85, damageMultiplier = 1.2, rewardMultiplier = 1.5,
    )

    val GEBIRGE = Zone(
        id = GEBIRGE_ID, order = 4, minLevel = 20, maxLevel = 30,
        monsters = listOf(MonsterType.STONE_GOLEM, MonsterType.SNOW_WOLF, MonsterType.ICE_WISP),
        boss = MonsterType.YETI,
        hpMultiplier = 3.35, damageMultiplier = 0.85, rewardMultiplier = 1.8,
    )

    val HOEHLE = Zone(
        id = HOEHLE_ID, order = 5, minLevel = 30, maxLevel = 40,
        monsters = listOf(MonsterType.BAT, MonsterType.CAVE_SPIDER, MonsterType.CRYSTAL_GOLEM),
        boss = MonsterType.DRAGON,
        hpMultiplier = 4.55, damageMultiplier = 0.75, rewardMultiplier = 2.2,
    )

    val all: List<Zone> = listOf(WIESE, WALD, SUMPF, GEBIRGE, HOEHLE)

    /** Zone with [id], falling back to the first zone for unknown ids (e.g. old/edited saves). */
    fun byId(id: String?): Zone = all.firstOrNull { it.id == id } ?: all.first()

    /** The zone after [zone] in progression order, or null for the last zone. */
    fun next(zone: Zone): Zone? = all.firstOrNull { it.order == zone.order + 1 }

    /** The zone before [zone], or null for the first zone. */
    fun previous(zone: Zone): Zone? = all.firstOrNull { it.order == zone.order - 1 }
}
