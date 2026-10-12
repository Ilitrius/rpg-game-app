package com.idlerpg.game.domain.character

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Stats
import com.idlerpg.game.domain.equipment.ItemBaseTypes

/**
 * What the hero does with the weapon in one attack cycle. The combat loop (Spielwelt) picks the
 * action from the monster's behavior:
 *
 * | Monster            | Action   | Idea                                              |
 * |--------------------|----------|---------------------------------------------------|
 * | normal             | [SWING]  | solid standard slash                              |
 * | staggered / open   | [STAB]   | precise thrust, high crit bonus                   |
 * | guarding           | [STRIKE] | heavy overhead blow that breaks the guard         |
 * | winds up an attack | [PARRY]  | blocks the incoming hit, deals no damage          |
 *
 * How strong each action is depends on the weapon ([WeaponActions.modifier]); parrying success
 * depends on dexterity/luck ([WeaponActions.parryChance]).
 */
enum class WeaponAction {
    /** Horizontal/diagonal slash. */
    SWING,

    /** Straight thrust at the center. */
    STAB,

    /** Heavy overhead blow. */
    STRIKE,

    /** Defensive block + deflect. Deals no damage. */
    PARRY,
}

/**
 * Mechanical weapon type, independent of the graphics (the UI's `WeaponKind` additionally
 * distinguishes the rusty starter dagger, which fights like a [DAGGER]).
 */
enum class WeaponType {
    /** No weapon equipped. */
    FIST,
    DAGGER,
    SHORT_SWORD,
    MACE,
    AXE,
}

/**
 * Maps an (equipped) item to its [WeaponType]:
 * - `null` or a non-weapon item → [WeaponType.FIST]
 * - starter dagger / legacy weapon without base type → [WeaponType.DAGGER]
 * - dolch/kurzschwert/streitkolben/axt → DAGGER/SHORT_SWORD/MACE/AXE
 * - unknown base type on a weapon → [WeaponType.DAGGER]
 */
fun weaponTypeOf(item: Item?): WeaponType {
    if (item == null || item.slot != EquipmentSlot.WEAPON) return WeaponType.FIST
    if (item.id == StarterItems.RUSTY_DAGGER_ID || item.baseType.isBlank()) return WeaponType.DAGGER
    return when (item.baseType) {
        ItemBaseTypes.DAGGER -> WeaponType.DAGGER
        ItemBaseTypes.SHORT_SWORD -> WeaponType.SHORT_SWORD
        ItemBaseTypes.MACE -> WeaponType.MACE
        ItemBaseTypes.AXE -> WeaponType.AXE
        else -> WeaponType.DAGGER
    }
}

/**
 * Effect of one [WeaponAction] with one [WeaponType].
 *
 * @property damageMultiplier factor on the rolled base damage (0 for [WeaponAction.PARRY])
 * @property critChanceBonus added to the crit chance (absolute, 0.10 = +10 percentage points)
 * @property critMultiplierBonus added to the crit multiplier (0.25 → 1.5× becomes 1.75×)
 */
data class ActionModifier(
    val damageMultiplier: Double,
    val critChanceBonus: Double = 0.0,
    val critMultiplierBonus: Double = 0.0,
) {
    /** Expected damage factor relative to a plain hit, for ratings/comparisons. */
    fun expectedFactor(critChance: Double, critMultiplier: Double): Double {
        val c = (critChance + critChanceBonus).coerceIn(0.0, WeaponActions.MAX_ACTION_CRIT_CHANCE)
        val m = critMultiplier + critMultiplierBonus
        return damageMultiplier * (1.0 + c * (m - 1.0))
    }
}

/**
 * Weapon affinities and parry formula (pure, unit-tested).
 *
 * Affinity table (damage × / crit chance + / crit multiplier +):
 *
 * | Type        | SWING            | STAB                 | STRIKE              |
 * |-------------|------------------|----------------------|---------------------|
 * | FIST        | 0.80             | 0.85                 | 0.90                |
 * | DAGGER      | 0.85             | 1.15 / +12 % / +0.30 | 0.75                |
 * | SHORT_SWORD | 1.05             | 1.05 / +6 % / +0.15  | 1.00                |
 * | MACE        | 0.95             | 0.70                 | 1.30 / +2 % / +0.20 |
 * | AXE         | 1.25 / +4 %      | 0.70                 | 1.10 / +0 % / +0.10 |
 *
 * PARRY deals no damage (multiplier 0) for every type; how well a type parries is in
 * [parryBonus] / [parryChance].
 */
