package com.idlerpg.game.ui.character.weapon

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.min

/**
 * High-resolution vector dagger, drawn with paths and gradients (no bitmaps), so it stays sharp at
 * any size. The dagger is defined in a local unit space of [WIDTH] × [HEIGHT] units, pointing up
 * (tip at y = 0, pommel at y = [HEIGHT]).
 *
 * Use [DaggerImage] for a fitted, standalone graphic, or [drawDagger] inside your own Canvas
 * (apply translate/rotate/scale first — everything incl. stroke widths scales with the transform).
 */
object DaggerPainter {
    const val WIDTH = 100f
    const val HEIGHT = 320f

    /** Center of the grip in unit space — natural pivot for hand/rotation transforms. */
    val GRIP_CENTER = Offset(50f, 252f)

    /** Tip of the blade in unit space. */
    val TIP = Offset(50f, 2f)

    // ---- Geometry (built once, immutable afterwards) ----

    internal val bladeLeft: Path by lazy {
        Path().apply {
            moveTo(50f, 2f)
            cubicTo(46f, 40f, 33f, 95f, 33.5f, 145f)
            cubicTo(34f, 165f, 37f, 178f, 38f, 188f)
            lineTo(50f, 188f)
            close()
        }
    }

    internal val bladeRight: Path by lazy {
        Path().apply {
            moveTo(50f, 2f)
            cubicTo(54f, 40f, 67f, 95f, 66.5f, 145f)
            cubicTo(66f, 165f, 63f, 178f, 62f, 188f)
            lineTo(50f, 188f)
            close()
        }
    }

    internal val bladeOutline: Path by lazy {
        Path().apply {
            moveTo(50f, 2f)
            cubicTo(46f, 40f, 33f, 95f, 33.5f, 145f)
            cubicTo(34f, 165f, 37f, 178f, 38f, 188f)
            lineTo(62f, 188f)
            cubicTo(63f, 178f, 66f, 165f, 66.5f, 145f)
            cubicTo(67f, 95f, 54f, 40f, 50f, 2f)
            close()
        }
    }

    /** Sharpened edge bevel (inner line parallel to the cutting edge). */
    private val leftBevel: Path by lazy {
        Path().apply {
            moveTo(50f, 8f)
            cubicTo(47f, 42f, 36.5f, 96f, 37f, 145f)
            cubicTo(37.4f, 163f, 39.6f, 176f, 40.5f, 186f)
        }
    }
    private val rightBevel: Path by lazy {
        Path().apply {
            moveTo(50f, 8f)
            cubicTo(53f, 42f, 63.5f, 96f, 63f, 145f)
            cubicTo(62.6f, 163f, 60.4f, 176f, 59.5f, 186f)
        }
    }
    private val leftEdge: Path by lazy {
        Path().apply {
            moveTo(50f, 2.5f)
            cubicTo(46f, 40f, 33.2f, 95f, 33.7f, 145f)
            cubicTo(34.2f, 165f, 37.2f, 178f, 38.2f, 187f)
        }
    }
    private val rightEdge: Path by lazy {
        Path().apply {
            moveTo(50f, 2.5f)
            cubicTo(54f, 40f, 66.8f, 95f, 66.3f, 145f)
            cubicTo(65.8f, 165f, 62.8f, 178f, 61.8f, 187f)
        }
    }

    /** Fuller ("Blutrinne"): tapered groove down the middle of the blade. */
    private val fuller: Path by lazy {
        Path().apply {
            moveTo(50f, 34f)
            cubicTo(51.4f, 34f, 52f, 36f, 52f, 40f)
            cubicTo(52.4f, 90f, 53.3f, 140f, 53.4f, 172f)
            cubicTo(53.4f, 177f, 51.8f, 179f, 50f, 179f)
            cubicTo(48.2f, 179f, 46.6f, 177f, 46.6f, 172f)
            cubicTo(46.7f, 140f, 47.6f, 90f, 48f, 40f)
            cubicTo(48f, 36f, 48.6f, 34f, 50f, 34f)
            close()
        }
    }

