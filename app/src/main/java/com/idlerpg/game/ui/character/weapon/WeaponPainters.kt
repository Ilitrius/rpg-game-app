package com.idlerpg.game.ui.character.weapon

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * High-resolution vector weapons (paths + gradients, no bitmaps) for every weapon base type.
 *
 * **Common coordinate system:** every weapon is defined in its own unit space, pointing up (-y),
 * with the grip axis at x = 50 and a 16-unit wide, ~74-unit long grip around [WeaponShape.gripCenter]
 * — exactly the grip of [DaggerPainter]. So the same [HandPainter] holds every weapon: translate
 * by `-gripCenter` and the grip lands in the gloved hand. Stroke widths are in units and scale
 * with the transform.
 */
internal interface WeaponShape {
    /** Bounding box of the drawing in unit space (for fitting). */
    val bounds: Rect

    /** Center of the grip — where the hand holds the weapon. */
    val gripCenter: Offset

    /** Striking point (blade tip / head center), e.g. for motion trails. */
    val strikePoint: Offset

    /** Outline of the striking part (blade or head) — used for the rarity glow and shimmer. */
    val glowPath: Path

    fun DrawScope.drawUnits(look: WeaponLook)
}

internal object WeaponPainters {

    fun shapeOf(kind: WeaponKind): WeaponShape? = when (kind) {
        WeaponKind.RUSTY_DAGGER, WeaponKind.DAGGER -> DaggerShape
        WeaponKind.SHORT_SWORD -> SwordPainter
        WeaponKind.MACE -> MacePainter
        WeaponKind.AXE -> AxePainter
        WeaponKind.FIST -> null
    }

    /**
     * Draws the weapon of [look] in its unit space incl. rarity effects.
     * @param shimmer 0..1 phase of the legendary shimmer band (animate it for a moving sheen).
     */
    fun DrawScope.drawWeaponUnits(look: WeaponLook, shimmer: Float = 0f) {
        val shape = shapeOf(look.kind) ?: return
        val glow = glowColor(look.rarity)
        if (glow != null) drawRarityGlow(shape.glowPath, glow)
        with(shape) { drawUnits(look) }
        if (glow != null) drawRaritySheen(shape.glowPath, glow, look.rarity, shimmer)
    }

    /** Glow color for EPIC (purple) / LEGENDARY (orange); null for lower rarities. */
    fun glowColor(rarity: Rarity): Color? = when (rarity) {
        Rarity.EPIC -> Color(0xFFC77DFF)
        Rarity.LEGENDARY -> Color(0xFFFFA43A)
        else -> null
    }

    private fun DrawScope.drawRarityGlow(p: Path, c: Color) {
        drawPath(p, c.copy(alpha = 0.07f), style = Stroke(width = 22f, join = StrokeJoin.Round))
        drawPath(p, c.copy(alpha = 0.12f), style = Stroke(width = 12f, join = StrokeJoin.Round))
        drawPath(p, c.copy(alpha = 0.22f), style = Stroke(width = 5f, join = StrokeJoin.Round))
    }

    private fun DrawScope.drawRaritySheen(p: Path, c: Color, rarity: Rarity, shimmer: Float) {
        val b = p.getBounds()
        clipPath(p) {
            // Subtle tint of the metal.
            drawRect(c.copy(alpha = if (rarity == Rarity.LEGENDARY) 0.10f else 0.09f), b.topLeft, b.size)
            if (rarity == Rarity.LEGENDARY) {
                // Diagonal light band sweeping from the tip down to the hilt.
                val t = (shimmer % 1f + 1f) % 1f
                val cy = b.top - 40f + (b.height + 80f) * t
                drawRect(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.42f to Color(0xFFFFE2A8).copy(alpha = 0.10f),
                            0.5f to Color(0xFFFFF4DC).copy(alpha = 0.55f),
                            0.58f to Color(0xFFFFE2A8).copy(alpha = 0.10f),
                            1f to Color.Transparent,
                        ),
                        start = Offset(b.left, cy - 30f), end = Offset(b.right, cy + 10f),
                    ),
                    b.topLeft, b.size,
                )
            }
        }
        // Rim light along the edge.
        drawPath(p, c.copy(alpha = 0.55f), style = Stroke(width = 0.9f, join = StrokeJoin.Round))
    }
}

// ============================================================================ shared helpers

private val Outline = Color(0xFF14171B)
private val BrassDark = Color(0xFF3A2506)

private val steelStops = arrayOf(
    0f to Color(0xFF4A545C), 0.28f to Color(0xFFDCE4E9), 0.5f to Color(0xFF9AA6AE),
    0.78f to Color(0xFF5F6A72), 1f to Color(0xFF2F363C),
)
private val darkSteelStops = arrayOf(
    0f to Color(0xFF353C42), 0.3f to Color(0xFF9DA8B0), 0.55f to Color(0xFF5E6870), 1f to Color(0xFF22282D),
)
private val goldStops = arrayOf(
    0f to Color(0xFF5A3A0A), 0.25f to Color(0xFFFFEDB3), 0.5f to Color(0xFFE2B650),
    0.78f to Color(0xFF9A6B1E), 1f to Color(0xFF4A2F08),
)
private val leatherStops = arrayOf(
    0f to Color(0xFF2A190D), 0.22f to Color(0xFF7A4E2D), 0.45f to Color(0xFFA6744A),
    0.72f to Color(0xFF603C22), 1f to Color(0xFF26160B),
)
private val darkLeatherStops = arrayOf(
    0f to Color(0xFF1A0F07), 0.3f to Color(0xFF4E301B), 0.5f to Color(0xFF6A4327), 1f to Color(0xFF170D06),
)
private val woodStops = arrayOf(
    0f to Color(0xFF2B1A0E), 0.25f to Color(0xFF6B4528), 0.45f to Color(0xFF9A6B42),
    0.72f to Color(0xFF5A3920), 1f to Color(0xFF22140A),
)

