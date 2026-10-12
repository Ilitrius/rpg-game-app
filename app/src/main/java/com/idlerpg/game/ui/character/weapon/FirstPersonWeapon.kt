package com.idlerpg.game.ui.character.weapon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp
import com.idlerpg.game.data.Item
import com.idlerpg.game.domain.character.WeaponAction
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * First-person view of the hero's gloved right hand holding the currently equipped [weapon]
 * (bare fist if `null`). Comes in from the bottom-right corner, sways gently while idle and plays
 * one [action] every time [attackTrigger] changes. Every weapon kind has four distinct moves:
 *
 * - [WeaponAction.SWING]: diagonal/horizontal slash across the monster with a motion trail
 *   (fist: hook).
 * - [WeaponAction.STAB]: straight thrust at the center (mace/axe: short jab with the top;
 *   fist: straight punch).
 * - [WeaponAction.STRIKE]: heavy overhead blow (dagger/fist: downward pommel/hammer-fist strike).
 * - [WeaponAction.PARRY]: weapon (fist: armored forearm) raised crosswise, deflect flick with a
 *   spark at the contact point, short hold, back to rest. Deals no damage.
 *
 * Timing: the animation runs linearly for [attackDurationMillis]; the contact moment is at
 * [impactFractionOf] (0.38 for attacks, 0.30 for parry) of it. Compute a per-action duration with
 * [attackDurationMillisOf] (strike slower, stab quicker) and keep it <= the attack interval.
 * Place it in a `Box` on top of the world, filling the whole view.
 *
 * @param attackTrigger change this value (e.g. increment) to play one move. The initial value does not play.
 * @param attackDurationMillis full duration of the move (forward + back), min [MIN_ATTACK_MILLIS].
 * @param onStabImpact legacy hit callback: fires at the contact moment of SWING/STAB/STRIKE (not PARRY).
 * @param action move to play for the next trigger; `null` → [defaultActionOf] the weapon kind
 *   (dagger STAB, mace STRIKE, sword/axe/fist SWING). Read when the trigger changes.
 * @param onImpact fires at the contact moment of SWING/STAB/STRIKE with the action played.
 * @param onParryMoment fires when a PARRY meets the blow (spark moment).
 */
@Composable
fun FirstPersonWeapon(
    weapon: Item?,
    attackTrigger: Int,
    modifier: Modifier = Modifier,
    attackDurationMillis: Int = 340,
    onStabImpact: (() -> Unit)? = null,
    action: WeaponAction? = null,
    onImpact: ((WeaponAction) -> Unit)? = null,
    onParryMoment: (() -> Unit)? = null,
) {
    FirstPersonWeapon(
        look = weaponLookOf(weapon),
        attackTrigger = attackTrigger,
        modifier = modifier,
        attackDurationMillis = attackDurationMillis,
        onStabImpact = onStabImpact,
        action = action,
        onImpact = onImpact,
        onParryMoment = onParryMoment,
    )
}

/** [FirstPersonWeapon] for an explicit [WeaponLook]. */
@Composable
fun FirstPersonWeapon(
    look: WeaponLook,
    attackTrigger: Int,
    modifier: Modifier = Modifier,
    attackDurationMillis: Int = 340,
    onStabImpact: (() -> Unit)? = null,
    action: WeaponAction? = null,
    onImpact: ((WeaponAction) -> Unit)? = null,
    onParryMoment: (() -> Unit)? = null,
) {
    val progress = remember { Animatable(0f) }
    val initialTrigger = remember { attackTrigger }
    val legacyImpact by rememberUpdatedState(onStabImpact)
    val impact by rememberUpdatedState(onImpact)
    val parried by rememberUpdatedState(onParryMoment)
    val requested by rememberUpdatedState(action ?: defaultActionOf(look.kind))
    val duration by rememberUpdatedState(attackDurationMillis)
    var playing by remember { mutableStateOf(action ?: defaultActionOf(look.kind)) }

    LaunchedEffect(attackTrigger) {
        if (attackTrigger == initialTrigger && progress.value == 0f) return@LaunchedEffect
        val act = requested
        playing = act
        val d = duration.coerceAtLeast(MIN_ATTACK_MILLIS)
        val at = impactFractionOf(act)
        progress.snapTo(0f)
        progress.animateTo(at, tween((d * at).toInt(), easing = LinearEasing))
        if (act == WeaponAction.PARRY) {
            parried?.invoke()
        } else {
            impact?.invoke(act)
            legacyImpact?.invoke()
        }
        progress.animateTo(1f, tween((d * (1f - at)).toInt(), easing = LinearEasing))
        progress.snapTo(0f)
    }

    val idle = rememberInfiniteTransition(label = "weaponIdle")
    val swayPhase by idle.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "sway",
    )
    val shimmer by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )

    Canvas(modifier) {
        drawFirstPersonWeapon(look, progress.value, swayPhase, shimmer, playing)
    }
}

