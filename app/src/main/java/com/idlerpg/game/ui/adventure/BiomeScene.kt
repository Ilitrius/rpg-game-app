package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.idlerpg.game.domain.world.Zone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural landscape of the generated zones 6..100: biome template ([BiomeStyle]) plus intensity
 * (stage 0..4 inside the biome and the overall [Zone.threat]). Higher intensity gives a darker sky,
 * thicker fog, more and taller silhouettes, more particles, glowing eyes in the dark, lightning
 * (stormy biomes) and a heavier vignette with thorny framing. All paths and brushes are built once;
 * per frame only cheap alpha/offset animation runs. Seeded by the zone order, so every zone looks
 * different but stable.
 */
internal class BiomeScene(zone: Zone, size: Size) : ZoneScene {
    private val st = BiomeStyles.of(zone.biome!!)
    private val stage = zone.stage
    /** Intensity 0..1: overall zone threat (60 %) plus escalation inside the biome (40 %). */
    private val t = 0.6f * zone.threat + 0.4f * (zone.stage / 4f)
    /** 0..1 intensity inside the biome. */
    private val si = stage / 4f
    private val w = size.width
    private val h = size.height
    override val horizonY = h * 0.44f
    override val monsterGroundY = h * 0.69f
    private val vp = Offset(w * 0.5f, horizonY)
    private val seed = zone.order * 7919

    private fun yAt(d: Float) = horizonY + (h - horizonY) * d
    private fun mix(a: Color, b: Color, f: Float) = SceneKit.mix(a, b, f.coerceIn(0f, 1f))
    private fun frac(x: Float) = x - floor(x)

    // ------------------------------------------------------------------ sky
    private val skyTop = mix(st.skyTop, Color.Black, 0.15f + 0.45f * t)
    private val skyHorizon = mix(mix(st.skyHorizon, Color.Black, 0.1f + 0.35f * t), st.glow, 0.05f + 0.10f * si)
    private val skyBrush = Brush.verticalGradient(0f to skyTop, 0.55f to mix(skyTop, skyHorizon, 0.55f), 1f to skyHorizon, startY = 0f, endY = horizonY)

    private val orbCenter = Offset(w * (0.22f + 0.56f * ((zone.order * 37 % 100) / 100f)), horizonY * (0.42f + 0.12f * si))
    private val orbRadius = w * (0.07f + 0.05f * si + 0.04f * t)
    private val craters: List<Triple<Offset, Float, Float>> = Random(seed + 5).let { r ->
        List(5) { Triple(Offset((r.nextFloat() - 0.5f) * orbRadius, (r.nextFloat() - 0.5f) * orbRadius), orbRadius * (0.08f + r.nextFloat() * 0.12f), 0.1f + r.nextFloat() * 0.15f) }
    }
    private val riftPath = Path().apply {
        val r = Random(seed + 9)
        val x = orbCenter.x
        val top = horizonY * 0.05f
        moveTo(x, top)
        var y = top
        val pts = ArrayList<Offset>()
        while (y < horizonY * 0.95f) { y += horizonY * (0.04f + r.nextFloat() * 0.05f); pts += Offset(x + (r.nextFloat() - 0.5f) * w * 0.05f, y) }
        pts.forEach { lineTo(it.x + w * 0.012f, it.y) }
        pts.reversed().forEach { lineTo(it.x - w * 0.012f, it.y) }
        close()
    }

    // ------------------------------------------------------------------ far layers
    private class Layer(val path: Path, val brush: Brush, val lights: Path?)

    private val layers: List<Layer> = (0..2).map { i ->
        val r = Random(seed + 31 * i)
        val depth = i / 2f
        val baseY = horizonY + h * 0.012f * i
        val amp = h * (0.07f + 0.05f * i) * (0.8f + 0.5f * t + 0.15f * si)
        val density = 1f + 1.2f * t + 0.3f * si
        val far = mix(mix(st.farFar, skyHorizon, 0.35f - 0.2f * depth), Color.Black, 0.1f * t)
        val near = mix(st.farNear, Color.Black, 0.25f + 0.3f * depth)
        val base = if (i == 0) far else mix(far, near, 0.4f + 0.6f * depth)
        val lights = if (st.far == Far.TOWERS || st.far == Far.RUINS) Path() else null
        val path = silhouette(st.far, r, baseY, amp, density, i, lights)
        Layer(
            path,
            Brush.verticalGradient(0f to mix(base, st.fog, 0.15f), 1f to mix(base, Color.Black, 0.45f), startY = baseY - amp * 1.4f, endY = baseY + h * 0.03f),
            lights,
        )
    }

