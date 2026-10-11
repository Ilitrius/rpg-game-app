package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zone 4 "Gebirge": a rocky mountain path under a cold blue sky. Two ranges of jagged peaks with
 * shaded faces and snow caps, cloud wisps between them, pine forests on the slopes, alpine meadow
 * with snow patches, a gravel path lined with faceted boulders, and gently falling snow.
 */
class MountainScene(size: Size) : ZoneScene {
    private val w = size.width
    private val h = size.height
    override val horizonY = h * 0.45f
    override val monsterGroundY = h * 0.69f

    private fun yAtDepth(d: Float) = horizonY + (h - horizonY) * d
    private fun pathCenterX(d: Float) = w * 0.5f + w * 0.08f * sin(d * 2.6f + 0.4f) * d
    private fun pathHalfWidth(d: Float) = w * (0.008f + 0.30f * d)

    private val skyBrush = Brush.verticalGradient(
        0f to Color(0xFF173E78), 0.45f to Color(0xFF4E86C4), 0.85f to Color(0xFFA9CBE8), 1f to Color(0xFFE2EEF6),
        startY = 0f, endY = horizonY,
    )
    private val sun = Offset(w * 0.2f, h * 0.07f)
    private val sunGlow = Brush.radialGradient(
        0f to Color.White, 0.06f to Color(0xFFFFFBEA), 0.2f to Color(0xFFFFF6DA).copy(alpha = 0.35f), 1f to Color.Transparent,
        center = sun, radius = w * 0.6f,
    )

    // ------------------------------------------------------------------ ranges
    private class Range(val outline: Path, val rock: Brush, val faces: Path, val faceColor: Color, val snow: Path, val snowBrush: Brush, val cracks: Path)