private fun linear(stops: Array<Pair<Float, Color>>, x0: Float, y0: Float, x1: Float, y1: Float) =
    Brush.linearGradient(colorStops = stops, start = Offset(x0, y0), end = Offset(x1, y1))

private fun radial(cx: Float, cy: Float, r: Float, vararg stops: Pair<Float, Color>) =
    Brush.radialGradient(colorStops = stops, center = Offset(cx, cy), radius = r)

private fun path(block: Path.() -> Unit) = Path().apply(block)

/** Builds a path and its mirror image at x = 50 with the same [block]. */
private fun mirrored(block: Path.(mx: (Float) -> Float) -> Unit) = Path().apply { block { 100f - it } }

private fun DrawScope.fillOutlined(p: Path, brush: Brush, outline: Color = Outline, width: Float = 1f) {
    drawPath(p, brush)
    drawPath(p, outline, style = Stroke(width, join = StrokeJoin.Round))
}

private fun DrawScope.rivet(c: Offset, r: Float, gold: Boolean = false) {
    val (hi, mid, lo) = if (gold) {
        Triple(Color(0xFFFFF3CC), Color(0xFFD9A944), Color(0xFF4F3209))
    } else {
        Triple(Color(0xFFFFFFFF), Color(0xFF9AA6AE), Color(0xFF262C31))
    }
    drawCircle(radial(c.x - r * 0.35f, c.y - r * 0.35f, r * 1.3f, 0f to hi, 0.45f to mid, 1f to lo), r, c)
    drawCircle(Outline.copy(alpha = 0.85f), r, c, style = Stroke(r * 0.28f))
}

/** Gem colors (highlight, body, shadow) by rarity. */
private fun gemColors(rarity: Rarity): Triple<Color, Color, Color> = when (rarity) {
    Rarity.COMMON -> Triple(Color(0xFFFFB4A8), Color(0xFFC62828), Color(0xFF3E0505)) // ruby
    Rarity.UNCOMMON -> Triple(Color(0xFFC8F7D4), Color(0xFF2EAD5B), Color(0xFF0A4220))
    Rarity.RARE -> Triple(Color(0xFFCFE6FF), Color(0xFF1E73D8), Color(0xFF0A2860))
    Rarity.EPIC -> Triple(Color(0xFFF0D2F7), Color(0xFF9C3FCB), Color(0xFF380C52))
    Rarity.LEGENDARY -> Triple(Color(0xFFFFE9C4), Color(0xFFFF8F1F), Color(0xFF702900))
}

/** Cabochon gem in a gold bezel. */
private fun DrawScope.gem(c: Offset, rx: Float, ry: Float, rarity: Rarity) {
    val (light, mid, dark) = gemColors(rarity)
    drawOval(BrassDark, Offset(c.x - rx - 1.1f, c.y - ry - 1.1f), Size((rx + 1.1f) * 2, (ry + 1.1f) * 2))
    drawOval(
        Brush.radialGradient(
            colorStops = arrayOf(0f to light, 0.32f to mid, 0.78f to dark, 1f to Color.Black),
            center = Offset(c.x - rx * 0.3f, c.y - ry * 0.35f), radius = maxOf(rx, ry) * 1.25f,
        ),
        Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2),
    )
    drawOval(Color.White.copy(alpha = 0.85f), Offset(c.x - rx * 0.55f, c.y - ry * 0.62f), Size(rx * 0.55f, ry * 0.4f))
    drawCircle(Color.White.copy(alpha = 0.35f), minOf(rx, ry) * 0.14f, Offset(c.x + rx * 0.4f, c.y + ry * 0.42f))
}

/** Leather grip with diagonal wraps, clipped to [grip]. */
private fun DrawScope.wrappedGrip(grip: Path, x0: Float, x1: Float, y0: Float, y1: Float, step: Float = 7.5f) {
    drawPath(grip, linear(darkLeatherStops, x0, 0f, x1, 0f))
    clipPath(grip) {
        var y = y0 - 8f
        while (y < y1 + 4f) {
            val band = path {
                moveTo(x0 - 1, y); lineTo(x1 + 1, y + 4.5f); lineTo(x1 + 1, y + step + 3f); lineTo(x0 - 1, y + step - 1.5f); close()
            }
            drawPath(band, linear(leatherStops, x0, 0f, x1, 0f))
            drawLine(Color(0xFFD7A57A).copy(alpha = 0.35f), Offset(x0, y + 0.6f), Offset(x1, y + 5.1f), 0.6f)
            drawLine(Color.Black.copy(alpha = 0.6f), Offset(x0, y + step - 1.5f), Offset(x1, y + step + 3f), 1.1f)
            y += step
        }
    }
    drawPath(grip, Color(0xFF120A05), style = Stroke(0.8f))
}

/** Wood grain strokes along a vertical haft. */
private fun DrawScope.woodGrain(xs: List<Float>, y0: Float, y1: Float) {
    xs.forEachIndexed { i, x ->
        val wob = if (i % 2 == 0) 0.7f else -0.6f
        drawPath(
            path { moveTo(x, y0); cubicTo(x + wob, y0 + (y1 - y0) * 0.3f, x - wob, y0 + (y1 - y0) * 0.65f, x + wob * 0.4f, y1) },
            Color(0xFF1E1108).copy(alpha = 0.32f), style = Stroke(0.55f, cap = StrokeCap.Round),
        )
    }
    // knot
    val k = Offset(xs.first() + 2.4f, y0 + (y1 - y0) * 0.55f)
    drawOval(Color(0xFF1E1108).copy(alpha = 0.35f), Offset(k.x - 1.6f, k.y - 3f), Size(3.2f, 6f), style = Stroke(0.5f))
}