    private fun silhouette(kind: Far, r: Random, baseY: Float, amp: Float, density: Float, layer: Int, lights: Path?): Path = Path().apply {
        val bottom = baseY + h * 0.05f
        fun ground() { lineTo(w * 1.05f, bottom); lineTo(-w * 0.05f, bottom); close() }
        when (kind) {
            Far.DUNES -> {
                moveTo(-w * 0.05f, baseY)
                val f1 = 2f + r.nextFloat() * 2f
                val ph = r.nextFloat() * 6f
                var x = -w * 0.05f
                while (x <= w * 1.05f) { lineTo(x, baseY - amp * (0.35f + 0.35f * sin(x / w * f1 * PI.toFloat() + ph) + 0.2f * sin(x / w * f1 * 2.7f + ph * 2))); x += w / 40f }
                ground()
            }
            Far.PEAKS, Far.CRAGS -> {
                moveTo(-w * 0.05f, baseY)
                var x = -w * 0.05f
                while (x < w * 1.05f) {
                    val peak = baseY - amp * (0.4f + r.nextFloat() * 0.8f)
                    val step = w * (0.05f + r.nextFloat() * 0.09f) / density.coerceAtLeast(0.6f).pow(0.3f)
                    if (kind == Far.PEAKS) { lineTo(x + step * 0.5f, peak); lineTo(x + step, baseY - amp * 0.1f * r.nextFloat()) }
                    else { lineTo(x + step * 0.12f, peak); lineTo(x + step * 0.55f, peak + amp * 0.05f * r.nextFloat()); lineTo(x + step * 0.7f, peak + amp * 0.12f); lineTo(x + step, baseY - amp * 0.25f * r.nextFloat()) }
                    x += step
                }
                ground()
            }
            Far.SPIRES -> {
                moveTo(-w * 0.05f, baseY)
                var x = -w * 0.05f
                val count = (7 * density).toInt() + 3
                val gap = w * 1.1f / count
                repeat(count) {
                    val hh = amp * (0.5f + r.nextFloat() * 1.3f)
                    val wd = gap * (0.18f + r.nextFloat() * 0.22f)
                    lineTo(x, baseY); lineTo(x + gap * 0.5f - wd, baseY - hh * 0.35f)
                    quadraticTo(x + gap * 0.5f - wd * 0.3f, baseY - hh * 0.8f, x + gap * 0.5f + (r.nextFloat() - 0.5f) * wd, baseY - hh)
                    quadraticTo(x + gap * 0.5f + wd * 0.3f, baseY - hh * 0.8f, x + gap * 0.5f + wd, baseY - hh * 0.35f)
                    x += gap
                }
                ground()
            }
            Far.RUINS, Far.COLUMNS, Far.TOWERS -> {
                moveTo(-w * 0.05f, baseY)
                var x = -w * 0.05f
                while (x < w * 1.05f) {
                    val wd = w * (if (kind == Far.COLUMNS) 0.035f + r.nextFloat() * 0.02f else 0.05f + r.nextFloat() * 0.07f)
                    val tall = if (kind == Far.TOWERS) amp * (0.8f + r.nextFloat() * 1.6f) else amp * (0.25f + r.nextFloat() * 0.9f)
                    lineTo(x, baseY - tall)
                    when (kind) {
                        Far.TOWERS -> { lineTo(x + wd * 0.5f, baseY - tall - wd * (1.2f + r.nextFloat() * 1.5f)); lineTo(x + wd, baseY - tall) }
                        Far.COLUMNS -> { lineTo(x - wd * 0.15f, baseY - tall); lineTo(x - wd * 0.15f, baseY - tall - wd * 0.2f); lineTo(x + wd * 1.15f, baseY - tall - wd * 0.2f); lineTo(x + wd * 1.15f, baseY - tall) }
                        else -> { val k = 3; repeat(k) { j -> val hh = tall * (0.75f + r.nextFloat() * 0.25f); lineTo(x + wd * j / k, baseY - hh); lineTo(x + wd * (j + 1) / k, baseY - hh) } }
                    }
                    lineTo(x + wd, baseY - tall * 0.4f)
                    if (lights != null) {
                        val n = (tall / (h * 0.03f)).toInt().coerceIn(0, 6)
                        repeat(n) { j -> if (r.nextFloat() < 0.6f) lights.addRect(Rect(x + wd * 0.3f, baseY - tall + j * h * 0.03f + h * 0.01f, x + wd * 0.3f + wd * 0.12f, baseY - tall + j * h * 0.03f + h * 0.025f)) }
                    }
                    x += wd + w * (if (kind == Far.COLUMNS) 0.03f + r.nextFloat() * 0.07f else 0.005f + r.nextFloat() * 0.05f) / density.coerceAtLeast(0.7f)
                }
                lineTo(w * 1.05f, baseY); ground()
            }
            Far.TREES -> {
                moveTo(-w * 0.05f, baseY); lineTo(w * 1.05f, baseY); lineTo(w * 1.05f, bottom); lineTo(-w * 0.05f, bottom); close()
                val n = (9 * density).toInt() + 4
                repeat(n) {
                    val x = r.nextFloat() * w
                    val hh = amp * (0.8f + r.nextFloat() * 1.0f)
                    val tw = w * (0.008f + r.nextFloat() * 0.01f)
                    moveTo(x - tw, baseY); lineTo(x - tw * 0.5f, baseY - hh); lineTo(x + tw * 0.5f, baseY - hh); lineTo(x + tw, baseY); close()
                    repeat(3) { addOval(Rect(x - w * 0.06f + r.nextFloat() * w * 0.04f, baseY - hh - amp * 0.2f + r.nextFloat() * amp * 0.15f, x + w * 0.02f + r.nextFloat() * w * 0.05f, baseY - hh + amp * 0.25f)) }
                }
            }
            Far.ISLANDS -> {
                val n = (3 * density).toInt() + 2
                repeat(n) {
                    val cx = r.nextFloat() * w
                    val cy = baseY - amp * (0.3f + r.nextFloat() * 1.6f)
                    val rw = w * (0.05f + r.nextFloat() * 0.1f)
                    addOval(Rect(cx - rw, cy - rw * 0.12f, cx + rw, cy + rw * 0.12f))
                    moveTo(cx - rw * 0.9f, cy); lineTo(cx - rw * 0.25f, cy + rw * (0.5f + r.nextFloat() * 0.5f)); lineTo(cx + rw * 0.1f, cy + rw * (0.9f + r.nextFloat() * 0.6f))
                    lineTo(cx + rw * 0.35f, cy + rw * 0.5f); lineTo(cx + rw * 0.9f, cy); close()
                }
                moveTo(-w * 0.05f, baseY); lineTo(w * 1.05f, baseY); lineTo(w * 1.05f, bottom); lineTo(-w * 0.05f, bottom); close()
            }
            Far.GEARS -> {
                fillType = PathFillType.EvenOdd
                val n = if (density > 2f) 3 else 2
                val gap = w / n
                repeat(n) { gi ->
                    val rad = minOf(amp * (2.4f + r.nextFloat() * 1.6f), gap * 0.5f)
                    val cx = gap * (gi + 0.5f) + (r.nextFloat() - 0.5f) * gap * 0.2f
                    val cy = baseY - rad * (0.25f + r.nextFloat() * 0.4f)
                    val teeth = 12 + r.nextInt(8)
                    for (k in 0 until teeth * 2) {
                        val a = k * PI.toFloat() / teeth
                        val rr = if (k % 2 == 0) rad else rad * 1.14f
                        val a2 = a + PI.toFloat() / teeth * 0.5f
                        val x1 = cx + cos(a) * rr; val y1 = cy + sin(a) * rr
                        val x2 = cx + cos(a2) * rr; val y2 = cy + sin(a2) * rr
                        if (k == 0) moveTo(x1, y1) else lineTo(x1, y1)
                        lineTo(x2, y2)
                    }
                    close()
                    addOval(Rect(cx - rad * 0.25f, cy - rad * 0.25f, cx + rad * 0.25f, cy + rad * 0.25f))
                    repeat(5) { j -> val a = j * 2 * PI.toFloat() / 5; addOval(Rect(cx + cos(a) * rad * 0.6f - rad * 0.1f, cy + sin(a) * rad * 0.6f - rad * 0.1f, cx + cos(a) * rad * 0.6f + rad * 0.1f, cy + sin(a) * rad * 0.6f + rad * 0.1f)) }
                }
            }
            Far.TENDRILS -> {
                val n = (9 * density).toInt() + 4
                repeat(n) {
                    val x = r.nextFloat() * w
                    val hh = amp * (0.6f + r.nextFloat() * 1.5f)
                    val sway = (r.nextFloat() - 0.5f) * w * 0.12f
                    val tw = w * (0.006f + r.nextFloat() * 0.01f)
                    moveTo(x - tw, baseY)
                    cubicTo(x - tw + sway * 0.2f, baseY - hh * 0.4f, x + sway, baseY - hh * 0.6f, x + sway * 0.7f, baseY - hh)
                    cubicTo(x + sway + tw, baseY - hh * 0.6f, x + tw + sway * 0.2f, baseY - hh * 0.4f, x + tw, baseY)
                    close()
                    addOval(Rect(x + sway * 0.7f - tw * 2.2f, baseY - hh - tw * 2.2f, x + sway * 0.7f + tw * 2.2f, baseY - hh + tw * 2.2f))
                }
                moveTo(-w * 0.05f, baseY); lineTo(w * 1.05f, baseY); lineTo(w * 1.05f, bottom); lineTo(-w * 0.05f, bottom); close()
            }
            Far.WAVES -> {
                moveTo(-w * 0.05f, baseY)
                val f = 5f + r.nextFloat() * 3f
                val ph = r.nextFloat() * 6f
                var x = -w * 0.05f
                while (x <= w * 1.05f) {
                    val s = sin(x / w * f * PI.toFloat() + ph)
                    lineTo(x, baseY - amp * (0.25f + 0.3f * s + 0.12f * sin(x / w * f * 3.3f)) * (0.7f + 0.3f * layer))
                    x += w / 60f
                }
                ground()
            }
        }
    }

