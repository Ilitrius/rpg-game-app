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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zone 3 "Sumpf": a rotten wooden boardwalk leads over murky water. Overcast green-grey sky,
 * a misty far treeline, skeletal dead trees with hanging moss (mirrored in the water), reeds and
 * cattails on tussocks, lily pads, drifting mist banks, expanding ripples and pulsing will-o'-wisp
 * lights.
 */
class SwampScene(size: Size) : ZoneScene {
    private val w = size.width
    private val h = size.height
    override val horizonY = h * 0.44f
    override val monsterGroundY = h * 0.69f

    private fun yAtDepth(d: Float) = horizonY + (h - horizonY) * d
    private fun walkCenter(d: Float) = w * 0.5f + w * 0.04f * sin(d * 3f) * d
    private fun walkHalf(d: Float) = w * (0.006f + 0.27f * d)

    // ------------------------------------------------------------------ sky + far
    private val skyBrush = Brush.verticalGradient(
        0f to Color(0xFF3E4A44), 0.4f to Color(0xFF6E7C6A), 0.8f to Color(0xFFA8B094), 1f to Color(0xFFC4C6A6),
        startY = 0f, endY = horizonY,
    )
    private val sunGlow = Brush.radialGradient(
        0f to Color(0xFFF2EFC8).copy(alpha = 0.7f), 0.3f to Color(0xFFE2E2B0).copy(alpha = 0.25f), 1f to Color.Transparent,
        center = Offset(w * 0.3f, h * 0.16f), radius = w * 0.5f,
    )

    private fun treeline(base: Float, amp: Float, seed: Int, color: Color): Path {
        val r = Random(seed)
        return Path().apply {
            moveTo(-5f, horizonY + 5f)
            var x = -5f
            while (x < w + 10f) {
                val bump = amp * (0.5f + r.nextFloat() * 0.5f)
                val bw = w * (0.02f + r.nextFloat() * 0.04f)
                val b0 = base - amp * 0.6f * (0.5f + 0.5f * sin(x / w * 9f + seed))
                lineTo(x, b0)
                quadraticTo(x + bw * (0.3f + r.nextFloat() * 0.4f), b0 - bump * (1.2f + r.nextFloat() * 1.6f), x + bw, b0 - r.nextFloat() * amp * 0.3f)
                x += bw
            }
            lineTo(w + 10f, horizonY + 5f)
            close()
        }
    }
    private val farLine = treeline(horizonY - h * 0.01f, h * 0.025f, 3, Color(0xFF7D8A76))
    private val midLine = treeline(horizonY + h * 0.002f, h * 0.018f, 7, Color(0xFF55634F))

    // ------------------------------------------------------------------ dead trees
    private class DeadTree(val branches: List<Triple<Path, Float, Color>>, val moss: Path, val mossColor: Color)

    private fun deadTree(x: Float, baseY: Float, height: Float, seed: Int, color: Color, mossColor: Color): DeadTree {
        val r = Random(seed)
        val list = ArrayList<Triple<Path, Float, Color>>()
        val moss = Path()
        fun branch(sx: Float, sy: Float, angle: Float, len: Float, width: Float, depth: Int) {
            val ex = sx + cos(angle) * len
            val ey = sy + sin(angle) * len
            val p = Path().apply {
                moveTo(sx, sy)
                val bend = (r.nextFloat() - 0.5f) * len * 0.3f
                quadraticTo((sx + ex) / 2f + bend, (sy + ey) / 2f, ex, ey)
            }
            list += Triple(p, width, color)
            if (depth > 0) {
                val n = if (depth > 2) 2 else 1 + r.nextInt(2)
                repeat(n) {
                    val a = angle + (r.nextFloat() - 0.5f) * 1.6f
                    branch(ex, ey, a, len * (0.55f + r.nextFloat() * 0.2f), width * 0.62f, depth - 1)
                }
                if (depth <= 2 && r.nextFloat() < 0.7f) {
                    // hanging moss strands
                    repeat(3) {
                        val t = r.nextFloat()
                        val mx = sx + (ex - sx) * t
                        val my = sy + (ey - sy) * t
                        val ml = height * (0.04f + r.nextFloat() * 0.1f)
                        moss.moveTo(mx, my)
                        moss.cubicTo(mx + ml * 0.1f, my + ml * 0.35f, mx - ml * 0.1f, my + ml * 0.7f, mx + ml * 0.05f, my + ml)
                    }
                }
            }
        }
        // trunk as a thick lower branch
        branch(x, baseY, -PI.toFloat() / 2f + (r.nextFloat() - 0.5f) * 0.2f, height * 0.45f, height * 0.06f, 4)
        return DeadTree(list, moss, mossColor)
    }

