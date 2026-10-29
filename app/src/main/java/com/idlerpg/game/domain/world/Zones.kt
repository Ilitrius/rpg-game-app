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
    /** Gold multiplier; defaults to [rewardMultiplier] (the handmade zones), generated zones scale gold steeper than XP. */
    val goldMultiplier: Double = rewardMultiplier,
    /** Biome of generated zones (6..100); null for the five handmade zones. */
    val biome: Biome? = null,
    /** Escalation inside the biome, 0..[Biome.STAGES]-1 (0 for the handmade zones). */
    val stage: Int = 0,
) {
    /** 0 (first zone) .. 1 (last zone): how fearsome this zone looks; drives monster/scene detail. */
    val threat: Float get() = ((order - 1) / (Zones.COUNT - 1).toFloat()).coerceIn(0f, 1f)

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

    /** Total number of zones. */
    const val COUNT = 100

    private val HANDMADE = listOf(WIESE, WALD, SUMPF, GEBIRGE, HOEHLE)

    /** Level span of every generated zone; zone 100 ends at level 990 (hero cap is 999). */
    const val GENERATED_LEVEL_SPAN = 10

    private val GENERATED_ROLES = listOf(MonsterType.GEN_SWARM, MonsterType.GEN_BEAST, MonsterType.GEN_BRUTE)

    /** Builds generated zone number [order] (6..[COUNT]) from biome template + stage. */
    internal fun generated(order: Int): Zone {
        val idx = order - HANDMADE.size - 1
        val biome = Biome.entries[idx / Biome.STAGES]
        val stage = idx % Biome.STAGES
        val min = HOEHLE.maxLevel + idx * GENERATED_LEVEL_SPAN
        return Zone(
            id = "${biome.id}_${stage + 1}", order = order, minLevel = min, maxLevel = min + GENERATED_LEVEL_SPAN,
            monsters = GENERATED_ROLES, boss = MonsterType.GEN_BOSS,
            hpMultiplier = ZoneScaling.hp(order), damageMultiplier = ZoneScaling.damage(order),
            rewardMultiplier = ZoneScaling.reward(order), goldMultiplier = ZoneScaling.gold(order),
            biome = biome, stage = stage,
        )
    }

    val all: List<Zone> = HANDMADE + (HANDMADE.size + 1..COUNT).map { generated(it) }

    val last: Zone get() = all.last()

    private val byIdMap: Map<String, Zone> = all.associateBy { it.id }

    /** Zone with [id], falling back to the first zone for unknown ids (e.g. old/edited saves). */
    fun byId(id: String?): Zone = byIdMap[id] ?: all.first()

    /** The zone after [zone] in progression order, or null for the last zone. */
    fun next(zone: Zone): Zone? = all.getOrNull(zone.order)

    /** The zone before [zone], or null for the first zone. */
    fun previous(zone: Zone): Zone? = all.getOrNull(zone.order - 2)
}
