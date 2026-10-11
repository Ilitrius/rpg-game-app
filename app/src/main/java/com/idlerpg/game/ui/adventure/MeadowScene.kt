package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * High-resolution painted landscape of zone 1 "Wiese", built once per size (all paths and
 * brushes cached) and drawn every frame with only the clouds drifting.
 *
 * Layers (back to front): sky gradient + sun glow, drifting clouds, three hill ridges with
 * atmospheric perspective and tree lines, horizon haze, meadow ground with soft patches, dirt path
 * in perspective with ruts and stones, grass blades and flowers. [drawForeground] adds the
 * nearest tall grass and a vignette on top of the monster.
 */
class MeadowScene(private val size: Size) : ZoneScene {
    private val w = size.width
    private val h = size.height
    override val horizonY = h * 0.40f

    /** Screen y where a monster at full size stands. */
    override val monsterGroundY = h * 0.68f

    // ------------------------------------------------------------------ sky
    private val skyBrush = Brush.verticalGradient(
        0f to Color(0xFF2F6DBA),
        0.35f to Color(0xFF5893D3),
        0.68f to Color(0xFF94C0E6),
        0.88f to Color(0xFFCFE2EE),
        1f to Color(0xFFF3EAD0),
        startY = 0f, endY = horizonY,
    )
    private val sunCenter = Offset(w * 0.80f, h * 0.085f)
    private val sunGlow = Brush.radialGradient(
        0f to Color(0xFFFFF5DA).copy(alpha = 0.85f),
        0.18f to Color(0xFFFFF0C8).copy(alpha = 0.45f),
        0.55f to Color(0xFFFFE9C0).copy(alpha = 0.12f),
        1f to Color.Transparent,
        center = sunCenter, radius = w * 0.75f,
    )
    private val sunDisc = Brush.radialGradient(
        0f to Color.White,
        0.55f to Color(0xFFFFFBEA),
        0.8f to Color(0xFFFFF1C4).copy(alpha = 0.6f),
        1f to Color.Transparent,
        center = sunCenter, radius = w * 0.06f,
    )

    // ------------------------------------------------------------------ clouds
    private class Puff(val center: Offset, val radius: Float, val body: Brush, val shade: Brush)
    private class Cloud(val baseX: Float, val y: Float, val width: Float, val speed: Float, val alpha: Float, val puffs: List<Puff>)

    private val clouds: List<Cloud> = run {
        val r = Random(7)
        val specs = listOf(
            // x, y, scale, speed (screen widths per cycle), alpha
            floatArrayOf(0.15f, 0.07f, 1.10f, 1.2f, 0.95f),
            floatArrayOf(0.62f, 0.15f, 0.85f, 0.9f, 0.9f),
            floatArrayOf(1.05f, 0.05f, 0.70f, 1.4f, 0.85f),
            floatArrayOf(0.35f, 0.24f, 0.55f, 0.6f, 0.75f),
            floatArrayOf(0.85f, 0.28f, 0.45f, 0.5f, 0.65f),
            floatArrayOf(-0.1f, 0.30f, 0.40f, 0.45f, 0.6f),
        )
        specs.map { s ->
            val unit = w * 0.11f * s[2]
            val count = 6 + r.nextInt(3)
            val puffs = (0 until count).map { i ->
                val t = i / (count - 1f) * 2f - 1f // -1..1 along the cloud
                val rad = unit * (0.55f + 0.45f * (1f - t * t)) * (0.85f + r.nextFloat() * 0.3f)
                val cx = t * unit * 1.9f + (r.nextFloat() - 0.5f) * unit * 0.3f
                val cy = -(1f - t * t) * unit * 0.45f + (r.nextFloat() - 0.5f) * unit * 0.15f
                val c = Offset(cx, cy)
                Puff(
                    center = c,
                    radius = rad,
                    body = Brush.radialGradient(
                        0f to Color.White,
                        0.55f to Color(0xFFF7FAFD),
                        0.8f to Color(0xFFE6EDF6).copy(alpha = 0.85f),
                        1f to Color(0xFFDDE6F2).copy(alpha = 0f),
                        center = c - Offset(rad * 0.18f, rad * 0.22f), radius = rad,
                    ),
                    shade = Brush.radialGradient(
                        0f to Color(0xFF9FB2CB).copy(alpha = 0.55f),
                        0.7f to Color(0xFFAFC0D6).copy(alpha = 0.3f),
                        1f to Color.Transparent,
                        center = c + Offset(0f, rad * 0.25f), radius = rad * 1.05f,
                    ),
                )
            }
            Cloud(s[0] * w, s[1] * h, unit * 4.6f, s[3], s[4], puffs)
        }
    }