    private val trees: List<Pair<DeadTree, Float>> = listOf(
        // x fraction, depth, height fraction of h
        Triple(0.12f, 0.03f, 0.30f), Triple(0.80f, 0.02f, 0.26f), Triple(0.33f, 0.008f, 0.16f),
        Triple(0.64f, 0.006f, 0.13f), Triple(0.95f, 0.12f, 0.50f), Triple(-0.02f, 0.16f, 0.55f),
    ).mapIndexed { i, (fx, d, fh) ->
        val near = d > 0.1f
        val haze = (1f - d * 6f).coerceIn(0f, 1f)
        val col = SceneKit.mix(Color(0xFF2A2620), Color(0xFF8C9682), haze * 0.75f)
        val moss = SceneKit.mix(Color(0xFF59684A), Color(0xFFA2AC92), haze * 0.6f)
        deadTree(w * fx, yAtDepth(d), h * fh, 100 + i, col, moss) to (if (near) 1f else 0.6f)
    }

    // ------------------------------------------------------------------ water
    private val waterBrush = Brush.verticalGradient(
        0f to Color(0xFFA9AF92), 0.06f to Color(0xFF6E7A5E), 0.35f to Color(0xFF3F4C34), 1f to Color(0xFF1C2416),
        startY = horizonY, endY = h,
    )
    private val waterSheen = Brush.horizontalGradient(
        0f to Color.Transparent, 0.3f to Color(0xFFD8DCC0).copy(alpha = 0.14f), 0.5f to Color(0xFFD8DCC0).copy(alpha = 0.2f), 0.7f to Color.Transparent,
        startX = 0f, endX = w,
    )
    private class Pad(val path: Path, val brush: Brush, val flower: Offset?, val fr: Float)

    private val pads: List<Pad> = run {
        val r = Random(17)
        (0 until 60).mapNotNull {
            val d = 0.04f + r.nextFloat().pow(1.1f) * 0.92f
            val x = r.nextFloat() * w
            if (abs(x - walkCenter(d)) < walkHalf(d) * 1.2f) return@mapNotNull null
            val y = yAtDepth(d)
            val rx = w * (0.006f + 0.045f * d)
            val ry = rx * (0.25f + 0.15f * d)
            val notch = r.nextFloat() * 6.28f
            val path = Path().apply {
                val n = 24
                moveTo(x, y)
                for (i in 0..n) {
                    val a = notch + 0.35f + i / n.toFloat() * (2f * PI.toFloat() - 0.7f)
                    lineTo(x + cos(a) * rx, y + sin(a) * ry)
                }
                close()
            }
            Pad(
                path,
                Brush.radialGradient(
                    0f to Color(0xFF8FB25A), 0.7f to Color(0xFF4E7A30), 1f to Color(0xFF2E4E1C),
                    center = Offset(x - rx * 0.3f, y - ry * 0.3f), radius = rx * 1.2f,
                ),
                if (r.nextFloat() < 0.25f) Offset(x + rx * 0.2f, y - ry * 0.4f) else null, rx * 0.35f,
            )
        }
    }

    // ------------------------------------------------------------------ boardwalk
    private class Plank(val path: Path, val brush: Brush, val y: Float)

