package com.idlerpg.game.ui.character.weapon

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Backwards-compatible wrapper: first-person hand holding the dagger, stabbing whenever
 * [attackTrigger] changes. New code should use [FirstPersonWeapon] with the equipped item.
 *
 * @param rust rust amount of the blade (0f polished … 1f rusty). Starter dagger: ~0.35f.
 * @param onStabImpact called when the blade reaches its furthest point ([WEAPON_IMPACT_FRACTION]).
 */
@Composable
fun FirstPersonDagger(
    attackTrigger: Int,
    modifier: Modifier = Modifier,
    rust: Float = 0.35f,
    attackDurationMillis: Int = 340,
    onStabImpact: (() -> Unit)? = null,
) {
    FirstPersonWeapon(
        look = daggerLook(rust),
        attackTrigger = attackTrigger,
        modifier = modifier,
        attackDurationMillis = attackDurationMillis,
        onStabImpact = onStabImpact,
    )
}

/**
 * Stateless single frame of [FirstPersonDagger] — use it if you drive the animation yourself.
 *
 * @param attackProgress 0f = rest, rising to [WEAPON_IMPACT_FRACTION] ≈ fully extended, 1f = back at rest.
 * @param swayPhase idle sway phase in radians (0..2π, loops).
 */
@Composable
fun FirstPersonDaggerFrame(
    attackProgress: Float,
    swayPhase: Float,
    modifier: Modifier = Modifier,
    rust: Float = 0.35f,
) {
    Canvas(modifier) {
        drawFirstPersonDagger(attackProgress, swayPhase, rust)
    }
}

/** Draws the whole first-person arm + dagger into this DrawScope (fills its size). */
fun DrawScope.drawFirstPersonDagger(attackProgress: Float, swayPhase: Float, rust: Float = 0.35f) =
    drawFirstPersonWeapon(daggerLook(rust), attackProgress, swayPhase)

private fun daggerLook(rust: Float) =
    WeaponLook(if (rust > 0f) WeaponKind.RUSTY_DAGGER else WeaponKind.DAGGER, rust = rust)
