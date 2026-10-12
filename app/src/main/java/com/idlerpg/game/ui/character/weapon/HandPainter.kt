package com.idlerpg.game.ui.character.weapon

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * Gloved right hand holding a weapon, defined in dagger unit space: the fingers wrap a grip at
 * x 42..58, y 216..289 around [DaggerPainter.GRIP_CENTER]. Every weapon painter uses the same grip
 * size, so this hand can hold any weapon (see [WeaponPainters]). Forearm and glove cuff come from
 * [ArmArt]; the empty hand is drawn by [FistPainter].
 */
internal object HandPainter {

    private val leatherDark = ArmArt.leatherDark
    private val seam = ArmArt.seam
    private val stitch = ArmArt.stitch

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

    /** Sleeve, vambrace, cuff and back of the hand — everything behind the weapon's grip. */
    fun drawArmBehind(scope: DrawScope) = with(scope) {
        drawArt(ArmArt.forearm)
        drawHandBack(this)
    }

    /** Glove cuff + back of the hand (no forearm). */
    fun drawHandBack(scope: DrawScope) = with(scope) {
        drawArt(ArmArt.cuff)

        drawPath(handBack, handBackBrush)
        tendons.forEach { drawPath(it, Color(0xFFB48660).copy(alpha = 0.25f), style = Stroke(width = 2.2f, cap = StrokeCap.Round)) }
        drawPath(handBackStitch, stitch.copy(alpha = 0.7f), style = Stroke(width = 0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2.2f))))
        drawPath(handBack, seam, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
    }

    /** Curled fingers + thumb in front of the weapon's grip. */
    fun drawFingers(scope: DrawScope) = with(scope) {
        // Fingers from bottom (little finger) to top (index) so upper ones overlap.
        fingers.asReversed().forEach { r ->
            val p = Path().apply { addRoundRect(r) }
            // contact shadow on the grip/finger below
            drawRoundRect(
                Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(r.left + 1f, r.top + 3f),
                size = Size(r.width, r.height),
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