    private val ricasso: Path by lazy {
        Path().apply {
            moveTo(38f, 187f)
            lineTo(62f, 187f)
            lineTo(61f, 199f)
            lineTo(39f, 199f)
            close()
        }
    }

    private val guard: Path by lazy {
        Path().apply {
            moveTo(7f, 196.5f)
            cubicTo(15f, 200.5f, 30f, 198.5f, 50f, 198.5f)
            cubicTo(70f, 198.5f, 85f, 200.5f, 93f, 196.5f)
            cubicTo(98f, 195f, 99.5f, 203.5f, 95f, 205.5f)
            cubicTo(86f, 210f, 70f, 211.5f, 50f, 211.5f)
            cubicTo(30f, 211.5f, 14f, 210f, 5f, 205.5f)
            cubicTo(0.5f, 203.5f, 2f, 195f, 7f, 196.5f)
            close()
        }
    }

    private val guardBlock: RoundRect = RoundRect(39.5f, 193f, 60.5f, 217f, CornerRadius(5f, 5f))

    internal val grip: Path by lazy {
        Path().apply {
            moveTo(42.5f, 216f)
            cubicTo(40.6f, 240f, 40.6f, 266f, 42.5f, 289f)
            lineTo(57.5f, 289f)
            cubicTo(59.4f, 266f, 59.4f, 240f, 57.5f, 216f)
            close()
        }
    }

    private val pommel: Path by lazy {
        Path().apply {
            moveTo(50f, 289f)
            cubicTo(60f, 289f, 64f, 297f, 63f, 304f)
            cubicTo(62f, 313f, 56f, 318.5f, 50f, 318.5f)
            cubicTo(44f, 318.5f, 38f, 313f, 37f, 304f)
            cubicTo(36f, 297f, 40f, 289f, 50f, 289f)
            close()
        }
    }

    // ---- Palette ----

    private val outline = Color(0xFF1E2328)
    private val brassDark = Color(0xFF3A2506)
    private val brassStops = arrayOf(
        0f to Color(0xFFFFEDB3),
        0.35f to Color(0xFFE2B650),
        0.7f to Color(0xFF9A6B1E),
        1f to Color(0xFF51330A),
    )

