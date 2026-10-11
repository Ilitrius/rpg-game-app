package com.idlerpg.game.ui.equipment

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.ui.character.weapon.DaggerPainter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-resolution vector item graphics (paths + gradients, no bitmaps).
 *
 * Every `draw…` function paints into a 100 × 100 unit square of the current transform
 * (long weapons are drawn upright in a 100 × 320 space and rotated diagonally into it).
 * All stroke widths are in units, so everything scales crisply with the transform.
 */
internal object ItemPainters {

    // ---------------------------------------------------------------- palette

    private val Outline = Color(0xFF14171B)

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
    private val silverStops = arrayOf(
        0f to Color(0xFF4B545B), 0.25f to Color(0xFFFFFFFF), 0.5f to Color(0xFFC6CED5),
        0.78f to Color(0xFF7C8790), 1f to Color(0xFF353C42),
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
    private val BrassDark = Color(0xFF3A2506)
    private val Stitch = Color(0xFFE2C79C)

    /** Gem colors (highlight, body, shadow) — tied to the item's rarity. */
    data class Gem(val light: Color, val mid: Color, val dark: Color)

    fun gemFor(rarity: Rarity): Gem = when (rarity) {
        Rarity.COMMON -> Gem(Color(0xFFF4F1E6), Color(0xFFB9B39E), Color(0xFF5E5948))
        Rarity.UNCOMMON -> Gem(Color(0xFFC8F7D4), Color(0xFF2EAD5B), Color(0xFF0A4220))
        Rarity.RARE -> Gem(Color(0xFFCFE6FF), Color(0xFF1E73D8), Color(0xFF0A2860))
        Rarity.EPIC -> Gem(Color(0xFFF0D2F7), Color(0xFF9C3FCB), Color(0xFF380C52))
        Rarity.LEGENDARY -> Gem(Color(0xFFFFE9C4), Color(0xFFFF8F1F), Color(0xFF702900))
    }

    private fun linear(stops: Array<Pair<Float, Color>>, x0: Float, y0: Float, x1: Float, y1: Float) =
        Brush.linearGradient(colorStops = stops, start = Offset(x0, y0), end = Offset(x1, y1))

    private fun radial(cx: Float, cy: Float, r: Float, vararg stops: Pair<Float, Color>) =
        Brush.radialGradient(colorStops = stops, center = Offset(cx, cy), radius = r)

    private fun stitchStroke(width: Float = 0.7f, on: Float = 2.2f, off: Float = 1.6f) =
        Stroke(width = width, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off)))

    private fun path(block: Path.() -> Unit) = Path().apply(block)

    /** Mirrors a path built with [block] at x = 50 (for symmetric parts). */
    private fun mirrored(block: Path.(mx: (Float) -> Float) -> Unit) = Path().apply { block { 100f - it } }

    private fun DrawScope.fillOutlined(p: Path, brush: Brush, outline: Color = Outline, width: Float = 1.1f) {
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

    /** Soft specular glint (4-point star). */
    private fun DrawScope.sparkle(c: Offset, r: Float, alpha: Float = 0.9f) {
        val p = path {
            moveTo(c.x, c.y - r)
            quadraticTo(c.x, c.y, c.x + r, c.y)
            quadraticTo(c.x, c.y, c.x, c.y + r)
            quadraticTo(c.x, c.y, c.x - r, c.y)
            quadraticTo(c.x, c.y, c.x, c.y - r)
            close()
        }
        drawPath(p, Color.White.copy(alpha = alpha))
        drawCircle(Color.White.copy(alpha = alpha * 0.35f), r * 0.45f, c)
    }

    /** Brilliant-cut gem seen from above: octagon girdle, table and shaded facets. */
    private fun DrawScope.facetedGem(c: Offset, rx: Float, ry: Float, gem: Gem) {
        val n = 8
        val outer = List(n) { i ->
            val a = Math.toRadians(22.5 + 45.0 * i)
            Offset(c.x + rx * cos(a).toFloat(), c.y + ry * sin(a).toFloat())
        }
        val tc = Offset(c.x - rx * 0.06f, c.y - ry * 0.08f)
        val table = outer.map { Offset(tc.x + (it.x - c.x) * 0.5f, tc.y + (it.y - c.y) * 0.5f) }
        val girdle = path {
            moveTo(outer[0].x, outer[0].y)
            outer.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(girdle, radial(c.x, c.y, maxOf(rx, ry), 0f to gem.mid, 1f to gem.dark))
        for (i in 0 until n) {
            val j = (i + 1) % n
            val facet = path {
                moveTo(outer[i].x, outer[i].y); lineTo(outer[j].x, outer[j].y)
                lineTo(table[j].x, table[j].y); lineTo(table[i].x, table[i].y); close()
            }
            // Light from top-left: facets facing up-left are bright.
            val mid = Offset((outer[i].x + outer[j].x) / 2 - c.x, (outer[i].y + outer[j].y) / 2 - c.y)
            val facing = (-(mid.x / rx) - (mid.y / ry)) / 1.42f // -1..1
            val color = when {
                facing > 0.5f -> gem.light.copy(alpha = 0.85f)
                facing > 0f -> lerp(gem.mid, gem.light, 0.35f)
                facing > -0.5f -> gem.mid
                else -> gem.dark
            }
            drawPath(facet, color)
            drawPath(facet, gem.dark.copy(alpha = 0.55f), style = Stroke(0.35f))
        }
        val tablePath = path {
            moveTo(table[0].x, table[0].y)
            table.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(tablePath, linear(arrayOf(0f to gem.light, 0.55f to gem.mid, 1f to lerp(gem.mid, gem.dark, 0.4f)), tc.x - rx * 0.5f, tc.y - ry * 0.5f, tc.x + rx * 0.5f, tc.y + ry * 0.5f))
        drawPath(tablePath, gem.dark.copy(alpha = 0.5f), style = Stroke(0.35f))
        drawPath(girdle, Outline.copy(alpha = 0.9f), style = Stroke(0.7f, join = StrokeJoin.Round))
        sparkle(Offset(tc.x - rx * 0.22f, tc.y - ry * 0.22f), minOf(rx, ry) * 0.42f)
    }

    private fun lerp(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t,
    )

    /** Leather grip with diagonal wraps, clipped to [grip]. */
    private fun DrawScope.wrappedGrip(grip: Path, x0: Float, x1: Float, y0: Float, y1: Float, step: Float = 7f) {
        drawPath(grip, linear(darkLeatherStops, x0, 0f, x1, 0f))
        clipPath(grip) {
            var y = y0 - 8f
            while (y < y1 + 4f) {
                val band = path {
                    moveTo(x0 - 1, y); lineTo(x1 + 1, y + 4f); lineTo(x1 + 1, y + step + 2.5f); lineTo(x0 - 1, y + step - 1.5f); close()
                }
                drawPath(band, linear(leatherStops, x0, 0f, x1, 0f))
                drawLine(Color(0xFFD7A57A).copy(alpha = 0.35f), Offset(x0, y + 0.6f), Offset(x1, y + 4.6f), 0.6f)
                drawLine(Color.Black.copy(alpha = 0.6f), Offset(x0, y + step - 1.5f), Offset(x1, y + step + 2.5f), 1f)
                y += step
            }
        }
        drawPath(grip, Color(0xFF120A05), style = Stroke(0.8f))
    }

    /** Draws a weapon defined upright in a 100 × 320 space diagonally into the 100 × 100 icon square. */
    private fun DrawScope.diagonal(scale: Float = 0.37f, height: Float = 320f, block: DrawScope.() -> Unit) {
        withTransform({
            rotate(45f, pivot = Offset(50f, 50f))
            translate(50f - 50f * scale, 50f - height / 2f * scale)
            scale(scale, scale, pivot = Offset.Zero)
        }) { block() }
    }

    // ---------------------------------------------------------------- weapons

    fun DrawScope.drawDaggerIcon(rust: Float) = diagonal(0.38f) {
        with(DaggerPainter) { drawDaggerUnits(rust) }
    }

    private val swordBlade = path {
        moveTo(50f, 4f); cubicTo(47f, 16f, 40f, 30f, 37f, 44f); lineTo(38.5f, 197f)
        lineTo(61.5f, 197f); lineTo(63f, 44f); cubicTo(60f, 30f, 53f, 16f, 50f, 4f); close()
    }
    private val swordLeft = path {
        moveTo(50f, 4f); cubicTo(47f, 16f, 40f, 30f, 37f, 44f); lineTo(38.5f, 197f); lineTo(50f, 197f); close()
    }
    private val swordRight = path {
        moveTo(50f, 4f); cubicTo(53f, 16f, 60f, 30f, 63f, 44f); lineTo(61.5f, 197f); lineTo(50f, 197f); close()
    }
    private val swordFuller = path {
        moveTo(47.2f, 58f); quadraticTo(47.2f, 50f, 50f, 50f); quadraticTo(52.8f, 50f, 52.8f, 58f)
        lineTo(52.8f, 184f); quadraticTo(52.8f, 189f, 50f, 189f); quadraticTo(47.2f, 189f, 47.2f, 184f); close()
    }
    private val swordGuard = path {
        moveTo(8f, 196f); cubicTo(30f, 199f, 70f, 199f, 92f, 196f); lineTo(93f, 208f)
        cubicTo(70f, 211.5f, 30f, 211.5f, 7f, 208f); close()
    }
    private val swordGuardBlock = path {
        moveTo(50f, 190f); lineTo(61f, 197f); lineTo(61f, 209f); lineTo(50f, 217f); lineTo(39f, 209f); lineTo(39f, 197f); close()
    }
    private val swordGrip = path {
        moveTo(43f, 214f); cubicTo(41.5f, 236f, 41.5f, 258f, 43f, 280f); lineTo(57f, 280f)
        cubicTo(58.5f, 258f, 58.5f, 236f, 57f, 214f); close()
    }

    fun DrawScope.drawSwordIcon(gem: Gem) = diagonal(0.39f, 312f) {
        // Blade
        drawPath(swordBlade, Outline, style = Stroke(2.2f, join = StrokeJoin.Round))
        drawPath(swordLeft, linear(arrayOf(0f to Color(0xFF8E9AA4), 0.55f to Color(0xFFDCE4EA), 1f to Color(0xFFF4F8FA)), 37f, 0f, 50f, 0f))
        drawPath(swordRight, linear(arrayOf(0f to Color(0xFF7D8992), 0.6f to Color(0xFF5A666F), 1f to Color(0xFF6E7A83)), 50f, 0f, 63f, 0f))
        drawPath(
            swordBlade,
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.3f), 0.2f to Color.Transparent, 0.45f to Color.White.copy(alpha = 0.2f),
                0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f), startY = 0f, endY = 200f,
            ),
        )
        drawPath(path { moveTo(49.6f, 6f); cubicTo(46.8f, 17f, 40f, 31f, 37.3f, 44f); lineTo(38.8f, 195f) }, Color.White.copy(alpha = 0.85f), style = Stroke(1.1f, cap = StrokeCap.Round))
        drawPath(path { moveTo(50f, 14f); cubicTo(47.8f, 23f, 42.5f, 34f, 40.6f, 46f); lineTo(41.6f, 195f) }, Color(0xFF6F7B84).copy(alpha = 0.6f), style = Stroke(0.7f))
        drawPath(path { moveTo(50.4f, 6f); cubicTo(53.2f, 17f, 60f, 31f, 62.7f, 44f); lineTo(61.2f, 195f) }, Color(0xFFC9D3DA).copy(alpha = 0.55f), style = Stroke(0.9f))
        drawPath(path { moveTo(50f, 14f); cubicTo(52.2f, 23f, 57.5f, 34f, 59.4f, 46f); lineTo(58.4f, 195f) }, Color(0xFF30383E).copy(alpha = 0.55f), style = Stroke(0.7f))
        drawLine(Color.White.copy(alpha = 0.7f), Offset(50f, 8f), Offset(50f, 50f), 0.8f, StrokeCap.Round)
        drawPath(swordFuller, linear(arrayOf(0f to Color(0xFF2E363C), 0.45f to Color(0xFF59656E), 1f to Color(0xFFE4ECF1)), 47.2f, 0f, 52.8f, 0f))
        drawPath(swordFuller, Color(0xFF1C2227).copy(alpha = 0.8f), style = Stroke(0.6f))
        drawPath(swordBlade, Outline.copy(alpha = 0.9f), style = Stroke(0.8f, join = StrokeJoin.Round))

        // Grip: dark leather core with twisted silver wire.
        drawPath(swordGrip, linear(darkLeatherStops, 42f, 0f, 58f, 0f))
        clipPath(swordGrip) {
            var y = 210f
            while (y < 284f) {
                drawLine(Color(0xFFE9EEF2), Offset(41f, y), Offset(59f, y + 3.2f), 1.5f)
                drawLine(Color(0xFF5B656D), Offset(41f, y + 1.3f), Offset(59f, y + 4.5f), 0.9f)
                y += 3.3f
            }
            drawRect(linear(arrayOf(0f to Color.Black.copy(alpha = 0.55f), 0.35f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)), 42f, 0f, 58f, 0f), Offset(40f, 210f), Size(20f, 75f))
        }
        drawPath(swordGrip, Outline, style = Stroke(0.8f))
        drawRoundRect(linear(steelStops, 43f, 0f, 57f, 0f), Offset(43.5f, 278f), Size(13f, 6f), CornerRadius(1.5f))
        drawRoundRect(Outline, Offset(43.5f, 278f), Size(13f, 6f), CornerRadius(1.5f), style = Stroke(0.7f))

        // Wheel pommel
        val pc = Offset(50f, 296f)
        drawCircle(radial(45f, 290f, 18f, 0f to Color(0xFFF2F6F8), 0.4f to Color(0xFF9AA6AE), 1f to Color(0xFF2B3237)), 14f, pc)
        drawCircle(Outline, 14f, pc, style = Stroke(0.9f))
        drawCircle(Color(0xFF2B3237).copy(alpha = 0.7f), 9f, pc, style = Stroke(1.4f))
        drawCircle(Color.White.copy(alpha = 0.5f), 9.8f, Offset(pc.x - 0.6f, pc.y - 0.6f), style = Stroke(0.5f))
        rivet(pc, 3.6f, gold = true)

        // Crossguard with finials and gem block
        fillOutlined(swordGuard, linear(darkSteelStops, 0f, 196f, 0f, 211f), width = 0.9f)
        drawPath(swordGuard, Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.3f to Color.White.copy(alpha = 0.18f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.4f), startX = 6f, endX = 94f))
        drawLine(Color.White.copy(alpha = 0.5f), Offset(12f, 199.8f), Offset(38f, 201f), 0.7f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.3f), Offset(62f, 201f), Offset(88f, 199.8f), 0.7f, StrokeCap.Round)
        for (x in listOf(6f, 94f)) {
            drawCircle(radial(x - 2f, 199f, 9f, 0f to Color(0xFFF2F6F8), 0.45f to Color(0xFF8A969F), 1f to Color(0xFF262C31)), 6.5f, Offset(x, 202f))
            drawCircle(Outline, 6.5f, Offset(x, 202f), style = Stroke(0.8f))
        }
        fillOutlined(swordGuardBlock, linear(goldStops, 40f, 190f, 60f, 217f), outline = BrassDark, width = 0.9f)
        facetedGem(Offset(50f, 203.5f), 6.2f, 6.2f, gem)
    }

    private val maceShaft = path {
        moveTo(45.5f, 110f); lineTo(54.5f, 110f); lineTo(54.8f, 210f); lineTo(45.2f, 210f); close()
    }
    private val maceGrip = path {
        moveTo(44.5f, 150f); cubicTo(43.8f, 170f, 43.8f, 190f, 44.5f, 208f); lineTo(55.5f, 208f)
        cubicTo(56.2f, 190f, 56.2f, 170f, 55.5f, 150f); close()
    }
    private val maceFlangeLeft = path {
        moveTo(38f, 24f); lineTo(13f, 40f); quadraticTo(5f, 50f, 9f, 60f); lineTo(14f, 70f); lineTo(9f, 80f)
        quadraticTo(5f, 90f, 13f, 100f); lineTo(38f, 116f); close()
    }
    private val maceFlangeRight = mirrored { m ->
        moveTo(m(38f), 24f); lineTo(m(13f), 40f); quadraticTo(m(5f), 50f, m(9f), 60f); lineTo(m(14f), 70f); lineTo(m(9f), 80f)
        quadraticTo(m(5f), 90f, m(13f), 100f); lineTo(m(38f), 116f); close()
    }
    private val maceBackLeft = path { moveTo(39f, 32f); lineTo(23f, 44f); quadraticTo(17f, 70f, 23f, 96f); lineTo(39f, 108f); close() }
    private val maceBackRight = mirrored { m -> moveTo(m(39f), 32f); lineTo(m(23f), 44f); quadraticTo(m(17f), 70f, m(23f), 96f); lineTo(m(39f), 108f); close() }
    private val maceFrontL = path { moveTo(50f, 18f); lineTo(45f, 32f); lineTo(44.5f, 104f); lineTo(50f, 120f); close() }
    private val maceFrontR = path { moveTo(50f, 18f); lineTo(55f, 32f); lineTo(55.5f, 104f); lineTo(50f, 120f); close() }

    fun DrawScope.drawMaceIcon(gem: Gem) = diagonal(0.52f, 232f) {
        // Shaft + grip + pommel
        fillOutlined(maceShaft, linear(woodStops, 45f, 0f, 55f, 0f), width = 0.8f)
        for ((x, a) in listOf(47.5f to 0.35f, 50.8f to 0.25f, 52.8f to 0.3f)) {
            drawLine(Color(0xFF1E1108).copy(alpha = a), Offset(x, 128f), Offset(x + 0.4f, 150f), 0.5f)
        }
        wrappedGrip(maceGrip, 43.5f, 56.5f, 150f, 208f)
        val knob = path { addOval(Rect(41f, 204f, 59f, 218f)) }
        fillOutlined(knob, radial(46f, 207f, 12f, 0f to Color(0xFFF2F6F8), 0.45f to Color(0xFF8A969F), 1f to Color(0xFF262C31)), width = 0.8f)
        drawCircle(radial(47f, 220f, 10f, 0f to Color(0xFFF2F6F8), 0.45f to Color(0xFF8A969F), 1f to Color(0xFF262C31)), 7f, Offset(50f, 224f))
        drawCircle(Outline, 7f, Offset(50f, 224f), style = Stroke(0.8f))

        // Collar rings
        val collar = path { addRoundRect(RoundRect(40f, 106f, 60f, 120f, CornerRadius(3f))) }
        fillOutlined(collar, linear(steelStops, 40f, 0f, 60f, 0f), width = 0.8f)
        val collar2 = path { addRoundRect(RoundRect(42.5f, 121f, 57.5f, 127f, CornerRadius(2f))) }
        fillOutlined(collar2, linear(goldStops, 42.5f, 0f, 57.5f, 0f), outline = BrassDark, width = 0.7f)
        rivet(Offset(44.5f, 113f), 1.8f); rivet(Offset(50f, 113f), 1.8f); rivet(Offset(55.5f, 113f), 1.8f)

        // Head: back flanges, core, side flanges, front flange, spike.
        fillOutlined(maceBackLeft, linear(darkSteelStops, 17f, 0f, 39f, 0f), width = 0.8f)
        fillOutlined(maceBackRight, linear(darkSteelStops, 61f, 0f, 83f, 0f), width = 0.8f)
        val core = path { addRoundRect(RoundRect(35f, 28f, 65f, 112f, CornerRadius(12f))) }
        fillOutlined(core, radial(43f, 52f, 46f, 0f to Color(0xFFE6ECF0), 0.5f to Color(0xFF7D8992), 1f to Color(0xFF2A3136)), width = 0.9f)
        fillOutlined(maceFlangeLeft, linear(arrayOf(0f to Color(0xFFF4F8FA), 0.35f to Color(0xFFB9C4CB), 0.8f to Color(0xFF66727B), 1f to Color(0xFF3A434A)), 6f, 0f, 38f, 0f), width = 0.9f)
        fillOutlined(maceFlangeRight, linear(arrayOf(0f to Color(0xFF2E363C), 0.4f to Color(0xFF59656E), 0.85f to Color(0xFF8E9AA4), 1f to Color(0xFFB6C0C7)), 62f, 0f, 95f, 0f), width = 0.9f)
        // Flange edge highlights + lower shading
        drawPath(path { moveTo(13.5f, 41f); quadraticTo(6f, 50f, 9.8f, 59.5f); lineTo(14.6f, 69f) }, Color.White.copy(alpha = 0.8f), style = Stroke(0.9f, cap = StrokeCap.Round))
        drawPath(path { moveTo(14.6f, 71f); lineTo(9.8f, 80.5f); quadraticTo(6f, 90f, 13.5f, 99f) }, Color.White.copy(alpha = 0.45f), style = Stroke(0.8f, cap = StrokeCap.Round))
        clipPath(maceFlangeLeft) { drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startY = 24f, endY = 116f), Offset(0f, 24f), Size(40f, 92f)) }
        clipPath(maceFlangeRight) { drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.15f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startY = 24f, endY = 116f), Offset(60f, 24f), Size(40f, 92f)) }
        fillOutlined(maceFrontL, linear(arrayOf(0f to Color(0xFFB4BFC6), 1f to Color(0xFFF4F8FA)), 44f, 0f, 50f, 0f), width = 0.7f)
        fillOutlined(maceFrontR, linear(arrayOf(0f to Color(0xFF6D7881), 1f to Color(0xFF3E474E)), 50f, 0f, 56f, 0f), width = 0.7f)
        drawLine(Color.White.copy(alpha = 0.9f), Offset(49.6f, 22f), Offset(49.6f, 116f), 0.6f, StrokeCap.Round)
        val spike = path { moveTo(50f, 1f); lineTo(56.5f, 26f); quadraticTo(50f, 30f, 43.5f, 26f); close() }
        fillOutlined(spike, linear(arrayOf(0f to Color(0xFFF4F8FA), 0.5f to Color(0xFF9AA6AE), 1f to Color(0xFF3A434A)), 44f, 0f, 57f, 0f), width = 0.8f)
        // Gem inlay at the base of the head
        facetedGem(Offset(50f, 98f), 4.5f, 4.5f, gem)
    }

    private val axeHaft = path {
        moveTo(45f, 18f); cubicTo(44f, 80f, 44.5f, 140f, 45.5f, 206f); lineTo(55.5f, 206f)
        cubicTo(55f, 140f, 55f, 80f, 56f, 18f); quadraticTo(50.5f, 11f, 45f, 18f); close()
    }
    private val axeGrip = path {
        moveTo(44.6f, 146f); cubicTo(44.4f, 165f, 44.6f, 185f, 45.2f, 202f); lineTo(55.8f, 202f)
        cubicTo(56f, 185f, 55.9f, 165f, 55.7f, 146f); close()
    }
    private val axeBlade = path {
        moveTo(42f, 42f); cubicTo(32f, 40f, 20f, 31f, 10f, 15f)
        cubicTo(-1f, 46f, -1f, 94f, 8f, 126f)
        cubicTo(18f, 114f, 30f, 98f, 42f, 88f); close()
    }
    private val axeEdge = path {
        moveTo(10f, 15f); cubicTo(-1f, 46f, -1f, 94f, 8f, 126f); lineTo(12.5f, 116f)
        cubicTo(5.5f, 92f, 5.5f, 48f, 14.5f, 24f); close()
    }

    fun DrawScope.drawAxeIcon(gem: Gem) = diagonal(0.54f, 216f) {
        // Haft
        fillOutlined(axeHaft, linear(woodStops, 44f, 0f, 56f, 0f), width = 0.8f)
        for ((x, a) in listOf(47.3f to 0.35f, 50.2f to 0.25f, 53f to 0.3f)) {
            drawPath(path { moveTo(x, 100f); cubicTo(x - 0.6f, 115f, x + 0.6f, 130f, x, 144f) }, Color(0xFF1E1108).copy(alpha = a), style = Stroke(0.5f))
        }
        wrappedGrip(axeGrip, 44f, 56.5f, 146f, 202f)
        val butt = path { addRoundRect(RoundRect(43.8f, 200f, 57.2f, 216f, CornerRadius(3f))) }
        fillOutlined(butt, linear(steelStops, 44f, 0f, 57f, 0f), width = 0.8f)
        rivet(Offset(50.5f, 208f), 2f)

        // Blade
        fillOutlined(axeBlade, linear(arrayOf(0f to Color(0xFFE9EEF1), 0.22f to Color(0xFFB7C1C8), 0.55f to Color(0xFF6F7A83), 1f to Color(0xFF3B434A)), 2f, 0f, 42f, 0f), width = 1.4f)
        clipPath(axeBlade) {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.18f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startY = 15f, endY = 126f), Offset(0f, 10f), Size(45f, 120f))
            // Forge hammer marks
            for ((c, r) in listOf(Offset(28f, 60f) to 4f, Offset(33f, 76f) to 3.2f, Offset(24f, 88f) to 3.6f, Offset(35f, 52f) to 2.6f)) {
                drawOval(Color.Black.copy(alpha = 0.12f), Offset(c.x - r, c.y - r * 0.7f), Size(r * 2, r * 1.4f))
                drawArc(Color.White.copy(alpha = 0.18f), 180f, 90f, false, Offset(c.x - r, c.y - r * 0.7f), Size(r * 2, r * 1.4f), style = Stroke(0.5f))
            }
        }
        drawPath(axeEdge, Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.75f), 1f to Color.White.copy(alpha = 0.15f), startX = 1f, endX = 15f))
        drawPath(path { moveTo(14.5f, 24f); cubicTo(5.5f, 48f, 5.5f, 92f, 12.5f, 116f) }, Color(0xFF39424A).copy(alpha = 0.7f), style = Stroke(0.7f))
        drawPath(path { moveTo(10.2f, 16.5f); cubicTo(0.2f, 46f, 0.2f, 94f, 8.6f, 124f) }, Color.White.copy(alpha = 0.9f), style = Stroke(0.8f, cap = StrokeCap.Round))
        // Engraved decorative line following the blade
        drawPath(path { moveTo(38f, 50f); cubicTo(28f, 56f, 24f, 70f, 26f, 84f); cubicTo(28f, 90f, 33f, 92f, 38f, 86f) }, Color(0xFF262C31).copy(alpha = 0.55f), style = Stroke(0.8f, cap = StrokeCap.Round))
        drawPath(path { moveTo(38.4f, 51f); cubicTo(28.6f, 57f, 24.8f, 70f, 26.8f, 84f) }, Color.White.copy(alpha = 0.25f), style = Stroke(0.4f))

        // Back poll and socket
        val poll = path { moveTo(61f, 48f); lineTo(76f, 52f); quadraticTo(78f, 64f, 76f, 76f); lineTo(61f, 80f); close() }
        fillOutlined(poll, linear(darkSteelStops, 61f, 48f, 76f, 80f), width = 1f)
        val socket = path { addRoundRect(RoundRect(39.5f, 30f, 62.5f, 98f, CornerRadius(5f))) }
        fillOutlined(socket, linear(darkSteelStops, 39.5f, 0f, 62.5f, 0f), width = 1.1f)
        drawLine(Color.White.copy(alpha = 0.45f), Offset(43f, 34f), Offset(43f, 94f), 0.7f, StrokeCap.Round)
        drawLine(Outline.copy(alpha = 0.6f), Offset(40f, 46f), Offset(62f, 46f), 0.6f)
        drawLine(Outline.copy(alpha = 0.6f), Offset(40f, 82f), Offset(62f, 82f), 0.6f)
        rivet(Offset(51f, 38.5f), 2.6f); rivet(Offset(51f, 90f), 2.6f)
        facetedGem(Offset(51f, 64f), 6f, 7.5f, gem)
    }

    // ---------------------------------------------------------------- helmets

    private val capDome = path {
        moveTo(15f, 64f); cubicTo(14f, 30f, 32f, 12f, 50f, 12f); cubicTo(68f, 12f, 86f, 30f, 85f, 64f)
        cubicTo(70f, 60f, 30f, 60f, 15f, 64f); close()
    }
    private val capBand = path {
        moveTo(11f, 59f); cubicTo(30f, 53f, 70f, 53f, 89f, 59f); lineTo(90f, 71f)
        cubicTo(70f, 65f, 30f, 65f, 10f, 71f); close()
    }
    private val capFlapL = path {
        moveTo(13f, 66f); lineTo(28f, 66f); quadraticTo(30f, 84f, 23f, 91f); quadraticTo(16f, 93f, 13f, 86f)
        quadraticTo(11f, 76f, 13f, 66f); close()
    }
    private val capFlapR = mirrored { m ->
        moveTo(m(13f), 66f); lineTo(m(28f), 66f); quadraticTo(m(30f), 84f, m(23f), 91f); quadraticTo(m(16f), 93f, m(13f), 86f)
        quadraticTo(m(11f), 76f, m(13f), 66f); close()
    }

    fun DrawScope.drawLeatherCap() {
        // Ear flaps
        for (flap in listOf(capFlapL, capFlapR)) {
            fillOutlined(flap, linear(leatherStops, 10f, 66f, 32f, 92f))
            clipPath(flap) { drawRect(Color.Black.copy(alpha = 0.25f), Offset(0f, 0f), Size(100f, 100f)) }
            drawPath(flap, Stitch.copy(alpha = 0.55f), style = stitchStroke(0.6f))
        }
        drawPath(capFlapL, Outline, style = Stroke(1.1f))
        drawPath(capFlapR, Outline, style = Stroke(1.1f))
        // Dome
        drawPath(capDome, radial(38f, 26f, 62f, 0f to Color(0xFFC18D5D), 0.35f to Color(0xFF8A5A33), 0.75f to Color(0xFF55331D), 1f to Color(0xFF2A190D)))
        val seams = listOf(
            path { moveTo(50f, 13f); lineTo(50f, 59f) },
            path { moveTo(50f, 13f); cubicTo(36f, 22f, 28f, 40f, 28.5f, 60f) },
            path { moveTo(50f, 13f); cubicTo(64f, 22f, 72f, 40f, 71.5f, 60f) },
        )
        clipPath(capDome) {
            seams.forEach { s ->
                drawPath(s, Color(0xFF1E1108).copy(alpha = 0.85f), style = Stroke(1.3f))
                drawPath(s, Color(0xFFD9A77A).copy(alpha = 0.35f), style = Stroke(0.5f))
            }
            // Stitches running beside each seam
            listOf(
                path { moveTo(47.8f, 17f); lineTo(47.8f, 59f) }, path { moveTo(52.2f, 17f); lineTo(52.2f, 59f) },
                path { moveTo(47.5f, 16.5f); cubicTo(35f, 25f, 29.5f, 41f, 30.6f, 60f) },
                path { moveTo(52.5f, 16.5f); cubicTo(65f, 25f, 70.5f, 41f, 69.4f, 60f) },
            ).forEach { drawPath(it, Stitch.copy(alpha = 0.6f), style = stitchStroke(0.55f, 1.8f, 1.4f)) }
            // Soft sheen and worn patches
            drawCircle(radial(32f, 26f, 18f, 0f to Color.White.copy(alpha = 0.32f), 1f to Color.Transparent), 18f, Offset(32f, 26f))
            drawCircle(radial(70f, 46f, 14f, 0f to Color.Black.copy(alpha = 0.18f), 1f to Color.Transparent), 14f, Offset(70f, 46f))
        }
        drawPath(capDome, Outline, style = Stroke(1.2f, join = StrokeJoin.Round))
        // Top button
        drawCircle(radial(48.8f, 11.4f, 5f, 0f to Color(0xFFC79466), 0.6f to Color(0xFF6A4327), 1f to Color(0xFF2A190D)), 3.6f, Offset(50f, 12.5f))
        drawCircle(Outline, 3.6f, Offset(50f, 12.5f), style = Stroke(0.8f))
        // Band with stitching and brass rivets
        fillOutlined(capBand, linear(arrayOf(0f to Color(0xFF7A4E2D), 0.45f to Color(0xFF5A3720), 1f to Color(0xFF2E1B0E)), 0f, 54f, 0f, 71f), width = 1.1f)
        drawPath(path { moveTo(13f, 61.6f); cubicTo(30f, 56f, 70f, 56f, 87f, 61.6f) }, Stitch.copy(alpha = 0.7f), style = stitchStroke(0.6f))
        drawPath(path { moveTo(12.5f, 68.6f); cubicTo(30f, 63f, 70f, 63f, 87.5f, 68.6f) }, Stitch.copy(alpha = 0.55f), style = stitchStroke(0.6f))
        drawPath(path { moveTo(12f, 59.6f); cubicTo(30f, 54f, 70f, 54f, 88f, 59.6f) }, Color(0xFFD9A77A).copy(alpha = 0.45f), style = Stroke(0.6f))
        for (x in listOf(20f, 35f, 50f, 65f, 80f)) {
            val t = (x - 50f) / 39f
            rivet(Offset(x, 55.5f + 4.6f * t * t + 6f), 1.9f, gold = true)
        }
    }

    private val helmDome = path {
        moveTo(18f, 64f); cubicTo(17f, 34f, 32f, 10f, 50f, 6f); cubicTo(68f, 10f, 83f, 34f, 82f, 64f)
        cubicTo(66f, 60f, 34f, 60f, 18f, 64f); close()
    }
    private val helmBandC = path { moveTo(47.2f, 7.5f); lineTo(52.8f, 7.5f); lineTo(54f, 62f); lineTo(46f, 62f); close() }
    private val helmBandL = path {
        moveTo(46.5f, 8.5f); cubicTo(36f, 18f, 30f, 36f, 27.5f, 61.8f); lineTo(33.5f, 61f)
        cubicTo(35f, 38f, 40f, 20f, 49f, 8.5f); close()
    }
    private val helmBandR = mirrored { m ->
        moveTo(m(46.5f), 8.5f); cubicTo(m(36f), 18f, m(30f), 36f, m(27.5f), 61.8f); lineTo(m(33.5f), 61f)
        cubicTo(m(35f), 38f, m(40f), 20f, m(49f), 8.5f); close()
    }
    private val helmBrim = path {
        moveTo(15.5f, 59f); cubicTo(34f, 54f, 66f, 54f, 84.5f, 59f); lineTo(85f, 70f)
        cubicTo(66f, 65f, 34f, 65f, 15f, 70f); close()
    }
    private val helmNasal = path {
        moveTo(45.3f, 62f); lineTo(54.7f, 62f); lineTo(53.6f, 90f); quadraticTo(50f, 96f, 46.4f, 90f); close()
    }

    fun DrawScope.drawIronHelm() {
        // Dark inner lining visible below the rim
        drawOval(Brush.verticalGradient(listOf(Color(0xFF2A2F33), Color(0xFF07090A)), startY = 58f, endY = 82f), Offset(20f, 57f), Size(60f, 25f))
        drawOval(Outline, Offset(20f, 57f), Size(60f, 25f), style = Stroke(1f))
        // Dome
        drawPath(helmDome, radial(36f, 22f, 70f, 0f to Color(0xFFF6FAFC), 0.28f to Color(0xFFBAC5CC), 0.65f to Color(0xFF66727B), 1f to Color(0xFF262D32)))
        clipPath(helmDome) {
            drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.3f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.4f), startX = 18f, endX = 82f), Offset(0f, 0f), Size(100f, 70f))
            drawPath(path { moveTo(26f, 54f); cubicTo(26f, 36f, 32f, 22f, 40f, 15f) }, Color.White.copy(alpha = 0.65f), style = Stroke(2.2f, cap = StrokeCap.Round))
            drawPath(path { moveTo(60f, 16f); cubicTo(66f, 22f, 70f, 30f, 72f, 40f) }, Color.White.copy(alpha = 0.25f), style = Stroke(1.2f, cap = StrokeCap.Round))
        }
        drawPath(helmDome, Outline, style = Stroke(1.2f, join = StrokeJoin.Round))
        // Brass spangen
        for (b in listOf(helmBandL, helmBandR, helmBandC)) fillOutlined(b, linear(goldStops, 26f, 10f, 74f, 60f), outline = BrassDark, width = 0.8f)
        drawLine(Color.White.copy(alpha = 0.55f), Offset(49f, 10f), Offset(48.8f, 58f), 0.6f, StrokeCap.Round)
        for (y in listOf(20f, 32f, 44f)) rivet(Offset(50f, y), 1.5f, gold = true)
        for ((x, y) in listOf(39.5f to 24f, 34.5f to 38f, 31.5f to 52f)) {
            rivet(Offset(x, y), 1.4f, gold = true); rivet(Offset(100f - x, y), 1.4f, gold = true)
        }
        // Rim
        fillOutlined(helmBrim, linear(goldStops, 0f, 54f, 0f, 72f), outline = BrassDark, width = 1f)
        drawPath(path { moveTo(17f, 61f); cubicTo(34f, 56f, 66f, 56f, 83f, 61f) }, Color.White.copy(alpha = 0.45f), style = Stroke(0.6f))
        for (x in listOf(22f, 34f, 66f, 78f)) {
            val t = (x - 50f) / 34.5f
            rivet(Offset(x, 56f + 4f * t * t + 5.5f), 1.6f)
        }
        // Nasal guard
        fillOutlined(helmNasal, linear(arrayOf(0f to Color(0xFFDCE4E9), 0.5f to Color(0xFF9AA6AE), 1f to Color(0xFF3E474E)), 45f, 0f, 55f, 0f), width = 1f)
        drawLine(Color.White.copy(alpha = 0.7f), Offset(48.6f, 64f), Offset(48.9f, 90f), 0.6f, StrokeCap.Round)
        rivet(Offset(50f, 64.5f), 1.8f)
        // Finial
        drawCircle(radial(49f, 5.2f, 4.5f, 0f to Color(0xFFFFF3CC), 0.5f to Color(0xFFD9A944), 1f to Color(0xFF4F3209)), 2.8f, Offset(50f, 6.5f))
        drawCircle(BrassDark, 2.8f, Offset(50f, 6.5f), style = Stroke(0.6f))
    }

    // ---------------------------------------------------------------- body armor

    private val vestLeft = path {
        moveTo(33f, 9f); lineTo(48.6f, 50f); lineTo(48.6f, 91f); cubicTo(40f, 91f, 30f, 90f, 22f, 87f)
        lineTo(24.5f, 42f); cubicTo(28f, 36f, 26f, 22f, 19f, 13f); close()
    }
    private val vestRight = mirrored { m ->
        moveTo(m(33f), 9f); lineTo(m(48.6f), 50f); lineTo(m(48.6f), 91f); cubicTo(m(40f), 91f, m(30f), 90f, m(22f), 87f)
        lineTo(m(24.5f), 42f); cubicTo(m(28f), 36f, m(26f), 22f, m(19f), 13f); close()
    }

    fun DrawScope.drawLeatherVest() {
        // Linen shirt behind the neckline and the front gap
        val shirt = path { moveTo(31f, 9f); lineTo(69f, 9f); lineTo(52f, 92f); lineTo(48f, 92f); close() }
        drawPath(shirt, linear(arrayOf(0f to Color(0xFFF1E7CF), 0.6f to Color(0xFFC9B68E), 1f to Color(0xFF8C7A57)), 40f, 10f, 60f, 60f))
        drawPath(path { moveTo(38f, 12f); quadraticTo(50f, 20f, 62f, 12f) }, Color(0xFF8C7A57), style = Stroke(1f))
        drawPath(shirt, Outline, style = Stroke(0.9f))
        // Panels
        fillOutlined(vestLeft, linear(leatherStops, 14f, 10f, 56f, 80f), width = 1.2f)
        fillOutlined(vestRight, linear(arrayOf(0f to Color(0xFF26160B), 0.35f to Color(0xFF5E3B22), 0.6f to Color(0xFF7A4E2D), 1f to Color(0xFF2A190D)), 50f, 20f, 86f, 80f), width = 1.2f)
        for (panel in listOf(vestLeft, vestRight)) {
            clipPath(panel) {
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f), startY = 9f, endY = 92f), Offset(0f, 0f), Size(100f, 100f))
                // Scuffs
                drawLine(Color(0xFFD9A77A).copy(alpha = 0.18f), Offset(30f, 30f), Offset(36f, 33f), 0.6f, StrokeCap.Round)
                drawLine(Color(0xFFD9A77A).copy(alpha = 0.15f), Offset(66f, 58f), Offset(71f, 55f), 0.6f, StrokeCap.Round)
            }
        }
        // Stitched edges
        drawPath(path { moveTo(34.5f, 12f); lineTo(46.4f, 51f); lineTo(46.4f, 88.5f) }, Stitch.copy(alpha = 0.6f), style = stitchStroke(0.6f))
        drawPath(path { moveTo(65.5f, 12f); lineTo(53.6f, 51f); lineTo(53.6f, 88.5f) }, Stitch.copy(alpha = 0.45f), style = stitchStroke(0.6f))
        drawPath(path { moveTo(24.6f, 85f); cubicTo(31f, 87.4f, 39f, 88.4f, 46f, 88.5f) }, Stitch.copy(alpha = 0.55f), style = stitchStroke(0.6f))
        drawPath(path { moveTo(75.4f, 85f); cubicTo(69f, 87.4f, 61f, 88.4f, 54f, 88.5f) }, Stitch.copy(alpha = 0.45f), style = stitchStroke(0.6f))
        drawPath(path { moveTo(21.5f, 15f); cubicTo(28f, 24f, 29f, 35f, 26.8f, 42f) }, Stitch.copy(alpha = 0.5f), style = stitchStroke(0.6f))
        drawPath(path { moveTo(78.5f, 15f); cubicTo(72f, 24f, 71f, 35f, 73.2f, 42f) }, Stitch.copy(alpha = 0.4f), style = stitchStroke(0.6f))
        // Shoulder seams
        drawLine(Color(0xFF1E1108), Offset(22f, 16.5f), Offset(33.5f, 13f), 0.9f)
        drawLine(Color(0xFF1E1108), Offset(78f, 16.5f), Offset(66.5f, 13f), 0.9f)
        // Lacing
        val rows = listOf(54f, 61f, 68f)
        for (i in 0 until rows.size - 1) {
            drawLine(Color(0xFF3B2A14), Offset(46.6f, rows[i]), Offset(53.4f, rows[i + 1]), 1.6f, StrokeCap.Round)
            drawLine(Color(0xFF3B2A14), Offset(53.4f, rows[i]), Offset(46.6f, rows[i + 1]), 1.6f, StrokeCap.Round)
            drawLine(Color(0xFFE4CFA4), Offset(46.6f, rows[i]), Offset(53.4f, rows[i + 1]), 0.9f, StrokeCap.Round)
            drawLine(Color(0xFFE4CFA4), Offset(53.4f, rows[i]), Offset(46.6f, rows[i + 1]), 0.9f, StrokeCap.Round)
        }
        drawLine(Color(0xFFE4CFA4), Offset(46.6f, 54f), Offset(53.4f, 54f), 0.9f, StrokeCap.Round)
        for (y in rows) {
            for (x in listOf(46.6f, 53.4f)) {
                drawCircle(Color(0xFFD9A944), 1.3f, Offset(x, y))
                drawCircle(BrassDark, 1.3f, Offset(x, y), style = Stroke(0.5f))
            }
        }
        // Belt + buckle
        val belt = path { moveTo(22.6f, 74f); quadraticTo(50f, 78f, 77.4f, 74f); lineTo(77.2f, 81f); quadraticTo(50f, 85f, 22.8f, 81f); close() }
        fillOutlined(belt, linear(arrayOf(0f to Color(0xFF4E301B), 0.5f to Color(0xFF2E1B0E), 1f to Color(0xFF170D06)), 0f, 74f, 0f, 84f), width = 0.9f)
        drawPath(path { moveTo(23f, 75.4f); quadraticTo(50f, 79.4f, 77f, 75.4f) }, Color(0xFFD9A77A).copy(alpha = 0.35f), style = Stroke(0.5f))
        val buckle = RoundRect(43.5f, 72.5f, 56.5f, 84.5f, CornerRadius(2.5f))
        val buckleOuter = path { addRoundRect(buckle) }
        val buckleInner = path { addRoundRect(RoundRect(46.2f, 75.2f, 53.8f, 81.8f, CornerRadius(1.2f))) }
        val buckleRing = Path.combine(PathOperation.Difference, buckleOuter, buckleInner)
        fillOutlined(buckleRing, linear(goldStops, 43.5f, 72.5f, 56.5f, 84.5f), outline = BrassDark, width = 0.6f)
        drawLine(Color(0xFFE2B650), Offset(46.5f, 78.5f), Offset(54.5f, 78.5f), 1.1f, StrokeCap.Round)
        drawLine(BrassDark, Offset(46.5f, 79.3f), Offset(54.5f, 79.3f), 0.4f)
    }

    private val mailShape = path {
        moveTo(37f, 11f); quadraticTo(50f, 22f, 63f, 11f); lineTo(80f, 15f); cubicTo(87f, 22f, 92f, 32f, 94f, 44f)
        lineTo(81f, 49f); lineTo(78f, 40f); lineTo(79f, 90f); quadraticTo(50f, 94f, 21f, 90f); lineTo(22f, 40f)
        lineTo(19f, 49f); lineTo(6f, 44f); cubicTo(8f, 32f, 13f, 22f, 20f, 15f); close()
    }

    /** Interlocking ring texture, built once. */
    private val mailRings: Path by lazy {
        Path().apply {
            var row = 0
            var y = 8f
            while (y < 98f) {
                val off = if (row % 2 == 0) 0f else 2.1f
                var x = 2f + off
                while (x < 98f) {
                    addOval(Rect(Offset(x, y), 2.3f))
                    x += 4.2f
                }
                y += 3.5f
                row++
            }
        }
    }

    fun DrawScope.drawChainmail() {
        drawPath(mailShape, Color(0xFF1E2328))
        clipPath(mailShape) {
            drawPath(mailRings, Color(0xFF7F8B94), style = Stroke(1.05f))
            withTransform({ translate(-0.45f, -0.45f) }) {
                drawPath(mailRings, Color(0xFFE8EEF2).copy(alpha = 0.55f), style = Stroke(0.45f))
            }
            // Volume: lit from top-left, darker at the sides and hem
            drawRect(radial(40f, 34f, 62f, 0f to Color.White.copy(alpha = 0.22f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f)), Offset.Zero, Size(100f, 100f))
            drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startY = 10f, endY = 92f), Offset.Zero, Size(100f, 100f))
            // Fold shadows
            drawPath(path { moveTo(36f, 50f); quadraticTo(34f, 70f, 37f, 90f) }, Color.Black.copy(alpha = 0.3f), style = Stroke(3f, cap = StrokeCap.Round))
            drawPath(path { moveTo(64f, 52f); quadraticTo(66f, 70f, 63f, 90f) }, Color.Black.copy(alpha = 0.25f), style = Stroke(3f, cap = StrokeCap.Round))
        }
        drawPath(mailShape, Outline, style = Stroke(1.2f, join = StrokeJoin.Round))
        // Leather trims: collar, sleeve hems, bottom hem
        val trims = listOf(
            path { moveTo(36.5f, 12f); quadraticTo(50f, 23.5f, 63.5f, 12f) },
            path { moveTo(6.8f, 44.5f); lineTo(19.2f, 49.2f) },
            path { moveTo(93.2f, 44.5f); lineTo(80.8f, 49.2f) },
            path { moveTo(21.3f, 88.6f); quadraticTo(50f, 92.6f, 78.7f, 88.6f) },
        )
        trims.forEach { t ->
            drawPath(t, Color(0xFF120A05), style = Stroke(5f, cap = StrokeCap.Round))
            drawPath(t, Color(0xFF6A4327), style = Stroke(3.6f, cap = StrokeCap.Round))
            drawPath(t, Color(0xFFA6744A).copy(alpha = 0.6f), style = Stroke(1f, cap = StrokeCap.Round))
            drawPath(t, Stitch.copy(alpha = 0.6f), style = stitchStroke(0.45f, 1.5f, 1.2f))
        }
        for (x in listOf(28f, 40f, 50f, 60f, 72f)) {
            val t = (x - 50f) / 29f
            rivet(Offset(x, 92f - 3.6f * t * t - 1.2f), 1.3f)
        }
    }

    // ---------------------------------------------------------------- gloves

    private data class Finger(val x: Float, val w: Float, val top: Float, val bottom: Float, val angle: Float)

    private val fingers = listOf(
        Finger(61.2f, 8f, 24f, 52f, 10f),  // pinky
        Finger(52f, 9.2f, 13f, 50f, 4f),   // ring
        Finger(42.4f, 9.4f, 8.5f, 50f, -1f), // middle
        Finger(32.8f, 9.2f, 13f, 50f, -6f),  // index
    )
    private val thumb = Finger(21.5f, 10f, 30f, 64f, -38f)

    private val gloveBack = path {
        moveTo(32f, 42f); lineTo(69.5f, 45f); cubicTo(72f, 56f, 71f, 66f, 68f, 73f); lineTo(33.5f, 73f)
        cubicTo(29.5f, 64f, 29.5f, 52f, 32f, 42f); close()
    }
    private val gloveCuff = path {
        moveTo(33f, 70f); lineTo(68.5f, 70f); lineTo(77f, 95f); quadraticTo(51f, 99f, 24f, 95f); close()
    }

    fun DrawScope.drawGlove(plated: Boolean) {
        val baseStops = if (plated) steelStops else leatherStops
        val all = listOf(thumb) + fingers
        for (f in all) {
            val pivot = Offset(f.x + f.w / 2, f.bottom)
            withTransform({ rotate(f.angle, pivot) }) {
                val rr = RoundRect(f.x, f.top, f.x + f.w, f.bottom, CornerRadius(f.w / 2))
                val p = path { addRoundRect(rr) }
                // Leather underlayer for plated fingers
                drawPath(p, linear(if (plated) darkLeatherStops else baseStops, f.x, 0f, f.x + f.w, 0f))
                if (plated) {
                    var y = f.top + 1f
                    val segH = (f.bottom - f.top - 6f) / 3.4f
                    while (y < f.bottom - 6f) {
                        val seg = path { addRoundRect(RoundRect(f.x + 0.4f, y, f.x + f.w - 0.4f, y + segH, CornerRadius(f.w * 0.42f, segH * 0.45f))) }
                        drawPath(seg, linear(steelStops, f.x, 0f, f.x + f.w, 0f))
                        drawPath(seg, Outline.copy(alpha = 0.85f), style = Stroke(0.55f))
                        drawLine(Color.White.copy(alpha = 0.6f), Offset(f.x + f.w * 0.3f, y + 1.2f), Offset(f.x + f.w * 0.3f, y + segH - 1.4f), 0.5f, StrokeCap.Round)
                        y += segH - 1.1f
                    }
                } else {
                    clipPath(p) {
                        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 1f to Color.Black.copy(alpha = 0.25f), startY = f.top, endY = f.bottom), Offset(f.x, f.top), Size(f.w, f.bottom - f.top))
                    }
                    drawLine(Stitch.copy(alpha = 0.55f), Offset(f.x + f.w * 0.5f, f.top + 4f), Offset(f.x + f.w * 0.5f, f.bottom - 4f), 0.5f, StrokeCap.Round)
                    val ky = f.top + (f.bottom - f.top) * 0.42f
                    drawArc(Color(0xFF1E1108).copy(alpha = 0.55f), 20f, 140f, false, Offset(f.x + 1.5f, ky - 1.5f), Size(f.w - 3f, 3f), style = Stroke(0.5f))
                }
                drawPath(p, Outline, style = Stroke(1f))
            }
        }
        // Back of hand
        fillOutlined(gloveBack, linear(baseStops, 28f, 40f, 72f, 74f), width = 1.1f)
        if (plated) {
            // Knuckle plate with rivets and a raised center ridge
            val knuckle = path { addRoundRect(RoundRect(31f, 41f, 70.5f, 52f, CornerRadius(5f))) }
            fillOutlined(knuckle, linear(darkSteelStops, 31f, 41f, 70f, 52f), width = 0.9f)
            drawLine(Color.White.copy(alpha = 0.55f), Offset(34f, 43.4f), Offset(67f, 43.6f), 0.6f, StrokeCap.Round)
            for (x in listOf(37.5f, 47f, 56.5f, 65.5f)) rivet(Offset(x, 46.8f), 1.6f, gold = true)
            drawPath(path { moveTo(50.5f, 53f); lineTo(50.5f, 72f) }, Color.White.copy(alpha = 0.5f), style = Stroke(0.8f))
            drawPath(path { moveTo(51.4f, 53f); lineTo(51.4f, 72f) }, Outline.copy(alpha = 0.5f), style = Stroke(0.6f))
        } else {
            clipPath(gloveBack) {
                drawCircle(radial(42f, 52f, 16f, 0f to Color.White.copy(alpha = 0.2f), 1f to Color.Transparent), 16f, Offset(42f, 52f))
            }
            drawPath(path { moveTo(33f, 48f); quadraticTo(51f, 50.5f, 69.5f, 50f) }, Stitch.copy(alpha = 0.6f), style = stitchStroke(0.55f))
            drawPath(path { moveTo(38f, 54f); lineTo(40f, 70f) }, Color(0xFF1E1108).copy(alpha = 0.35f), style = Stroke(0.6f))
            drawPath(path { moveTo(47f, 54f); lineTo(47.5f, 70f) }, Color(0xFF1E1108).copy(alpha = 0.35f), style = Stroke(0.6f))
            drawPath(path { moveTo(56f, 54f); lineTo(55.5f, 70f) }, Color(0xFF1E1108).copy(alpha = 0.35f), style = Stroke(0.6f))
        }
        // Cuff
        if (plated) {
            fillOutlined(gloveCuff, linear(steelStops, 24f, 70f, 77f, 96f), width = 1.1f)
            for (i in 1..2) {
                val y = 70f + i * 8.3f
                val l = 33f - (y - 70f) * 0.36f
                val r = 68.5f + (y - 70f) * 0.34f
                drawLine(Outline.copy(alpha = 0.8f), Offset(l + 0.5f, y), Offset(r - 0.5f, y), 0.7f)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(l + 0.8f, y + 0.8f), Offset(r - 0.8f, y + 0.8f), 0.5f)
            }
            drawPath(path { moveTo(24.6f, 94.6f); quadraticTo(51f, 98.6f, 76.4f, 94.6f) }, Color(0xFFE2B650), style = Stroke(2.2f, cap = StrokeCap.Round))
            drawPath(path { moveTo(24.6f, 94.6f); quadraticTo(51f, 98.6f, 76.4f, 94.6f) }, BrassDark, style = Stroke(0.5f))
            drawLine(Color(0xFFE2B650), Offset(33.3f, 70.5f), Offset(68.2f, 70.5f), 1.6f, StrokeCap.Round)
        } else {
            fillOutlined(gloveCuff, linear(arrayOf(0f to Color(0xFF26160B), 0.3f to Color(0xFF5E3B22), 0.55f to Color(0xFF8A5A33), 1f to Color(0xFF2A190D)), 24f, 0f, 77f, 0f), width = 1.1f)
            drawPath(path { moveTo(34.2f, 73f); lineTo(67.4f, 73f) }, Stitch.copy(alpha = 0.6f), style = stitchStroke(0.55f))
            drawPath(path { moveTo(26.5f, 92.4f); quadraticTo(51f, 96f, 74.5f, 92.4f) }, Stitch.copy(alpha = 0.6f), style = stitchStroke(0.55f))
            // Lacing slit
            drawLine(Color(0xFF120A05), Offset(51f, 78f), Offset(51f, 96f), 1.2f)
            for (y in listOf(80f, 85f, 90f)) {
                drawLine(Color(0xFFE4CFA4), Offset(48.4f, y), Offset(53.6f, y + 3f), 0.8f, StrokeCap.Round)
                drawLine(Color(0xFFE4CFA4), Offset(53.6f, y), Offset(48.4f, y + 3f), 0.8f, StrokeCap.Round)
            }
        }
    }

    // ---------------------------------------------------------------- boots

    private val bootShape = path {
        moveTo(29f, 10f); lineTo(57f, 10f); cubicTo(58f, 26f, 58f, 42f, 59f, 56f); cubicTo(66f, 62f, 78f, 64f, 86f, 70f)
        cubicTo(92f, 74f, 93f, 84f, 88f, 86f); lineTo(30f, 86f); cubicTo(26f, 78f, 26f, 70f, 27f, 60f)
        cubicTo(28f, 44f, 28f, 26f, 29f, 10f); close()
    }
    private val bootSole = path {
        moveTo(25f, 84f); lineTo(88.5f, 84f); quadraticTo(93.5f, 85f, 91f, 90f); lineTo(46f, 90.5f); lineTo(44.5f, 94f)
        lineTo(27f, 94f); quadraticTo(24f, 90f, 25f, 84f); close()
    }
    private val bootCuff = path {
        moveTo(27.5f, 7f); lineTo(58.5f, 7f); lineTo(59f, 21f); quadraticTo(43f, 25.5f, 27f, 21f); close()
    }

    private fun DrawScope.boot(plated: Boolean) {
        fillOutlined(bootShape, linear(leatherStops, 22f, 20f, 72f, 70f), width = 1.2f)
        clipPath(bootShape) {
            drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Transparent, 0.45f to Color.White.copy(alpha = 0.12f), 0.6f to Color.Transparent, startX = 26f, endX = 60f), Offset(0f, 0f), Size(100f, 100f))
            drawCircle(radial(80f, 73f, 12f, 0f to Color.White.copy(alpha = 0.3f), 1f to Color.Transparent), 12f, Offset(80f, 73f))
            drawPath(path { moveTo(57f, 58f); cubicTo(60f, 66f, 62f, 74f, 62f, 84f) }, Color.Black.copy(alpha = 0.3f), style = Stroke(1.6f))
            drawPath(path { moveTo(59.5f, 57f); cubicTo(66f, 62f, 78f, 64.5f, 85.5f, 70.5f) }, Color(0xFFD9A77A).copy(alpha = 0.4f), style = Stroke(0.6f))
        }
        if (plated) {
            val greave = path { moveTo(41f, 22f); lineTo(57.6f, 22f); cubicTo(58.2f, 34f, 58.5f, 46f, 59f, 56f); lineTo(41.5f, 56f); quadraticTo(39f, 39f, 41f, 22f); close() }
            fillOutlined(greave, linear(steelStops, 40f, 0f, 60f, 0f), width = 0.9f)
            drawLine(Color.White.copy(alpha = 0.6f), Offset(45f, 25f), Offset(45.2f, 53f), 0.7f, StrokeCap.Round)
            for (y in listOf(26f, 52f)) { rivet(Offset(43.5f, y), 1.3f); rivet(Offset(55.5f, y), 1.3f) }
            // Sabaton: overlapping lames over the foot
            val lames = listOf(
                path { moveTo(58f, 57f); cubicTo(63f, 60f, 66f, 62f, 68f, 63f); lineTo(66f, 84f); lineTo(58f, 84f); close() },
                path { moveTo(67f, 62.5f); cubicTo(72f, 64f, 75f, 65f, 77f, 66f); lineTo(76f, 84f); lineTo(65.5f, 84f); close() },
                path { moveTo(76f, 65.5f); cubicTo(82f, 67f, 86f, 69f, 88f, 72f); cubicTo(91f, 76f, 92f, 83f, 88f, 84.5f); lineTo(75.5f, 84.5f); close() },
            )
            lames.forEach { l -> fillOutlined(l, linear(steelStops, 58f, 58f, 90f, 86f), width = 0.8f) }
            drawCircle(radial(82f, 72f, 8f, 0f to Color.White.copy(alpha = 0.6f), 1f to Color.Transparent), 6f, Offset(82f, 73f))
        } else {
            // Straps with brass buckles
            for ((y, slope) in listOf(34f to 0f, 50f to 1.5f)) {
                val strap = path { moveTo(28f, y); lineTo(58.4f, y + slope); lineTo(58.6f, y + slope + 5f); lineTo(27.8f, y + 5f); close() }
                fillOutlined(strap, linear(darkLeatherStops, 0f, y, 0f, y + 5f), width = 0.7f)
                drawLine(Stitch.copy(alpha = 0.5f), Offset(29f, y + 1f), Offset(51f, y + 1f + slope * 0.7f), 0.4f)
                val bx = 49f
                val frame = Path.combine(
                    PathOperation.Difference,
                    path { addRoundRect(RoundRect(bx, y - 1.4f + slope * 0.7f, bx + 7f, y + 6.4f + slope * 0.7f, CornerRadius(1.2f))) },
                    path { addRoundRect(RoundRect(bx + 1.6f, y + 0.2f + slope * 0.7f, bx + 5.4f, y + 4.8f + slope * 0.7f, CornerRadius(0.6f))) },
                )
                fillOutlined(frame, linear(goldStops, bx, y, bx + 7f, y + 6f), outline = BrassDark, width = 0.45f)
                drawLine(Color(0xFFE2B650), Offset(bx + 1f, y + 2.5f + slope * 0.7f), Offset(bx + 6f, y + 2.5f + slope * 0.7f), 0.8f)
            }
            // Toe cap seam
            drawPath(path { moveTo(72f, 65.5f); cubicTo(70f, 72f, 71f, 79f, 73f, 85f) }, Color(0xFF1E1108).copy(alpha = 0.6f), style = Stroke(0.7f))
            drawPath(path { moveTo(73.8f, 66f); cubicTo(72f, 72f, 73f, 79f, 74.8f, 85f) }, Stitch.copy(alpha = 0.5f), style = stitchStroke(0.5f, 1.6f, 1.2f))
        }
        // Turned-down cuff
        fillOutlined(bootCuff, linear(arrayOf(0f to Color(0xFF603C22), 0.35f to Color(0xFFB98557), 0.6f to Color(0xFF8A5A33), 1f to Color(0xFF3A2414)), 27f, 0f, 59f, 0f), width = 1f)
        drawPath(path { moveTo(28.5f, 19.4f); quadraticTo(43f, 23.6f, 57.8f, 19.4f) }, Stitch.copy(alpha = 0.6f), style = stitchStroke(0.5f))
        drawOval(Color(0xFF120A05), Offset(29f, 5.5f), Size(28.5f, 3.6f))
        // Sole + heel
        fillOutlined(bootSole, linear(arrayOf(0f to Color(0xFF3A2A1E), 0.5f to Color(0xFF1C130C), 1f to Color(0xFF0B0704)), 0f, 84f, 0f, 94f), width = 1f)
        drawLine(Color(0xFF8A6A4A).copy(alpha = 0.5f), Offset(27f, 85f), Offset(88f, 85f), 0.6f)
        drawPath(path { moveTo(31f, 82.6f); lineTo(87f, 82.6f) }, Stitch.copy(alpha = 0.55f), style = stitchStroke(0.5f, 1.6f, 1.2f))
    }

    fun DrawScope.drawBoots(plated: Boolean) {
        // Rear boot: offset and darkened in its own layer.
        val bounds = Rect(0f, 0f, 100f, 100f)
        drawContext.canvas.saveLayer(bounds, Paint())
        withTransform({ translate(-12f, -5f); scale(0.94f, 0.94f, pivot = Offset(50f, 50f)) }) { boot(plated) }
        drawRect(Color(0xFF0B0D10).copy(alpha = 0.5f), blendMode = BlendMode.SrcAtop)
        drawContext.canvas.restore()
        withTransform({ translate(6f, 2f) }) { boot(plated) }
    }

    // ---------------------------------------------------------------- jewelry

    fun DrawScope.drawRing(gold: Boolean, gem: Gem) {
        val metal = if (gold) goldStops else silverStops
        val dark = if (gold) BrassDark else Color(0xFF262C31)
        val outer = Rect(15f, 38f, 85f, 93f)
        val inner = Rect(25f, 49.5f, 75f, 82f)
        val band = path {
            fillType = PathFillType.EvenOdd
            addOval(outer); addOval(inner)
        }
        // Back inner wall seen through the hole
        val hole = path { addOval(inner) }
        val shifted = path { addOval(inner.translate(0f, 6f)) }
        val wall = Path.combine(PathOperation.Difference, hole, shifted)
        drawPath(wall, linear(if (gold) arrayOf(0f to Color(0xFF4A2F08), 0.5f to Color(0xFFB98A30), 1f to Color(0xFF5A3A0A)) else arrayOf(0f to Color(0xFF353C42), 0.5f to Color(0xFF9AA6AE), 1f to Color(0xFF3E464D)), 25f, 0f, 75f, 0f))
        drawPath(band, linear(metal, 18f, 40f, 82f, 92f))
        drawPath(band, Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.3f), 0.35f to Color.Transparent, 0.8f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startY = 38f, endY = 93f))
        drawArc(Color.White.copy(alpha = 0.8f), 112f, 48f, false, Offset(17.5f, 40.5f), Size(65f, 50f), style = Stroke(1.4f, cap = StrokeCap.Round))
        drawArc(Color.White.copy(alpha = 0.45f), 20f, 40f, false, Offset(17.5f, 40.5f), Size(65f, 50f), style = Stroke(1f, cap = StrokeCap.Round))
        drawArc(Color.White.copy(alpha = 0.35f), 200f, 60f, false, Offset(26.5f, 51f), Size(47f, 29.5f), style = Stroke(0.8f, cap = StrokeCap.Round))
        drawPath(band, dark, style = Stroke(1f))
        // Shoulders + basket setting
        val shoulders = path {
            moveTo(31f, 45f); quadraticTo(40f, 39f, 41f, 34f); lineTo(59f, 34f); quadraticTo(60f, 39f, 69f, 45f)
            quadraticTo(50f, 41f, 31f, 45f); close()
        }
        fillOutlined(shoulders, linear(metal, 30f, 34f, 70f, 46f), outline = dark, width = 0.8f)
        val basket = path { moveTo(38f, 36f); lineTo(62f, 36f); lineTo(57f, 44f); lineTo(43f, 44f); close() }
        fillOutlined(basket, linear(metal, 38f, 36f, 62f, 44f), outline = dark, width = 0.8f)
        // Gem + prongs
        val gc = Offset(50f, 26f)
        facetedGem(gc, 15.5f, 12.5f, gem)
        for (a in listOf(45.0, 135.0, 225.0, 315.0)) {
            val r = Math.toRadians(a)
            val p = Offset(gc.x + 14.6f * cos(r).toFloat(), gc.y + 11.8f * sin(r).toFloat())
            val prong = path { addOval(Rect(p.x - 2.2f, p.y - 2.6f, p.x + 2.2f, p.y + 2.6f)) }
            fillOutlined(prong, radial(p.x - 0.8f, p.y - 1f, 3.5f, 0f to Color.White, 0.5f to (if (gold) Color(0xFFE2B650) else Color(0xFFC6CED5)), 1f to dark), outline = dark, width = 0.5f)
        }
        sparkle(Offset(26f, 62f), 3.2f, 0.75f)
    }

    /** Point and tangent angle on a cubic bezier. */
    private fun bezier(t: Float, p0: Offset, p1: Offset, p2: Offset, p3: Offset): Pair<Offset, Float> {
        val u = 1 - t
        val pt = p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
        val d = (p1 - p0) * (3 * u * u) + (p2 - p1) * (6 * u * t) + (p3 - p2) * (3 * t * t)
        return pt to Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat()
    }

    private fun DrawScope.chain(end: Offset) {
        val halves = listOf(
            listOf(Offset(14f, 0f), Offset(15f, 26f), Offset(34f, end.y - 4f), end),
            listOf(Offset(86f, 0f), Offset(85f, 26f), Offset(66f, end.y - 4f), end),
        )
        halves.forEach { (p0, p1, p2, p3) ->
            val n = 12
            for (i in 0 until n) {
                val (pt, ang) = bezier((i + 0.5f) / n, p0, p1, p2, p3)
                withTransform({ rotate(ang, pt) }) {
                    if (i % 2 == 0) {
                        drawOval(Color(0xFF3A2506), Offset(pt.x - 3.3f, pt.y - 2.1f), Size(6.6f, 4.2f), style = Stroke(1.9f))
                        drawOval(Color(0xFFE2B650), Offset(pt.x - 3.3f, pt.y - 2.1f), Size(6.6f, 4.2f), style = Stroke(1.1f))
                        drawArc(Color(0xFFFFF1C2), 190f, 100f, false, Offset(pt.x - 3.3f, pt.y - 2.1f), Size(6.6f, 4.2f), style = Stroke(0.5f))
                    } else {
                        drawRoundRect(Color(0xFF3A2506), Offset(pt.x - 3.4f, pt.y - 1f), Size(6.8f, 2f), CornerRadius(1f))
                        drawRoundRect(Color(0xFFC99A3A), Offset(pt.x - 3f, pt.y - 0.55f), Size(6f, 1.1f), CornerRadius(0.55f))
                    }
                }
            }
        }
    }

    private fun teardrop(cx: Float, cy: Float, s: Float) = path {
        // Unit teardrop: tip at (0,-30), round bottom radius ~20, center (0,0)
        fun p(x: Float, y: Float) = Offset(cx + x * s, cy + y * s)
        val a = p(0f, -30f); moveTo(a.x, a.y)
        val c1 = p(10f, -22f); val c2 = p(22f, -12f); val e1 = p(20f, 0f); cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        val c3 = p(18f, 12f); val c4 = p(8f, 18f); val e2 = p(0f, 18f); cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        val c5 = p(-8f, 18f); val c6 = p(-18f, 12f); val e3 = p(-20f, 0f); cubicTo(c5.x, c5.y, c6.x, c6.y, e3.x, e3.y)
        val c7 = p(-22f, -12f); val c8 = p(-10f, -22f); cubicTo(c7.x, c7.y, c8.x, c8.y, a.x, a.y)
        close()
    }

    fun DrawScope.drawAmulet(gem: Gem) {
        chain(Offset(50f, 44f))
        // Bail
        drawCircle(BrassDark, 4f, Offset(50f, 46f), style = Stroke(2.6f))
        drawCircle(Color(0xFFE2B650), 4f, Offset(50f, 46f), style = Stroke(1.5f))
        drawArc(Color(0xFFFFF1C2), 200f, 90f, false, Offset(46f, 42f), Size(8f, 8f), style = Stroke(0.6f))
        // Filigree curls beside the pendant
        for (side in listOf(-1f, 1f)) {
            val cx = 50f + side * 21.5f
            drawArc(BrassDark, if (side < 0) 300f else 0f, 240f, false, Offset(cx - 5f, 62f), Size(10f, 10f), style = Stroke(2.4f, cap = StrokeCap.Round))
            drawArc(Color(0xFFE2B650), if (side < 0) 300f else 0f, 240f, false, Offset(cx - 5f, 62f), Size(10f, 10f), style = Stroke(1.3f, cap = StrokeCap.Round))
            drawCircle(Color(0xFFE2B650), 1.6f, Offset(cx, 67f))
            drawCircle(BrassDark, 1.6f, Offset(cx, 67f), style = Stroke(0.4f))
        }
        // Gold frame
        val frame = teardrop(50f, 76f, 1.05f)
        fillOutlined(frame, linear(goldStops, 28f, 46f, 72f, 96f), outline = BrassDark, width = 1f)
        drawPath(teardrop(50f, 76f, 0.93f), Color(0xFFFFF1C2).copy(alpha = 0.55f), style = Stroke(0.6f))
        // Granulation dots around the frame
        for (i in 0 until 14) {
            val a = Math.toRadians(-90.0 + 360.0 / 14 * i)
            val r = 18.5f
            val p = Offset(50f + r * cos(a).toFloat(), 79f + r * 0.95f * sin(a).toFloat())
            if (p.y > 60f) {
                drawCircle(Color(0xFFFFE08A), 0.9f, p)
                drawCircle(BrassDark, 0.9f, p, style = Stroke(0.3f))
            }
        }
        // Gem
        val stone = teardrop(50f, 78f, 0.72f)
        drawPath(stone, radial(46f, 72f, 18f, 0f to gem.light, 0.4f to gem.mid, 1f to gem.dark))
        clipPath(stone) {
            for (i in 0 until 8) {
                val a = Math.toRadians(22.5 + 45.0 * i)
                drawLine(gem.dark.copy(alpha = 0.45f), Offset(50f, 78f), Offset(50f + 20f * cos(a).toFloat(), 78f + 20f * sin(a).toFloat()), 0.45f)
            }
            drawPath(teardrop(50f, 78f, 0.38f), gem.light.copy(alpha = 0.35f))
        }
        drawPath(stone, BrassDark, style = Stroke(0.9f))
        sparkle(Offset(45.5f, 70f), 3.6f)
        // Drop
        drawCircle(radial(49f, 98f, 3f, 0f to gem.light, 0.5f to gem.mid, 1f to gem.dark), 2.4f, Offset(50f, 97.4f))
    }

    fun DrawScope.drawTalisman(gem: Gem) {
        chain(Offset(50f, 46f))
        drawCircle(BrassDark, 4f, Offset(50f, 48f), style = Stroke(2.6f))
        drawCircle(Color(0xFFE2B650), 4f, Offset(50f, 48f), style = Stroke(1.5f))
        val c = Offset(50f, 74f)
        // Rim
        drawCircle(linear(goldStops, 28f, 52f, 72f, 96f), 23f, c)
        drawCircle(BrassDark, 23f, c, style = Stroke(1f))
        // Inner disc (sunken)
        drawCircle(linear(arrayOf(0f to Color(0xFF6E4A12), 0.5f to Color(0xFFC99A3A), 1f to Color(0xFFF1D58A)), 36f, 60f, 64f, 88f), 18f, c)
        drawCircle(BrassDark, 18f, c, style = Stroke(0.8f))
        drawArc(Color(0xFFFFF1C2).copy(alpha = 0.8f), 20f, 140f, false, Offset(c.x - 18f, c.y - 18f), Size(36f, 36f), style = Stroke(0.6f))
        // Rune ticks on the rim
        for (i in 0 until 24) {
            val a = Math.toRadians(360.0 / 24 * i)
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            drawLine(BrassDark.copy(alpha = 0.7f), Offset(c.x + 19.5f * ca, c.y + 19.5f * sa), Offset(c.x + 21.8f * ca, c.y + 21.8f * sa), if (i % 3 == 0) 0.9f else 0.5f)
        }
        // Embossed 8-point star
        val star = path {
            for (i in 0 until 16) {
                val a = Math.toRadians(-90.0 + 22.5 * i)
                val r = if (i % 2 == 0) 15.5f else 6.2f
                val x = c.x + r * cos(a).toFloat()
                val y = c.y + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        withTransform({ translate(0.8f, 0.8f) }) { drawPath(star, Color.Black.copy(alpha = 0.35f)) }
        drawPath(star, linear(goldStops, c.x - 15f, c.y - 15f, c.x + 15f, c.y + 15f))
        drawPath(star, BrassDark, style = Stroke(0.6f, join = StrokeJoin.Miter))
        for (i in 0 until 8) {
            val a = Math.toRadians(-90.0 + 45.0 * i)
            drawLine(BrassDark.copy(alpha = 0.55f), c, Offset(c.x + 15.5f * cos(a).toFloat(), c.y + 15.5f * sin(a).toFloat()), 0.4f)
        }
        facetedGem(c, 5.8f, 5.8f, gem)
        drawCircle(BrassDark, 6.2f, c, style = Stroke(0.8f))
        sparkle(Offset(38f, 60f), 3f, 0.7f)
    }
}