// ============================================================================ dagger adapter

private object DaggerShape : WeaponShape {
    override val bounds = Rect(0f, 0f, DaggerPainter.WIDTH, DaggerPainter.HEIGHT)
    override val gripCenter = DaggerPainter.GRIP_CENTER
    override val strikePoint = DaggerPainter.TIP
    override val glowPath: Path get() = DaggerPainter.bladeOutline
    override fun DrawScope.drawUnits(look: WeaponLook) {
        with(DaggerPainter) { drawDaggerUnits(look.rust) }
    }
}

// ============================================================================ short sword

/** Kurzschwert: broad double-edged blade with fuller, steel crossguard, wire grip, wheel pommel. */
internal object SwordPainter : WeaponShape {
    override val bounds = Rect(-1f, 0f, 101f, 392f)
    override val gripCenter = Offset(50f, 318f)
    override val strikePoint = Offset(50f, 2f)

    private val bladeLeft = path {
        moveTo(50f, 2f); cubicTo(47f, 14f, 39.5f, 30f, 36.5f, 46f)
        cubicTo(35.4f, 110f, 38.4f, 170f, 37.8f, 214f); cubicTo(37.5f, 236f, 37.8f, 250f, 38.5f, 262f)
        lineTo(50f, 262f); close()
    }
    private val bladeRight = mirrored { m ->
        moveTo(m(50f), 2f); cubicTo(m(47f), 14f, m(39.5f), 30f, m(36.5f), 46f)
        cubicTo(m(35.4f), 110f, m(38.4f), 170f, m(37.8f), 214f); cubicTo(m(37.5f), 236f, m(37.8f), 250f, m(38.5f), 262f)
        lineTo(50f, 262f); close()
    }
    private val blade = path {
        moveTo(50f, 2f); cubicTo(47f, 14f, 39.5f, 30f, 36.5f, 46f)
        cubicTo(35.4f, 110f, 38.4f, 170f, 37.8f, 214f); cubicTo(37.5f, 236f, 37.8f, 250f, 38.5f, 262f)
        lineTo(61.5f, 262f)
        cubicTo(62.2f, 250f, 62.5f, 236f, 62.2f, 214f); cubicTo(61.6f, 170f, 64.6f, 110f, 63.5f, 46f)
        cubicTo(60.5f, 30f, 53f, 14f, 50f, 2f); close()
    }
    override val glowPath: Path get() = blade

    private val leftEdge = path {
        moveTo(49.6f, 4f); cubicTo(46.7f, 15f, 39.6f, 31f, 36.8f, 46f)
        cubicTo(35.7f, 110f, 38.7f, 170f, 38.1f, 214f); cubicTo(37.8f, 236f, 38.1f, 250f, 38.8f, 260f)
    }
    private val leftBevel = path {
        moveTo(50f, 13f); cubicTo(47.6f, 22f, 42.6f, 34f, 40.4f, 48f)
        cubicTo(39.4f, 110f, 42f, 170f, 41.4f, 214f); cubicTo(41.2f, 236f, 41.4f, 250f, 41.9f, 260f)
    }
    private val rightEdge = mirrored { m ->
        moveTo(m(49.6f), 4f); cubicTo(m(46.7f), 15f, m(39.6f), 31f, m(36.8f), 46f)
        cubicTo(m(35.7f), 110f, m(38.7f), 170f, m(38.1f), 214f); cubicTo(m(37.8f), 236f, m(38.1f), 250f, m(38.8f), 260f)
    }
    private val rightBevel = mirrored { m ->
        moveTo(m(50f), 13f); cubicTo(m(47.6f), 22f, m(42.6f), 34f, m(40.4f), 48f)
        cubicTo(m(39.4f), 110f, m(42f), 170f, m(41.4f), 214f); cubicTo(m(41.2f), 236f, m(41.4f), 250f, m(41.9f), 260f)
    }
    private val fuller = path {
        moveTo(47f, 62f); quadraticTo(47f, 54f, 50f, 54f); quadraticTo(53f, 54f, 53f, 62f)
        lineTo(53.2f, 236f); quadraticTo(53.2f, 242f, 50f, 242f); quadraticTo(46.8f, 242f, 46.8f, 236f); close()
    }
    private val ricasso = path { moveTo(38.5f, 258f); lineTo(61.5f, 258f); lineTo(60.8f, 270f); lineTo(39.2f, 270f); close() }
    private val guard = path {
        moveTo(4f, 261f); quadraticTo(9f, 269.5f, 21f, 269.5f); lineTo(79f, 269.5f); quadraticTo(91f, 269.5f, 96f, 261f)
        quadraticTo(101.5f, 261f, 100.5f, 267.5f); quadraticTo(97f, 281f, 80f, 281f); lineTo(20f, 281f)
        quadraticTo(3f, 281f, -0.5f, 267.5f); quadraticTo(-1.5f, 261f, 4f, 261f); close()
    }
    private val guardBlock = path {
        moveTo(50f, 259f); lineTo(61.5f, 266f); lineTo(61.5f, 280f); lineTo(50f, 289f); lineTo(38.5f, 280f); lineTo(38.5f, 266f); close()
    }
    private val grip = path {
        moveTo(42.5f, 284f); cubicTo(41f, 306f, 41f, 330f, 42.5f, 352f); lineTo(57.5f, 352f)
        cubicTo(59f, 330f, 59f, 306f, 57.5f, 284f); close()
    }