// ============================================================================ pose keyframes

/** Pose of the hand: screen position of the grip center, rotation (degrees) and px-per-unit scale. */
internal data class HandPose(val pivot: Offset, val rotation: Float, val scale: Float) {
    /** Screen position of [handPoint] (hand space, relative to the grip center). */
    fun toScreen(handPoint: Offset): Offset = pivot + rotateVec(handPoint, rotation) * scale
}

/** Pixels per hand-space unit for a first-person view of [size]. */
internal fun firstPersonUnit(size: Size): Float = minOf(size.width, size.height * 0.62f) / 360f

private enum class Ease { LINEAR, SMOOTH, IN, OUT }

/** Which hand-space point a keyframe places at its screen position. */
internal enum class Aim { PIVOT, TIP, POMMEL, TOP, CONTACT }

/**
 * One keyframe. Screen position = (x × width, y × height + dy × unit); with [aim] != PIVOT that
 * position is where the chosen weapon point (tip, pommel, …) lands and the grip is solved from it.
 * [ease] shapes the segment that ends at this key.
 */
private class Key(
    val t: Float,
    val x: Float,
    val y: Float,
    val rot: Float,
    val scale: Float = 1f,
    val dy: Float = 0f,
    val aim: Aim = Aim.PIVOT,
    val ease: Ease = Ease.SMOOTH,
)

private fun ease(e: Ease, k: Float): Float {
    val t = k.coerceIn(0f, 1f)
    return when (e) {
        Ease.LINEAR -> t
        Ease.SMOOTH -> t * t * (3f - 2f * t)
        Ease.IN -> t * t * (1.6f - 0.6f * t) // accelerating into the hit
        Ease.OUT -> 1f - (1f - t) * (1f - t)
    }
}