    private val planks: List<Plank> = run {
        val r = Random(23)
        val list = ArrayList<Plank>()
        var d = 0.004f
        while (d < 1.08f) {
            val step = 0.012f + d * 0.055f
            val d2 = d + step * 0.82f
            if (r.nextFloat() < 0.06f && d > 0.1f && d < 0.9f) { d += step; continue } // missing plank
            val tilt = (r.nextFloat() - 0.5f) * walkHalf(d) * 0.08f
            val shift = (r.nextFloat() - 0.5f) * walkHalf(d) * 0.1f
            val l1 = walkCenter(d) - walkHalf(d) * 1.05f + shift
            val r1 = walkCenter(d) + walkHalf(d) * 1.05f + shift
            val l2 = walkCenter(d2) - walkHalf(d2) * 1.05f + shift
            val r2 = walkCenter(d2) + walkHalf(d2) * 1.05f + shift
            val y1 = yAtDepth(d)
            val y2 = yAtDepth(d2)
            val p = Path().apply {
                moveTo(l1, y1 - tilt); lineTo(r1, y1 + tilt); lineTo(r2, y2 + tilt); lineTo(l2, y2 - tilt); close()
            }
            val tone = r.nextFloat()
            list += Plank(
                p,
                Brush.verticalGradient(
                    0f to SceneKit.mix(Color(0xFF9A8460), Color(0xFF7A6A4E), tone),
                    1f to SceneKit.mix(Color(0xFF5E4C34), Color(0xFF3E3424), tone),
                    startY = y1, endY = y2,
                ),
                y2,
            )
            d += step
        }
        list
    }
    private val walkUnder = Path().apply {
        val n = 40
        moveTo(walkCenter(0f) - walkHalf(0f), horizonY)
        for (i in 1..n) { val d = i / n.toFloat() * 1.08f; lineTo(walkCenter(d) - walkHalf(d) * 1.0f, yAtDepth(d) + h * 0.004f * d) }
        for (i in n downTo 0) { val d = i / n.toFloat() * 1.08f; lineTo(walkCenter(d) + walkHalf(d) * 1.0f, yAtDepth(d) + h * 0.004f * d) }
        close()
    }
    private val grain: Path = run {
        val r = Random(29)
        Path().apply {
            planks.forEach { pl ->
                // a few grain lines across each plank
                repeat(2) {
                    val b = pl.path.getBounds()
                    val y = b.top + b.height * (0.3f + r.nextFloat() * 0.4f)
                    val x0 = b.left + b.width * r.nextFloat() * 0.3f
                    moveTo(x0, y)
                    lineTo(x0 + b.width * (0.3f + r.nextFloat() * 0.4f), y + (r.nextFloat() - 0.5f) * b.height * 0.2f)
                }
            }
        }
    }
    private class Post(val rect: Rect, val brush: Brush)

    private val posts: List<Post> = run {
        val list = ArrayList<Post>()
        var d = 0.03f
        while (d < 1.05f) {
            listOf(-1f, 1f).forEach { side ->
                val x = walkCenter(d) + side * walkHalf(d) * 1.08f
                val y = yAtDepth(d)
                val pw = w * (0.002f + 0.022f * d)
                val ph = h * (0.004f + 0.06f * d)
                list += Post(
                    Rect(x - pw / 2f, y - ph, x + pw / 2f, y + ph * 0.25f),
                    Brush.horizontalGradient(listOf(Color(0xFF2A2016), Color(0xFF6A5638), Color(0xFF2A2016)), startX = x - pw / 2f, endX = x + pw / 2f),
                )
            }
            d += 0.07f + d * 0.25f
        }
        list.sortedBy { it.rect.bottom }
    }