    override fun DrawScope.drawUnits(look: WeaponLook) {
        // ---- Blade
        drawPath(blade, Outline, style = Stroke(2.2f, join = StrokeJoin.Round))
        drawPath(bladeLeft, linear(arrayOf(0f to Color(0xFF8E9AA4), 0.55f to Color(0xFFDCE4EA), 1f to Color(0xFFF4F8FA)), 36f, 0f, 50f, 0f))
        drawPath(bladeRight, linear(arrayOf(0f to Color(0xFF7D8992), 0.6f to Color(0xFF5A666F), 1f to Color(0xFF6E7A83)), 50f, 0f, 64f, 0f))
        drawPath(
            blade,
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.32f), 0.16f to Color.Transparent,
                    0.4f to Color.White.copy(alpha = 0.22f), 0.5f to Color.Transparent,
                    0.82f to Color.Black.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0.3f),
                ),
                startY = 0f, endY = 264f,
            ),
        )
        drawPath(leftEdge, Color.White.copy(alpha = 0.85f), style = Stroke(1.1f, cap = StrokeCap.Round))
        drawPath(leftBevel, Color(0xFF6F7B84).copy(alpha = 0.6f), style = Stroke(0.7f, cap = StrokeCap.Round))
        drawPath(rightEdge, Color(0xFFC9D3DA).copy(alpha = 0.55f), style = Stroke(0.9f, cap = StrokeCap.Round))
        drawPath(rightBevel, Color(0xFF30383E).copy(alpha = 0.55f), style = Stroke(0.7f, cap = StrokeCap.Round))
        drawLine(Color.White.copy(alpha = 0.7f), Offset(50f, 8f), Offset(50f, 54f), 0.8f, StrokeCap.Round)
        drawPath(fuller, linear(arrayOf(0f to Color(0xFF2E363C), 0.45f to Color(0xFF59656E), 1f to Color(0xFFE4ECF1)), 46.8f, 0f, 53.2f, 0f))
        drawPath(fuller, Color(0xFF1C2227).copy(alpha = 0.8f), style = Stroke(0.6f))
        drawLine(Color.White.copy(alpha = 0.5f), Offset(52.7f, 64f), Offset(52.9f, 234f), 0.5f, StrokeCap.Round)
        // Etched runes near the hilt
        for (i in 0 until 3) {
            val y = 200f + i * 13f
            drawPath(
                path { moveTo(44f, y); lineTo(42.6f, y + 5f); moveTo(44f, y); lineTo(45.4f, y + 6f); moveTo(42.9f, y + 3.6f); lineTo(45.2f, y + 3f) },
                Color(0xFF2E363C).copy(alpha = 0.45f), style = Stroke(0.5f, cap = StrokeCap.Round),
            )
        }
        drawPath(blade, Outline.copy(alpha = 0.9f), style = Stroke(0.8f, join = StrokeJoin.Round))

        // ---- Ricasso
        fillOutlined(ricasso, linear(arrayOf(0f to Color(0xFF6D7881), 0.45f to Color(0xFFB9C4CB), 0.55f to Color(0xFF7B8790), 1f to Color(0xFF4B555D)), 38.5f, 0f, 61.5f, 0f), width = 0.7f)

        // ---- Grip: dark leather core with twisted silver wire.
        drawPath(grip, linear(darkLeatherStops, 42f, 0f, 58f, 0f))
        clipPath(grip) {
            var y = 280f
            while (y < 356f) {
                drawLine(Color(0xFFE9EEF2), Offset(41f, y), Offset(59f, y + 3.2f), 1.5f)
                drawLine(Color(0xFF5B656D), Offset(41f, y + 1.3f), Offset(59f, y + 4.5f), 0.9f)
                y += 3.3f
            }
            drawRect(
                linear(arrayOf(0f to Color.Black.copy(alpha = 0.55f), 0.35f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)), 42f, 0f, 58f, 0f),
                Offset(40f, 280f), Size(20f, 76f),
            )
        }
        drawPath(grip, Outline, style = Stroke(0.8f))
        drawRoundRect(linear(steelStops, 42f, 0f, 58f, 0f), Offset(42f, 349f), Size(16f, 7f), CornerRadius(1.6f))
        drawRoundRect(Outline, Offset(42f, 349f), Size(16f, 7f), CornerRadius(1.6f), style = Stroke(0.7f))

        // ---- Wheel pommel
        val pc = Offset(50f, 370f)
        drawCircle(radial(44f, 363f, 22f, 0f to Color(0xFFF2F6F8), 0.4f to Color(0xFF9AA6AE), 1f to Color(0xFF2B3237)), 15f, pc)
        drawCircle(Outline, 15f, pc, style = Stroke(0.9f))
        drawCircle(Color(0xFF2B3237).copy(alpha = 0.7f), 9.6f, pc, style = Stroke(1.5f))
        drawCircle(Color.White.copy(alpha = 0.5f), 10.4f, Offset(pc.x - 0.6f, pc.y - 0.6f), style = Stroke(0.5f))
        drawArc(Color.White.copy(alpha = 0.6f), 190f, 70f, false, Offset(pc.x - 13f, pc.y - 13f), Size(26f, 26f), style = Stroke(1f, cap = StrokeCap.Round))
        rivet(pc, 3.8f, gold = true)
        drawCircle(radial(48.5f, 386f, 6f, 0f to Color(0xFFE6ECF0), 0.5f to Color(0xFF7D8992), 1f to Color(0xFF262C31)), 4.2f, Offset(50f, 387f))
        drawCircle(Outline, 4.2f, Offset(50f, 387f), style = Stroke(0.7f))

        // ---- Crossguard with finials and gem block
        fillOutlined(guard, linear(darkSteelStops, 0f, 261f, 0f, 281f), width = 0.9f)
        drawPath(
            guard,
            Brush.horizontalGradient(
                colorStops = arrayOf(0f to Color.Black.copy(alpha = 0.35f), 0.3f to Color.White.copy(alpha = 0.18f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.4f)),
                startX = -1f, endX = 101f,
            ),
        )
        drawPath(path { moveTo(5f, 263.5f); quadraticTo(10f, 271.5f, 22f, 271.5f); lineTo(38f, 271.5f) }, Color.White.copy(alpha = 0.55f), style = Stroke(0.8f, cap = StrokeCap.Round))
        drawPath(path { moveTo(62f, 271.5f); lineTo(78f, 271.5f); quadraticTo(90f, 271.5f, 95f, 263.5f) }, Color.White.copy(alpha = 0.3f), style = Stroke(0.7f, cap = StrokeCap.Round))
        fillOutlined(guardBlock, linear(goldStops, 38f, 259f, 62f, 289f), outline = BrassDark, width = 0.9f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(39.5f, 267f), Offset(49.5f, 260.8f), 0.6f, StrokeCap.Round)
        gem(Offset(50f, 274f), 5.6f, 6.4f, look.rarity)
    }
}