    private val leftFacet = Brush.linearGradient(
        colorStops = arrayOf(0f to Color(0xFF8E9AA4), 0.55f to Color(0xFFDCE4EA), 1f to Color(0xFFF4F8FA)),
        start = Offset(33f, 0f), end = Offset(50f, 0f),
    )
    private val rightFacet = Brush.linearGradient(
        colorStops = arrayOf(0f to Color(0xFF7D8992), 0.6f to Color(0xFF5A666F), 1f to Color(0xFF6E7A83)),
        start = Offset(50f, 0f), end = Offset(67f, 0f),
    )
    private val bladeReflections = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.30f),
            0.18f to Color.Transparent,
            0.42f to Color.White.copy(alpha = 0.22f),
            0.52f to Color.Transparent,
            0.80f to Color.Black.copy(alpha = 0.05f),
            1f to Color.Black.copy(alpha = 0.28f),
        ),
        startY = 0f, endY = 190f,
    )
    private val fullerBrush = Brush.linearGradient(
        colorStops = arrayOf(0f to Color(0xFF2E363C), 0.45f to Color(0xFF59656E), 1f to Color(0xFFE4ECF1)),
        start = Offset(46.6f, 0f), end = Offset(53.4f, 0f),
    )
    private val ricassoBrush = Brush.linearGradient(
        colorStops = arrayOf(0f to Color(0xFF6D7881), 0.45f to Color(0xFFB9C4CB), 0.55f to Color(0xFF7B8790), 1f to Color(0xFF4B555D)),
        start = Offset(38f, 0f), end = Offset(62f, 0f),
    )
    private val guardBrush = Brush.verticalGradient(colorStops = brassStops, startY = 196f, endY = 212f)
    private val guardSheen = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Black.copy(alpha = 0.35f),
            0.3f to Color.White.copy(alpha = 0.18f),
            0.5f to Color.Transparent,
            0.72f to Color.White.copy(alpha = 0.10f),
            1f to Color.Black.copy(alpha = 0.40f),
        ),
        startX = 2f, endX = 98f,
    )
    private val blockBrush = Brush.linearGradient(
        colorStops = brassStops, start = Offset(42f, 193f), end = Offset(58f, 217f),
    )
    private val gemBrush = Brush.radialGradient(
        colorStops = arrayOf(0f to Color(0xFFFFB4A8), 0.3f to Color(0xFFE53935), 0.75f to Color(0xFF8E1414), 1f to Color(0xFF3E0505)),
        center = Offset(48.6f, 203.4f), radius = 5.2f,
    )
    private val leatherBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF1F120A),
            0.25f to Color(0xFF5E3B24),
            0.45f to Color(0xFF8C5D3A),
            0.7f to Color(0xFF4A2D1B),
            1f to Color(0xFF1A0E07),
        ),
        startX = 41f, endX = 59f,
    )
    private val wrapBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF2A190E),
            0.3f to Color(0xFF7A5034),
            0.48f to Color(0xFFA77550),
            0.7f to Color(0xFF5A3822),
            1f to Color(0xFF22140B),
        ),
        startX = 41f, endX = 59f,
    )
    private val ferruleBrush = Brush.horizontalGradient(
        colorStops = arrayOf(0f to Color(0xFF51330A), 0.35f to Color(0xFFF1CE79), 0.55f to Color(0xFFB98A30), 1f to Color(0xFF3F2706)),
        startX = 41f, endX = 59f,
    )
    private val pommelBrush = Brush.radialGradient(
        colorStops = arrayOf(0f to Color(0xFFFFF4CF), 0.3f to Color(0xFFE6BC58), 0.75f to Color(0xFF8C5E17), 1f to Color(0xFF3F2706)),
        center = Offset(45f, 298f), radius = 19f,
    )
    private fun knobBrush(cx: Float) = Brush.radialGradient(
        colorStops = arrayOf(0f to Color(0xFFFFF1C2), 0.4f to Color(0xFFD9A944), 1f to Color(0xFF4F3209)),
        center = Offset(cx - 1.4f, 199.6f), radius = 6f,
    )

    /** Rust patches: (center, radius, strength) — tuned so they sit near the edges and the base. */
    private val rustSpots = listOf(
        Triple(Offset(36f, 150f), 9f, 1f),
        Triple(Offset(63f, 168f), 11f, 0.9f),
        Triple(Offset(42f, 182f), 8f, 1f),
        Triple(Offset(58f, 110f), 6f, 0.7f),
        Triple(Offset(39f, 92f), 5f, 0.6f),
        Triple(Offset(52f, 60f), 4f, 0.45f),
        Triple(Offset(61f, 135f), 5f, 0.8f),
    )
    private val rustPits = listOf(
        Offset(37.5f, 153f) to 0.9f, Offset(40f, 147f) to 0.6f, Offset(61.5f, 171f) to 0.8f,
        Offset(64f, 164f) to 0.6f, Offset(44f, 183f) to 0.7f, Offset(57f, 112f) to 0.5f,
        Offset(40.5f, 95f) to 0.5f, Offset(60f, 138f) to 0.6f,
    )

    /**
     * Draws the dagger into unit space (0..[WIDTH], 0..[HEIGHT]) of the current transform.
     *
     * @param rust 0f = polished steel, 1f = heavily rusted blade.
     * @param drawGrip false skips grip + pommel interior (e.g. if a hand covers it). Default true.
     */
    fun DrawScope.drawDaggerUnits(rust: Float = 0f, drawGrip: Boolean = true) {
        drawBlade(rust.coerceIn(0f, 1f))
        drawHilt(drawGrip)
    }

    private fun DrawScope.drawBlade(rust: Float) {
        // Soft dark backing so the blade reads on bright backgrounds.
        drawPath(bladeOutline, outline, style = Stroke(width = 2.2f, join = StrokeJoin.Round))
        drawPath(bladeLeft, leftFacet)
        drawPath(bladeRight, rightFacet)
        drawPath(bladeOutline, bladeReflections)

        // Edge bevels: polished cutting edges catch more light.
        drawPath(leftEdge, Color.White.copy(alpha = 0.85f), style = Stroke(width = 1.1f, cap = StrokeCap.Round))
        drawPath(leftBevel, Color(0xFF7F8B94).copy(alpha = 0.55f), style = Stroke(width = 0.7f, cap = StrokeCap.Round))
        drawPath(rightEdge, Color(0xFFC9D3DA).copy(alpha = 0.55f), style = Stroke(width = 0.9f, cap = StrokeCap.Round))
        drawPath(rightBevel, Color(0xFF3B444B).copy(alpha = 0.5f), style = Stroke(width = 0.7f, cap = StrokeCap.Round))

        // Center ridge above the fuller.
        drawLine(Color.White.copy(alpha = 0.7f), Offset(50f, 6f), Offset(50f, 34f), strokeWidth = 0.8f, cap = StrokeCap.Round)

        // Fuller with inner shadow and bright lower lip.
        drawPath(fuller, fullerBrush)
        drawPath(fuller, Color(0xFF1C2227).copy(alpha = 0.8f), style = Stroke(width = 0.6f))
        drawLine(Color.White.copy(alpha = 0.55f), Offset(52.6f, 44f), Offset(53.3f, 170f), strokeWidth = 0.5f, cap = StrokeCap.Round)

        // Rust / patina.
        if (rust > 0f) {
            clipPath(bladeOutline) {
                rustSpots.forEach { (c, r, k) ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color(0xFF7A3A12).copy(alpha = 0.75f * rust * k),
                                0.55f to Color(0xFFA4561F).copy(alpha = 0.45f * rust * k),
                                1f to Color.Transparent,
                            ),
                            center = c, radius = r,
                        ),
                        radius = r, center = c,
                    )
                }
                rustPits.forEach { (c, k) ->
                    drawCircle(Color(0xFF3B1A07).copy(alpha = 0.8f * rust * k), radius = 0.7f, center = c)
                }
            }
        }

        // Crisp outline last.
        drawPath(bladeOutline, outline.copy(alpha = 0.9f), style = Stroke(width = 0.8f, join = StrokeJoin.Round))
    }

    private fun DrawScope.drawHilt(drawGrip: Boolean) {
        // Ricasso
        drawPath(ricasso, ricassoBrush)
        drawPath(ricasso, outline, style = Stroke(width = 0.7f))

        if (drawGrip) {
            drawPath(grip, leatherBrush)
            clipPath(grip) {
                // Diagonal leather wraps.
                var y = 210f
                while (y < 292f) {
                    val band = Path().apply {
                        moveTo(40f, y)
                        lineTo(60f, y + 4.5f)
                        lineTo(60f, y + 11f)
                        lineTo(40f, y + 6.5f)
                        close()
                    }
                    drawPath(band, wrapBrush)
                    drawLine(Color(0xFFD7A57A).copy(alpha = 0.35f), Offset(40f, y + 0.6f), Offset(60f, y + 5.1f), strokeWidth = 0.6f)
                    drawLine(Color.Black.copy(alpha = 0.65f), Offset(40f, y + 6.5f), Offset(60f, y + 11f), strokeWidth = 1.1f)
                    y += 7.5f
                }
            }
            drawPath(grip, Color(0xFF120A05), style = Stroke(width = 0.8f))
            // Ferrules
            drawRoundRect(ferruleBrush, Offset(41.2f, 213.5f), Size(17.6f, 5f), CornerRadius(1.2f, 1.2f))
            drawRoundRect(ferruleBrush, Offset(41.2f, 286f), Size(17.6f, 5f), CornerRadius(1.2f, 1.2f))
            drawRoundRect(brassDark, Offset(41.2f, 213.5f), Size(17.6f, 5f), CornerRadius(1.2f, 1.2f), style = Stroke(0.6f))
            drawRoundRect(brassDark, Offset(41.2f, 286f), Size(17.6f, 5f), CornerRadius(1.2f, 1.2f), style = Stroke(0.6f))
        }

        // Pommel
        drawPath(pommel, pommelBrush)
        drawPath(pommel, brassDark, style = Stroke(width = 0.9f))
        drawOval(Color.White.copy(alpha = 0.65f), Offset(41.5f, 293.5f), Size(6f, 3.4f))
        drawLine(brassDark.copy(alpha = 0.6f), Offset(38.5f, 304f), Offset(61.5f, 304f), strokeWidth = 0.6f)

        // Crossguard with knobbed quillons.
        drawPath(guard, guardBrush)
        drawPath(guard, guardSheen)
        drawPath(guard, brassDark, style = Stroke(width = 0.9f, join = StrokeJoin.Round))
        drawCircle(knobBrush(5f), radius = 5f, center = Offset(5f, 201f))
        drawCircle(brassDark, radius = 5f, center = Offset(5f, 201f), style = Stroke(0.8f))
        drawCircle(knobBrush(95f), radius = 5f, center = Offset(95f, 201f))
        drawCircle(brassDark, radius = 5f, center = Offset(95f, 201f), style = Stroke(0.8f))
        drawLine(Color.White.copy(alpha = 0.45f), Offset(14f, 200.5f), Offset(38f, 200f), strokeWidth = 0.7f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.3f), Offset(62f, 200f), Offset(86f, 200.5f), strokeWidth = 0.7f, cap = StrokeCap.Round)

        // Quillon block with ruby.
        val block = Path().apply { addRoundRect(guardBlock) }
        drawPath(block, blockBrush)
        drawPath(block, brassDark, style = Stroke(width = 0.9f))
        drawCircle(Color(0xFF3A2506), radius = 5.4f, center = Offset(50f, 205f))
        drawCircle(gemBrush, radius = 4.4f, center = Offset(50f, 205f))
        drawCircle(Color.White.copy(alpha = 0.85f), radius = 1.1f, center = Offset(48.4f, 203.2f))
        drawCircle(Color.White.copy(alpha = 0.3f), radius = 0.6f, center = Offset(51.8f, 207f))
    }
}

/**
 * Draws the dagger fitted (aspect-preserving, centered) into [size] / the whole draw area.
 * Convenience for custom Canvases.
 */
fun DrawScope.drawDagger(rust: Float = 0f, size: Size = this.size, topLeft: Offset = Offset.Zero) {
    val s = min(size.width / DaggerPainter.WIDTH, size.height / DaggerPainter.HEIGHT)
    val dx = topLeft.x + (size.width - DaggerPainter.WIDTH * s) / 2f
    val dy = topLeft.y + (size.height - DaggerPainter.HEIGHT * s) / 2f
    withTransform({
        translate(dx, dy)
        scale(s, s, pivot = Offset.Zero)
    }) {
        with(DaggerPainter) { drawDaggerUnits(rust) }
    }
}

/**
 * Standalone high-res dagger graphic, pointing up. Aspect ratio 100:320 — give it e.g.
 * `Modifier.size(width = 60.dp, height = 192.dp)` or rotate it via `Modifier.rotate(...)`.
 *
 * @param rust 0f = polished steel, 1f = heavily rusted.
 */
@Composable
fun DaggerImage(
    modifier: Modifier = Modifier,
    rust: Float = 0f,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.then(semantics)) { drawDagger(rust) }
}