    // ------------------------------------------------------------------ reeds
    private fun reeds(seed: Int, count: Int, dMin: Float, dMax: Float, scale: Float): Pair<List<Pair<Path, Color>>, Path> {
        val r = Random(seed)
        val colors = listOf(Color(0xFF3E4A24), Color(0xFF5A6A30), Color(0xFF7A8A44), Color(0xFF9AA25A))
        val paths = colors.map { Path() }
        val heads = Path()
        repeat(count) {
            val d = dMin + (dMax - dMin) * r.nextFloat().pow(1.2f)
            val cx = r.nextFloat() * (w + 40f) - 20f
            if (abs(cx - walkCenter(d)) < walkHalf(d) * 1.25f) return@repeat
            val y = yAtDepth(d)
            val hgt = h * (0.012f + 0.16f * d.pow(1.1f)) * scale
            val blades = 4 + r.nextInt(5)
            repeat(blades) {
                val x = cx + (r.nextFloat() - 0.5f) * hgt * 0.25f
                val bh = hgt * (0.5f + r.nextFloat() * 0.6f)
                val lean = bh * (r.nextFloat() - 0.5f) * 0.4f
                val bw = bh * 0.025f + 0.8f
                val p = paths[r.nextInt(paths.size)]
                p.moveTo(x - bw, y)
                p.quadraticTo(x + lean * 0.3f, y - bh * 0.6f, x + lean, y - bh)
                p.quadraticTo(x + lean * 0.3f + bw, y - bh * 0.6f, x + bw, y)
                p.close()
                if (r.nextFloat() < 0.08f && bh > h * 0.02f) {
                    val hx = x + lean * 0.85f
                    val hy = y - bh * 0.85f
                    heads.addRoundRect(androidx.compose.ui.geometry.RoundRect(hx - bw * 1.6f, hy - bh * 0.11f, hx + bw * 1.6f, hy + bh * 0.04f, bw * 1.6f, bw * 1.6f))
                }
            }
        }
        return paths.zip(colors) to heads
    }
    private val backReeds = reeds(41, 95, 0.0f, 0.8f, 1f)
    private val frontReeds = reeds(43, 26, 0.86f, 1.05f, 1.2f)

    private class Tussock(val rect: Rect, val brush: Brush)

    private val tussocks: List<Tussock> = run {
        val r = Random(47)
        (0 until 16).mapNotNull {
            val d = 0.03f + r.nextFloat() * 0.85f
            val x = r.nextFloat() * w
            if (abs(x - walkCenter(d)) < walkHalf(d) * 1.4f) return@mapNotNull null
            val y = yAtDepth(d)
            val rx = w * (0.01f + 0.07f * d)
            val ry = rx * 0.3f
            Tussock(
                Rect(x - rx, y - ry, x + rx, y + ry * 0.5f),
                Brush.radialGradient(listOf(Color(0xFF5E6A36), Color(0xFF34401E)), center = Offset(x, y - ry), radius = rx),
            )
        }
    }

    // ------------------------------------------------------------------ mist + lights
    private class Mist(val y: Float, val width: Float, val height: Float, val speed: Float, val phase: Float, val alpha: Float)

    private val mists: List<Mist> = run {
        val r = Random(53)
        List(9) {
            Mist(
                horizonY + h * (-0.04f + r.nextFloat() * 0.2f), w * (0.5f + r.nextFloat() * 0.7f), h * (0.02f + r.nextFloat() * 0.04f),
                0.4f + r.nextFloat() * 0.8f, r.nextFloat(), 0.12f + r.nextFloat() * 0.16f,
            )
        }
    }
    private class Light(val pos: Offset, val r: Float, val phase: Float, val color: Color)

    private val lights: List<Light> = run {
        val r = Random(59)
        List(14) {
            val d = 0.02f + r.nextFloat() * 0.5f
            Light(
                Offset(r.nextFloat() * w, yAtDepth(d) - h * (0.01f + r.nextFloat() * 0.08f)),
                w * (0.004f + 0.012f * d), r.nextFloat() * 6.28f,
                if (r.nextBoolean()) Color(0xFFD8FF8A) else Color(0xFF9AF0E0),
            )
        }
    }
    private class Ripple(val c: Offset, val maxR: Float, val phase: Float)

    private val ripples: List<Ripple> = run {
        val r = Random(61)
        List(10) {
            val d = 0.1f + r.nextFloat() * 0.8f
            var x = r.nextFloat() * w
            if (abs(x - walkCenter(d)) < walkHalf(d) * 1.3f) x = (x + w * 0.5f) % w
            Ripple(Offset(x, yAtDepth(d)), w * (0.02f + 0.07f * d), r.nextFloat())
        }
    }

    private val vignette = SceneKit.vignette(w, h, 0.66f, Color(0xFF060A04))
    private val tint = Color(0xFF9AB070).copy(alpha = 0.06f)

    // ------------------------------------------------------------------ drawing

    private fun DrawScope.drawTree(t: DeadTree, scale: Float) {
        t.branches.forEach { (p, width, c) -> drawPath(p, c, style = Stroke(width * scale, cap = StrokeCap.Round)) }
        drawPath(t.moss, t.mossColor.copy(alpha = 0.8f), style = Stroke(w * 0.002f, cap = StrokeCap.Round))
    }