// ============================================================================ mace

/** Streitkolben: six-flanged steel head with spike, riveted collar, wooden shaft, wrapped grip. */
internal object MacePainter : WeaponShape {
    override val bounds = Rect(4f, 0f, 96f, 395f)
    override val gripCenter = Offset(50f, 323f)
    override val strikePoint = Offset(50f, 70f)

    private val flangeLeft = path {
        moveTo(37f, 30f); lineTo(14f, 44f); quadraticTo(5.5f, 54f, 9f, 64f); lineTo(15f, 76f); lineTo(9f, 88f)
        quadraticTo(5.5f, 98f, 14f, 108f); lineTo(37f, 122f); close()
    }
    private val flangeRight = mirrored { m ->
        moveTo(m(37f), 30f); lineTo(m(14f), 44f); quadraticTo(m(5.5f), 54f, m(9f), 64f); lineTo(m(15f), 76f); lineTo(m(9f), 88f)
        quadraticTo(m(5.5f), 98f, m(14f), 108f); lineTo(m(37f), 122f); close()
    }
    private val backLeft = path { moveTo(38f, 36f); lineTo(22f, 48f); quadraticTo(15.5f, 76f, 22f, 104f); lineTo(38f, 116f); close() }
    private val backRight = mirrored { m -> moveTo(m(38f), 36f); lineTo(m(22f), 48f); quadraticTo(m(15.5f), 76f, m(22f), 104f); lineTo(m(38f), 116f); close() }
    private val core = path { addRoundRect(RoundRect(34f, 28f, 66f, 126f, CornerRadius(13f))) }
    private val frontL = path { moveTo(50f, 20f); lineTo(44.5f, 36f); lineTo(44f, 112f); lineTo(50f, 130f); close() }
    private val frontR = path { moveTo(50f, 20f); lineTo(55.5f, 36f); lineTo(56f, 112f); lineTo(50f, 130f); close() }
    private val spike = path { moveTo(50f, 0f); lineTo(57.5f, 28f); quadraticTo(50f, 32.5f, 42.5f, 28f); close() }
    private val head = path {
        moveTo(50f, 0f); lineTo(57.5f, 28f); lineTo(63f, 30f); lineTo(86f, 44f); quadraticTo(94.5f, 54f, 91f, 64f); lineTo(85f, 76f)
        lineTo(91f, 88f); quadraticTo(94.5f, 98f, 86f, 108f); lineTo(63f, 122f); lineTo(50f, 130f); lineTo(37f, 122f); lineTo(14f, 108f)
        quadraticTo(5.5f, 98f, 9f, 88f); lineTo(15f, 76f); lineTo(9f, 64f); quadraticTo(5.5f, 54f, 14f, 44f); lineTo(37f, 30f)
        lineTo(42.5f, 28f); close()
    }
    override val glowPath: Path get() = head

    private val neck = path { moveTo(38f, 118f); lineTo(62f, 118f); lineTo(58f, 132f); lineTo(42f, 132f); close() }
    private val shaft = path {
        moveTo(45f, 140f); lineTo(55f, 140f); cubicTo(55.4f, 200f, 55.8f, 250f, 56f, 292f)
        lineTo(44f, 292f); cubicTo(44.2f, 250f, 44.6f, 200f, 45f, 140f); close()
    }
    private val grip = path {
        moveTo(42.6f, 286f); cubicTo(41.6f, 310f, 41.6f, 336f, 42.6f, 360f); lineTo(57.4f, 360f)
        cubicTo(58.4f, 336f, 58.4f, 310f, 57.4f, 286f); close()
    }

