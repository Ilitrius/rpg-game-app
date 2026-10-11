package com.idlerpg.game.ui.character.weapon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * First-person view of the hero's gloved right hand holding the dagger. Comes in from the
 * bottom-right corner, sways gently while idle and stabs towards the screen center every time
 * [attackTrigger] changes. Place it in a `Box` on top of the world, filling the whole view:
 *
 * ```
 * var attacks by remember { mutableIntStateOf(0) }
 * Box(Modifier.fillMaxSize()) {
 *     World()
 *     FirstPersonDagger(attackTrigger = attacks, modifier = Modifier.fillMaxSize(),
 *         onStabImpact = { /* apply damage / flash monster */ })
 * }
 * // in the combat loop: attacks++
 * ```
 *
 * @param attackTrigger change this value (e.g. increment) to play one stab. The initial value does not stab.
 * @param rust rust amount of the blade (0f polished … 1f rusty). Starter dagger: ~0.35f.
 * @param attackDurationMillis full stab duration (forward + back). Keep it <= attack interval.
 * @param onStabImpact called when the blade reaches its furthest point (good moment for hit effects).
 */
@Composable
fun FirstPersonDagger(
    attackTrigger: Int,
    modifier: Modifier = Modifier,
    rust: Float = 0.35f,
    attackDurationMillis: Int = 340,
    onStabImpact: (() -> Unit)? = null,
) {
    val progress = remember { Animatable(0f) }
    val initialTrigger = remember { attackTrigger }
    val impact by rememberUpdatedState(onStabImpact)

    LaunchedEffect(attackTrigger) {
        if (attackTrigger == initialTrigger && progress.value == 0f) return@LaunchedEffect
        val duration = attackDurationMillis.coerceAtLeast(120)
        progress.snapTo(0f)
        progress.animateTo(IMPACT_AT, tween((duration * IMPACT_AT).toInt(), easing = FastOutLinearInEasing))
        impact?.invoke()
        progress.animateTo(1f, tween((duration * (1f - IMPACT_AT)).toInt(), easing = LinearEasing))
        progress.snapTo(0f)
    }

    val idle = rememberInfiniteTransition(label = "daggerIdle")
    val swayPhase by idle.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "sway",
    )

    FirstPersonDaggerFrame(
        attackProgress = progress.value,
        swayPhase = swayPhase,
        modifier = modifier,
        rust = rust,
    )
}

/** Fraction of the stab animation at which the blade is fully extended. */
private const val IMPACT_AT = 0.38f

/**
 * Stateless single frame of [FirstPersonDagger] — use it if you drive the animation yourself.
 *
 * @param attackProgress 0f = rest, rising to [IMPACT_AT] ≈ fully extended, 1f = back at rest.
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

/** Thrust amount 0..1 for a given progress: slight wind-up, fast thrust, slower recovery. */
internal fun thrustCurve(p: Float): Float {
    val t = p.coerceIn(0f, 1f)
    return when {
        t < 0.08f -> -0.12f * (t / 0.08f) // wind-up
        t < IMPACT_AT -> {
            val k = (t - 0.08f) / (IMPACT_AT - 0.08f)
            lerp(-0.12f, 1f, 1f - (1f - k) * (1f - k))
        }
        else -> {
            val k = (t - IMPACT_AT) / (1f - IMPACT_AT)
            val e = k * k * (3f - 2f * k) // smoothstep
            1f - e
        }
    }
}

