package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zone 2 "Wald": deep forest seen along a leafy path. Hazy layers of tree trunks recede towards a
 * bright glade, a dark canopy closes the top, god rays fall through gaps (shimmering), dust motes
 * float in the light. Ground with moss, leaf litter, ferns and small mushrooms; big ferns and two
 * huge trunks frame the foreground.
 */
class ForestScene(size: Size) : ZoneScene {
    private val w = size.width
    private val h = size.height
    override val horizonY = h * 0.42f
    override val monsterGroundY = h * 0.68f

    private val glow = Offset(w * 0.54f, horizonY - h * 0.03f)
    private val skyBrush = Brush.verticalGradient(
        0f to Color(0xFF1E3320),
        0.35f to Color(0xFF5E8A5A),
        0.75f to Color(0xFFB9CF9A),
        1f to Color(0xFFE6E9BE),
        startY = 0f, endY = horizonY + 4f,
    )
    private val glowBrush = Brush.radialGradient(
        0f to Color(0xFFFFF6D2).copy(alpha = 0.95f),
        0.25f to Color(0xFFF4EBB0).copy(alpha = 0.55f),
        0.6f to Color(0xFFD8E2A0).copy(alpha = 0.15f),
        1f to Color.Transparent,
        center = glow, radius = w * 0.6f,
    )

    // ------------------------------------------------------------------ trunks
    private class TrunkLayer(val trunks: List<Pair<Path, Brush>>, val bark: Path?, val barkColor: Color, val haze: Brush?)

    private fun yAtDepth(d: Float) = horizonY + (h - horizonY) * d
    private fun pathCenterX(d: Float) = w * 0.52f - w * 0.06f * sin(d * PI.toFloat() * 0.9f) * d
    private fun pathHalfWidth(d: Float) = w * (0.01f + 0.30f * d)

    private fun trunkLayer(
        seed: Int, count: Int, depth: Float, wMin: Float, wMax: Float,
        dark: Color, light: Color, avoidPath: Boolean, barkDetail: Boolean, haze: Color?,
    ): TrunkLayer {
        val r = Random(seed)
        val yBase = yAtDepth(depth)
        val list = ArrayList<Pair<Path, Brush>>()
        val bark = if (barkDetail) Path() else null
        var placed = 0
        var tries = 0
        while (placed < count && tries < count * 8) {
            tries++
            val x = r.nextFloat() * w * 1.1f - w * 0.05f
            val tw = w * (wMin + r.nextFloat() * (wMax - wMin))
            if (avoidPath && abs(x - pathCenterX(depth)) < pathHalfWidth(depth) + tw * 0.9f) continue
            val y = yBase + r.nextFloat() * h * 0.01f * (1f + depth * 4f)
            val p = SceneKit.trunk(x, y, -h * 0.02f, tw, tw * 0.72f, (r.nextFloat() - 0.5f) * tw * 0.6f)
            val brush = Brush.horizontalGradient(
                0f to dark, 0.35f to light, 0.55f to SceneKit.mix(light, dark, 0.35f), 1f to dark,
                startX = x - tw * 0.6f, endX = x + tw * 0.6f,
            )
            list += p to brush
            if (bark != null) {
                repeat((14 + tw / 6f).toInt()) {
                    val bx = x + (r.nextFloat() - 0.5f) * tw * 0.8f
                    val by = r.nextFloat() * y
                    val len = h * (0.02f + r.nextFloat() * 0.05f)
                    bark.moveTo(bx, by)
                    bark.quadraticTo(bx + (r.nextFloat() - 0.5f) * tw * 0.12f, by + len / 2f, bx + (r.nextFloat() - 0.5f) * tw * 0.08f, by + len)
                }
            }
            placed++
        }
        val hazeBrush = haze?.let {
            Brush.verticalGradient(0f to it.copy(alpha = 0f), 0.6f to it, 1f to it.copy(alpha = it.alpha * 0.6f), startY = 0f, endY = yBase + h * 0.02f)
        }
        return TrunkLayer(list, bark, dark.copy(alpha = 0.55f), hazeBrush)
    }