    // ------------------------------------------------------------------ eyes watching from the dark
    private val eyes: List<Offset> = Random(seed + 77).let { r ->
        val n = (t * 7f).toInt() + (if (stage >= 3) 1 else 0)
        List(n) { Offset(w * (0.05f + r.nextFloat() * 0.9f), horizonY - h * (0.005f + r.nextFloat() * 0.07f)) }
    }
    private val eyeColor = st.eye

    // ------------------------------------------------------------------ ground
    private val groundBrush = Brush.verticalGradient(
        0f to mix(st.groundTop, st.fog, 0.35f), 0.25f to mix(st.groundTop, Color.Black, 0.15f * t),
        1f to mix(st.groundBottom, Color.Black, 0.3f * t), startY = horizonY, endY = h,
    )
    private val groundLight = Path()
    private val groundDark = Path()
    private val groundGlow = Path()
    private val groundGlowWide = Path()
    private val groundStrokeW = maxOf(1.5f, w * 0.0025f)

    init {
        val r = Random(seed + 101)
        fun line(p: Path, a: Offset, b: Offset) { p.moveTo(a.x, a.y); p.lineTo(b.x, b.y) }
        fun grid(p: Path) {
            for (i in -8..8) line(p, vp, Offset(w * (0.5f + i * 0.28f), h * 1.05f))
            for (j in 1..9) { val y = yAt((j / 9f).pow(1.9f)); line(p, Offset(0f, y), Offset(w, y)) }
        }
        fun crack(p: Path, from: Offset, depth: Int) {
            var o = from; var dir = -PI.toFloat() / 2 + (r.nextFloat() - 0.5f) * 1.4f
            p.moveTo(o.x, o.y)
            val len = h * (0.012f + r.nextFloat() * 0.022f)
            val steps = 5 + r.nextInt(5)
            repeat(steps) {
                dir += (r.nextFloat() - 0.5f) * 0.9f
                val k = (o.y - horizonY).coerceAtLeast(2f) / (h - horizonY)
                o = Offset(o.x + cos(dir) * len * (0.3f + k), (o.y + sin(dir) * len * (0.3f + k) * 0.55f).coerceAtLeast(horizonY + 2f))
                p.lineTo(o.x, o.y)
                if (depth < 2 && r.nextFloat() < 0.25f) { val keep = o; crack(p, keep, depth + 1); p.moveTo(keep.x, keep.y) }
            }
        }
        val n = (40 + 40 * t + 8 * stage).toInt()
        when (st.ground) {
            GroundKind.GRASS -> repeat(n * 5) {
                val d = r.nextFloat().pow(1.6f); val y = yAt(d); val x = r.nextFloat() * w
                val l = h * 0.012f * (0.3f + 1.7f * d)
                val target = if (r.nextBoolean()) groundLight else groundDark
                target.moveTo(x, y); target.quadraticTo(x + l * 0.2f * (r.nextFloat() - 0.5f), y - l * 0.6f, x + l * (r.nextFloat() - 0.5f), y - l)
            }
            GroundKind.SAND -> repeat(n) {
                val d = r.nextFloat().pow(1.5f); val y = yAt(d); val x = r.nextFloat() * w; val l = w * (0.04f + 0.2f * d)
                val target = if (r.nextBoolean()) groundLight else groundDark
                target.moveTo(x - l, y); target.quadraticTo(x, y - l * 0.1f, x + l, y)
            }
            GroundKind.ROCK, GroundKind.TILES -> { grid(groundDark); repeat(n) { val d = r.nextFloat().pow(1.6f); val y = yAt(d); val x = r.nextFloat() * w; val l = w * 0.03f * (0.3f + d); groundLight.moveTo(x, y); groundLight.lineTo(x + l, y + l * 0.15f) } }
            GroundKind.LAVA -> { repeat(3 + (t * 5).toInt()) { crack(groundGlow, Offset(w * (0.05f + r.nextFloat() * 0.9f), h * (0.78f + r.nextFloat() * 0.2f)), 0) }; repeat(n) { val d = r.nextFloat().pow(1.5f); val y = yAt(d); val x = r.nextFloat() * w; groundDark.moveTo(x, y); groundDark.lineTo(x + w * 0.04f * d, y + 2f) } }
            GroundKind.ICE -> { repeat(8 + (t * 10).toInt()) { crack(groundLight, Offset(w * (0.05f + r.nextFloat() * 0.9f), h * (0.75f + r.nextFloat() * 0.22f)), 0) }; grid(groundDark) }
            GroundKind.WATER -> repeat(n) {
                val d = r.nextFloat().pow(1.4f); val y = yAt(d); val x = r.nextFloat() * w * 1.2f - w * 0.1f; val l = w * (0.03f + 0.12f * d)
                val target = if (r.nextBoolean()) groundLight else groundDark
                target.moveTo(x - l, y); target.quadraticTo(x, y - l * 0.08f, x + l, y)
            }
            GroundKind.BONES -> repeat(n) {
                val d = r.nextFloat().pow(1.5f); val y = yAt(d); val x = r.nextFloat() * w; val l = w * 0.035f * (0.3f + 1.4f * d); val a = r.nextFloat() * PI.toFloat()
                groundLight.moveTo(x, y); groundLight.lineTo(x + cos(a) * l, y + sin(a) * l * 0.3f)
                if (r.nextFloat() < 0.12f) groundDark.addOval(Rect(x - l * 0.3f, y - l * 0.3f, x + l * 0.3f, y + l * 0.3f))
            }
            GroundKind.FLESH -> { repeat(4 + (t * 6).toInt()) { crack(groundGlow, Offset(w * (0.05f + r.nextFloat() * 0.9f), h * (0.78f + r.nextFloat() * 0.2f)), 0) }; repeat(n) { val d = r.nextFloat().pow(1.5f); val y = yAt(d); val x = r.nextFloat() * w; groundDark.addOval(Rect(x, y, x + w * 0.03f * (0.3f + d), y + w * 0.012f * (0.3f + d))) } }
            GroundKind.VOID -> { grid(groundGlow); repeat(n * 2) { val d = r.nextFloat().pow(1.4f); groundLight.addOval(Rect(r.nextFloat() * w, yAt(d), 0f, 0f).let { q -> Rect(q.left, q.top, q.left + 2.2f, q.top + 2.2f) }) } }
        }
        groundGlowWide.addPath(groundGlow)
    }