    override fun DrawScope.drawUnits(look: WeaponLook) {
        // ---- Shaft
        fillOutlined(shaft, linear(woodStops, 44f, 0f, 56f, 0f), width = 0.8f)
        woodGrain(listOf(47.3f, 50.4f, 53f), 146f, 284f)
        // Iron straps (langets) running down from the collar
        for (x in listOf(45.6f, 54.4f)) {
            drawLine(Outline, Offset(x, 136f), Offset(x + (x - 50f) * 0.02f, 196f), 2.8f, StrokeCap.Round)
            drawLine(Color(0xFFB9C4CB), Offset(x - 0.3f, 136f), Offset(x - 0.3f + (x - 50f) * 0.02f, 196f), 1.4f, StrokeCap.Round)
        }
        for (y in listOf(150f, 172f, 192f)) {
            rivet(Offset(45.6f, y), 1.5f); rivet(Offset(54.5f, y), 1.5f)
        }
        // Iron band above the grip
        val band = path { addRoundRect(RoundRect(43.2f, 276f, 56.8f, 287f, CornerRadius(2f))) }
        fillOutlined(band, linear(steelStops, 43f, 0f, 57f, 0f), width = 0.7f)

        // ---- Grip + pommel
        wrappedGrip(grip, 42f, 58f, 286f, 360f)
        val knob = path { addOval(Rect(40.5f, 356f, 59.5f, 374f)) }
        fillOutlined(knob, radial(45.5f, 360f, 15f, 0f to Color(0xFFF2F6F8), 0.45f to Color(0xFF8A969F), 1f to Color(0xFF262C31)), width = 0.8f)
        drawLine(Outline.copy(alpha = 0.6f), Offset(41f, 365f), Offset(59f, 365f), 0.5f)
        drawCircle(radial(47.5f, 380f, 10f, 0f to Color(0xFFF2F6F8), 0.45f to Color(0xFF8A969F), 1f to Color(0xFF262C31)), 7.5f, Offset(50f, 384f))
        drawCircle(Outline, 7.5f, Offset(50f, 384f), style = Stroke(0.8f))
        drawCircle(Color.White.copy(alpha = 0.75f), 1.6f, Offset(47.5f, 381.4f))

        // ---- Neck + collar
        fillOutlined(neck, linear(darkSteelStops, 38f, 0f, 62f, 0f), width = 0.8f)
        val collar = path { addRoundRect(RoundRect(39f, 124f, 61f, 140f, CornerRadius(3f))) }
        fillOutlined(collar, linear(steelStops, 39f, 0f, 61f, 0f), width = 0.8f)
        val ring = path { addRoundRect(RoundRect(41.5f, 139f, 58.5f, 146f, CornerRadius(2f))) }
        fillOutlined(ring, linear(goldStops, 41.5f, 0f, 58.5f, 0f), outline = BrassDark, width = 0.7f)
        rivet(Offset(43.5f, 132f), 1.7f); rivet(Offset(56.5f, 132f), 1.7f)

        // ---- Head: back flanges, core, side flanges, front flange, spike.
        fillOutlined(backLeft, linear(darkSteelStops, 15f, 0f, 38f, 0f), width = 0.8f)
        fillOutlined(backRight, linear(darkSteelStops, 62f, 0f, 85f, 0f), width = 0.8f)
        fillOutlined(core, radial(42f, 54f, 50f, 0f to Color(0xFFE6ECF0), 0.5f to Color(0xFF7D8992), 1f to Color(0xFF2A3136)), width = 0.9f)
        fillOutlined(flangeLeft, linear(arrayOf(0f to Color(0xFFF4F8FA), 0.35f to Color(0xFFB9C4CB), 0.8f to Color(0xFF66727B), 1f to Color(0xFF3A434A)), 5f, 0f, 37f, 0f), width = 0.9f)
        fillOutlined(flangeRight, linear(arrayOf(0f to Color(0xFF2E363C), 0.4f to Color(0xFF59656E), 0.85f to Color(0xFF8E9AA4), 1f to Color(0xFFB6C0C7)), 63f, 0f, 95f, 0f), width = 0.9f)
        // Flange edge highlights + lower shading
        drawPath(path { moveTo(14.5f, 45f); quadraticTo(6.5f, 54f, 9.8f, 63.5f); lineTo(15.6f, 75f) }, Color.White.copy(alpha = 0.8f), style = Stroke(0.9f, cap = StrokeCap.Round))
        drawPath(path { moveTo(15.6f, 77f); lineTo(9.8f, 88.5f); quadraticTo(6.5f, 98f, 14.5f, 107f) }, Color.White.copy(alpha = 0.45f), style = Stroke(0.8f, cap = StrokeCap.Round))
        drawPath(path { moveTo(85.5f, 45f); quadraticTo(93.5f, 54f, 90.2f, 63.5f) }, Color.White.copy(alpha = 0.35f), style = Stroke(0.7f, cap = StrokeCap.Round))
        clipPath(flangeLeft) {
            drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startY = 30f, endY = 122f), Offset(0f, 30f), Size(40f, 92f))
            // battle nicks
            drawLine(Color(0xFF2E363C).copy(alpha = 0.6f), Offset(9.5f, 66f), Offset(14f, 67f), 0.6f, StrokeCap.Round)
            drawLine(Color(0xFF2E363C).copy(alpha = 0.5f), Offset(9.6f, 93f), Offset(13f, 92f), 0.6f, StrokeCap.Round)
        }
        clipPath(flangeRight) {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.15f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startY = 30f, endY = 122f), Offset(60f, 30f), Size(40f, 92f))
        }
        fillOutlined(frontL, linear(arrayOf(0f to Color(0xFFB4BFC6), 1f to Color(0xFFF4F8FA)), 44f, 0f, 50f, 0f), width = 0.7f)
        fillOutlined(frontR, linear(arrayOf(0f to Color(0xFF6D7881), 1f to Color(0xFF3E474E)), 50f, 0f, 56f, 0f), width = 0.7f)
        drawLine(Color.White.copy(alpha = 0.9f), Offset(49.6f, 24f), Offset(49.6f, 124f), 0.6f, StrokeCap.Round)
        fillOutlined(spike, linear(arrayOf(0f to Color(0xFFF4F8FA), 0.5f to Color(0xFF9AA6AE), 1f to Color(0xFF3A434A)), 43f, 0f, 58f, 0f), width = 0.8f)
        drawLine(Color.White.copy(alpha = 0.8f), Offset(49.5f, 3f), Offset(46f, 26f), 0.6f, StrokeCap.Round)
        // Gem inlay on the collar
        gem(Offset(50f, 132f), 4.2f, 4.6f, look.rarity)
    }
}

