package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zone 5 "Höhle": a tunnel of rough stone receding towards a glowing crystal grotto. Concentric
 * rock rings give depth, stalactites hang from the ceiling, stalagmites and crystal clusters
 * (pulsing glow) line the floor, two wall torches flicker and light the walls warmly, a puddle
 * reflects the glow.
 */
class CaveScene(size: Size) : ZoneScene {
    private val w = size.width
    private val h = size.height
    override val horizonY = h * 0.44f
    override val monsterGroundY = h * 0.69f

    private val vp = Offset(w * 0.5f, horizonY - h * 0.04f)

    private fun yAtDepth(d: Float) = horizonY + (h - horizonY) * d

    // ------------------------------------------------------------------ grotto glow
    private val baseBrush = Brush.radialGradient(
        0f to Color(0xFF6FE0E8), 0.05f to Color(0xFF3A9AB8), 0.18f to Color(0xFF2A3E66), 0.45f to Color(0xFF16142A), 1f to Color(0xFF07060C),
        center = vp, radius = h * 0.55f,
    )

    // ------------------------------------------------------------------ rock rings
    private class Ring(val path: Path, val brush: Brush, val edge: Path, val cracks: Path)

    /** Opening of ring [t] (0 = far, 1 = near): irregular arch around the vanishing point. */
    private fun arch(t: Float, r: Random): Path {
        val rx = w * (0.08f + 0.62f * t.pow(1.4f))
        val ryTop = h * (0.05f + 0.42f * t.pow(1.4f))
        val floorY = yAtDepth(0.02f + 0.9f * t.pow(1.6f))
        val cx = vp.x + (r.nextFloat() - 0.5f) * w * 0.03f * t
        return Path().apply {
            val n = 30
            moveTo(cx - rx, floorY)
            for (i in 0..n) {
                val a = PI.toFloat() + i / n.toFloat() * PI.toFloat()
                val k = 0.9f + r.nextFloat() * 0.18f
                val x = cx + cos(a) * rx * k
                val y = vp.y + (floorY - vp.y) * 0.35f + sin(a) * ryTop * k
                lineTo(x, y.coerceAtMost(floorY))
            }
            lineTo(cx + rx, floorY)
            close()
        }
    }