    private val farTrunks = trunkLayer(2, 26, 0.004f, 0.006f, 0.012f, Color(0xFF8DA486), Color(0xFFAFC2A2), false, false, null)
    private val farHaze = Brush.verticalGradient(
        0f to Color.Transparent, 0.55f to Color(0xFFE2E6BC).copy(alpha = 0.45f), 1f to Color(0xFFE2E6BC).copy(alpha = 0.25f),
        startY = horizonY - h * 0.25f, endY = horizonY + h * 0.02f,
    )
    private val midTrunks = trunkLayer(5, 16, 0.025f, 0.016f, 0.03f, Color(0xFF4D5E3E), Color(0xFF7A8C62), true, false, null)
    private val midHaze = Brush.verticalGradient(
        0f to Color.Transparent, 0.7f to Color(0xFFD8E0AE).copy(alpha = 0.22f), 1f to Color.Transparent,
        startY = horizonY - h * 0.2f, endY = horizonY + h * 0.05f,
    )
    private val nearTrunks = trunkLayer(9, 6, 0.10f, 0.045f, 0.075f, Color(0xFF241A10), Color(0xFF5A4630), true, true, null)

    /** Two huge framing trunks at the left and right edges with roots. */
    private val edgeTrunks: List<Pair<Path, Brush>> = listOf(-0.04f to 0.17f, 1.05f to 0.15f).map { (fx, fw) ->
        val x = w * fx
        val tw = w * fw
        val p = SceneKit.trunk(x, h * 1.02f, -h * 0.05f, tw, tw * 0.8f, if (fx < 0.5f) tw * 0.1f else -tw * 0.1f)
        p to Brush.horizontalGradient(
            0f to Color(0xFF130D07), 0.4f to Color(0xFF4A3622), 0.6f to Color(0xFF2E2114), 1f to Color(0xFF0E0905),
            startX = x - tw * 0.7f, endX = x + tw * 0.7f,
        )
    }
    private val edgeBark: Path = run {
        val r = Random(13)
        Path().apply {
            listOf(-0.04f to 0.17f, 1.05f to 0.15f).forEach { (fx, fw) ->
                val x = w * fx
                val tw = w * fw
                repeat(70) {
                    val bx = x + (r.nextFloat() - 0.5f) * tw * 0.9f
                    val by = r.nextFloat() * h
                    val len = h * (0.03f + r.nextFloat() * 0.07f)
                    moveTo(bx, by)
                    cubicTo(bx + tw * 0.03f, by + len * 0.3f, bx - tw * 0.03f, by + len * 0.6f, bx + (r.nextFloat() - 0.5f) * tw * 0.05f, by + len)
                }
            }
        }
    }

    // ------------------------------------------------------------------ canopy
    private class Crown(val c: Offset, val r: Float, val brush: Brush)

    private val canopy: List<Crown> = run {
        val r = Random(21)
        val list = ArrayList<Crown>()
        repeat(150) {
            val x = r.nextFloat() * w * 1.2f - w * 0.1f
            val edgeDist = abs(x - w * 0.5f) / (w * 0.5f)
            // canopy hangs lower at the sides, leaving a bright opening above the path
            val y = -h * 0.02f + r.nextFloat().pow(1.6f) * h * (0.10f + 0.16f * edgeDist)
            val rad = w * (0.05f + r.nextFloat() * 0.07f)
            val light = SceneKit.mix(Color(0xFF3F6B2E), Color(0xFF8DB55A), r.nextFloat() * 0.6f)
            list += Crown(
                Offset(x, y), rad,
                Brush.radialGradient(
                    0f to light, 0.55f to Color(0xFF2A4A1E), 1f to Color(0xFF13240D),
                    center = Offset(x - rad * 0.25f, y + rad * 0.35f), radius = rad * 1.25f,
                ),
            )
        }
        list.sortedBy { it.c.y }
    }
    private val canopyLeaves: Pair<Path, Path> = run {
        val r = Random(22)
        val dark = Path()
        val light = Path()
        canopy.forEach { cr ->
            repeat(7) {
                val a = (PI * (0.15 + r.nextDouble() * 0.7)).toFloat()
                val d = cr.r * (0.8f + r.nextFloat() * 0.35f)
                val cx = cr.c.x + kotlin.math.cos(a) * d
                val cy = cr.c.y + sin(a) * d
                SceneKit.addLeaf(if (r.nextBoolean()) dark else light, cx, cy, cr.r * 0.32f, cr.r * 0.08f, a + (r.nextFloat() - 0.5f))
            }
        }
        dark to light
    }
    /** Hanging vines from the canopy. */
    private val vines: Path = run {
        val r = Random(23)
        Path().apply {
            repeat(14) {
                val x = r.nextFloat() * w
                val edge = abs(x - w * 0.5f) / (w * 0.5f)
                val top = h * (0.05f + 0.12f * edge)
                val len = h * (0.05f + r.nextFloat() * 0.14f) * (0.5f + edge)
                moveTo(x, top)
                cubicTo(x + w * 0.01f, top + len * 0.3f, x - w * 0.012f, top + len * 0.7f, x + w * 0.004f, top + len)
            }
        }
    }