    private val glowBrush = Brush.verticalGradient(
        0f to Color.Transparent, 1f to st.glow.copy(alpha = 0.22f + 0.2f * t),
        startY = horizonY, endY = h,
    )
    private val fogBrush = Brush.verticalGradient(
        0f to Color.Transparent, 0.5f to st.fog.copy(alpha = 0.25f + 0.4f * t + 0.1f * si), 1f to Color.Transparent,
        startY = horizonY - h * 0.1f, endY = horizonY + h * 0.12f,
    )

    // ------------------------------------------------------------------ particles
    private class P(val x: Float, val y: Float, val size: Float, val cycles: Int, val phase: Float)
    private val particles: List<P> = Random(seed + 303).let { r ->
        if (st.particle == Particle.NONE) emptyList()
        else List((16 + 44 * t + 5 * stage).toInt()) { P(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat(), 1 + r.nextInt(4), r.nextFloat() * 6.28f) }
    }
    private val lightning = zone.biome.let { b -> b == com.idlerpg.game.domain.world.Biome.KUESTE || b == com.idlerpg.game.domain.world.Biome.HIMMEL || b == com.idlerpg.game.domain.world.Biome.LEERE }
    private val strikeTimes = Random(seed + 404).let { r -> List(3) { 6f + it * 19f + r.nextFloat() * 8f } }
    private val bolt = Path().apply {
        val r = Random(seed + 505)
        var x = w * (0.2f + r.nextFloat() * 0.6f); var y = 0f
        moveTo(x, y)
        while (y < horizonY) { x += (r.nextFloat() - 0.5f) * w * 0.07f; y += horizonY * (0.07f + r.nextFloat() * 0.08f); lineTo(x, y.coerceAtMost(horizonY)) }
    }