// ============================================================================ axe

/** Axt: bearded steel blade with polished edge, hammer poll, riveted socket, ash haft, wrapped grip. */
internal object AxePainter : WeaponShape {
    override val bounds = Rect(-18f, 6f, 84f, 388f)
    override val gripCenter = Offset(50f, 334f)
    override val strikePoint = Offset(-12f, 80f)

    private val haft = path {
        moveTo(44.5f, 20f); cubicTo(43.6f, 120f, 44f, 250f, 44.5f, 372f); lineTo(55.5f, 372f)
        cubicTo(56f, 250f, 56.4f, 120f, 55.5f, 20f); quadraticTo(50f, 9f, 44.5f, 20f); close()
    }
    private val grip = path {
        moveTo(42.6f, 296f); cubicTo(41.8f, 320f, 41.8f, 346f, 42.6f, 370f); lineTo(57.4f, 370f)
        cubicTo(58.2f, 346f, 58.2f, 320f, 57.4f, 296f); close()
    }
    private val blade = path {
        moveTo(39f, 50f); cubicTo(28f, 47f, 14f, 37f, 2f, 16f)
        cubicTo(-14f, 52f, -16f, 112f, -2f, 152f)
        cubicTo(10f, 134f, 24f, 114f, 39f, 104f); close()
    }
    override val glowPath: Path get() = blade

    private val edge = path {
        moveTo(2f, 16f); cubicTo(-14f, 52f, -16f, 112f, -2f, 152f); lineTo(4.5f, 140f)
        cubicTo(-7f, 106f, -6f, 58f, 8.5f, 27f); close()
    }
    private val poll = path { moveTo(61f, 54f); lineTo(79f, 58f); quadraticTo(82.5f, 79f, 79f, 100f); lineTo(61f, 104f); close() }
    private val socket = path { addRoundRect(RoundRect(38f, 36f, 62f, 120f, CornerRadius(5f))) }

    override fun DrawScope.drawUnits(look: WeaponLook) {
        // ---- Haft
        fillOutlined(haft, linear(woodStops, 44f, 0f, 56f, 0f), width = 0.8f)
        woodGrain(listOf(47.2f, 50.2f, 53f), 136f, 290f)
        // Leather binding below the head
        val binding = path { addRoundRect(RoundRect(43.4f, 120f, 56.6f, 136f, CornerRadius(2f))) }
        wrappedGrip(binding, 43.4f, 56.6f, 120f, 136f, step = 4.2f)
        wrappedGrip(grip, 42f, 58f, 296f, 370f)
        val butt = path { addRoundRect(RoundRect(42.4f, 366f, 57.6f, 386f, CornerRadius(3.5f))) }
        fillOutlined(butt, linear(steelStops, 42.4f, 0f, 57.6f, 0f), width = 0.8f)
        drawLine(Outline.copy(alpha = 0.55f), Offset(42.8f, 372f), Offset(57.2f, 372f), 0.5f)
        rivet(Offset(50f, 379f), 2.1f)

        // ---- Blade
        fillOutlined(
            blade,
            linear(arrayOf(0f to Color(0xFFE9EEF1), 0.22f to Color(0xFFB7C1C8), 0.55f to Color(0xFF6F7A83), 1f to Color(0xFF3B434A)), -14f, 0f, 39f, 0f),
            width = 1.4f,
        )
        clipPath(blade) {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.18f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startY = 16f, endY = 152f), Offset(-20f, 10f), Size(62f, 146f))
            // Forge hammer marks
            for ((c, r) in listOf(Offset(26f, 66f) to 4.4f, Offset(31f, 84f) to 3.4f, Offset(21f, 98f) to 3.8f, Offset(33f, 57f) to 2.8f, Offset(18f, 76f) to 3f)) {
                drawOval(Color.Black.copy(alpha = 0.12f), Offset(c.x - r, c.y - r * 0.7f), Size(r * 2, r * 1.4f))
                drawArc(Color.White.copy(alpha = 0.2f), 180f, 90f, false, Offset(c.x - r, c.y - r * 0.7f), Size(r * 2, r * 1.4f), style = Stroke(0.5f))
            }
            // Rarity-free dark patina near the socket
            drawRect(Brush.horizontalGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f), startX = -14f, endX = 39f), Offset(-20f, 10f), Size(62f, 146f))
        }
        drawPath(edge, Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.8f), 1f to Color.White.copy(alpha = 0.15f), startX = -14f, endX = 9f))
        drawPath(path { moveTo(8.5f, 27f); cubicTo(-6f, 58f, -7f, 106f, 4.5f, 140f) }, Color(0xFF39424A).copy(alpha = 0.7f), style = Stroke(0.7f))
        drawPath(path { moveTo(2.4f, 18f); cubicTo(-13f, 52f, -15f, 112f, -1.4f, 150f) }, Color.White.copy(alpha = 0.9f), style = Stroke(0.8f, cap = StrokeCap.Round))
        // Engraved decorative line following the blade
        drawPath(path { moveTo(36f, 58f); cubicTo(25f, 64f, 19f, 80f, 21f, 94f); cubicTo(23f, 101f, 29f, 103f, 35f, 97f) }, Color(0xFF262C31).copy(alpha = 0.55f), style = Stroke(0.8f, cap = StrokeCap.Round))
        drawPath(path { moveTo(36.4f, 59f); cubicTo(25.6f, 65f, 19.8f, 80f, 21.8f, 94f) }, Color.White.copy(alpha = 0.25f), style = Stroke(0.4f))
        drawPath(blade, Outline.copy(alpha = 0.9f), style = Stroke(0.9f, join = StrokeJoin.Round))

        // ---- Poll and socket
        fillOutlined(poll, linear(darkSteelStops, 61f, 54f, 79f, 104f), width = 1f)
        drawLine(Color.White.copy(alpha = 0.4f), Offset(78.6f, 61f), Offset(80.6f, 79f), 0.7f, StrokeCap.Round)
        fillOutlined(socket, linear(darkSteelStops, 38f, 0f, 62f, 0f), width = 1.1f)
        drawLine(Color.White.copy(alpha = 0.45f), Offset(41.5f, 40f), Offset(41.5f, 116f), 0.7f, StrokeCap.Round)
        drawLine(Outline.copy(alpha = 0.6f), Offset(38.5f, 52f), Offset(61.5f, 52f), 0.6f)
        drawLine(Outline.copy(alpha = 0.6f), Offset(38.5f, 104f), Offset(61.5f, 104f), 0.6f)
        rivet(Offset(50f, 44f), 2.6f); rivet(Offset(50f, 112f), 2.6f)
        // Wedge of the haft visible on top
        drawRoundRect(linear(woodStops, 45f, 0f, 55f, 0f), Offset(45f, 30f), Size(10f, 7f), CornerRadius(2f))
        drawRoundRect(Outline, Offset(45f, 30f), Size(10f, 7f), CornerRadius(2f), style = Stroke(0.6f))
        gem(Offset(50f, 78f), 6f, 8f, look.rarity)
    }
}