    // ------------------------------------------------------------------ hills
    private class Ridge(val path: Path, val brush: Brush, val yAt: (Float) -> Float)

    private fun ridge(base: Float, amp: Float, seed: Int, fill: List<Pair<Float, Color>>): Ridge {
        val r = Random(seed)
        val f1 = (1.5f + r.nextFloat()) * 2f * PI.toFloat() / w
        val f2 = (3.5f + r.nextFloat() * 2f) * 2f * PI.toFloat() / w
        val f3 = (9f + r.nextFloat() * 4f) * 2f * PI.toFloat() / w
        val p1 = r.nextFloat() * 6.28f
        val p2 = r.nextFloat() * 6.28f
        val p3 = r.nextFloat() * 6.28f
        val yAt = { x: Float ->
            base - amp * (0.55f + 0.30f * sin(x * f1 + p1) + 0.15f * sin(x * f2 + p2) + 0.05f * sin(x * f3 + p3))
        }
        val path = Path().apply {
            moveTo(-4f, horizonY + 4f)
            var x = -4f
            while (x <= w + 8f) {
                lineTo(x, yAt(x))
                x += 6f
            }
            lineTo(w + 8f, horizonY + 4f)
            close()
        }
        val top = base - amp
        val brush = Brush.verticalGradient(*fill.toTypedArray(), startY = top, endY = horizonY + 4f)
        return Ridge(path, brush, yAt)
    }

    private val farRidge = ridge(
        horizonY - h * 0.030f, h * 0.070f, 3,
        listOf(0f to Color(0xFF8EA8C6), 1f to Color(0xFFB9CCDB)),
    )
    private val midRidge = ridge(
        horizonY - h * 0.012f, h * 0.045f, 11,
        listOf(0f to Color(0xFF7C9D93), 0.6f to Color(0xFF93B2A4), 1f to Color(0xFFAFC7B6)),
    )
    private val nearRidge = ridge(
        horizonY + h * 0.004f, h * 0.026f, 23,
        listOf(0f to Color(0xFF6E9852), 1f to Color(0xFF93B56C)),
    )

    /** Tree crowns on a ridge: circles with a soft radial gradient, faded with distance. */
    private class TreeLayer(val crowns: List<Triple<Offset, Float, Brush>>, val haze: Color)

    private fun trees(ridge: Ridge, seed: Int, count: Int, sizeFrac: Float, dark: Color, light: Color, haze: Color): TreeLayer {
        val r = Random(seed)
        val crowns = ArrayList<Triple<Offset, Float, Brush>>()
        var x = r.nextFloat() * w * 0.05f
        repeat(count) {
            // trees grow in small groves
            val grove = 2 + r.nextInt(4)
            repeat(grove) {
                val rad = h * sizeFrac * (0.7f + r.nextFloat() * 0.6f)
                val cx = x + rad * 0.9f
                val base = ridge.yAt(cx) + rad * 0.55f
                val c = Offset(cx, base - rad)
                listOf(Offset(0f, 0f), Offset(-rad * 0.55f, rad * 0.35f), Offset(rad * 0.55f, rad * 0.3f)).forEach { o ->
                    val cc = c + o
                    val rr = rad * (if (o == Offset.Zero) 1f else 0.78f)
                    crowns += Triple(
                        cc, rr,
                        Brush.radialGradient(
                            0f to light, 0.7f to dark, 1f to dark.copy(alpha = 0.9f),
                            center = cc - Offset(rr * 0.35f, rr * 0.4f), radius = rr * 1.3f,
                        ),
                    )
                }
                x += rad * 1.4f
            }
            x += w * (0.04f + r.nextFloat() * 0.14f)
            if (x > w * 1.05f) return TreeLayer(crowns, haze)
        }
        return TreeLayer(crowns, haze)
    }

    private val midTrees = trees(midRidge, 5, 12, 0.0075f, Color(0xFF5B7F69), Color(0xFF86A68D), Color(0xFFB4C9C2).copy(alpha = 0.35f))
    private val nearTrees = trees(nearRidge, 9, 9, 0.012f, Color(0xFF2F5A2C), Color(0xFF5E8E45), Color(0xFFC7D8C0).copy(alpha = 0.18f))