    private val rings: List<Ring> = run {
        val r = Random(5)
        val n = 6
        (0 until n).map { i ->
            val t = (i + 1f) / (n + 0.5f)
            val opening = arch(t, r)
            val ringPath = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(-10f, -10f, w + 10f, h + 10f))
                addPath(opening)
            }
            val lit = 1f - t
            val inner = SceneKit.mix(Color(0xFF1A1622), Color(0xFF4A5478), lit * 0.8f)
            val outer = SceneKit.mix(Color(0xFF0C0A10), Color(0xFF242A40), lit * 0.6f)
            val cracks = Path().apply {
                repeat(30) {
                    val a = r.nextFloat() * PI.toFloat() + PI.toFloat()
                    val rad = w * (0.1f + 0.7f * t) * (1.05f + r.nextFloat() * 0.3f)
                    val x = vp.x + cos(a) * rad
                    val y = vp.y + sin(a) * rad * 0.7f
                    moveTo(x, y)
                    lineTo(x + (r.nextFloat() - 0.5f) * w * 0.05f, y + r.nextFloat() * h * 0.03f)
                }
            }
            Ring(
                ringPath,
                Brush.radialGradient(0f to inner, 0.5f to inner, 1f to outer, center = vp, radius = w * (0.15f + 0.9f * t)),
                opening, cracks,
            )
        }
    }

    // ------------------------------------------------------------------ stalactites / stalagmites
    private class Spike(val path: Path, val brush: Brush)

    private fun spike(x: Float, y: Float, len: Float, width: Float, down: Boolean, lit: Float): Spike {
        val dir = if (down) 1f else -1f
        val p = Path().apply {
            moveTo(x - width / 2f, y)
            cubicTo(x - width * 0.35f, y + dir * len * 0.4f, x - width * 0.08f, y + dir * len * 0.8f, x, y + dir * len)
            cubicTo(x + width * 0.1f, y + dir * len * 0.75f, x + width * 0.3f, y + dir * len * 0.4f, x + width / 2f, y)
            close()
        }
        val light = SceneKit.mix(Color(0xFF2A2634), Color(0xFF8A8EAA), lit)
        return Spike(p, Brush.horizontalGradient(listOf(light, SceneKit.mix(light, Color(0xFF0A080E), 0.7f)), startX = x - width / 2f, endX = x + width / 2f))
    }

    private val stalactites: List<Spike> = run {
        val r = Random(11)
        val list = ArrayList<Spike>()
        repeat(34) {
            val t = r.nextFloat().pow(0.7f)
            val x = r.nextFloat() * w
            val y = (vp.y - h * (0.05f + 0.45f * t)).coerceAtLeast(-h * 0.02f) + (abs(x - vp.x) / w) * h * 0.1f * t
            val len = h * (0.02f + 0.12f * t) * (0.5f + r.nextFloat())
            list += spike(x, y - h * 0.01f, len, len * (0.18f + r.nextFloat() * 0.12f), true, (1f - t) * 0.6f)
        }
        list
    }
    private val stalagmites: List<Spike> = run {
        val r = Random(13)
        val list = ArrayList<Spike>()
        repeat(16) {
            val d = 0.02f + r.nextFloat().pow(1.4f) * 0.75f
            val side = if (r.nextBoolean()) 1f else -1f
            val x = vp.x + side * w * (0.12f + 0.5f * d + r.nextFloat() * 0.25f)
            val len = h * (0.015f + 0.14f * d) * (0.6f + r.nextFloat() * 0.6f)
            list += spike(x, yAtDepth(d), len, len * (0.35f + r.nextFloat() * 0.15f), false, (1f - d) * 0.5f)
        }
        list.sortedBy { it.path.getBounds().bottom }
    }

    // ------------------------------------------------------------------ floor
    private val floorBrush = Brush.verticalGradient(
        0f to Color(0xFF4A5A80), 0.12f to Color(0xFF2C2E40), 0.5f to Color(0xFF1A1820), 1f to Color(0xFF0C0A0E),
        startY = horizonY, endY = h,
    )
    /** Floor polygon that follows the bottoms of the ring openings, so no horizontal seams show. */
    private val floor = Path().apply {
        val n = 24
        fun rx(t: Float) = w * (0.08f + 0.62f * t.pow(1.4f)) * 1.12f
        fun fy(t: Float) = yAtDepth(0.02f + 0.9f * t.pow(1.6f))
        moveTo(vp.x - rx(0f), horizonY)
        for (i in 0..n) { val t = i / n.toFloat() * 1.15f; lineTo(vp.x - rx(t), fy(t)) }
        lineTo(-10f, h + 10f); lineTo(w + 10f, h + 10f)
        for (i in n downTo 0) { val t = i / n.toFloat() * 1.15f; lineTo(vp.x + rx(t), fy(t)) }
        close()
    }
    private val trail = Path().apply {
        val n = 40
        fun cx(d: Float) = vp.x + w * 0.03f * sin(d * 4f) * d
        fun hw(d: Float) = w * (0.01f + 0.26f * d)
        moveTo(cx(0f) - hw(0f), horizonY)
        for (i in 1..n) { val d = i / n.toFloat() * 1.05f; lineTo(cx(d) - hw(d), yAtDepth(d)) }
        for (i in n downTo 0) { val d = i / n.toFloat() * 1.05f; lineTo(cx(d) + hw(d), yAtDepth(d)) }
        close()
    }
    private val trailBrush = Brush.verticalGradient(
        0f to Color(0xFF5A6684).copy(alpha = 0.6f), 0.3f to Color(0xFF3A3A48).copy(alpha = 0.6f), 1f to Color(0xFF2A2630).copy(alpha = 0.6f),
        startY = horizonY, endY = h,
    )
    private val pebbles: List<Pair<Path, Color>> = run {
        val r = Random(17)
        val colors = listOf(Color(0xFF2E2C38), Color(0xFF454456), Color(0xFF5E6078), Color(0xFF16141C))
        val paths = colors.map { Path() }
        repeat(420) {
            val d = 0.02f + r.nextFloat().pow(0.9f)
            val x = r.nextFloat() * w
            val y = yAtDepth(d)
            val rx = w * (0.001f + 0.008f * d) * (0.5f + r.nextFloat())
            paths[r.nextInt(paths.size)].addOval(Rect(x - rx, y - rx * 0.5f, x + rx, y + rx * 0.5f))
        }
        paths.zip(colors)
    }
    private val puddle = Rect(w * 0.18f, yAtDepth(0.42f), w * 0.46f, yAtDepth(0.48f))

    // ------------------------------------------------------------------ crystals
    private class Crystal(val prisms: List<Pair<Path, Path>>, val base: Color, val light: Color, val glowCenter: Offset, val glowR: Float, val phase: Float)

    private fun crystal(c: Offset, size: Float, base: Color, light: Color, r: Random, hanging: Boolean = false): Crystal {
        val list = ArrayList<Pair<Path, Path>>()
        val n = 3 + r.nextInt(3)
        repeat(n) {
            val ang = (-90f + (r.nextFloat() - 0.5f) * 80f) * PI.toFloat() / 180f * (if (hanging) -1f else 1f)
            val len = size * (0.5f + r.nextFloat() * 0.6f)
            val wd = len * (0.18f + r.nextFloat() * 0.08f)
            val dx = cos(ang)
            val dy = sin(ang)
            val nx = -dy * wd / 2f
            val ny = dx * wd / 2f
            val bx = c.x + (r.nextFloat() - 0.5f) * size * 0.3f
            val by = c.y
            val tx = bx + dx * len
            val ty = by + dy * len
            val sx = bx + dx * len * 0.78f
            val sy = by + dy * len * 0.78f
            val full = Path().apply {
                moveTo(bx + nx, by + ny); lineTo(sx + nx, sy + ny); lineTo(tx, ty); lineTo(sx - nx, sy - ny); lineTo(bx - nx, by - ny); close()
            }
            val facet = Path().apply {
                moveTo(bx, by); lineTo(sx, sy); lineTo(tx, ty); lineTo(sx + nx, sy + ny); lineTo(bx + nx, by + ny); close()
            }
            list += full to facet
        }
        return Crystal(list, base, light, c + Offset(0f, if (hanging) size * 0.4f else -size * 0.4f), size * 1.6f, r.nextFloat() * 6.28f)
    }

    private val crystals: List<Crystal> = run {
        val r = Random(19)
        val cyan = Color(0xFF2AA8C8) to Color(0xFFB8F6FF)
        val violet = Color(0xFF8A4AD0) to Color(0xFFE8C8FF)
        val list = ArrayList<Crystal>()
        // deep grotto
        listOf(-0.07f, 0.06f, 0.0f).forEachIndexed { i, fx ->
            val (b, l) = if (i % 2 == 0) cyan else violet
            list += crystal(Offset(vp.x + w * fx, yAtDepth(0.01f + i * 0.004f)), h * 0.035f, b, l, r)
        }
        // floor sides
        repeat(7) {
            val d = 0.06f + r.nextFloat().pow(1.3f) * 0.6f
            val side = if (it % 2 == 0) -1f else 1f
            val x = vp.x + side * w * (0.2f + 0.45f * d + r.nextFloat() * 0.12f)
            val (b, l) = if (r.nextBoolean()) cyan else violet
            list += crystal(Offset(x, yAtDepth(d)), h * (0.02f + 0.09f * d), b, l, r)
        }
        // hanging from the ceiling
        repeat(3) {
            val x = w * (0.15f + r.nextFloat() * 0.7f)
            val (b, l) = if (r.nextBoolean()) cyan else violet
            list += crystal(Offset(x, h * (0.02f + r.nextFloat() * 0.08f)), h * 0.04f, b, l, r, hanging = true)
        }
        list
    }

    // ------------------------------------------------------------------ torches
    private class Torch(val pos: Offset, val scale: Float, val phase: Float)

    private val torches = listOf(Torch(Offset(w * 0.13f, h * 0.38f), w * 0.05f, 0f), Torch(Offset(w * 0.88f, h * 0.36f), w * 0.045f, 2.1f))
    private val handle = Path().apply {
        moveTo(-0.12f, 0f); lineTo(0.12f, 0f); lineTo(0.07f, 1.4f); lineTo(-0.07f, 1.4f); close()
    }
    private val handleBrush = Brush.horizontalGradient(listOf(Color(0xFF2A1A0E), Color(0xFF7A5434), Color(0xFF2A1A0E)), startX = -0.12f, endX = 0.12f)
    private val bracket = Path().apply {
        moveTo(-0.22f, 0.05f); lineTo(0.22f, 0.05f); lineTo(0.18f, 0.32f); lineTo(-0.18f, 0.32f); close()
        addRect(Rect(-0.04f, 0.9f, 0.04f, 1.25f))
    }
    private val flameOuter = Path().apply {
        moveTo(0f, -1.25f); cubicTo(0.18f, -0.8f, 0.34f, -0.45f, 0.28f, -0.12f); cubicTo(0.22f, 0.1f, -0.22f, 0.1f, -0.28f, -0.12f)
        cubicTo(-0.34f, -0.45f, -0.12f, -0.75f, 0f, -1.25f); close()
    }

    private val vignette = SceneKit.vignette(w, h, 0.8f, Color(0xFF020104), 0.44f)

    // ------------------------------------------------------------------ drawing

    private fun DrawScope.drawCrystal(c: Crystal, time: Float) {
        val pulse = 0.6f + 0.4f * SceneKit.wave(time, 6, c.phase)
        drawCircle(
            Brush.radialGradient(listOf(c.light.copy(alpha = 0.35f * pulse), c.base.copy(alpha = 0.12f * pulse), Color.Transparent), c.glowCenter, c.glowR),
            c.glowR, c.glowCenter, blendMode = BlendMode.Screen,
        )
        c.prisms.forEach { (full, facet) ->
            val b = full.getBounds()
            drawPath(full, Brush.linearGradient(listOf(c.light, c.base, SceneKit.mix(c.base, Color.Black, 0.5f)), start = b.topLeft, end = b.bottomRight))
            drawPath(facet, Color.White.copy(alpha = 0.22f))
            drawPath(full, c.light.copy(alpha = 0.7f), style = Stroke(w * 0.0012f))
        }
    }

    override fun drawBackground(scope: DrawScope, drift: Float, time: Float) = with(scope) {
        drawRect(baseBrush, size = size)
        crystals.take(3).forEach { drawCrystal(it, time) }

        rings.forEach { rg ->
            drawPath(rg.path, rg.brush)
            drawPath(rg.edge, Color(0xFF8A9CC8).copy(alpha = 0.12f), style = Stroke(w * 0.004f))
            drawPath(rg.cracks, Color(0xFF050408).copy(alpha = 0.5f), style = Stroke(w * 0.0018f, cap = StrokeCap.Round))
        }
        drawPath(floor, floorBrush)
        drawPath(
            floor,
            Brush.horizontalGradient(
                0f to Color(0xFF050408).copy(alpha = 0.6f), 0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color(0xFF050408).copy(alpha = 0.6f),
            ),
        )
        drawPath(trail, trailBrush)
        drawOval(
            Brush.radialGradient(listOf(Color(0xFF7FE6F0).copy(alpha = 0.45f), Color(0xFF2A3E66).copy(alpha = 0.3f), Color.Transparent), puddle.center, puddle.width / 2f),
            puddle.topLeft, puddle.size,
        )
        pebbles.forEach { (p, c) -> drawPath(p, c) }
        stalagmites.forEach { drawPath(it.path, it.brush) }
        stalactites.forEach { drawPath(it.path, it.brush) }
        crystals.drop(3).forEach { drawCrystal(it, time) }

        torches.forEach { t ->
            val flick = 0.82f + 0.1f * SceneKit.wave(time, 47, t.phase) + 0.08f * SceneKit.wave(time, 113, t.phase * 2f)
            val glowR = w * 0.42f * flick
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFB050).copy(alpha = 0.42f), Color(0xFFFF7A20).copy(alpha = 0.14f), Color.Transparent),
                    t.pos, glowR,
                ),
                glowR, t.pos, blendMode = BlendMode.Screen,
            )
            withTransform({ translate(t.pos.x, t.pos.y); scale(t.scale, t.scale, Offset.Zero) }) {
                drawPath(handle, handleBrush)
                drawPath(bracket, Brush.verticalGradient(listOf(Color(0xFF5A5A62), Color(0xFF1E1E24)), startY = 0f, endY = 1.3f))
                withTransform({ scale(1f + 0.06f * SceneKit.wave(time, 71, t.phase), flick * 1.1f, Offset.Zero) }) {
                    drawPath(flameOuter, Brush.verticalGradient(listOf(Color(0xFFFF4A10).copy(alpha = 0.0f), Color(0xFFFF6A18), Color(0xFFFFA030)), startY = -1.25f, endY = 0.1f))
                    withTransform({ scale(0.62f, 0.68f, Offset(0f, 0.05f)) }) {
                        drawPath(flameOuter, Brush.verticalGradient(listOf(Color(0xFFFFD060), Color(0xFFFFF0B0)), startY = -1.25f, endY = 0.1f))
                    }
                    withTransform({ scale(0.3f, 0.35f, Offset(0f, 0.05f)) }) { drawPath(flameOuter, Color.White) }
                }
            }
        }
    }

    override fun drawForeground(scope: DrawScope, time: Float) = with(scope) {
        drawRect(vignette, size = size)
    }
}