// ============================================================================ public drawing API

/**
 * Draws the weapon of [look] so that its grip center sits at the origin of the current transform,
 * pointing up (-y), in weapon units (16-unit grip). FIST draws nothing (use the hand painter).
 */
internal fun DrawScope.drawWeaponHeld(look: WeaponLook, shimmer: Float = 0f) {
    val shape = WeaponPainters.shapeOf(look.kind) ?: return
    withTransform({ translate(-shape.gripCenter.x, -shape.gripCenter.y) }) {
        with(WeaponPainters) { drawWeaponUnits(look, shimmer) }
    }
}

/**
 * Draws the weapon of [look] (or a gloved fist for [WeaponKind.FIST]) fitted, centered and rotated
 * by [rotationDegrees] (0 = pointing up, 90 = pointing right) into [size].
 */
fun DrawScope.drawWeaponFitted(
    look: WeaponLook,
    rotationDegrees: Float = 0f,
    size: Size = this.size,
    topLeft: Offset = Offset.Zero,
    shimmer: Float = 0f,
) {
    val shape = WeaponPainters.shapeOf(look.kind)
    val b = shape?.bounds ?: FistPainter.BOUNDS
    val rad = Math.toRadians(rotationDegrees.toDouble())
    val c = abs(cos(rad)).toFloat()
    val s = abs(sin(rad)).toFloat()
    val rw = b.width * c + b.height * s
    val rh = b.width * s + b.height * c
    val scale = min(size.width / rw, size.height / rh)
    if (scale <= 0f || scale.isNaN()) return
    withTransform({
        translate(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f)
        rotate(rotationDegrees, Offset.Zero)
        scale(scale, scale, Offset.Zero)
        translate(-b.center.x, -b.center.y)
    }) {
        if (shape == null) {
            FistPainter.draw(this)
        } else {
            with(WeaponPainters) { drawWeaponUnits(look, shimmer) }
        }
    }
}

/**
 * Standalone high-res graphic of the weapon of [item] (fist if `null`), fitted into the
 * composable's bounds and rotated by [rotationDegrees] (0 = pointing up, 90 = pointing right).
 */
@Composable
fun WeaponImage(
    item: Item?,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
    contentDescription: String? = null,
) = WeaponImage(weaponLookOf(item), modifier, rotationDegrees, contentDescription)

/** [WeaponImage] for an explicit [WeaponLook]. */
@Composable
fun WeaponImage(
    look: WeaponLook,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
    contentDescription: String? = null,
) {
    val sem = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(sem)) { drawWeaponFitted(look, rotationDegrees, shimmer = 0.45f) }
}

/**
 * Draws the weapon of [look] held in a small figure's hand: grip centered at [hand], rotated by
 * [rotationDegrees] (0 = pointing up), [scale] px (or parent units) per weapon unit. If [handColor]
 * is set, a fist of that color is drawn over the grip so the figure visibly holds it.
 * Nothing is drawn for [WeaponKind.FIST].
 */
fun DrawScope.drawWeaponInHand(
    look: WeaponLook,
    hand: Offset,
    rotationDegrees: Float,
    scale: Float,
    handColor: Color? = null,
    shimmer: Float = 0.45f,
) {
    if (look.kind == WeaponKind.FIST) return
    withTransform({
        translate(hand.x, hand.y)
        rotate(rotationDegrees, Offset.Zero)
        scale(scale, scale, Offset.Zero)
    }) {
        drawWeaponHeld(look, shimmer)
        if (handColor != null) {
            val r = RoundRect(-14f, -20f, 14f, 22f, CornerRadius(12f))
            val p = Path().apply { addRoundRect(r) }
            drawPath(p, handColor)
            drawPath(p, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.3f)), startX = -14f, endX = 14f))
            drawPath(p, Color.Black.copy(alpha = 0.55f), style = Stroke(1.6f))
        }
    }
}