    // ------------------------------------------------------------------ light
    private class Ray(val path: Path, val brush: Brush, val phase: Float, val speed: Int)

    private val rays: List<Ray> = run {
        val r = Random(31)
        (0 until 6).map { i ->
            val topX = w * (0.30f + i * 0.09f + r.nextFloat() * 0.04f)
            val topW = w * (0.015f + r.nextFloat() * 0.03f)
            val slant = w * 0.20f
            val bottomY = yAtDepth(0.25f + r.nextFloat() * 0.5f)
            val bottomW = topW * 3.5f
            val path = Path().apply {
                moveTo(topX, h * 0.05f)
                lineTo(topX + topW, h * 0.05f)
                lineTo(topX + topW + slant + bottomW / 2f, bottomY)
                lineTo(topX + slant - bottomW / 2f, bottomY)
                close()
            }
            Ray(
                path,
                Brush.verticalGradient(
                    0f to Color(0xFFFFF7C8).copy(alpha = 0.32f), 0.6f to Color(0xFFFFF2B0).copy(alpha = 0.14f), 1f to Color.Transparent,
                    startY = h * 0.05f, endY = bottomY,
                ),
                r.nextFloat() * 6.28f, 2 + r.nextInt(3),
            )
        }
    }
    private class Mote(val x: Float, val y: Float, val r: Float, val phase: Float)

    private val motes: List<Mote> = run {
        val r = Random(37)
        List(46) { Mote(w * (0.3f + r.nextFloat() * 0.6f), h * (0.12f + r.nextFloat() * 0.45f), w * (0.002f + r.nextFloat() * 0.003f), r.nextFloat() * 6.28f) }
    }