/** Draws the whole first-person arm + dagger into this DrawScope (fills its size). */
fun DrawScope.drawFirstPersonDagger(attackProgress: Float, swayPhase: Float, rust: Float = 0.35f) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val thrust = thrustCurve(attackProgress)
    val swayAmount = 1f - thrust.coerceIn(0f, 1f)
    val unit = minOf(w, h * 0.62f) / 360f // px per hand-space unit

    val rest = Offset(w * 0.80f, h - 110f * unit)
    val target = Offset(w * 0.54f, h * 0.48f)
    val sway = Offset(
        sin(swayPhase) * 9f * unit * swayAmount,
        sin(swayPhase * 2f) * 6f * unit * swayAmount,
    )
    val pivot = Offset(
        lerp(rest.x, target.x, 0.42f * thrust),
        lerp(rest.y, target.y, 0.42f * thrust),
    ) + sway
    val rotation = lerp(-26f, -12f, thrust.coerceAtLeast(0f)) + sin(swayPhase) * 1.6f * swayAmount
    val scale = unit * (1f - 0.20f * thrust)

    // Motion streak behind the blade during the fast part of the thrust.
    if (thrust > 0.35f && attackProgress < IMPACT_AT + 0.12f) {
        val a = ((thrust - 0.35f) / 0.65f).coerceIn(0f, 1f) * 0.22f
        withTransform({
            translate(pivot.x, pivot.y)
            rotate(rotation, Offset.Zero)
            scale(scale, scale, Offset.Zero)
        }) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = a), Color.White.copy(alpha = 0f)),
                    startY = -260f, endY = 40f,
                ),
                topLeft = Offset(-26f, -260f),
                size = androidx.compose.ui.geometry.Size(52f, 300f),
            )
        }
    }

    withTransform({
        translate(pivot.x, pivot.y)
        rotate(rotation, Offset.Zero)
        scale(scale, scale, Offset.Zero)
        // Hand space: grip center at origin, dagger points to -y, forearm towards +y.
        translate(-DaggerPainter.GRIP_CENTER.x, -DaggerPainter.GRIP_CENTER.y)
    }) {
        HandPainter.drawArmBehind(this)
        with(DaggerPainter) { drawDaggerUnits(rust) }
        HandPainter.drawFingers(this)
    }
}

/** Gloved hand and armored forearm, defined in dagger unit space (grip at x 42..58, y 216..289). */
private object HandPainter {

    private val leatherDark = Color(0xFF2A1A10)
    private val seam = Color(0xFF1A0F08)
    private val stitch = Color(0xFFC8A27A)