    private fun range(seed: Int, base: Float, peakMin: Float, peakMax: Float, count: Int, snowFrac: Float, rockTop: Color, rockBottom: Color, faceColor: Color, snowTint: Color): Range {
        val r = Random(seed)
        // peaks: alternating summits and valleys
        val pts = ArrayList<Offset>()
        val step = w * 1.2f / count
        var x = -w * 0.1f
        pts += Offset(x, base)
        while (x < w * 1.1f) {
            val px = x + step * (0.35f + r.nextFloat() * 0.3f)
            val py = base - (peakMin + r.nextFloat() * (peakMax - peakMin))
            val vx = x + step
            val vy = base - (peakMin * (0.2f + r.nextFloat() * 0.4f))
            pts += Offset(px, py)
            pts += Offset(vx, vy)
            x = vx
        }
        // jagged sub-points between the main points
        val jag = ArrayList<Offset>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            jag += a
            val n = 5
            for (k in 1 until n) {
                val t = k / n.toFloat()
                val len = abs(b.y - a.y) + abs(b.x - a.x)
                jag += Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t + (r.nextFloat() - 0.5f) * len * 0.07f)
            }
        }
        jag += pts.last()
        val outline = Path().apply {
            moveTo(-w * 0.1f, horizonY + 4f)
            jag.forEach { lineTo(it.x, it.y) }
            lineTo(w * 1.1f, horizonY + 4f)
            close()
        }
        // shadowed right faces: from each summit down to the next valley
        val faces = Path()
        for (i in 1 until pts.size - 1 step 2) {
            val s = pts[i]
            val v = pts[i + 1]
            faces.moveTo(s.x, s.y)
            faces.lineTo(v.x, v.y)
            faces.lineTo(v.x - (v.x - s.x) * 0.15f, base + 4f)
            faces.lineTo(s.x + (v.x - s.x) * 0.05f, base + 4f)
            faces.close()
        }
        // snow above an irregular snow line around each summit
        val snow = Path()
        for (i in 1 until pts.size - 1 step 2) {
            val s = pts[i]
            val l = pts[i - 1]
            val v = pts[i + 1]
            val drop = (base - s.y) * snowFrac
            snow.moveTo(s.x, s.y - 2f)
            val segs = 7
            // left flank down, zig-zag across, right flank up
            val lx = s.x + (l.x - s.x) * (drop / (l.y - s.y).coerceAtLeast(1f)).coerceAtMost(1f)
            val rx = s.x + (v.x - s.x) * (drop / (v.y - s.y).coerceAtLeast(1f)).coerceAtMost(1f)
            snow.lineTo(lx, s.y + drop)
            for (k in 1..segs) {
                val t = k / segs.toFloat()
                val zx = lx + (rx - lx) * t
                val zy = s.y + drop * (if (k % 2 == 0) 0.7f + r.nextFloat() * 0.5f else 1.1f + r.nextFloat() * 0.4f)
                snow.lineTo(zx, zy)
            }
            snow.lineTo(rx, s.y + drop)
            snow.close()
        }
        val cracks = Path().apply {
            repeat(count * 6) {
                val i = 1 + 2 * r.nextInt(((pts.size - 2) / 2).coerceAtLeast(1))
                val s = pts[i.coerceAtMost(pts.size - 2)]
                val cx = s.x + (r.nextFloat() - 0.5f) * step * 0.5f
                val cy = s.y + (base - s.y) * (0.2f + r.nextFloat() * 0.6f)
                moveTo(cx, cy)
                lineTo(cx + (r.nextFloat() - 0.3f) * step * 0.12f, cy + (base - s.y) * (0.05f + r.nextFloat() * 0.15f))
            }
        }
        val top = base - peakMax
        return Range(
            outline, Brush.verticalGradient(0f to rockTop, 1f to rockBottom, startY = top, endY = base),
            faces, faceColor, snow,
            Brush.verticalGradient(0f to Color.White, 0.7f to snowTint, 1f to SceneKit.mix(snowTint, rockTop, 0.4f), startY = top, endY = base - peakMin * 0.3f),
            cracks,
        )
    }

    private val farRange = range(3, horizonY - h * 0.02f, h * 0.10f, h * 0.24f, 5, 0.42f,
        Color(0xFF8DA2BE), Color(0xFFB8C8DA), Color(0xFF5E7598).copy(alpha = 0.45f), Color(0xFFDCE6F4))
    private val midRange = range(9, horizonY + h * 0.005f, h * 0.05f, h * 0.12f, 7, 0.28f,
        Color(0xFF6E7480), Color(0xFF8C8E86), Color(0xFF2E3440).copy(alpha = 0.45f), Color(0xFFE8EEF4))

    private class Cloud(val y: Float, val width: Float, val speed: Float, val phase: Float, val puffs: List<Triple<Offset, Float, Float>>)

    private val clouds: List<Cloud> = run {
        val r = Random(13)
        List(5) {
            val width = w * (0.35f + r.nextFloat() * 0.4f)
            Cloud(
                horizonY - h * (0.03f + r.nextFloat() * 0.12f), width, 0.5f + r.nextFloat() * 0.6f, r.nextFloat(),
                List(7) { Triple(Offset(r.nextFloat() * width, (r.nextFloat() - 0.5f) * h * 0.012f), w * (0.03f + r.nextFloat() * 0.05f), 0.4f + r.nextFloat() * 0.4f) },
            )
        }
    }

    // ------------------------------------------------------------------ slopes + pines
    private val slopeBrush = Brush.verticalGradient(
        0f to Color(0xFF9EA88A), 0.12f to Color(0xFF7F8C62), 0.5f to Color(0xFF66704A), 1f to Color(0xFF3E4430),
        startY = horizonY, endY = h,
    )
    /** Rolling foothill in front of the ranges that hides their straight base. */
    private val foothill = Path().apply {
        moveTo(-5f, horizonY + h * 0.05f)
        var x = -5f
        while (x <= w + 10f) {
            val y = horizonY - h * (0.012f + 0.012f * sin(x / w * 7f + 1f) + 0.006f * sin(x / w * 19f))
            lineTo(x, y)
            x += 6f
        }
        lineTo(w + 10f, horizonY + h * 0.05f)
        close()
    }
    private val foothillBrush = Brush.verticalGradient(
        0f to Color(0xFF8E9C86), 1f to Color(0xFF9EA88A), startY = horizonY - h * 0.03f, endY = horizonY + h * 0.02f,
    )

    private class Pine(val path: Path, val brush: Brush)

    private val pines: List<Pine> = run {
        val r = Random(17)
        val list = ArrayList<Pine>()
        repeat(70) {
            val d = -0.02f + r.nextFloat().pow(1.6f) * 0.2f
            val x = r.nextFloat() * w
            if (abs(x - pathCenterX(d)) < pathHalfWidth(d) * 2.2f) return@repeat
            val y = yAtDepth(d)
            val ph = h * (0.02f + 0.3f * d.coerceAtLeast(0f)) * (0.7f + r.nextFloat() * 0.6f)
            val pw = ph * 0.38f
            val p = Path().apply {
                val tiers = 4
                for (k in 0 until tiers) {
                    val t0 = k / tiers.toFloat()
                    val ty = y - ph * (0.15f + t0 * 0.85f)
                    val bw = pw * (1f - t0 * 0.7f)
                    moveTo(x, ty - ph * 0.32f)
                    lineTo(x + bw / 2f, ty + ph * 0.08f)
                    quadraticTo(x, ty, x - bw / 2f, ty + ph * 0.08f)
                    close()
                }
                addRect(Rect(x - pw * 0.05f, y - ph * 0.16f, x + pw * 0.05f, y))
            }
            val haze = (1f - (d + 0.02f) / 0.2f).coerceIn(0f, 1f)
            val dark = SceneKit.mix(Color(0xFF1E3A26), Color(0xFF7E9488), haze * 0.7f)
            list += Pine(p, Brush.horizontalGradient(listOf(SceneKit.mix(dark, Color(0xFF9CC0A0), 0.25f), dark), startX = x - pw / 2f, endX = x + pw / 2f))
        }
        list.sortedBy { it.path.getBounds().bottom }
    }

    private val snowPatches: List<Pair<Rect, Brush>> = run {
        val r = Random(19)
        (0 until 16).map {
            val d = 0.04f + r.nextFloat() * 0.9f
            val cy = yAtDepth(d)
            val rx = w * (0.03f + r.nextFloat() * 0.12f) * (0.4f + d)
            val ry = rx * (0.12f + 0.15f * d)
            var cx = r.nextFloat() * w
            if (abs(cx - pathCenterX(d)) < pathHalfWidth(d) + rx) cx = (cx + w * 0.5f) % w
            val rect = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
            rect to Brush.radialGradient(
                0f to Color(0xFFFAFCFF), 0.6f to Color(0xFFDDE7F2), 0.9f to Color(0xFFB8C8DC).copy(alpha = 0.6f), 1f to Color.Transparent,
                center = rect.center - Offset(0f, ry * 0.3f), radius = rx,
            )
        }
    }

    private val gravel = Path().apply {
        val n = 48
        moveTo(pathCenterX(0f) - pathHalfWidth(0f), horizonY)
        for (i in 1..n) { val d = i / n.toFloat() * 1.05f; lineTo(pathCenterX(d) - pathHalfWidth(d), yAtDepth(d)) }
        for (i in n downTo 0) { val d = i / n.toFloat() * 1.05f; lineTo(pathCenterX(d) + pathHalfWidth(d), yAtDepth(d)) }
        close()
    }
    private val gravelBrush = Brush.verticalGradient(
        0f to Color(0xFFD0CCC0), 0.3f to Color(0xFFA9A396), 0.7f to Color(0xFF857E70), 1f to Color(0xFF5E584C),
        startY = horizonY, endY = h,
    )
    private val pebbles: List<Pair<Path, Color>> = run {
        val r = Random(23)
        val colors = listOf(Color(0xFF6A6458), Color(0xFF8E887A), Color(0xFFB8B2A4), Color(0xFF4E4A42))
        val paths = colors.map { Path() }
        repeat(500) {
            val d = 0.02f + r.nextFloat().pow(0.9f) * 1.0f
            val x = pathCenterX(d) + pathHalfWidth(d) * (r.nextFloat() * 2f - 1f) * 1.02f
            val y = yAtDepth(d)
            val rx = w * (0.001f + 0.006f * d) * (0.5f + r.nextFloat())
            paths[r.nextInt(paths.size)].addOval(Rect(x - rx, y - rx * 0.55f, x + rx, y + rx * 0.55f))
        }
        paths.zip(colors)
    }

    private class Boulder(val path: Path, val brush: Brush, val facet: Path, val snowCap: Path?, val shadow: Rect)

    private fun boulder(c: Offset, rx: Float, ry: Float, r: Random, snowy: Boolean): Boulder {
        val outline = SceneKit.rock(c, rx, ry, r, 8)
        val facet = Path().apply {
            moveTo(c.x + rx * 0.1f, c.y - ry * 0.9f)
            lineTo(c.x + rx * 0.95f, c.y - ry * 0.2f)
            lineTo(c.x + rx, c.y)
            lineTo(c.x + rx * 0.2f, c.y)
            close()
        }
        val cap = if (snowy) Path().apply {
            moveTo(c.x - rx * 0.7f, c.y - ry * 0.55f)
            quadraticTo(c.x - rx * 0.1f, c.y - ry * 1.15f, c.x + rx * 0.6f, c.y - ry * 0.6f)
            quadraticTo(c.x, c.y - ry * 0.75f, c.x - rx * 0.7f, c.y - ry * 0.55f)
            close()
        } else null
        return Boulder(
            outline,
            Brush.radialGradient(
                0f to Color(0xFFC6C2B8), 0.5f to Color(0xFF8A8478), 1f to Color(0xFF4A453C),
                center = Offset(c.x - rx * 0.4f, c.y - ry * 0.7f), radius = rx * 1.5f,
            ),
            facet, cap,
            Rect(c.x - rx * 1.1f, c.y - ry * 0.08f, c.x + rx * 1.3f, c.y + ry * 0.22f),
        )
    }

    private val boulders: List<Boulder> = run {
        val r = Random(29)
        (0 until 20).map {
            val d = 0.03f + r.nextFloat().pow(1.3f) * 0.85f
            val side = if (r.nextBoolean()) 1f else -1f
            val rx = w * (0.008f + 0.07f * d) * (0.7f + r.nextFloat() * 0.6f)
            val x = pathCenterX(d) + side * (pathHalfWidth(d) + rx * (0.6f + r.nextFloat() * 1.5f))
            boulder(Offset(x, yAtDepth(d)), rx, rx * (0.6f + r.nextFloat() * 0.3f), r, r.nextFloat() < 0.5f)
        }.sortedBy { it.shadow.top }
    }
    private val frontBoulders: List<Boulder> = run {
        val r = Random(31)
        listOf(
            boulder(Offset(w * 0.02f, h * 1.01f), w * 0.2f, h * 0.07f, r, true),
            boulder(Offset(w * 1.0f, h * 1.0f), w * 0.16f, h * 0.06f, r, false),
        )
    }

    private val grass: List<Pair<Path, Color>> = run {
        val r = Random(37)
        val colors = listOf(Color(0xFF4E5A30), Color(0xFF6E7A3E), Color(0xFF94985A), Color(0xFFB8B47A))
        val paths = colors.map { Path() }
        repeat(1100) {
            val d = r.nextFloat().pow(1.3f) * 1.0f
            val x = r.nextFloat() * w
            if (abs(x - pathCenterX(d)) < pathHalfWidth(d) * 0.95f) return@repeat
            val y = yAtDepth(d)
            val bh = h * (0.003f + 0.035f * d) * (0.5f + r.nextFloat())
            val lean = bh * (r.nextFloat() - 0.5f) * 0.7f
            val bw = bh * 0.07f + 0.5f
            val p = paths[r.nextInt(paths.size)]
            p.moveTo(x - bw, y)
            p.quadraticTo(x + lean * 0.3f, y - bh * 0.6f, x + lean, y - bh)
            p.quadraticTo(x + lean * 0.3f + bw, y - bh * 0.5f, x + bw, y)
            p.close()
        }
        paths.zip(colors)
    }

    private class Flake(val x: Float, val y0: Float, val r: Float, val speed: Int, val phase: Float)

    private val flakes: List<Flake> = run {
        val r = Random(41)
        List(70) { Flake(r.nextFloat() * w, r.nextFloat() * h, w * (0.0015f + r.nextFloat() * 0.004f), 2 + r.nextInt(4), r.nextFloat() * 6.28f) }
    }

    private val vignette = SceneKit.vignette(w, h, 0.5f, Color(0xFF06101C))
    private val coldTint = Color(0xFFBFD8F0).copy(alpha = 0.05f)

    // ------------------------------------------------------------------ drawing

    private fun DrawScope.drawRange(rg: Range) {
        drawPath(rg.outline, rg.rock)
        clipPath(rg.outline) {
            drawPath(rg.faces, rg.faceColor)
            drawPath(rg.snow, rg.snowBrush)
            drawPath(rg.cracks, Color(0xFF2E3440).copy(alpha = 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.0015f))
        }
    }

    private fun DrawScope.drawBoulder(b: Boulder) {
        drawOval(Color.Black.copy(alpha = 0.25f), b.shadow.topLeft, b.shadow.size)
        drawPath(b.path, b.brush)
        clipPath(b.path) {
            drawPath(b.facet, Color(0xFF2A2620).copy(alpha = 0.35f))
            b.snowCap?.let { drawPath(it, Brush.verticalGradient(listOf(Color.White, Color(0xFFD0DCEA)), startY = b.shadow.top - b.shadow.height * 3f, endY = b.shadow.top)) }
        }
        drawPath(b.path, Color(0xFF2A2620).copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.0012f))
    }

    override fun drawBackground(scope: DrawScope, drift: Float, time: Float) = with(scope) {
        drawRect(skyBrush, size = Size(w, horizonY + 4f))
        drawRect(sunGlow, size = Size(w, horizonY))
        drawRange(farRange)
        clouds.forEach { c ->
            val x = ((c.phase + drift * c.speed * 2f) % 1f) * (w + c.width) - c.width
            translate(x, c.y) {
                c.puffs.forEach { (o, rad, a) ->
                    drawOval(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), center = o, radius = rad),
                        topLeft = o - Offset(rad, rad * 0.4f), size = Size(rad * 2f, rad * 0.8f),
                    )
                }
            }
        }
        drawRange(midRange)
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color(0xFFE6EEF6).copy(alpha = 0.5f), 1f to Color.Transparent, startY = horizonY - h * 0.06f, endY = horizonY + h * 0.04f),
            topLeft = Offset(0f, horizonY - h * 0.06f), size = Size(w, h * 0.1f),
        )
        drawPath(foothill, foothillBrush)
        drawRect(slopeBrush, topLeft = Offset(0f, horizonY), size = Size(w, h - horizonY))
        pines.forEach { drawPath(it.path, it.brush) }
        snowPatches.forEach { (rect, b) -> drawOval(b, rect.topLeft, rect.size) }
        drawPath(gravel, gravelBrush)
        drawPath(
            gravel,
            Brush.horizontalGradient(
                0f to Color(0xFF2A2620).copy(alpha = 0.35f), 0.2f to Color.Transparent, 0.8f to Color.Transparent, 1f to Color(0xFF2A2620).copy(alpha = 0.35f),
                startX = w * 0.12f, endX = w * 0.92f,
            ),
        )
        pebbles.forEach { (p, c) -> drawPath(p, c) }
        grass.forEach { (p, c) -> drawPath(p, c) }
        boulders.forEach { drawBoulder(it) }
        drawRect(coldTint, size = size)
    }

    override fun drawForeground(scope: DrawScope, time: Float) = with(scope) {
        frontBoulders.forEach { drawBoulder(it) }
        flakes.forEach { f ->
            val fall = ((time / 60f * f.speed * 3f + f.y0 / h) % 1f) * h
            val sway = SceneKit.wave(time, f.speed * 2, f.phase) * w * 0.015f
            drawCircle(Color.White.copy(alpha = 0.85f), f.r, Offset(f.x + sway, fall))
        }
        drawRect(vignette, size = size)
    }
}