    private val hazeBrush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.55f to Color(0xFFEFF0E4).copy(alpha = 0.38f),
        0.75f to Color(0xFFEFF0E4).copy(alpha = 0.30f),
        1f to Color.Transparent,
        startY = horizonY - h * 0.09f, endY = horizonY + h * 0.07f,
    )

    // ------------------------------------------------------------------ ground
    private val groundBrush = Brush.verticalGradient(
        0f to Color(0xFFA8C46C),
        0.12f to Color(0xFF8DBA50),
        0.45f to Color(0xFF5F9A35),
        0.8f to Color(0xFF40792A),
        1f to Color(0xFF2B5C1C),
        startY = horizonY, endY = h,
    )

    /** depth 0 at the horizon … 1 at the bottom edge. */
    private fun yAtDepth(d: Float) = horizonY + (h - horizonY) * d

    private fun pathCenterX(d: Float) = w * 0.5f + w * 0.07f * sin(d * PI.toFloat() * 0.85f) * d
    private fun pathHalfWidth(d: Float) = w * (0.008f + 0.36f * d)

    private val groundPatches: List<Pair<Rect, Brush>> = run {
        val r = Random(17)
        (0 until 14).map {
            val d = 0.05f + r.nextFloat() * 0.95f
            val cy = yAtDepth(d)
            val rx = w * (0.15f + r.nextFloat() * 0.25f) * (0.4f + d)
            val ry = rx * (0.10f + 0.25f * d)
            val cx = r.nextFloat() * w
            val light = r.nextBoolean()
            val col = if (light) Color(0xFFC4DA7A).copy(alpha = 0.30f) else Color(0xFF2E5A1E).copy(alpha = 0.28f)
            val rect = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
            rect to Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), center = rect.center, radius = rx)
        }
    }

    private val dirtPath = Path().apply {
        val n = 48
        moveTo(pathCenterX(0f) - pathHalfWidth(0f), horizonY)
        for (i in 1..n) {
            val d = i / n.toFloat() * 1.05f
            lineTo(pathCenterX(d) - pathHalfWidth(d), yAtDepth(d))
        }
        for (i in n downTo 0) {
            val d = i / n.toFloat() * 1.05f
            lineTo(pathCenterX(d) + pathHalfWidth(d), yAtDepth(d))
        }
        close()
    }
    private val dirtBrush = Brush.verticalGradient(
        0f to Color(0xFFDCC9A2),
        0.25f to Color(0xFFC6A577),
        0.6f to Color(0xFF9E7A51),
        1f to Color(0xFF6E4F31),
        startY = horizonY, endY = h,
    )
    private val dirtEdgeShade = Brush.horizontalGradient(
        0f to Color(0xFF3E2A16).copy(alpha = 0.35f),
        0.2f to Color.Transparent,
        0.8f to Color.Transparent,
        1f to Color(0xFF3E2A16).copy(alpha = 0.35f),
        startX = w * 0.12f, endX = w * 0.92f,
    )

    private fun lineAlongPath(offset: Float): Path = Path().apply {
        val n = 40
        for (i in 0..n) {
            val d = 0.02f + i / n.toFloat() * 1.03f
            val x = pathCenterX(d) + pathHalfWidth(d) * offset
            if (i == 0) moveTo(x, yAtDepth(d)) else lineTo(x, yAtDepth(d))
        }
    }
    private val ruts = listOf(lineAlongPath(-0.45f), lineAlongPath(0.45f))
    private val centerStrip = lineAlongPath(0f)

    private class Stone(val rect: Rect, val brush: Brush, val shadow: Rect)

    private val stones: List<Stone> = run {
        val r = Random(29)
        (0 until 26).map {
            val d = 0.04f + r.nextFloat().pow(0.8f) * 0.98f
            val off = (r.nextFloat() * 2f - 1f) * 0.92f
            val cx = pathCenterX(d) + pathHalfWidth(d) * off
            val cy = yAtDepth(d)
            val rx = w * (0.004f + 0.016f * d) * (0.6f + r.nextFloat() * 0.8f)
            val ry = rx * (0.55f + r.nextFloat() * 0.2f)
            val rect = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
            Stone(
                rect,
                Brush.radialGradient(
                    0f to Color(0xFFE2DCCD), 0.5f to Color(0xFFA79E8C), 1f to Color(0xFF5E5546),
                    center = Offset(cx - rx * 0.35f, cy - ry * 0.45f), radius = rx * 1.3f,
                ),
                Rect(cx - rx * 1.05f, cy - ry * 0.1f, cx + rx * 1.25f, cy + ry * 1.2f),
            )
        }
    }

    // ------------------------------------------------------------------ grass + flowers
    private class GrassLayer(val paths: List<Pair<Path, Color>>)

    private val grassColors = listOf(
        Color(0xFF2A5A1A), Color(0xFF3B7424), Color(0xFF4E8C2C), Color(0xFF69A63A), Color(0xFF8CC151), Color(0xFFB2D676),
    )

    private fun addBlade(p: Path, x: Float, y: Float, height: Float, lean: Float) {
        val bw = height * 0.075f + 0.6f
        p.moveTo(x - bw, y)
        p.cubicTo(x - bw * 0.6f, y - height * 0.45f, x + lean * 0.35f, y - height * 0.8f, x + lean, y - height)
        p.cubicTo(x + lean * 0.35f + bw * 0.4f, y - height * 0.75f, x + bw * 0.7f, y - height * 0.4f, x + bw, y)
        p.close()
    }

    private fun insidePath(x: Float, d: Float, margin: Float) =
        kotlin.math.abs(x - pathCenterX(d)) < pathHalfWidth(d) * margin

    private fun buildGrass(seed: Int, count: Int, dMin: Float, dMax: Float, heightScale: Float): GrassLayer {
        val r = Random(seed)
        val paths = grassColors.map { Path() }
        repeat(count) {
            // more samples near the horizon in screen space = uniform-ish ground coverage
            val u = r.nextFloat()
            val d = dMin + (dMax - dMin) * u.pow(1.35f)
            val x = r.nextFloat() * (w + 40f) - 20f
            if (insidePath(x, d, 0.86f)) return@repeat
            val y = yAtDepth(d) + r.nextFloat() * 2f
            val height = h * (0.004f + 0.075f * d.pow(1.3f)) * heightScale * (0.6f + r.nextFloat() * 0.8f)
            val lean = height * (r.nextFloat() - 0.45f) * 0.55f
            // far blades are hazier (lighter), near blades span the full range
            val shade = (r.nextFloat() * (2f + 4f * d) + (1f - d) * 3f).toInt().coerceIn(0, grassColors.lastIndex)
            addBlade(paths[shade], x, y, height, lean)
        }
        return GrassLayer(paths.zip(grassColors))
    }

    private val backGrass = buildGrass(41, 2600, 0.0f, 0.78f, 1f)
    private val frontGrass = buildGrass(43, 520, 0.80f, 1.08f, 1.15f)

    /** Grass tufts that spill over the path edges. */
    private val edgeGrass: GrassLayer = run {
        val r = Random(47)
        val paths = grassColors.map { Path() }
        repeat(700) {
            val d = 0.02f + r.nextFloat().pow(1.2f) * 0.8f
            val side = if (r.nextBoolean()) 1f else -1f
            val x = pathCenterX(d) + side * pathHalfWidth(d) * (0.8f + r.nextFloat() * 0.3f)
            val y = yAtDepth(d) + r.nextFloat() * 2f
            val height = h * (0.004f + 0.05f * d) * (0.5f + r.nextFloat() * 0.8f)
            val lean = height * (r.nextFloat() - 0.5f) * 0.6f - side * height * 0.1f
            val shade = (r.nextFloat() * 5f).toInt().coerceIn(0, grassColors.lastIndex)
            addBlade(paths[shade], x, y, height, lean)
        }
        GrassLayer(paths.zip(grassColors))
    }

    private class FlowerSet(val petals: Path, val petalColor: Color, val centers: Path, val centerColor: Color)

    private val flowers: List<FlowerSet> = run {
        val r = Random(53)
        val defs = listOf(
            Color(0xFFFFFFFF) to Color(0xFFF2C230),
            Color(0xFFFFE15A) to Color(0xFFD08A1A),
            Color(0xFFD9A0E8) to Color(0xFFF7E27A),
            Color(0xFFF4F0FF) to Color(0xFFE9B23A),
        )
        defs.mapIndexed { idx, (petal, center) ->
            val pp = Path()
            val cp = Path()
            repeat(if (idx == 0) 150 else 70) {
                val d = 0.03f + r.nextFloat().pow(1.25f) * 1.0f
                val x = r.nextFloat() * w
                if (insidePath(x, d, 1.0f)) return@repeat
                val y = yAtDepth(d) - h * 0.012f * d * r.nextFloat()
                val rad = w * (0.0012f + 0.0075f * d)
                for (k in 0 until 5) {
                    val a = k / 5f * 2f * PI.toFloat() + idx
                    val px = x + kotlin.math.cos(a) * rad
                    val py = y + sin(a) * rad * 0.65f
                    pp.addOval(Rect(px - rad * 0.62f, py - rad * 0.42f, px + rad * 0.62f, py + rad * 0.42f))
                }
                cp.addOval(Rect(x - rad * 0.42f, y - rad * 0.3f, x + rad * 0.42f, y + rad * 0.3f))
            }
            FlowerSet(pp, petal, cp, center)
        }
    }

    private val vignette = Brush.radialGradient(
        0f to Color.Transparent,
        0.62f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.42f),
        center = Offset(w * 0.5f, h * 0.46f), radius = max(w, h) * 0.78f,
    )
    private val bottomShade = Brush.verticalGradient(
        0f to Color.Transparent, 1f to Color(0xFF0E1A08).copy(alpha = 0.45f),
        startY = h * 0.82f, endY = h,
    )

    // ------------------------------------------------------------------ drawing

    /** Draws everything behind the monster. [drift] runs 0..1 over the cloud cycle. */
    override fun drawBackground(scope: DrawScope, drift: Float, time: Float) = with(scope) {
        drawRect(skyBrush, size = Size(w, horizonY + 6f))
        drawRect(sunGlow, size = Size(w, horizonY + 6f))
        drawCircle(sunDisc, radius = w * 0.06f, center = sunCenter)

        clouds.forEach { c ->
            val span = w + c.width * 2f
            val x = ((c.baseX + c.width + drift * c.speed * span) % span) - c.width
            translate(x, c.y) {
                c.puffs.forEach { p -> drawCircle(p.shade, p.radius * 1.05f, p.center, alpha = c.alpha) }
                c.puffs.forEach { p -> drawCircle(p.body, p.radius, p.center, alpha = c.alpha) }
            }
        }

        drawPath(farRidge.path, farRidge.brush)
        drawPath(midRidge.path, midRidge.brush)
        drawTrees(midTrees)
        drawPath(nearRidge.path, nearRidge.brush)
        drawTrees(nearTrees)

        drawRect(groundBrush, topLeft = Offset(0f, horizonY + 2f), size = Size(w, h - horizonY))
        drawRect(hazeBrush, topLeft = Offset(0f, horizonY - h * 0.09f), size = Size(w, h * 0.16f))
        groundPatches.forEach { (rect, brush) -> drawOval(brush, rect.topLeft, rect.size) }

        // path
        drawPath(dirtPath, dirtBrush)
        drawPath(dirtPath, dirtEdgeShade)
        val rutWidth = h * 0.006f
        ruts.forEach {
            drawPath(it, Color(0xFF4A3220).copy(alpha = 0.22f), style = Stroke(width = rutWidth * 2.2f, cap = StrokeCap.Round))
            drawPath(it, Color(0xFF3A2614).copy(alpha = 0.18f), style = Stroke(width = rutWidth, cap = StrokeCap.Round))
        }
        drawPath(centerStrip, Color(0xFFEBDDBE).copy(alpha = 0.18f), style = Stroke(width = rutWidth * 3f, cap = StrokeCap.Round))
        stones.forEach { s ->
            drawOval(Color.Black.copy(alpha = 0.22f), s.shadow.topLeft, s.shadow.size)
            drawOval(s.brush, s.rect.topLeft, s.rect.size)
        }

        backGrass.paths.forEach { (p, c) -> drawPath(p, c) }
        edgeGrass.paths.forEach { (p, c) -> drawPath(p, c) }
        flowers.forEach { f ->
            drawPath(f.petals, f.petalColor)
            drawPath(f.centers, f.centerColor)
        }
        drawRect(Color(0xFFFFF4D0).copy(alpha = 0.06f), size = size) // warm sunlight tint
    }

    /** Nearest grass + vignette, drawn on top of the monster. */
    override fun drawForeground(scope: DrawScope, time: Float) = with(scope) {
        frontGrass.paths.forEach { (p, c) -> drawPath(p, c) }
        drawRect(bottomShade, topLeft = Offset(0f, h * 0.82f), size = Size(w, h * 0.18f))
        drawRect(vignette, size = size)
    }

    private fun DrawScope.drawTrees(layer: TreeLayer) {
        layer.crowns.forEach { (c, r, b) ->
            drawCircle(Color.Black.copy(alpha = 0.12f), r * 1.02f, c + Offset(r * 0.12f, r * 0.15f))
            drawCircle(b, r, c)
        }
        layer.crowns.forEach { (c, r, _) -> drawCircle(layer.haze, r, c) }
    }
}
