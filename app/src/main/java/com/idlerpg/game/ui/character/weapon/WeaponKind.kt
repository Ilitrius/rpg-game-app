package com.idlerpg.game.ui.character.weapon

import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.character.WeaponAction
import com.idlerpg.game.domain.character.WeaponActions
import com.idlerpg.game.domain.character.WeaponType
import com.idlerpg.game.domain.character.weaponTypeOf
import kotlin.math.roundToInt

/** How a weapon is swung in first person. */
enum class AttackStyle {
    /** Forward thrust (dagger, short sword). */
    STAB,

    /** Overhead chop arc towards the monster (mace, axe). */
    SWING,

    /** Straight punch with the bare fist. */
    PUNCH,
}

/**
 * Graphic kind of the weapon in the hero's hand.
 *
 * @property attackStyle legacy classification of the signature move (kept for compatibility)
 * @property type mechanical [WeaponType] (domain) — affinities and parry chance
 */
enum class WeaponKind(val attackStyle: AttackStyle, val type: WeaponType) {
    /** The worn starter dagger (and legacy weapons without base type). */
    RUSTY_DAGGER(AttackStyle.STAB, WeaponType.DAGGER),
    DAGGER(AttackStyle.STAB, WeaponType.DAGGER),
    SHORT_SWORD(AttackStyle.STAB, WeaponType.SHORT_SWORD),
    MACE(AttackStyle.SWING, WeaponType.MACE),
    AXE(AttackStyle.SWING, WeaponType.AXE),

    /** No weapon equipped: bare gloved fist. */
    FIST(AttackStyle.PUNCH, WeaponType.FIST),
}

/**
 * Everything the weapon painters need to draw an item.
 *
 * @property rust 0f polished … 1f heavily rusted (only used by the daggers)
 * @property rarity adds a gem color and, for EPIC/LEGENDARY, a glow/shimmer
 */
data class WeaponLook(
    val kind: WeaponKind,
    val rarity: Rarity = Rarity.COMMON,
    val rust: Float = 0f,
) {
    companion object {
        val Fist = WeaponLook(WeaponKind.FIST)
    }
}

/** Rust amount used for the rusty starter dagger. */
const val RUSTY_DAGGER_RUST = 0.45f

/**
 * Default fraction of a first-person attack animation at which the weapon hits. Used by
 * [WeaponAction.SWING], [WeaponAction.STAB] and [WeaponAction.STRIKE] for every [WeaponKind];
 * see [impactFractionOf] for the per-action value (parry contact comes earlier).
 */
const val WEAPON_IMPACT_FRACTION = 0.38f

/** Fraction of the [WeaponAction.PARRY] animation at which the weapon meets the monster's blow. */
const val WEAPON_PARRY_CONTACT_FRACTION = 0.30f

/**
 * Fraction (0..1) of the animation of [action] at which it connects — the moment
 * `onImpact(action)` (attacks) or `onParryMoment` (parry) fires. Identical for every [WeaponKind].
 * SWING/STAB/STRIKE → [WEAPON_IMPACT_FRACTION] (0.38), PARRY → [WEAPON_PARRY_CONTACT_FRACTION] (0.30).
 */
fun impactFractionOf(action: WeaponAction): Float =
    if (action == WeaponAction.PARRY) WEAPON_PARRY_CONTACT_FRACTION else WEAPON_IMPACT_FRACTION

/**
 * Relative animation length of [action]: STRIKE is slower (heavy), STAB a bit quicker,
 * SWING/PARRY normal. Apply via [attackDurationMillisOf].
 */
fun actionDurationFactor(action: WeaponAction): Float = when (action) {
    WeaponAction.SWING -> 1.0f
    WeaponAction.STAB -> 0.9f
    WeaponAction.STRIKE -> 1.25f
    WeaponAction.PARRY -> 1.0f
}

/**
 * Duration (ms) of one [action] animation: `baseMillis × actionDurationFactor(action)`,
 * never longer than [maxMillis] (pass the attack interval, ideally with some margin) and never
 * shorter than 120 ms. The hit then lands `duration × impactFractionOf(action)` after the trigger.
 */
fun attackDurationMillisOf(action: WeaponAction, baseMillis: Int, maxMillis: Int = Int.MAX_VALUE): Int =
    (baseMillis * actionDurationFactor(action)).roundToInt().coerceAtMost(maxMillis).coerceAtLeast(MIN_ATTACK_MILLIS)

/** Delay (ms) from trigger to contact for an [action] animation of [durationMillis]. */
fun impactDelayMillisOf(action: WeaponAction, durationMillis: Int): Long =
    (durationMillis.coerceAtLeast(MIN_ATTACK_MILLIS) * impactFractionOf(action)).toLong()

/** Shortest attack animation [FirstPersonWeapon] plays. */
const val MIN_ATTACK_MILLIS = 120

/**
 * Action played when the caller does not choose one: the weapon's signature move
 * (dagger → STAB, mace → STRIKE, sword/axe/fist → SWING), see [WeaponActions.signatureAction].
 */
fun defaultActionOf(kind: WeaponKind): WeaponAction = WeaponActions.signatureAction(kind.type)

/**
 * Maps an (equipped) item to its weapon graphic:
 * - `null` (or a non-weapon item) → [WeaponKind.FIST]
 * - starter dagger id or legacy weapon without base type → [WeaponKind.RUSTY_DAGGER]
 * - base types dolch/kurzschwert/streitkolben/axt → DAGGER/SHORT_SWORD/MACE/AXE
 * - any other/unknown base type on a weapon → [WeaponKind.DAGGER]
 */
fun weaponKindOf(item: Item?): WeaponKind = when (weaponTypeOf(item)) {
    WeaponType.FIST -> WeaponKind.FIST
    WeaponType.DAGGER ->
        if (item != null && (item.id == StarterItems.RUSTY_DAGGER_ID || item.baseType.isBlank())) WeaponKind.RUSTY_DAGGER
        else WeaponKind.DAGGER
    WeaponType.SHORT_SWORD -> WeaponKind.SHORT_SWORD
    WeaponType.MACE -> WeaponKind.MACE
    WeaponType.AXE -> WeaponKind.AXE
}

/** [weaponKindOf] plus rarity and rust — the full look of [item] for the painters. */
fun weaponLookOf(item: Item?): WeaponLook {
    val kind = weaponKindOf(item)
    if (kind == WeaponKind.FIST || item == null) return WeaponLook.Fist
    return WeaponLook(
        kind = kind,
        rarity = item.rarity,
        rust = if (kind == WeaponKind.RUSTY_DAGGER) RUSTY_DAGGER_RUST else 0f,
    )
}