private fun lerpOffset(a: Offset, b: Offset, t: Float) = Offset(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

/** Rotates [v] by [degrees] (clockwise on screen, like Compose's rotate). */
internal fun rotateVec(v: Offset, degrees: Float): Offset {
    val r = Math.toRadians(degrees.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    return Offset(v.x * c - v.y * s, v.x * s + v.y * c)
}

/** Characteristic hand-space points (relative to the grip center, weapon pointing to -y). */
internal class Points(val tip: Offset, val pommel: Offset, val top: Offset, val contact: Offset) {
    fun of(aim: Aim): Offset = when (aim) {
        Aim.PIVOT -> Offset.Zero
        Aim.TIP -> tip
        Aim.POMMEL -> pommel
        Aim.TOP -> top
        Aim.CONTACT -> contact
    }
}

private val pointsCache = HashMap<WeaponKind, Points>()

internal fun pointsOf(kind: WeaponKind): Points = pointsCache.getOrPut(kind) {
    val shape = WeaponPainters.shapeOf(kind)
    if (shape == null) {
        // Fist: punches land with the knuckle row, the hammer fist with the low thumb-side edge,
        // parries on the forearm's steel plate.
        val g = DaggerPainter.GRIP_CENTER
        val knuckles = FistPainter.KNUCKLE_FRONT - g
        Points(tip = knuckles, pommel = FistPainter.HAMMER_POINT - g, top = knuckles, contact = Offset(40f, 140f))
    } else {
        val g = shape.gripCenter
        val tip = shape.strikePoint - g
        val long = kind == WeaponKind.MACE || kind == WeaponKind.AXE
        Points(
            tip = tip,
            pommel = Offset(0f, shape.bounds.bottom - g.y - 8f),
            top = Offset(0f, shape.bounds.top - g.y + 4f),
            // Blades parry with the forte of the blade, mace/axe with the haft.
            contact = if (long) Offset(0f, tip.y * 0.45f) else tip * 0.58f,
        )
    }
}

/** Idle pose (t = 0 and t = 1) of each kind. */
private fun restKey(kind: WeaponKind, t: Float, ease: Ease = Ease.SMOOTH): Key = when (kind) {
    WeaponKind.RUSTY_DAGGER, WeaponKind.DAGGER -> Key(t, 0.80f, 1f, -26f, dy = -110f, ease = ease)
    WeaponKind.SHORT_SWORD -> Key(t, 0.82f, 1f, -30f, dy = -70f, ease = ease)
    WeaponKind.MACE, WeaponKind.AXE -> Key(t, 0.77f, 1f, 6f, dy = -62f, ease = ease)
    WeaponKind.FIST -> Key(t, 0.74f, 1f, -14f, scale = 1.15f, dy = -60f, ease = ease)
}

private val keyCache = HashMap<Pair<WeaponKind, WeaponAction>, List<Key>>()

private fun keysOf(kind: WeaponKind, action: WeaponAction): List<Key> = keyCache.getOrPut(kind to action) {
    val hit = WEAPON_IMPACT_FRACTION
    val pc = WEAPON_PARRY_CONTACT_FRACTION
    val r0 = restKey(kind, 0f)
    val r1 = restKey(kind, 1f)
    when (kind) {
        WeaponKind.RUSTY_DAGGER, WeaponKind.DAGGER -> when (action) {
            WeaponAction.STAB -> listOf(
                r0,
                Key(0.09f, 0.84f, 1f, -29f, 1.03f, dy = -88f),
                Key(hit, 0.52f, 0.45f, -12f, 0.80f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.08f, 0.525f, 0.46f, -13f, 0.81f, aim = Aim.TIP, ease = Ease.OUT),
                r1,
            )
            WeaponAction.SWING -> listOf(
                r0,
                Key(0.15f, 0.84f, 0.66f, 48f, 0.95f),
                Key(hit, 0.40f, 0.48f, -72f, 0.82f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.12f, 0.46f, 0.80f, -122f, 0.90f, ease = Ease.OUT),
                r1,
            )
            WeaponAction.STRIKE -> listOf( // raised high, hammered down onto the monster
                r0,
                Key(0.18f, 0.72f, 0.50f, 6f, 0.92f),
                Key(hit, 0.47f, 0.54f, -50f, 0.78f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.08f, 0.47f, 0.555f, -52f, 0.79f, aim = Aim.TIP, ease = Ease.OUT),
                r1,
            )
            WeaponAction.PARRY -> parryKeys(r0, r1, pc, pivotX = 0.66f, pivotY = 0.64f, guardRot = -62f, scale = 0.96f)
        }
        WeaponKind.SHORT_SWORD -> when (action) {
            WeaponAction.STAB -> listOf(
                r0,
                Key(0.09f, 0.86f, 1f, -32f, 1.03f, dy = -48f),
                Key(hit, 0.52f, 0.43f, -10f, 0.72f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.08f, 0.525f, 0.44f, -11f, 0.73f, aim = Aim.TIP, ease = Ease.OUT),
                r1,
            )
            WeaponAction.SWING -> listOf(
                r0,
                Key(0.15f, 0.86f, 0.70f, 52f, 0.88f),
                Key(hit, 0.38f, 0.50f, -74f, 0.76f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.12f, 0.48f, 0.86f, -126f, 0.84f, ease = Ease.OUT),
                r1,
            )
            WeaponAction.STRIKE -> listOf( // overhead chop
                r0,
                Key(0.20f, 0.72f, 0.52f, 4f, 0.90f),
                Key(hit, 0.48f, 0.55f, -38f, 0.72f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.10f, 0.48f, 0.57f, -40f, 0.73f, aim = Aim.TIP, ease = Ease.OUT),
                r1,
            )
            WeaponAction.PARRY -> parryKeys(r0, r1, pc, pivotX = 0.68f, pivotY = 0.66f, guardRot = -64f, scale = 0.88f)
        }
        WeaponKind.MACE, WeaponKind.AXE -> when (action) {
            WeaponAction.STAB -> listOf( // short forward jab with the top
                r0,
                Key(0.12f, 0.80f, 1f, 2f, 1.04f, dy = -36f),
                Key(hit, 0.53f, 0.40f, -10f, 0.70f, aim = Aim.TOP, ease = Ease.IN),
                Key(hit + 0.08f, 0.535f, 0.41f, -10f, 0.71f, aim = Aim.TOP, ease = Ease.OUT),
                r1,
            )
            WeaponAction.SWING -> listOf( // horizontal sweep from the right
                r0,
                Key(0.15f, 0.88f, 0.80f, 56f, 0.92f),
                Key(hit, 0.44f, 0.48f, -70f, 0.74f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.12f, 0.42f, 0.86f, -112f, 0.84f, ease = Ease.OUT),
                r1,
            )
            WeaponAction.STRIKE -> listOf( // overhead chop (wind-up high, heavy fall, brief hold)
                r0,
                Key(0.20f, 0.70f, 0.60f, -6f, 0.92f),
                Key(hit, 0.49f, 0.52f, -30f, 0.70f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.10f, 0.49f, 0.535f, -31f, 0.705f, aim = Aim.TIP, ease = Ease.OUT),
                r1,
            )
            WeaponAction.PARRY -> parryKeys(r0, r1, pc, pivotX = 0.70f, pivotY = 0.66f, guardRot = -78f, scale = 0.86f)
        }
        WeaponKind.FIST -> when (action) {
            WeaponAction.STAB -> listOf( // straight punch
                r0,
                Key(0.08f, 0.76f, 1f, -15f, 1.17f, dy = -50f),
                Key(hit, 0.53f, 0.48f, -4f, 0.80f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.07f, 0.535f, 0.49f, -5f, 0.81f, aim = Aim.TIP, ease = Ease.OUT),
                r1,
            )
            WeaponAction.SWING -> listOf( // hook from the right
                r0,
                Key(0.15f, 0.92f, 0.66f, 30f, 1.08f),
                Key(hit, 0.47f, 0.48f, -80f, 0.86f, aim = Aim.TIP, ease = Ease.IN),
                Key(hit + 0.10f, 0.40f, 0.52f, -92f, 0.88f, ease = Ease.OUT),
                r1,
            )
            WeaponAction.STRIKE -> listOf( // hammer fist from above
                r0,
                Key(0.18f, 0.68f, 0.18f, 18f, 0.86f, aim = Aim.TIP),
                Key(hit, 0.49f, 0.53f, -22f, 0.80f, aim = Aim.POMMEL, ease = Ease.IN),
                Key(hit + 0.08f, 0.49f, 0.545f, -23f, 0.81f, aim = Aim.POMMEL, ease = Ease.OUT),
                r1,
            )
            WeaponAction.PARRY -> parryKeys(r0, r1, pc, pivotX = 0.46f, pivotY = 0.60f, guardRot = -84f, scale = 1.0f)
        }
    }
}

/**
 * Shared parry choreography: raise crosswise into the guard (slightly tilted back), meet the blow at
 * [contact] with a deflect flick (rotation kicks outward, pushed a little forward), short hold, rest.
 */
private fun parryKeys(r0: Key, r1: Key, contact: Float, pivotX: Float, pivotY: Float, guardRot: Float, scale: Float) = listOf(
    r0,
    Key(contact - 0.12f, pivotX + 0.03f, pivotY + 0.04f, guardRot - 6f, scale * 1.04f, ease = Ease.OUT),
    Key(contact, pivotX, pivotY, guardRot, scale, ease = Ease.SMOOTH),
    Key(contact + 0.07f, pivotX + 0.02f, pivotY - 0.012f, guardRot + 14f, scale * 0.97f, ease = Ease.OUT),
    Key(contact + 0.30f, pivotX + 0.01f, pivotY, guardRot + 6f, scale, ease = Ease.SMOOTH),
    r1,
)

private fun keyPose(key: Key, pts: Points, unit: Float, size: Size): HandPose {
    val target = Offset(key.x * size.width, key.y * size.height + key.dy * unit)
    val sc = unit * key.scale
    val pivot = if (key.aim == Aim.PIVOT) target else target - rotateVec(pts.of(key.aim), key.rot) * sc
    return HandPose(pivot, key.rot, sc)
}

/** Pose at progress [p] (no idle sway). */
private fun poseAt(kind: WeaponKind, action: WeaponAction, p: Float, unit: Float, size: Size): HandPose {
    val keys = keysOf(kind, action)
    val pts = pointsOf(kind)
    val t = p.coerceIn(0f, 1f)
    var i = 1
    while (i < keys.size - 1 && keys[i].t < t) i++
    val a = keys[i - 1]
    val b = keys[i]
    val k = if (b.t <= a.t) 1f else ease(b.ease, (t - a.t) / (b.t - a.t))
    val pa = keyPose(a, pts, unit, size)
    val pb = keyPose(b, pts, unit, size)
    return HandPose(lerpOffset(pa.pivot, pb.pivot, k), lerp(pa.rotation, pb.rotation, k), lerp(pa.scale, pb.scale, k))
}

/**
 * Pose of the hand in a first-person view of [size] at progress [p] of [action], incl. idle sway
 * ([swayPhase] radians) near rest; [unit] = [firstPersonUnit] (size).
 */
internal fun posedAt(kind: WeaponKind, action: WeaponAction, p: Float, swayPhase: Float, unit: Float, size: Size): HandPose {
    val base = poseAt(kind, action, p, unit, size)
    val t = p.coerceIn(0f, 1f)
    val edge = minOf(t, 1f - t)
    val sway = 1f - ease(Ease.SMOOTH, edge / 0.12f)
    if (sway <= 0f) return base
    return HandPose(
        base.pivot + Offset(sin(swayPhase) * 9f * unit * sway, sin(swayPhase * 2f) * 6f * unit * sway),
        base.rotation + sin(swayPhase) * 1.6f * sway,
        base.scale,
    )
}

// ============================================================================ drawing

/**
 * Draws the whole first-person arm + weapon into this DrawScope (fills its size).
 *
 * @param attackProgress 0f = rest, [impactFractionOf] ([action]) = contact, 1f = back at rest (linear time).
 * @param swayPhase idle sway phase in radians (0..2π, loops).
 * @param shimmer 0..1 legendary shimmer phase.
 * @param action move being played; defaults to the weapon's signature move ([defaultActionOf]).
 */
fun DrawScope.drawFirstPersonWeapon(
    look: WeaponLook,
    attackProgress: Float,
    swayPhase: Float,
    shimmer: Float = 0f,
    action: WeaponAction = defaultActionOf(look.kind),
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val unit = firstPersonUnit(size) // px per hand-space unit
    val kind = look.kind
    val pose = posedAt(kind, action, attackProgress, swayPhase, unit, size)

    // ---- Motion trail behind the weapon
    drawMotionTrail(kind, action, attackProgress, swayPhase, unit)

    // ---- Arm, weapon, fingers
    withTransform({
        translate(pose.pivot.x, pose.pivot.y)
        rotate(pose.rotation, Offset.Zero)
        scale(pose.scale, pose.scale, Offset.Zero)
    }) {
        // Hand space: grip center at origin, weapon points to -y, forearm towards +y.
        if (kind == WeaponKind.FIST) {
            withTransform({ translate(-DaggerPainter.GRIP_CENTER.x, -DaggerPainter.GRIP_CENTER.y) }) {
                FistPainter.drawWithForearm(this)
            }
        } else {
            withTransform({ translate(-DaggerPainter.GRIP_CENTER.x, -DaggerPainter.GRIP_CENTER.y) }) {
                HandPainter.drawArmBehind(this)
            }
            drawWeaponHeld(look, shimmer)
            withTransform({ translate(-DaggerPainter.GRIP_CENTER.x, -DaggerPainter.GRIP_CENTER.y) }) {
                HandPainter.drawFingers(this)
            }
        }
    }

    // ---- Parry spark in front of everything
    if (action == WeaponAction.PARRY) {
        val pc = WEAPON_PARRY_CONTACT_FRACTION
        val k = (attackProgress - pc) / 0.22f
        if (k in 0f..1f) drawParrySpark(pose.toScreen(pointsOf(kind).contact), k, unit)
    }
}

/** Trail window (progress) and how far down the weapon the smear reaches (0 = tip only, 1 = grip). */
private fun trailParams(kind: WeaponKind, action: WeaponAction): Pair<Float, Float> {
    val fist = kind == WeaponKind.FIST
    val long = kind == WeaponKind.MACE || kind == WeaponKind.AXE
    return when (action) {
        WeaponAction.SWING -> 0.13f to (if (fist) 0.0f else if (long) 0.30f else 0.55f)
        WeaponAction.STRIKE -> 0.11f to (if (fist) 0.0f else if (long) 0.30f else 0.45f)
        WeaponAction.STAB -> 0.07f to 0.0f
        WeaponAction.PARRY -> 0.05f to 0.0f
    }
}

/**
 * Translucent smear following the striking point (and, for slashes, part of the blade) over the
 * last few percent of the animation. Only drawn while moving fast (wind-up → just after contact).
 */
private fun DrawScope.drawMotionTrail(kind: WeaponKind, action: WeaponAction, p: Float, swayPhase: Float, unit: Float) {
    val contact = impactFractionOf(action)
    if (p <= 0.04f || p > contact + 0.16f) return
    val (window, inner) = trailParams(kind, action)
    val pts = pointsOf(kind)
    val head = if (action == WeaponAction.STRIKE && kind == WeaponKind.FIST) pts.pommel
    else if (action == WeaponAction.STAB && (kind == WeaponKind.MACE || kind == WeaponKind.AXE)) pts.top
    else pts.tip
    val innerPoint = head * (1f - inner)
    val n = 12
    val outer = ArrayList<Offset>(n + 1)
    val innerList = ArrayList<Offset>(n + 1)
    for (i in 0..n) {
        val q = p - window * i / n
        if (q < 0f) break
        val pose = posedAt(kind, action, q, swayPhase, unit, size)
        outer += pose.toScreen(head)
        innerList += pose.toScreen(innerPoint)
    }
    if (outer.size < 3) return
    var length = 0f
    for (i in 1 until outer.size) length += hypot(outer[i].x - outer[i - 1].x, outer[i].y - outer[i - 1].y)
    val speed = ((length / unit - 40f) / 160f).coerceIn(0f, 1f)
    if (speed <= 0f) return
    val fade = if (p > contact) 1f - ((p - contact) / 0.16f) else 1f
    val strength = speed * fade * when (action) {
        WeaponAction.SWING -> 0.42f
        WeaponAction.STRIKE -> 0.36f
        WeaponAction.STAB -> 0.26f
        WeaponAction.PARRY -> 0.16f
    }
    val tint = Color(0xFFE8F1FF)
    if (inner > 0f) {
        // Ribbon between the tip path and the inner blade path.
        for (i in 0 until outer.size - 1) {
            val a = strength * (1f - i.toFloat() / (outer.size - 1))
            val path = Path().apply {
                moveTo(outer[i].x, outer[i].y)
                lineTo(outer[i + 1].x, outer[i + 1].y)
                lineTo(innerList[i + 1].x, innerList[i + 1].y)
                lineTo(innerList[i].x, innerList[i].y)
                close()
            }
            drawPath(path, tint.copy(alpha = a * 0.55f))
        }
    }
    // Bright edge streak along the striking point.
    val width = (if (kind == WeaponKind.FIST) 26f else 14f) * unit
    for (i in 0 until outer.size - 1) {
        val f = 1f - i.toFloat() / (outer.size - 1)
        drawLine(
            tint.copy(alpha = strength * f),
            outer[i], outer[i + 1],
            strokeWidth = width * (0.35f + 0.65f * f),
            cap = StrokeCap.Round,
        )
    }
}

/** Spark burst where the parry meets the blow; [k] 0..1 over its lifetime. */
private fun DrawScope.drawParrySpark(c: Offset, k: Float, unit: Float) {
    val fade = 1f - k
    // Flash
    val r = (36f + 70f * k) * unit
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFFFFF).copy(alpha = 0.95f * fade),
            0.25f to Color(0xFFFFF0B0).copy(alpha = 0.75f * fade),
            0.6f to Color(0xFFFFB040).copy(alpha = 0.25f * fade),
            1f to Color.Transparent,
            center = c, radius = r,
        ),
        radius = r, center = c,
    )
    // Shock ring
    drawCircle(
        Color(0xFFFFF4D0).copy(alpha = 0.55f * fade * fade),
        radius = (20f + 90f * k) * unit, center = c,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * unit * fade + 0.5f),
    )
    // Radiating sparks (fixed pseudo-random directions, biased up/right = deflected away)
    for (i in 0 until 11) {
        val ang = Math.toRadians((-150.0 + i * 29.0 + (i * 37 % 11) * 2.0)).toFloat()
        val len = (40f + (i * 53 % 7) * 9f) * unit
        val d = Offset(cos(ang), sin(ang))
        val start = c + d * (len * (0.15f + 0.85f * k))
        val end = c + d * (len * (0.45f + 1.1f * k))
        drawLine(Color(0xFFFFD27A).copy(alpha = fade), start, end, strokeWidth = 3.2f * unit * fade + 0.6f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = fade), start, start + (end - start) * 0.4f, strokeWidth = 1.6f * unit * fade + 0.4f, cap = StrokeCap.Round)
    }
    // Star glint
    val s = (30f + 30f * (1f - k)) * unit * fade
    drawLine(Color.White.copy(alpha = fade), c - Offset(s, 0f), c + Offset(s, 0f), strokeWidth = 2.4f * unit, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = fade), c - Offset(0f, s), c + Offset(0f, s), strokeWidth = 2.4f * unit, cap = StrokeCap.Round)
}