    // ------------------------------------------------------------------ ground
    private val groundBrush = Brush.verticalGradient(
        0f to Color(0xFF9DA770),
        0.1f to Color(0xFF6E7E3E),
        0.45f to Color(0xFF475A26),
        1f to Color(0xFF223015),
        startY = horizonY, endY = h,
    )
    private val dirtPath = Path().apply {
        val n = 48
        moveTo(pathCenterX(0f) - pathHalfWidth(0f), horizonY)
        for (i in 1..n) {
            val d = i / n.toFloat() * 1.05f
            lineTo(pathCenterX(d) - pathHalfWidth(d) * (1f + 0.06f * sin(d * 40f)), yAtDepth(d))
        }
        for (i in n downTo 0) {
            val d = i / n.toFloat() * 1.05f
            lineTo(pathCenterX(d) + pathHalfWidth(d) * (1f + 0.06f * sin(d * 33f + 1f)), yAtDepth(d))
        }
        close()
    }
    private val dirtBrush = Brush.verticalGradient(
        0f to Color(0xFFCDBB8C), 0.2f to Color(0xFF9A7E55), 0.6f to Color(0xFF6E5236), 1f to Color(0xFF45301E),
        startY = horizonY, endY = h,
    )
    private val mossPatches: List<Pair<Rect, Brush>> = run {
        val r = Random(41)
        (0 until 18).map {
            val d = 0.03f + r.nextFloat() * 0.97f
            val cy = yAtDepth(d)
            val rx = w * (0.08f + r.nextFloat() * 0.2f) * (0.4f + d)
            val ry = rx * (0.12f + 0.2f * d)
            val cx = r.nextFloat() * w
            val col = if (r.nextBoolean()) Color(0xFF8FB04A).copy(alpha = 0.35f) else Color(0xFF1C2A0E).copy(alpha = 0.35f)
            val rect = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
            rect to Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), center = rect.center, radius = rx)
        }
    }

    /** Fallen leaves grouped by color. */
    private val leafLitter: List<Pair<Path, Color>> = run {
        val r = Random(43)
        val colors = listOf(Color(0xFFB5652A), Color(0xFFD69A3A), Color(0xFF8A4A22), Color(0xFF9C8F3A), Color(0xFF6B3A1E))
        val paths = colors.map { Path() }
        repeat(900) {
            val d = 0.02f + r.nextFloat().pow(1.3f) * 1.0f
            val x = r.nextFloat() * w
            val onPath = abs(x - pathCenterX(d)) < pathHalfWidth(d)
            if (!onPath && r.nextFloat() < 0.5f) return@repeat
            val y = yAtDepth(d)
            val len = w * (0.004f + 0.022f * d) * (0.6f + r.nextFloat() * 0.6f)
            SceneKit.addLeaf(paths[r.nextInt(paths.size)], x, y, len, len * 0.28f * (0.4f + 0.6f * d), r.nextFloat() * 3.14f)
        }
        paths.zip(colors)
    }

    private fun fernLayer(seed: Int, count: Int, dMin: Float, dMax: Float, scale: Float, colors: List<Color>): List<Pair<Path, Color>> {
        val r = Random(seed)
        val paths = colors.map { Path() }
        repeat(count) {
            val d = dMin + (dMax - dMin) * r.nextFloat().pow(1.2f)
            val x = r.nextFloat() * (w + 60f) - 30f
            if (abs(x - pathCenterX(d)) < pathHalfWidth(d) * 1.05f) return@repeat
            val y = yAtDepth(d)
            val hgt = h * (0.01f + 0.10f * d.pow(1.2f)) * scale * (0.6f + r.nextFloat() * 0.6f)
            val idx = (r.nextFloat() * colors.size).toInt().coerceIn(0, colors.lastIndex)
            val fronds = 3 + r.nextInt(3)
            repeat(fronds) { k ->
                val lean = hgt * ((k - (fronds - 1) / 2f) * 0.55f + (r.nextFloat() - 0.5f) * 0.3f)
                SceneKit.addFern(paths[idx], x, y, hgt * (0.75f + r.nextFloat() * 0.3f), lean, r)
            }
        }
        return paths.zip(colors)
    }

    private val fernColors = listOf(Color(0xFF2D5A1E), Color(0xFF3E7428), Color(0xFF558F34), Color(0xFF78AE48))
    private val backFerns = fernLayer(51, 120, 0.02f, 0.75f, 1f, fernColors)
    private val frontFerns = fernLayer(53, 16, 0.85f, 1.05f, 1.35f, listOf(Color(0xFF1F4214), Color(0xFF2E5C1C), Color(0xFF41762A)))

    private class Shroom(val cap: Path, val stem: Rect, val capBrush: Brush, val dots: List<Pair<Offset, Float>>)

    private val shrooms: List<Shroom> = run {
        val r = Random(57)
        (0 until 14).mapNotNull {
            val d = 0.15f + r.nextFloat() * 0.75f
            val side = if (r.nextBoolean()) 1f else -1f
            val x = pathCenterX(d) + side * pathHalfWidth(d) * (1.15f + r.nextFloat() * 0.6f)
            if (x < 0 || x > w) return@mapNotNull null
            val y = yAtDepth(d)
            val s = w * (0.004f + 0.02f * d)
            val cap = Path().apply {
                moveTo(x - s, y - s * 0.9f)
                cubicTo(x - s, y - s * 2.2f, x + s, y - s * 2.2f, x + s, y - s * 0.9f)
                quadraticTo(x, y - s * 0.6f, x - s, y - s * 0.9f)
                close()
            }
            Shroom(
                cap, Rect(x - s * 0.28f, y - s, x + s * 0.28f, y),
                Brush.radialGradient(
                    0f to Color(0xFFFF7A5A), 0.6f to Color(0xFFC62E1E), 1f to Color(0xFF6E140C),
                    center = Offset(x - s * 0.3f, y - s * 1.8f), radius = s * 1.6f,
                ),
                List(3) { Offset(x + (r.nextFloat() - 0.5f) * s * 1.2f, y - s * (1.2f + r.nextFloat() * 0.6f)) to s * 0.13f },
            )
        }
    }

    /** Hazy undergrowth along the horizon that softens the ground line. */
    private val bushes: List<Crown> = run {
        val r = Random(61)
        val list = ArrayList<Crown>()
        repeat(60) {
            val x = r.nextFloat() * w
            if (abs(x - pathCenterX(0.01f)) < w * 0.05f) return@repeat
            val rad = w * (0.018f + r.nextFloat() * 0.035f)
            val y = horizonY + h * 0.006f + r.nextFloat() * h * 0.012f
            val light = SceneKit.mix(Color(0xFFB6C48C), Color(0xFF7E9658), r.nextFloat())
            list += Crown(
                Offset(x, y), rad,
                Brush.radialGradient(
                    0f to light, 0.8f to SceneKit.mix(light, Color(0xFF4E6436), 0.5f), 1f to SceneKit.mix(light, Color(0xFF4E6436), 0.6f),
                    center = Offset(x - rad * 0.3f, y - rad * 0.4f), radius = rad * 1.2f,
                ),
            )
        }
        list
    }
    private val horizonHaze = Brush.verticalGradient(
        0f to Color(0xFFE6E8BC).copy(alpha = 0.0f), 0.45f to Color(0xFFE6E8BC).copy(alpha = 0.55f), 1f to Color(0xFFE6E8BC).copy(alpha = 0f),
        startY = horizonY - h * 0.05f, endY = horizonY + h * 0.05f,
    )

    private val vignette = SceneKit.vignette(w, h, 0.62f, Color(0xFF050A03))
    private val bottomShade = Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xFF0A1205).copy(alpha = 0.55f), startY = h * 0.8f, endY = h)

    // ------------------------------------------------------------------ drawing

    override fun drawBackground(scope: DrawScope, drift: Float, time: Float) = with(scope) {
        drawRect(skyBrush, size = Size(w, horizonY + 6f))
        drawRect(glowBrush, size = Size(w, horizonY + h * 0.1f))

        farTrunks.trunks.forEach { (p, b) -> drawPath(p, b) }
        drawRect(farHaze, topLeft = Offset(0f, horizonY - h * 0.25f), size = Size(w, h * 0.27f))
        midTrunks.trunks.forEach { (p, b) -> drawPath(p, b) }

        drawRect(groundBrush, topLeft = Offset(0f, horizonY), size = Size(w, h - horizonY))
        bushes.forEach { b -> drawCircle(b.brush, b.r, b.c) }
        drawRect(horizonHaze, topLeft = Offset(0f, horizonY - h * 0.05f), size = Size(w, h * 0.1f))
        drawRect(midHaze, topLeft = Offset(0f, horizonY - h * 0.2f), size = Size(w, h * 0.25f))
        mossPatches.forEach { (rect, b) -> drawOval(b, rect.topLeft, rect.size) }
        drawPath(dirtPath, dirtBrush)
        drawPath(
            dirtPath,
            Brush.horizontalGradient(
                0f to Color(0xFF1E140A).copy(alpha = 0.45f), 0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color(0xFF1E140A).copy(alpha = 0.45f),
                startX = w * 0.1f, endX = w * 0.95f,
            ),
        )

        nearTrunks.trunks.forEach { (p, b) ->
            drawPath(p, b)
        }
        nearTrunks.bark?.let { drawPath(it, Color(0xFF120C06).copy(alpha = 0.5f), style = Stroke(w * 0.0018f, cap = StrokeCap.Round)) }

        leafLitter.forEach { (p, c) -> drawPath(p, c) }
        backFerns.forEach { (p, c) -> drawPath(p, c) }
        shrooms.forEach { s ->
            drawRect(Color(0xFFEDE4CC), s.stem.topLeft, s.stem.size)
            drawPath(s.cap, s.capBrush)
            s.dots.forEach { (c, r) -> drawCircle(Color(0xFFFFF4E0), r, c) }
        }

        // canopy
        canopy.forEach { cr ->
            drawCircle(Color.Black.copy(alpha = 0.18f), cr.r * 1.04f, cr.c + Offset(0f, cr.r * 0.12f))
            drawCircle(cr.brush, cr.r, cr.c)
        }
        drawPath(canopyLeaves.first, Color(0xFF16290E))
        drawPath(canopyLeaves.second, Color(0xFF4E7A30))
        drawPath(vines, Color(0xFF213A14), style = Stroke(w * 0.003f, cap = StrokeCap.Round))
        edgeTrunks.forEach { (p, b) -> drawPath(p, b) }
        drawPath(edgeBark, Color(0xFF0A0603).copy(alpha = 0.6f), style = Stroke(w * 0.003f, cap = StrokeCap.Round))

        // god rays + dust
        rays.forEach { ray ->
            val a = 0.65f + 0.35f * SceneKit.wave(time, ray.speed, ray.phase)
            drawPath(ray.path, ray.brush, alpha = a, blendMode = BlendMode.Screen)
        }
        motes.forEach { m ->
            val dx = SceneKit.wave(time, 3, m.phase) * w * 0.012f
            val dy = SceneKit.wave(time, 2, m.phase * 1.3f) * h * 0.01f
            val a = 0.35f + 0.35f * SceneKit.wave(time, 5, m.phase)
            drawCircle(Color(0xFFFFF7D0).copy(alpha = a.coerceIn(0f, 1f)), m.r, Offset(m.x + dx, m.y + dy))
        }
        drawRect(Color(0xFFFFF0B8).copy(alpha = 0.05f), size = size)
    }

    override fun drawForeground(scope: DrawScope, time: Float) = with(scope) {
        frontFerns.forEach { (p, c) -> drawPath(p, c) }
        drawRect(bottomShade, topLeft = Offset(0f, h * 0.8f), size = Size(w, h * 0.2f))
        drawRect(vignette, size = size)
    }
}