object WeaponActions {

    /** Crit chance after action bonuses is capped here (above [CombatStats.MAX_CRIT_CHANCE]). */
    const val MAX_ACTION_CRIT_CHANCE = 0.75

    const val BASE_PARRY_CHANCE = 0.35
    const val PARRY_PER_DEX = 0.01
    const val PARRY_PER_LUCK = 0.0025
    const val MIN_PARRY_CHANCE = 0.05
    const val MAX_PARRY_CHANCE = 0.85

    private val PARRY = ActionModifier(0.0)

    /** Affinity of [type] for [action] (see table in the class KDoc). */
    fun modifier(type: WeaponType, action: WeaponAction): ActionModifier = when (action) {
        WeaponAction.PARRY -> PARRY
        WeaponAction.SWING -> when (type) {
            WeaponType.FIST -> ActionModifier(0.80)
            WeaponType.DAGGER -> ActionModifier(0.85)
            WeaponType.SHORT_SWORD -> ActionModifier(1.05)
            WeaponType.MACE -> ActionModifier(0.95)
            WeaponType.AXE -> ActionModifier(1.25, critChanceBonus = 0.04)
        }
        WeaponAction.STAB -> when (type) {
            WeaponType.FIST -> ActionModifier(0.85)
            WeaponType.DAGGER -> ActionModifier(1.15, critChanceBonus = 0.12, critMultiplierBonus = 0.30)
            WeaponType.SHORT_SWORD -> ActionModifier(1.05, critChanceBonus = 0.06, critMultiplierBonus = 0.15)
            WeaponType.MACE -> ActionModifier(0.70)
            WeaponType.AXE -> ActionModifier(0.70)
        }
        WeaponAction.STRIKE -> when (type) {
            WeaponType.FIST -> ActionModifier(0.90)
            WeaponType.DAGGER -> ActionModifier(0.75)
            WeaponType.SHORT_SWORD -> ActionModifier(1.00)
            WeaponType.MACE -> ActionModifier(1.30, critChanceBonus = 0.02, critMultiplierBonus = 0.20)
            WeaponType.AXE -> ActionModifier(1.10, critMultiplierBonus = 0.10)
        }
    }

    /** Parry bonus of a weapon type: dagger/sword +5 %, fist (armored forearm) ±0, mace/axe −5 %. */
    fun parryBonus(type: WeaponType): Double = when (type) {
        WeaponType.DAGGER, WeaponType.SHORT_SWORD -> 0.05
        WeaponType.FIST -> 0.0
        WeaponType.MACE, WeaponType.AXE -> -0.05
    }

    /**
     * Chance that a parry succeeds:
     * `35 % + dexterity × 1 % + luck × 0.25 % + parryBonus(type)`, clamped to 5 %..85 %
     * (negative stats count as 0). Uses the total stats (base + equipment).
     */
    fun parryChance(totalStats: Stats, type: WeaponType): Double {
        val dex = totalStats.dexterity.coerceAtLeast(0)
        val luck = totalStats.luck.coerceAtLeast(0)
        return (BASE_PARRY_CHANCE + dex * PARRY_PER_DEX + luck * PARRY_PER_LUCK + parryBonus(type))
            .coerceIn(MIN_PARRY_CHANCE, MAX_PARRY_CHANCE)
    }

    /** The action a weapon type naturally prefers when the monster does nothing special. */
    fun signatureAction(type: WeaponType): WeaponAction = when (type) {
        WeaponType.DAGGER -> WeaponAction.STAB
        WeaponType.SHORT_SWORD, WeaponType.AXE, WeaponType.FIST -> WeaponAction.SWING
        WeaponType.MACE -> WeaponAction.STRIKE
    }
}