    // ------------------------------------------------------------------ foreground framing
    private val frame = Path().apply {
        val r = Random(seed + 606)
        val n = (3 + 8 * t + stage).toInt()
        for (side in 0..1) repeat(n) {
            val x0 = if (side == 0) -w * 0.02f * r.nextFloat() else w * (1f + 0.02f * r.nextFloat())
            val y0 = h * (0.5f + r.nextFloat() * 0.5f)
            val len = w * (0.08f + r.nextFloat() * 0.18f) * (0.7f + t)
            val dir = if (side == 0) 1f else -1f
            val tipY = y0 - len * (0.7f + r.nextFloat() * 1.6f)
            moveTo(x0, y0 + h * 0.03f)
            quadraticTo(x0 + dir * len * 0.5f, y0 - (y0 - tipY) * 0.4f, x0 + dir * len * (0.2f + r.nextFloat() * 0.5f), tipY)
            quadraticTo(x0 + dir * len * 0.25f, y0 - (y0 - tipY) * 0.5f, x0 + dir * len * 0.02f, y0 - h * 0.01f)
            close()
        }
    }
    private val frameBrush = Brush.verticalGradient(0f to mix(st.skinMid, Color.Black, 0.75f), 1f to mix(st.skinDark, Color.Black, 0.8f), startY = h * 0.3f, endY = h)
    private val vignette = SceneKit.vignette(w, h, 0.5f + 0.35f * t + 0.1f * si, mix(Color.Black, st.skinDark, 0.2f))
    private val darkness = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f * t), 1f to Color.Black.copy(alpha = 0.32f * t), startY = 0f, endY = h)

    // ------------------------------------------------------------------ static layers baked once
    private class Star(val x: Float, val y: Float, val r: Float, val cycles: Int, val phase: Float)
    private val stars: List<Star> = Random(seed + 1).let { r ->
        if (st.particle == Particle.STARS || st.orb == Orb.PLANET || st.ground == GroundKind.VOID)
            List(60 + (40 * t).toInt()) { Star(r.nextFloat() * w, r.nextFloat() * horizonY * 0.95f, (0.8f + r.nextFloat() * 1.8f) * w / 1080f, 1 + it % 4, it * 1.3f) }
        else emptyList()
    }

    /** Ground kinds whose detail strokes animate; all others are baked into the ground layer. */
    private val animatedGround = st.ground == GroundKind.LAVA || st.ground == GroundKind.FLESH || st.ground == GroundKind.VOID || st.ground == GroundKind.WATER

    private val bakeH = (horizonY + h * 0.06f).toInt().coerceAtMost(h.toInt())
    private var skyLayer: ImageBitmap? = null      // sky + calm stars, 0..horizonY
    private var farLayer: ImageBitmap? = null      // far silhouettes + fog, 0..bakeH (transparent sky)
    private var groundLayer: ImageBitmap? = null   // ground (+ static details + glow), horizonY..h

    private fun bake(width: Int, height: Int, block: DrawScope.() -> Unit): ImageBitmap {
        val bmp = ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(width.toFloat(), height.toFloat())) { block() }
        return bmp
    }

    private fun ensureBaked() {
        if (skyLayer != null) return
        val wi = w.toInt(); val hz = horizonY.toInt()
        skyLayer = bake(wi, hz + 1) {
            drawRect(skyBrush, size = Size(w, horizonY + 1f))
            stars.forEachIndexed { i, sp -> if (i % 5 != 0) drawCircle(Color.White.copy(alpha = 0.55f), sp.r, Offset(sp.x, sp.y)) }
        }
        farLayer = bake(wi, bakeH) {
            layers.forEach { l ->
                drawPath(l.path, l.brush)
                l.lights?.let { lp -> drawPath(lp, st.glow.copy(alpha = 0.35f)) }
            }
            drawRect(fogBrush, topLeft = Offset(0f, horizonY - h * 0.1f), size = Size(w, h * 0.22f))
        }
        groundLayer = bake(wi, (h - horizonY).toInt() + 1) {
            withTransform({ translate(0f, -horizonY) }) {
                drawRect(groundBrush, topLeft = Offset(0f, horizonY), size = Size(w, h - horizonY))
                if (!animatedGround) drawGroundDetails(0f)
                drawRect(glowBrush, topLeft = Offset(0f, horizonY), size = Size(w, h - horizonY))
            }
        }
    }

    // cached per-eye halo brushes (eyes do not move)
    private val eyeSize = w * 0.0045f
    private val eyeHalo: List<Brush> = eyes.map { e -> Brush.radialGradient(listOf(eyeColor.copy(alpha = 0.5f), Color.Transparent), center = e, radius = eyeSize * 4f) }

    // ------------------------------------------------------------------ draw
    override fun drawBackground(scope: DrawScope, drift: Float, time: Float) = with(scope) {
        ensureBaked()
        drawImage(skyLayer!!)
        stars.forEachIndexed { i, sp ->
            if (i % 5 == 0) drawCircle(Color.White, sp.r, Offset(sp.x, sp.y), alpha = (0.28f + 0.5f * (0.5f + 0.5f * SceneKit.wave(time, sp.cycles, sp.phase))).coerceIn(0f, 1f))
        }
        drawOrb(time)
        drawImage(farLayer!!)
        layers.forEachIndexed { i, l -> l.lights?.let { lp -> drawPath(lp, st.glow.copy(alpha = 0.12f * (0.5f + 0.5f * SceneKit.wave(time, 3, i * 2f)))) } }
        eyes.forEachIndexed { i, e ->
            val a = (0.55f + 0.45f * SceneKit.wave(time, 2 + i % 3, i * 1.7f)).coerceIn(0f, 1f) * (if (time % 20f in 0f..0.25f && i % 2 == 0) 0.2f else 1f)
            val s = eyeSize
            drawCircle(eyeHalo[i], s * 4f, e, alpha = a)
            drawCircle(eyeColor, s, Offset(e.x - s * 2f, e.y), alpha = a)
            drawCircle(eyeColor, s, Offset(e.x + s * 2f, e.y), alpha = a)
        }
        drawImage(groundLayer!!, Offset(0f, horizonY))
        if (animatedGround) drawGroundDetails(time)
        drawParticles(time)
        if (lightning) strike(time)
    }

    // orb brushes are built once; the pulse scales them around the orb center
    private val orbSunColor = if (st.orb == Orb.RED_SUN) Color(0xFFFF4A1A) else mix(Color(0xFFFFE9A8), st.glow, 0.3f)
    private val orbHalo: Brush? = when (st.orb) {
        Orb.SUN, Orb.RED_SUN -> Brush.radialGradient(listOf(orbSunColor.copy(alpha = 0.55f), Color.Transparent), center = orbCenter, radius = orbRadius * 4f)
        Orb.MOON -> Brush.radialGradient(listOf(Color(0xFFDDE8FF).copy(alpha = 0.35f), Color.Transparent), center = orbCenter, radius = orbRadius * 3.2f)
        Orb.ECLIPSE -> Brush.radialGradient(0.7f to st.glow.copy(alpha = 0.7f), 0.85f to st.glow.copy(alpha = 0.25f), 1f to Color.Transparent, center = orbCenter, radius = orbRadius * 2.6f)
        Orb.PLANET -> Brush.radialGradient(listOf(st.glow.copy(alpha = 0.4f), Color.Transparent), center = orbCenter, radius = orbRadius * 3f)
        else -> null
    }
    private val orbBody: Brush? = when (st.orb) {
        Orb.SUN, Orb.RED_SUN -> Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), orbSunColor, mix(orbSunColor, Color.Black, 0.3f)), center = orbCenter, radius = orbRadius)
        Orb.MOON -> Brush.radialGradient(listOf(Color(0xFFF4F6FF), Color(0xFFB9C3DC)), center = orbCenter.copy(x = orbCenter.x - orbRadius * 0.3f, y = orbCenter.y - orbRadius * 0.3f), radius = orbRadius * 1.4f)
        Orb.PLANET -> Brush.linearGradient(listOf(mix(st.skinLight, Color.White, 0.2f), st.skinMid, st.skinDark), start = orbCenter.copy(y = orbCenter.y - orbRadius), end = orbCenter.copy(y = orbCenter.y + orbRadius))
        else -> null
    }

    private fun DrawScope.drawOrb(time: Float) {
        val c = orbCenter; val rad = orbRadius
        when (st.orb) {
            Orb.NONE -> Unit
            Orb.SUN, Orb.RED_SUN -> {
                val pulse = 1f + 0.03f * SceneKit.wave(time, 3)
                withTransform({ scale(pulse, pulse, c) }) { drawCircle(orbHalo!!, rad * 4f, c) }
                drawCircle(orbBody!!, rad, c)
            }
            Orb.MOON -> {
                drawCircle(orbHalo!!, rad * 3.2f, c)
                drawCircle(orbBody!!, rad, c)
                craters.forEach { (o, rr, a) -> drawCircle(Color(0xFF6A7490), rr, c + o, alpha = a) }
                if (t > 0.5f) drawCircle(Color(0xFFB02030).copy(alpha = (t - 0.5f) * 0.8f), rad, c, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
            }
            Orb.ECLIPSE -> {
                val pulse = 1f + 0.04f * SceneKit.wave(time, 2)
                withTransform({ scale(pulse, pulse, c) }) { drawCircle(orbHalo!!, rad * 2.6f, c) }
                drawCircle(Color(0xFF020104), rad, c)
                drawCircle(st.glow, rad * 1.02f, c, alpha = 0.9f, style = Stroke(width = rad * 0.05f))
            }
            Orb.PLANET -> {
                drawCircle(orbHalo!!, rad * 3f, c)
                drawCircle(orbBody!!, rad * 1.3f, c)
                repeat(4) { k -> drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, topLeft = Offset(c.x - rad * 1.3f, c.y - rad * 0.9f + k * rad * 0.5f), size = Size(rad * 2.6f, rad * 0.15f)) }
                drawOval(st.accent.copy(alpha = 0.55f), Offset(c.x - rad * 2.4f, c.y - rad * 0.28f), Size(rad * 4.8f, rad * 0.56f), style = Stroke(width = rad * 0.08f))
            }
            Orb.RIFT -> {
                val a = 0.75f + 0.25f * SceneKit.wave(time, 4)
                drawPath(riftPath, st.glow.copy(alpha = 0.35f * a), style = Stroke(width = w * 0.05f))
                drawPath(riftPath, st.accent.copy(alpha = 0.6f * a), style = Stroke(width = w * 0.018f))
                drawPath(riftPath, Color.White.copy(alpha = a))
            }
        }
    }

    private fun DrawScope.drawGroundDetails(time: Float) {
        val sw = groundStrokeW
        when (st.ground) {
            GroundKind.GRASS -> { drawPath(groundDark, st.skinDark.copy(alpha = 0.7f), style = Stroke(sw, cap = StrokeCap.Round)); drawPath(groundLight, mix(st.groundTop, st.skinLight, 0.4f).copy(alpha = 0.6f), style = Stroke(sw, cap = StrokeCap.Round)) }
            GroundKind.SAND, GroundKind.WATER -> {
                val dx = if (st.ground == GroundKind.WATER) SceneKit.wave(time, 3) * w * 0.012f else 0f
                withTransform({ translate(dx, 0f) }) {
                    drawPath(groundDark, Color.Black.copy(alpha = 0.25f), style = Stroke(sw * 1.4f, cap = StrokeCap.Round))
                    drawPath(groundLight, Color.White.copy(alpha = if (st.ground == GroundKind.WATER) 0.28f else 0.2f), style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
            GroundKind.ROCK, GroundKind.TILES, GroundKind.ICE -> {
                drawPath(groundDark, Color.Black.copy(alpha = 0.3f), style = Stroke(sw))
                drawPath(groundLight, Color.White.copy(alpha = 0.22f), style = Stroke(sw * 1.2f, cap = StrokeCap.Round))
            }
            GroundKind.LAVA, GroundKind.FLESH -> {
                val pulse = 0.6f + 0.4f * SceneKit.wave(time, 3)
                drawPath(groundDark, Color.Black.copy(alpha = 0.3f), style = Stroke(sw * 2f))
                drawPath(groundGlow, st.glow.copy(alpha = 0.25f * pulse), style = Stroke(sw * 7f, cap = StrokeCap.Round))
                drawPath(groundGlow, mix(st.glow, Color.White, 0.5f).copy(alpha = 0.55f + 0.4f * pulse), style = Stroke(sw * 2f, cap = StrokeCap.Round))
            }
            GroundKind.BONES -> {
                drawPath(groundLight, Color(0xFFEFE6CC).copy(alpha = 0.8f), style = Stroke(sw * 2.4f, cap = StrokeCap.Round))
                drawPath(groundDark, Color(0xFFEFE6CC).copy(alpha = 0.85f))
            }
            GroundKind.VOID -> {
                val pulse = 0.5f + 0.5f * SceneKit.wave(time, 2)
                drawPath(groundGlow, st.glow.copy(alpha = 0.18f + 0.2f * pulse), style = Stroke(sw))
                drawPath(groundLight, Color.White.copy(alpha = 0.7f))
            }
        }
    }

    private fun DrawScope.drawParticles(time: Float) {
        val k = time / 60f
        particles.forEach { p ->
            val f = frac(p.y + p.cycles * k); val g = frac(p.x + p.cycles * k)
            val sway = sin(time * 0.5f + p.phase) * w * 0.02f
            when (st.particle) {
                Particle.EMBERS -> { val y = h * (1f - f); val a = (1f - f) * (0.6f + 0.4f * sin(time * 6f + p.phase)); drawCircle(st.accent.copy(alpha = a.coerceIn(0f, 1f)), w * 0.003f * p.size, Offset(p.x * w + sway, y)) }
                Particle.ASH -> drawCircle(Color(0xFFBDB6A8).copy(alpha = 0.5f), w * 0.0025f * p.size, Offset(p.x * w + sway, h * f))
                Particle.SNOW -> drawCircle(Color.White.copy(alpha = 0.85f), w * 0.0032f * p.size, Offset(p.x * w + sway, h * f))
                Particle.SPORES, Particle.MOTES -> { val a = 0.3f + 0.4f * (0.5f + 0.5f * sin(time * 2f + p.phase)); val o = Offset(p.x * w + sway, h * (1f - f)); drawCircle(st.accent, w * 0.011f * p.size, o, alpha = a * 0.3f); drawCircle(st.accent, w * 0.0035f * p.size, o, alpha = a) }
                Particle.SAND -> { val y = yAt(0.15f + p.y * 0.6f); drawLine(st.skinLight.copy(alpha = 0.35f), Offset(g * w, y), Offset(g * w - w * 0.06f * p.size, y + 4f), strokeWidth = 2f) }
                Particle.RAIN -> drawLine(Color(0xFFBFD8E8).copy(alpha = 0.45f), Offset(p.x * w + f * w * 0.1f, h * f), Offset(p.x * w + f * w * 0.1f - w * 0.012f, h * f + h * 0.03f), strokeWidth = 2.2f)
                Particle.PETALS -> drawCircle(st.accent.copy(alpha = 0.8f), w * 0.004f * p.size, Offset(p.x * w + sway * 2f, h * f))
                Particle.STARS, Particle.NONE -> Unit
            }
        }
    }

    private fun DrawScope.strike(time: Float) {
        strikeTimes.forEach { ts ->
            val dt = time - ts
            if (dt in 0f..0.5f) {
                val a = (1f - dt / 0.5f) * (if ((dt * 20f).toInt() % 2 == 0) 1f else 0.5f)
                drawRect(Color.White.copy(alpha = 0.22f * a), size = androidx.compose.ui.geometry.Size(w, h))
                drawPath(bolt, st.accent.copy(alpha = 0.5f * a), style = Stroke(w * 0.012f))
                drawPath(bolt, Color.White.copy(alpha = a), style = Stroke(w * 0.004f))
            }
        }
    }

    override fun drawForeground(scope: DrawScope, time: Float) = with(scope) {
        drawRect(darkness)
        drawPath(frame, frameBrush)
        drawRect(vignette)
    }
}