    // Sleeve (cloth) far down the arm.
    private val sleeve = Path().apply {
        moveTo(40f, 470f)
        cubicTo(30f, 560f, 20f, 660f, 10f, 820f)
        lineTo(230f, 820f)
        cubicTo(205f, 660f, 175f, 560f, 150f, 455f)
        close()
    }
    private val sleeveBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF1C0B0E),
            0.3f to Color(0xFF5A2229),
            0.55f to Color(0xFF7A3039),
            0.8f to Color(0xFF3E161B),
            1f to Color(0xFF14070A),
        ),
        startX = 20f, endX = 220f,
    )
    private val sleeveFolds = listOf(
        Path().apply { moveTo(70f, 500f); cubicTo(80f, 560f, 70f, 640f, 75f, 800f) },
        Path().apply { moveTo(120f, 490f); cubicTo(130f, 580f, 140f, 680f, 150f, 800f) },
        Path().apply { moveTo(165f, 520f); cubicTo(175f, 600f, 185f, 700f, 200f, 800f) },
    )

    // Leather vambrace with steel plate.
    private val vambrace = Path().apply {
        moveTo(54f, 330f)
        cubicTo(48f, 380f, 42f, 430f, 36f, 485f)
        cubicTo(80f, 500f, 120f, 498f, 156f, 470f)
        cubicTo(146f, 420f, 132f, 370f, 120f, 322f)
        cubicTo(100f, 334f, 74f, 336f, 54f, 330f)
        close()
    }
    private val vambraceBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF1E120A),
            0.25f to Color(0xFF4E321F),
            0.5f to Color(0xFF6B4630),
            0.8f to Color(0xFF3A2416),
            1f to Color(0xFF160D07),
        ),
        startX = 36f, endX = 156f,
    )
    private val plate = Path().apply {
        moveTo(66f, 346f)
        cubicTo(62f, 385f, 58f, 425f, 56f, 468f)
        cubicTo(84f, 478f, 110f, 476f, 134f, 460f)
        cubicTo(128f, 420f, 120f, 382f, 112f, 342f)
        cubicTo(96f, 350f, 80f, 350f, 66f, 346f)
        close()
    }
    private val plateBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF4B555D),
            0.22f to Color(0xFFA9B4BC),
            0.38f to Color(0xFFE7EDF1),
            0.55f to Color(0xFF8C98A1),
            0.8f to Color(0xFF59646D),
            1f to Color(0xFF353D44),
        ),
        startX = 56f, endX = 134f,
    )
    private val plateRivets = listOf(
        Offset(70f, 356f), Offset(108f, 352f), Offset(66f, 400f), Offset(118f, 396f),
        Offset(62f, 450f), Offset(126f, 446f),
    )
    private val straps = listOf(
        Path().apply { moveTo(48f, 372f); cubicTo(80f, 384f, 110f, 382f, 134f, 366f) },
        Path().apply { moveTo(42f, 440f); cubicTo(80f, 452f, 118f, 450f, 148f, 432f) },
    )

    // Glove cuff (flared) between hand and vambrace.
    private val cuff = Path().apply {
        moveTo(56f, 282f)
        cubicTo(52f, 300f, 50f, 318f, 50f, 338f)
        cubicTo(76f, 346f, 102f, 344f, 124f, 330f)
        cubicTo(120f, 312f, 114f, 296f, 108f, 278f)
        close()
    }
    private val cuffBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF24150C),
            0.3f to Color(0xFF6A4329),
            0.55f to Color(0xFF8A5B3A),
            1f to Color(0xFF2A190E),
        ),
        startX = 50f, endX = 124f,
    )

    // Back of the hand / palm mass behind the grip.
    private val handBack = Path().apply {
        moveTo(54f, 218f)
        cubicTo(78f, 206f, 108f, 214f, 116f, 246f)
        cubicTo(122f, 272f, 116f, 296f, 104f, 304f)
        cubicTo(88f, 312f, 66f, 306f, 56f, 296f)
        close()
    }
    private val handBackBrush = Brush.radialGradient(
        colorStops = arrayOf(0f to Color(0xFF9A6A47), 0.45f to Color(0xFF6A4329), 1f to Color(0xFF26160C)),
        center = Offset(88f, 240f), radius = 62f,
    )
    private val handBackStitch = Path().apply {
        moveTo(70f, 222f)
        cubicTo(92f, 216f, 106f, 228f, 110f, 252f)
    }
    private val tendons = listOf(
        Path().apply { moveTo(66f, 236f); cubicTo(78f, 252f, 86f, 272f, 90f, 296f) },
        Path().apply { moveTo(78f, 230f); cubicTo(92f, 248f, 100f, 268f, 102f, 290f) },
    )

    // Four curled fingers wrapping the grip (front), index finger on top.
    private val fingers: List<RoundRect> = (0 until 4).map { i ->
        val top = 219f + i * 17.5f
        val inset = if (i == 3) 4f else 0f
        RoundRect(24f + inset, top, 68f, top + 18.5f - (if (i == 3) 1.5f else 0f), CornerRadius(9.2f, 9.2f))
    }
    private fun fingerBrush(r: RoundRect) = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color(0xFFA77550),
            0.35f to Color(0xFF7A5034),
            0.8f to Color(0xFF3E2616),
            1f to Color(0xFF22140B),
        ),
        startY = r.top, endY = r.bottom,
    )
    private fun knuckleBrush(r: RoundRect) = Brush.radialGradient(
        colorStops = arrayOf(0f to Color(0xFFC79A72).copy(alpha = 0.9f), 1f to Color.Transparent),
        center = Offset(r.left + 8f, r.top + 6f), radius = 9f,
    )

    // Thumb wrapping over the index finger.
    private val thumb = Path().apply {
        moveTo(104f, 246f)
        cubicTo(96f, 222f, 72f, 210f, 44f, 214f)
        cubicTo(32f, 216f, 30f, 231f, 42f, 233f)
        cubicTo(62f, 233f, 76f, 236f, 88f, 256f)
        close()
    }
    private val thumbBrush = Brush.linearGradient(
        colorStops = arrayOf(0f to Color(0xFFB5835C), 0.4f to Color(0xFF80553A), 1f to Color(0xFF2E1B0F)),
        start = Offset(60f, 212f), end = Offset(70f, 245f),
    )
    private val thumbNailPlate = Path().apply {
        moveTo(46f, 217f)
        cubicTo(40f, 218f, 38f, 226f, 44f, 228f)
        cubicTo(50f, 229f, 56f, 228f, 58f, 222f)
        cubicTo(56f, 217f, 52f, 216f, 46f, 217f)
        close()
    }
    private val thumbNailBrush = Brush.linearGradient(
        colorStops = arrayOf(0f to Color(0xFFDCE4EA), 0.5f to Color(0xFF8C98A1), 1f to Color(0xFF3B444B)),
        start = Offset(42f, 216f), end = Offset(56f, 229f),
    )

    fun drawArmBehind(scope: DrawScope) = with(scope) {
        // Sleeve
        drawPath(sleeve, sleeveBrush)
        sleeveFolds.forEach {
            drawPath(it, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 5f, cap = StrokeCap.Round))
            drawPath(it, Color(0xFFB0505C).copy(alpha = 0.18f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
        }

        // Vambrace + steel plate
        drawPath(vambrace, vambraceBrush)
        drawPath(vambrace, seam, style = Stroke(width = 1.4f, join = StrokeJoin.Round))
        straps.forEach {
            drawPath(it, Color(0xFF1A0F08), style = Stroke(width = 9f, cap = StrokeCap.Butt))
            drawPath(it, Color(0xFF5A3A24), style = Stroke(width = 7f, cap = StrokeCap.Butt))
            drawPath(it, stitch.copy(alpha = 0.6f), style = Stroke(width = 0.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2.5f))))
        }
        drawPath(plate, plateBrush)
        drawPath(plate, Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.35f)),
            startY = 340f, endY = 478f,
        ))
        drawPath(plate, Color(0xFF1E2328), style = Stroke(width = 1.4f, join = StrokeJoin.Round))
        // central ridge on the plate
        drawLine(Color.White.copy(alpha = 0.55f), Offset(86f, 352f), Offset(84f, 470f), strokeWidth = 1.2f, cap = StrokeCap.Round)
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(90f, 352f), Offset(89f, 470f), strokeWidth = 1.2f, cap = StrokeCap.Round)
        plateRivets.forEach { c ->
            drawCircle(Color(0xFF22282D), radius = 3.6f, center = c)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFF2F6F8), Color(0xFF7E8A93), Color(0xFF2E363C)), center = c - Offset(1f, 1f), radius = 3.4f),
                radius = 2.9f, center = c,
            )
        }

        // Glove cuff
        drawPath(cuff, cuffBrush)
        drawPath(cuff, seam, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
        drawLine(stitch.copy(alpha = 0.5f), Offset(54f, 330f), Offset(120f, 324f), strokeWidth = 0.9f)

        // Back of hand
        drawPath(handBack, handBackBrush)
        tendons.forEach { drawPath(it, Color(0xFFB48660).copy(alpha = 0.25f), style = Stroke(width = 2.2f, cap = StrokeCap.Round)) }
        drawPath(handBackStitch, stitch.copy(alpha = 0.7f), style = Stroke(width = 0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2.2f))))
        drawPath(handBack, seam, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
    }

    fun drawFingers(scope: DrawScope) = with(scope) {
        // Fingers from bottom (little finger) to top (index) so upper ones overlap.
        fingers.asReversed().forEach { r ->
            val p = Path().apply { addRoundRect(r) }
            // contact shadow on the grip/finger below
            drawRoundRect(
                Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(r.left + 1f, r.top + 3f),
                size = androidx.compose.ui.geometry.Size(r.width, r.height),
                cornerRadius = CornerRadius(9f, 9f),
            )
            drawPath(p, fingerBrush(r))
            clipPath(p) {
                drawCircle(knuckleBrush(r), radius = 9f, center = Offset(r.left + 8f, r.top + 6f))
            }
            // finger seam line at the curl
            drawLine(seam.copy(alpha = 0.7f), Offset(r.left + 17f, r.top + 3f), Offset(r.left + 17f, r.bottom - 3f), strokeWidth = 1f, cap = StrokeCap.Round)
            drawLine(Color(0xFFC79A72).copy(alpha = 0.35f), Offset(r.left + 22f, r.top + 3.5f), Offset(r.right - 8f, r.top + 3.5f), strokeWidth = 1.2f, cap = StrokeCap.Round)
            drawPath(p, leatherDark, style = Stroke(width = 1.1f))
        }

        // Thumb
        drawPath(thumb, Color.Black.copy(alpha = 0.3f), style = Stroke(width = 4f, join = StrokeJoin.Round))
        drawPath(thumb, thumbBrush)
        drawPath(thumbNailPlate, thumbNailBrush)
        drawPath(thumbNailPlate, Color(0xFF1E2328), style = Stroke(width = 0.8f))
        drawLine(stitch.copy(alpha = 0.6f), Offset(62f, 220f), Offset(96f, 238f), strokeWidth = 0.8f)
        drawPath(thumb, leatherDark, style = Stroke(width = 1.1f, join = StrokeJoin.Round))
    }
}