    override fun drawBackground(scope: DrawScope, drift: Float, time: Float) = with(scope) {
        drawRect(skyBrush, size = Size(w, horizonY + 4f))
        drawRect(sunGlow, size = Size(w, horizonY))
        drawPath(farLine, Color(0xFF8A967F))
        drawPath(midLine, Color(0xFF5E6B55))

        drawRect(waterBrush, topLeft = Offset(0f, horizonY), size = Size(w, h - horizonY))
        drawRect(waterSheen, topLeft = Offset(0f, horizonY), size = Size(w, h * 0.12f))

        // reflections of the far trees (mirrored at the waterline, faint)
        trees.forEach { (t, s) ->
            withTransform({ scale(1f, -0.55f, Offset(w / 2f, horizonY + h * 0.004f)) }) {
                t.branches.forEach { (p, width, c) -> drawPath(p, c.copy(alpha = 0.16f), style = Stroke(width * s, cap = StrokeCap.Round)) }
            }
        }
        trees.filter { it.second < 1f }.forEach { (t, s) -> drawTree(t, s) }

        ripples.forEach { rp ->
            val p = ((time / 4f + rp.phase) % 1f)
            val rad = rp.maxR * p
            drawOval(
                Color(0xFFDCE2C6).copy(alpha = 0.3f * (1f - p)),
                topLeft = Offset(rp.c.x - rad, rp.c.y - rad * 0.22f), size = Size(rad * 2f, rad * 0.44f),
                style = Stroke(w * 0.0016f),
            )
        }
        pads.forEach { pd ->
            drawPath(pd.path, pd.brush)
            pd.flower?.let {
                drawCircle(Color(0xFFF4C6D8), pd.fr, it)
                drawCircle(Color(0xFFFFF0C0), pd.fr * 0.4f, it)
            }
        }
        tussocks.forEach { drawOval(it.brush, it.rect.topLeft, it.rect.size) }
        backReeds.first.forEach { (p, c) -> drawPath(p, c) }
        drawPath(backReeds.second, Color(0xFF5A3A1E))

        // boardwalk
        drawPath(walkUnder, Color(0xFF151A10).copy(alpha = 0.6f))
        planks.forEach { pl -> drawPath(pl.path, pl.brush) }
        drawPath(grain, Color(0xFF2E2416).copy(alpha = 0.45f), style = Stroke(w * 0.0012f))
        posts.forEach { drawRect(it.brush, it.rect.topLeft, it.rect.size) }

        trees.filter { it.second >= 1f }.forEach { (t, s) -> drawTree(t, s) }

        mists.forEach { m ->
            val x = ((m.phase + drift * m.speed * 3f) % 1f) * (w + m.width) - m.width
            translate(x, m.y) {
                drawOval(
                    Brush.radialGradient(listOf(Color(0xFFE4E8D6).copy(alpha = m.alpha), Color.Transparent), center = Offset(m.width / 2f, 0f), radius = m.width / 2f),
                    topLeft = Offset(0f, -m.height), size = Size(m.width, m.height * 2f),
                )
            }
        }
        lights.forEach { l ->
            val a = (0.45f + 0.45f * SceneKit.wave(time, 7, l.phase)).coerceIn(0f, 1f)
            val pos = l.pos + Offset(SceneKit.wave(time, 2, l.phase) * w * 0.01f, SceneKit.wave(time, 3, l.phase) * h * 0.006f)
            drawCircle(Brush.radialGradient(listOf(l.color.copy(alpha = 0.5f * a), Color.Transparent), pos, l.r * 4f), l.r * 4f, pos, blendMode = BlendMode.Screen)
            drawCircle(Color.White.copy(alpha = a), l.r * 0.5f, pos)
        }
        drawRect(tint, size = size)
    }

    override fun drawForeground(scope: DrawScope, time: Float) = with(scope) {
        frontReeds.first.forEach { (p, c) -> drawPath(p, c) }
        drawPath(frontReeds.second, Color(0xFF4A2E16))
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xFFDDE2CC).copy(alpha = 0.12f), startY = h * 0.75f, endY = h),
            topLeft = Offset(0f, h * 0.75f), size = Size(w, h * 0.25f),
        )
        drawRect(vignette, size = size)
    }
}
