package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.idlerpg.game.domain.world.MonsterType
import com.idlerpg.game.domain.world.Zone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedurally painted monsters of the generated zones (6..100) in the 200x200 unit space of
 * [MonsterPainters]. A creature is "body plan ([Body], from the biome) + skin palette (biome) +
 * fear level". The fear level grows with the zone ([Zone.threat]) and for bosses: more and larger
 * eyes, bigger fangs, horns, armor plates and spikes, glowing cracks, an aura with orbiting wisps,
 * and for bosses extra heads/crowns/halos. Paths and brushes are built once per (zone, type);
 * per frame only pulses, blinks and wing/tongue motion run.
 */
internal object ProceduralMonsters {
    private val cache = object : LinkedHashMap<String, Creature>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Creature>?) = size > 12
    }

    // last creature kept outside the map: no key allocation in the per-frame path
    private var lastZone: Zone? = null
    private var lastType: MonsterType? = null
    private var last: Creature? = null

    fun draw(scope: DrawScope, zone: Zone, type: MonsterType, time: Float) {
        var c = last
        if (c == null || lastZone !== zone || lastType !== type) {
            val key = zone.id + "/" + type.id
            c = synchronized(cache) { cache.getOrPut(key) { Creature(zone, type) } }
            last = c; lastZone = zone; lastType = type
        }
        c.draw(scope, time)
    }

    /** Number of draw ops of the creature (for the performance budget test). */
    fun opCount(zone: Zone, type: MonsterType): Int = synchronized(cache) { cache.getOrPut(zone.id + "/" + type.id) { Creature(zone, type) } }.opCount

    /** Fear level 0..1 of [type] in [zone] (drives all the scary detail). */
    fun fear(zone: Zone, type: MonsterType): Float {
        // overall escalation (0.6) plus escalation inside the biome (0.4): stage 0..4 is clearly visible
        val base = 0.08f + 0.6f * zone.threat + 0.4f * (zone.stage / 4f)
        return (if (type.isBoss) base + 0.12f else base).coerceIn(0f, 1f)
    }
}

private typealias Op = DrawScope.(Float) -> Unit

private class Creature(val zone: Zone, val type: MonsterType) {
    private val st = BiomeStyles.of(zone.biome!!)
    private val boss = type.isBoss
    private val role = zone.monsters.indexOf(type)
    private val body = st.bodyOf(role, boss)
    private val fear = ProceduralMonsters.fear(zone, type)
    private val r = Random(zone.order * 131 + (role + 2) * 17)

    private val dark = SceneKit.mix(st.skinDark, Color.Black, 0.25f * fear)
    private val mid = SceneKit.mix(st.skinMid, st.skinDark, 0.3f * fear)
    private val light = st.skinLight
    private val accent = st.accent
    private val eyeCol = st.eye

    private val back = ArrayList<Op>()   // aura etc., behind the body
    private val ops = ArrayList<Op>()
    private val front = ArrayList<Op>()  // glow, wisps in front

    // --- scary feature counts
    private val extraEyes = (if (fear > 0.3f) 2 else 0) + (if (fear > 0.6f) 2 else 0) + (if (boss && fear > 0.45f) 2 else 0)
    private val fangCount = 3 + (fear * 7).toInt()
    private val hornCount = if (fear < 0.18f) 0 else if (fear < 0.5f) 2 else if (fear < 0.8f) 4 else 6
    private val plates = fear > 0.4f
    private val crack = fear > 0.3f

    init {
        buildAura()
        when (body) {
            Body.BLOB -> blob()
            Body.HOUND -> hound()
            Body.HUMANOID -> humanoid()
            Body.INSECT -> insect()
            Body.WINGED -> winged()
            Body.SERPENT -> serpent()
            Body.WRAITH -> wraith()
            Body.GOLEM -> golem()
        }
        if (boss) bossCrest()
    }

    val opCount: Int get() = back.size + ops.size + front.size

    fun draw(scope: DrawScope, time: Float) {
        back.forEach { scope.it(time) }
        ops.forEach { scope.it(time) }
        front.forEach { scope.it(time) }
    }

    // ------------------------------------------------------------------ helpers
    private fun path(b: Path.() -> Unit) = Path().apply(b)
    private fun Path.poly(vararg p: Float, close: Boolean = true) {
        moveTo(p[0], p[1]); var i = 2
        while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
        if (close) close()
    }
    private fun skin(x0: Float, y0: Float, x1: Float, y1: Float) =
        Brush.linearGradient(0f to light, 0.4f to mid, 1f to dark, start = Offset(x0, y0), end = Offset(x1, y1))

    private fun fill(p: Path, b: Brush) { ops += { drawPath(p, b) } }
    private fun fill(p: Path, c: Color) { ops += { drawPath(p, c) } }
    private fun line(p: Path, c: Color, w: Float) { ops += { drawPath(p, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }

    /** Body piece with gradient fill, dark outline and a lit rim. */
    private fun piece(p: Path, x0: Float, y0: Float, x1: Float, y1: Float, outline: Float = 1.6f) {
        fill(p, skin(x0, y0, x1, y1))
        line(p, dark.copy(alpha = 0.9f), outline)
    }

    private fun pulse(time: Float, hz: Float, phase: Float = 0f) = 0.5f + 0.5f * sin(time * hz * 2f * PI.toFloat() + phase)

    private fun glowDot(c: Offset, rad: Float, col: Color, hz: Float = 0.5f, base: Float = 0.7f) {
        val br = Brush.radialGradient(listOf(col.copy(alpha = 0.9f), col.copy(alpha = 0.25f), Color.Transparent), center = c, radius = rad)
        ops += { drawCircle(br, rad, c, alpha = base + (1f - base) * pulse(it, hz)) }
    }

    /** Glowing eyes with slit pupils at [centers]; [size] = eye radius. Blinks now and then. */
    private fun eyes(centers: List<Offset>, size: Float, slit: Boolean = true) {
        centers.forEachIndexed { i, c ->
            val halo = Brush.radialGradient(listOf(eyeCol.copy(alpha = 0.55f), Color.Transparent), center = c, radius = size * 3.2f)
            val off = i * 1.3f
            ops += { t ->
                val closed = MonsterPainters.blink(t, 4.6f + (i % 3) * 0.7f, off)
                drawCircle(halo, size * 3.2f, c)
                withTransform({ scale(1f, 1f - 0.9f * closed, c) }) {
                    drawOval(eyeCol, Offset(c.x - size, c.y - size * 0.8f), androidx.compose.ui.geometry.Size(size * 2f, size * 1.6f))
                    drawOval(Color.White.copy(alpha = 0.55f), Offset(c.x - size * 0.5f, c.y - size * 0.55f), androidx.compose.ui.geometry.Size(size, size * 0.6f))
                    if (slit) drawOval(Color(0xFF14080A), Offset(c.x - size * 0.16f, c.y - size * 0.75f), androidx.compose.ui.geometry.Size(size * 0.32f, size * 1.5f))
                }
            }
        }
    }

    /** Row of triangular teeth along the line a->b, pointing [dirY] (+1 down, -1 up). */
    private fun teeth(a: Offset, b: Offset, count: Int, len: Float, dirY: Float, col: Color = Color(0xFFF2EAD6)) {
        val p = Path()
        for (i in 0 until count) {
            val f0 = i / count.toFloat(); val f1 = (i + 1) / count.toFloat(); val fm = (f0 + f1) / 2f
            val x0 = a.x + (b.x - a.x) * f0; val y0 = a.y + (b.y - a.y) * f0
            val x1 = a.x + (b.x - a.x) * f1; val y1 = a.y + (b.y - a.y) * f1
            val xm = a.x + (b.x - a.x) * fm; val ym = a.y + (b.y - a.y) * fm
            val l = len * (0.6f + 0.6f * (if (i % 2 == 0) 1f else 0.55f))
            p.moveTo(x0, y0); p.lineTo(xm, ym + dirY * l); p.lineTo(x1, y1); p.close()
        }
        fill(p, col)
        line(p, Color(0xFF3A2A22).copy(alpha = 0.6f), 0.7f)
    }

    /** Curved horn from [base] growing [dir] (-1 left / +1 right) up and out. */
    private fun horn(base: Offset, dir: Float, len: Float, thick: Float, curl: Float = 0.5f) {
        val p = path {
            moveTo(base.x - thick * dir * 0.5f, base.y)
            cubicTo(base.x + dir * len * 0.1f, base.y - len * 0.5f, base.x + dir * len * curl, base.y - len * 0.8f, base.x + dir * len * (curl + 0.25f), base.y - len)
            cubicTo(base.x + dir * len * (curl - 0.05f), base.y - len * 0.55f, base.x + dir * len * 0.3f, base.y - len * 0.25f, base.x + thick * dir * 0.5f, base.y)
            close()
        }
        fill(p, Brush.linearGradient(listOf(SceneKit.mix(light, Color.White, 0.2f), SceneKit.mix(mid, Color.Black, 0.3f)), start = base, end = Offset(base.x + dir * len, base.y - len)))
        line(p, dark, 1.2f)
    }

    private fun hornsAround(head: Offset, headR: Float) {
        val pairs = hornCount / 2
        for (i in 0 until pairs) {
            val spread = 0.5f + i * 0.35f
            val len = headR * (0.9f + 0.45f * fear + 0.2f * (pairs - i))
            for (d in listOf(-1f, 1f)) horn(Offset(head.x + d * headR * (0.35f + i * 0.28f), head.y - headR * (0.7f - i * 0.15f)), d, len, headR * 0.28f, spread)
        }
    }

    private fun spikeRow(from: Offset, to: Offset, ctrl: Offset, count: Int, len: Float) {
        val p = Path()
        for (i in 0 until count) {
            val f = (i + 0.5f) / count
            val x = (1 - f) * (1 - f) * from.x + 2 * f * (1 - f) * ctrl.x + f * f * to.x
            val y = (1 - f) * (1 - f) * from.y + 2 * f * (1 - f) * ctrl.y + f * f * to.y
            val nx = (y - ctrl.y); val ny = -(x - ctrl.x)
            val n = kotlin.math.sqrt(nx * nx + ny * ny).coerceAtLeast(1f)
            val l = len * (0.7f + 0.6f * ((i * 7 % 5) / 4f))
            val w = len * 0.22f
            p.moveTo(x - w, y); p.lineTo(x + nx / n * l * 0.15f, y - l); p.lineTo(x + w, y); p.close()
        }
        fill(p, Brush.verticalGradient(listOf(light, dark), startY = from.y - len, endY = from.y + len))
        line(p, dark, 1f)
    }

    private fun glowCracks(area: Rect, count: Int) {
        val p = Path()
        repeat(count) {
            var x = area.left + r.nextFloat() * area.width; var y = area.top + r.nextFloat() * area.height
            p.moveTo(x, y)
            repeat(4) { x += (r.nextFloat() - 0.5f) * area.width * 0.3f; y += r.nextFloat() * area.height * 0.18f; p.lineTo(x.coerceIn(area.left, area.right), y.coerceAtMost(area.bottom)) }
        }
        ops += { t ->
            val a = 0.5f + 0.5f * pulse(t, 0.4f)
            drawPath(p, accent.copy(alpha = 0.3f * a), style = Stroke(5f, cap = StrokeCap.Round))
            drawPath(p, SceneKit.mix(accent, Color.White, 0.5f).copy(alpha = 0.9f * a), style = Stroke(1.6f, cap = StrokeCap.Round))
        }
    }

    // ------------------------------------------------------------------ aura (behind) and wisps (in front)
    private fun buildAura() {
        val strength = ((fear - 0.15f) * 1.1f + (if (boss) 0.2f else 0f)).coerceIn(0f, 1f)
        if (strength <= 0.05f) return
        val c = Offset(100f, 115f)
        val rad = 105f + 25f * strength
        val br = Brush.radialGradient(0.2f to accent.copy(alpha = 0.35f * strength), 0.65f to accent.copy(alpha = 0.12f * strength), 1f to Color.Transparent, center = c, radius = rad)
        back += { t -> drawCircle(br, rad * (1f + 0.04f * sin(t * 2f)), c) }
        val wisps = (strength * 6).toInt() + (if (boss) 2 else 0)
        val wispBrush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), accent.copy(alpha = 0.5f), Color.Transparent), center = Offset.Zero, radius = 7f)
        repeat(wisps) { i ->
            val phase = i * 2f * PI.toFloat() / wisps; val orbit = 70f + (i % 3) * 14f
            front += { t ->
                val a = phase + t * (0.5f + 0.15f * (i % 3)) * (if (i % 2 == 0) 1f else -1f)
                val o = Offset(100f + cos(a) * orbit, 115f + sin(a) * orbit * 0.55f - 20f)
                val vis = if (sin(a) > 0f) 1f else 0.5f
                withTransform({ translate(o.x, o.y) }) { drawCircle(wispBrush, 7f, Offset.Zero, alpha = vis) }
            }
        }
    }

    /** Boss only: halo ring behind the head and a jagged crown, sized by fear. */
    private fun bossCrest() {
        val c = Offset(100f, if (body == Body.GOLEM) 62f else if (body == Body.SERPENT) 60f else 70f)
        val ringR = 34f + 14f * fear
        back += { t ->
            drawCircle(accent.copy(alpha = 0.45f), ringR, c, style = Stroke(3f))
            drawCircle(accent.copy(alpha = 0.18f), ringR + 6f, c, style = Stroke(9f))
            val n = 8 + (fear * 8).toInt()
            for (i in 0 until n) {
                val a = i * 2f * PI.toFloat() / n + t * 0.15f
                drawLine(accent.copy(alpha = 0.55f), Offset(c.x + cos(a) * ringR, c.y + sin(a) * ringR), Offset(c.x + cos(a) * (ringR + 10f), c.y + sin(a) * (ringR + 10f)), 2f)
            }
        }
    }

    // ------------------------------------------------------------------ BLOB
    private fun blob() {
        val bodyP = path {
            moveTo(24f, 198f)
            cubicTo(8f, 150f, 36f, 84f, 100f, 74f)
            cubicTo(164f, 84f, 192f, 150f, 176f, 198f)
            quadraticTo(150f, 205f, 100f, 202f); quadraticTo(52f, 205f, 24f, 198f); close()
        }
        // pseudopod spikes at fear
        if (fear > 0.25f) spikeRow(Offset(36f, 118f), Offset(164f, 118f), Offset(100f, 40f), 5 + (fear * 8).toInt(), 18f + 22f * fear)
        piece(bodyP, 40f, 70f, 170f, 200f)
        // translucent core
        val core = Offset(100f, 150f)
        ops += { t -> drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.7f + 0.2f * pulse(t, 0.4f)), accent.copy(alpha = 0.1f), Color.Transparent), center = core, radius = 38f), 38f, core) }
        if (fear > 0.45f) { // skull swallowed inside
            fill(path { addOval(Rect(84f, 138f, 116f, 166f)); addRect(Rect(90f, 162f, 110f, 174f)) }, Color.Black.copy(alpha = 0.45f))
            fill(path { addOval(Rect(89f, 148f, 97f, 156f)); addOval(Rect(103f, 148f, 111f, 156f)) }, accent.copy(alpha = 0.8f))
        }
        fill(path { addOval(Rect(52f, 90f, 100f, 112f)) }, Color.White.copy(alpha = 0.22f))
        repeat(6) { val x = 40f + r.nextFloat() * 120f; val y = 120f + r.nextFloat() * 70f; val rr = 3f + r.nextFloat() * 6f; fill(path { addOval(Rect(x - rr, y - rr, x + rr, y + rr)) }, Color.White.copy(alpha = 0.18f)) }
        if (crack) glowCracks(Rect(40f, 100f, 160f, 190f), 2 + (fear * 4).toInt())
        val ex = listOf(Offset(78f, 122f), Offset(122f, 122f)) + List(extraEyes) { Offset(60f + 80f * (it + 1) / (extraEyes + 1), 100f + (it % 2) * 8f) }
        eyes(ex, 8f - extraEyes * 0.5f)
        val a = Offset(62f, 152f); val b = Offset(138f, 152f)
        fill(path { moveTo(a.x, a.y); quadraticTo(100f, 190f, b.x, b.y); quadraticTo(100f, 160f, a.x, a.y); close() }, Color(0xFF1A0A0E))
        teeth(Offset(66f, 156f), Offset(134f, 156f), fangCount, 8f + 8f * fear, 1f)
    }

    // ------------------------------------------------------------------ HOUND
    private fun hound() {
        val legL = path { poly(50f, 128f, 80f, 128f, 78f, 190f, 84f, 198f, 44f, 198f, 52f, 188f) }
        val legR = path { poly(120f, 128f, 150f, 128f, 148f, 188f, 156f, 198f, 116f, 198f, 122f, 190f) }
        piece(legL, 40f, 128f, 90f, 198f); piece(legR, 110f, 128f, 160f, 198f)
        line(path { for (x in listOf(50f, 58f, 66f)) { moveTo(x, 198f); lineTo(x - 3f, 207f) }; for (x in listOf(124f, 132f, 140f)) { moveTo(x, 198f); lineTo(x - 3f, 207f) } }, Color(0xFFEDE3CC), 2.4f)
        val chest = path { moveTo(30f, 150f); cubicTo(20f, 96f, 60f, 70f, 100f, 70f); cubicTo(140f, 70f, 180f, 96f, 170f, 150f); cubicTo(150f, 170f, 50f, 170f, 30f, 150f); close() }
        if (fear > 0.2f) spikeRow(Offset(34f, 110f), Offset(166f, 110f), Offset(100f, 30f), 6 + (fear * 8).toInt(), 16f + 22f * fear)
        piece(chest, 30f, 70f, 170f, 170f)
        val fur = MonsterPainters.furStrokes(zone.order, Rect(34f, 76f, 166f, 160f), 70, 12f, 90f, 40f)
        line(fur, dark.copy(alpha = 0.55f), 1.2f)
        // head
        val head = Offset(100f, 108f)
        val ears = path { poly(66f, 76f, 56f, 36f, 88f, 62f); poly(134f, 76f, 144f, 36f, 112f, 62f) }
        piece(ears, 50f, 36f, 140f, 76f)
        val skull = path { moveTo(64f, 96f); cubicTo(64f, 66f, 136f, 66f, 136f, 96f); cubicTo(142f, 120f, 124f, 146f, 100f, 148f); cubicTo(76f, 146f, 58f, 120f, 64f, 96f); close() }
        piece(skull, 64f, 66f, 140f, 148f)
        val muzzle = path { moveTo(76f, 112f); cubicTo(80f, 100f, 120f, 100f, 124f, 112f); cubicTo(130f, 134f, 118f, 150f, 100f, 150f); cubicTo(82f, 150f, 70f, 134f, 76f, 112f); close() }
        fill(muzzle, Brush.verticalGradient(listOf(SceneKit.mix(light, mid, 0.5f), dark), 100f, 150f))
        fill(path { addOval(Rect(92f, 108f, 108f, 120f)) }, Color(0xFF120A0C))
        fill(path { moveTo(78f, 130f); quadraticTo(100f, 150f, 122f, 130f); quadraticTo(100f, 138f, 78f, 130f); close() }, Color(0xFF220C10))
        teeth(Offset(80f, 131f), Offset(120f, 131f), fangCount.coerceAtMost(8), 7f + 9f * fear, 1f)
        teeth(Offset(86f, 140f), Offset(114f, 140f), 4, 6f + 4f * fear, -1f)
        hornsAround(Offset(100f, 82f), 20f)
        val e = listOf(Offset(82f, 94f), Offset(118f, 94f)) + List(extraEyes.coerceAtMost(4)) { Offset(86f + 28f * (it % 2), 80f - 6f * (it / 2)) }
        eyes(e, 6f - extraEyes * 0.3f)
        if (plates) {
            val pad = path { poly(26f, 120f, 40f, 84f, 66f, 100f, 56f, 132f); poly(174f, 120f, 160f, 84f, 134f, 100f, 144f, 132f) }
            piece(pad, 26f, 84f, 66f, 132f)
            spikeRow(Offset(28f, 112f), Offset(62f, 96f), Offset(40f, 80f), 3, 14f); spikeRow(Offset(172f, 112f), Offset(138f, 96f), Offset(160f, 80f), 3, 14f)
        }
        if (crack) glowCracks(Rect(44f, 110f, 156f, 160f), 2 + (fear * 3).toInt())
    }

    // ------------------------------------------------------------------ HUMANOID
    private fun humanoid() {
        if (boss) { // cape
            val cape = path { moveTo(52f, 80f); cubicTo(0f, 130f, 6f, 180f, -4f, 200f); lineTo(204f, 200f); cubicTo(194f, 180f, 200f, 130f, 148f, 80f); close() }
            back += { drawPath(cape, Brush.verticalGradient(listOf(dark, Color.Black), 80f, 200f)); drawPath(cape, accent.copy(alpha = 0.35f), style = Stroke(2f)) }
        }
        val legs = path { poly(66f, 140f, 96f, 140f, 94f, 192f, 104f, 198f, 58f, 198f, 66f, 190f); poly(104f, 140f, 134f, 140f, 134f, 190f, 142f, 198f, 96f, 198f, 106f, 192f) }
        piece(legs, 58f, 140f, 140f, 198f)
        val torso = path { moveTo(56f, 82f); lineTo(144f, 82f); lineTo(136f, 148f); lineTo(64f, 148f); close() }
        piece(torso, 56f, 82f, 140f, 148f)
        fill(path { poly(64f, 128f, 136f, 128f, 134f, 138f, 66f, 138f) }, dark) // belt
        if (plates) { // chest plate with ribs
            line(path { for (i in 0..3) { moveTo(76f, 96f + i * 9f); quadraticTo(100f, 104f + i * 9f, 124f, 96f + i * 9f) } }, light.copy(alpha = 0.8f), 3f)
        }
        // arms with claws
        val armL = path { moveTo(56f, 84f); cubicTo(30f, 100f, 28f, 134f, 34f, 160f); lineTo(48f, 160f); cubicTo(48f, 134f, 54f, 112f, 70f, 98f); close() }
        val armR = path { moveTo(144f, 84f); cubicTo(170f, 100f, 172f, 134f, 166f, 160f); lineTo(152f, 160f); cubicTo(152f, 134f, 146f, 112f, 130f, 98f); close() }
        piece(armL, 28f, 84f, 70f, 160f); piece(armR, 130f, 84f, 172f, 160f)
        line(path { for (d in listOf(-1f, 1f)) { val bx = if (d < 0) 41f else 159f; for (k in -1..1) { moveTo(bx + k * 5f, 160f); lineTo(bx + k * 7f, 176f + 4f * (1 - kotlin.math.abs(k))) } } }, Color(0xFFEDE3CC), 2.6f)
        // pauldrons
        val padL = path { poly(48f, 90f, 54f, 70f, 80f, 78f, 76f, 100f) }; val padR = path { poly(152f, 90f, 146f, 70f, 120f, 78f, 124f, 100f) }
        piece(padL, 48f, 70f, 80f, 100f); piece(padR, 120f, 70f, 152f, 100f)
        if (plates) { spikeRow(Offset(48f, 90f), Offset(76f, 74f), Offset(50f, 66f), 2 + (fear * 2).toInt(), 12f + 8f * fear); spikeRow(Offset(152f, 90f), Offset(124f, 74f), Offset(150f, 66f), 2 + (fear * 2).toInt(), 12f + 8f * fear) }
        // weapon
        if (fear > 0.45f || boss) {
            val len = 60f + 40f * fear
            val blade = path { poly(160f, 164f - len, 170f, 156f - len, 168f, 168f, 162f, 172f) }
            piece(blade, 158f, 100f, 172f, 170f, 1f)
            fill(path { poly(150f, 168f, 178f, 168f, 176f, 174f, 152f, 174f) }, dark)
        }
        // head
        val head = Offset(100f, 58f)
        val hood = path { moveTo(68f, 84f); cubicTo(60f, 40f, 82f, 24f, 100f, 24f); cubicTo(118f, 24f, 140f, 40f, 132f, 84f); quadraticTo(100f, 68f, 68f, 84f); close() }
        piece(hood, 62f, 24f, 138f, 84f)
        fill(path { moveTo(78f, 76f); cubicTo(74f, 52f, 86f, 40f, 100f, 40f); cubicTo(114f, 40f, 126f, 52f, 122f, 76f); quadraticTo(100f, 86f, 78f, 76f); close() }, Color(0xFF0C0608))
        // skull-like face glimmer
        fill(path { addOval(Rect(88f, 52f, 112f, 74f)) }, light.copy(alpha = 0.18f))
        teeth(Offset(86f, 70f), Offset(114f, 70f), fangCount.coerceAtMost(7), 5f + 6f * fear, 1f)
        teeth(Offset(88f, 74f), Offset(112f, 74f), 5, 4f + 3f * fear, -1f)
        hornsAround(Offset(100f, 46f), 18f)
        val e = listOf(Offset(90f, 58f), Offset(110f, 58f)) + List(extraEyes.coerceAtMost(4)) { Offset(92f + 16f * (it % 2), 48f - 6f * (it / 2)) }
        eyes(e, 4.4f - extraEyes * 0.25f)
        if (crack) glowCracks(Rect(66f, 90f, 134f, 138f), 2 + (fear * 3).toInt())
    }

    // ------------------------------------------------------------------ INSECT
    private fun insect() {
        val legs = Path(); val legsHi = Path()
        for (i in 0 until 4) for (d in listOf(-1f, 1f)) {
            val ox = 100f + d * (20f + i * 8f); val oy = 128f + i * 4f
            val kx = 100f + d * (58f + i * 9f); val ky = 62f + i * 22f
            val fx = 100f + d * (66f + i * 13f); val fy = 198f
            legs.moveTo(ox, oy); legs.cubicTo(ox + d * 24f, oy - 40f, kx - d * 10f, ky - 10f, kx, ky); legs.cubicTo(kx + d * 10f, ky + 40f, fx, fy - 60f, fx, fy)
            legsHi.moveTo(ox, oy - 1f); legsHi.cubicTo(ox + d * 24f, oy - 41f, kx - d * 10f, ky - 11f, kx, ky - 1f)
        }
        ops += { drawPath(legs, dark, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round)); drawPath(legs, mid, style = Stroke(4.5f, cap = StrokeCap.Round)); drawPath(legsHi, light.copy(alpha = 0.7f), style = Stroke(1.6f, cap = StrokeCap.Round)) }
        // stinger tail arching over the back
        val tail = path { moveTo(100f, 150f); cubicTo(40f, 150f, 28f, 60f, 92f, 36f) }
        ops += { drawPath(tail, dark, style = Stroke(14f, cap = StrokeCap.Round)); drawPath(tail, mid, style = Stroke(9f, cap = StrokeCap.Round)) }
        fill(path { poly(86f, 36f, 100f, 20f, 112f, 38f, 100f, 40f) }, accent); line(path { poly(86f, 36f, 100f, 20f, 112f, 38f) }, dark, 1.2f)
        val abd = path { addOval(Rect(52f, 100f, 148f, 184f)) }
        piece(abd, 60f, 100f, 144f, 184f)
        line(path { for (i in 1..4) { moveTo(60f + i * 3f, 108f + i * 14f); quadraticTo(100f, 116f + i * 14f, 140f - i * 3f, 108f + i * 14f) } }, dark.copy(alpha = 0.7f), 1.6f)
        if (fear > 0.2f) spikeRow(Offset(56f, 130f), Offset(144f, 130f), Offset(100f, 70f), 4 + (fear * 7).toInt(), 12f + 16f * fear)
        val thorax = path { addOval(Rect(72f, 92f, 128f, 138f)) }
        piece(thorax, 76f, 92f, 126f, 138f)
        val headP = path { moveTo(70f, 112f); cubicTo(70f, 80f, 130f, 80f, 130f, 112f); cubicTo(130f, 132f, 70f, 132f, 70f, 112f); close() }
        piece(headP, 70f, 84f, 130f, 130f)
        // mandibles
        for (d in listOf(-1f, 1f)) {
            val m = path { moveTo(100f + d * 6f, 124f); cubicTo(100f + d * 34f, 126f, 100f + d * 36f, 150f, 100f + d * 14f, 160f); cubicTo(100f + d * 26f, 146f, 100f + d * 22f, 134f, 100f + d * 4f, 132f); close() }
            piece(m, 100f, 124f, 100f + d * 30f, 160f, 1f)
        }
        teeth(Offset(92f, 126f), Offset(108f, 126f), 5, 5f + 5f * fear, 1f)
        val eyeN = 2 + extraEyes + (if (fear > 0.5f) 2 else 0)
        val big = listOf(Offset(86f, 106f), Offset(114f, 106f))
        val small = List(eyeN - 2) { Offset(80f + 40f * (it + 1) / (eyeN - 1), 94f + (it % 2) * 6f) }
        eyes(big, 7f); eyes(small, 3.2f, slit = false)
        if (crack) glowCracks(Rect(62f, 108f, 138f, 178f), 2 + (fear * 3).toInt())
    }

    // ------------------------------------------------------------------ WINGED
    private fun winged() {
        for (d in listOf(-1f, 1f)) {
            val sx = 100f + d * 18f; val sy = 100f
            val tipX = 100f + d * 106f; val tipY = 14f - 20f * fear
            val membrane = path {
                moveTo(sx, sy)
                cubicTo(sx + d * 40f, sy - 50f, tipX - d * 40f, tipY + 6f, tipX, tipY)
                val fingers = 3 + (fear * 2).toInt()
                for (i in 1..fingers) { val f = i / fingers.toFloat(); val fx = tipX - d * (tipX - sx) * f * 0.85f * (0.9f); val fy = tipY + (sy + 70f - tipY) * f; quadraticTo((fx + (tipX - d * (tipX - sx) * (f - 1f / fingers) * 0.85f)) / 2f, fy - 22f, fx, fy) }
                lineTo(sx, sy + 40f); close()
            }
            val bones = path { moveTo(sx, sy); cubicTo(sx + d * 40f, sy - 50f, tipX - d * 40f, tipY + 6f, tipX, tipY); for (i in 1..3) { moveTo(sx, sy); lineTo(tipX - d * (tipX - sx) * i * 0.22f, tipY + (sy + 70f - tipY) * i * 0.3f) } }
            val pivot = Offset(sx, sy)
            ops += { t ->
                withTransform({ rotate(d * -5f * sin(t * 2.4f), pivot) }) {
                    drawPath(membrane, Brush.linearGradient(listOf(SceneKit.mix(mid, accent, 0.25f), dark), start = Offset(sx, sy - 60f), end = Offset(tipX, sy + 70f)))
                    drawPath(membrane, dark, style = Stroke(1.6f))
                    drawPath(bones, light.copy(alpha = 0.85f), style = Stroke(3.2f, cap = StrokeCap.Round))
                }
            }
        }
        val legs = path { poly(76f, 150f, 92f, 150f, 90f, 192f, 98f, 198f, 66f, 198f, 74f, 190f); poly(108f, 150f, 124f, 150f, 126f, 190f, 134f, 198f, 102f, 198f, 110f, 192f) }
        piece(legs, 66f, 150f, 134f, 198f)
        val torso = path { moveTo(68f, 100f); cubicTo(60f, 130f, 70f, 160f, 100f, 164f); cubicTo(130f, 160f, 140f, 130f, 132f, 100f); cubicTo(120f, 90f, 80f, 90f, 68f, 100f); close() }
        piece(torso, 66f, 90f, 134f, 164f)
        line(path { for (i in 0..3) { moveTo(82f, 112f + i * 11f); quadraticTo(100f, 120f + i * 11f, 118f, 112f + i * 11f) } }, dark.copy(alpha = 0.7f), 1.6f)
        if (plates) spikeRow(Offset(74f, 100f), Offset(126f, 100f), Offset(100f, 82f), 5, 12f + 10f * fear)
        val head = path { moveTo(78f, 84f); cubicTo(76f, 48f, 124f, 48f, 122f, 84f); cubicTo(122f, 104f, 112f, 112f, 100f, 112f); cubicTo(88f, 112f, 78f, 104f, 78f, 84f); close() }
        piece(head, 76f, 48f, 124f, 112f)
        fill(path { moveTo(86f, 98f); quadraticTo(100f, 116f, 114f, 98f); quadraticTo(100f, 104f, 86f, 98f); close() }, Color(0xFF220C10))
        teeth(Offset(88f, 100f), Offset(112f, 100f), fangCount.coerceAtMost(7), 6f + 8f * fear, 1f)
        hornsAround(Offset(100f, 58f), 20f)
        val e = listOf(Offset(90f, 80f), Offset(110f, 80f)) + List(extraEyes.coerceAtMost(4)) { Offset(92f + 16f * (it % 2), 68f - 5f * (it / 2)) }
        eyes(e, 5f - extraEyes * 0.25f)
        if (crack) glowCracks(Rect(72f, 106f, 128f, 158f), 2 + (fear * 3).toInt())
    }

    // ------------------------------------------------------------------ SERPENT
    private fun serpent() {
        val coil = path { addOval(Rect(30f, 158f, 170f, 200f)); }
        piece(coil, 30f, 158f, 170f, 200f)
        ops += { drawOval(light.copy(alpha = 0.25f), Offset(50f, 164f), androidx.compose.ui.geometry.Size(60f, 10f)) }
        val heads = if (boss) (1 + (fear * 4).toInt() * 1).coerceAtMost(5).let { if (it % 2 == 0) it + 1 else it } else 1
        val spots = listOf(Offset(100f, 58f), Offset(52f, 78f), Offset(148f, 78f), Offset(22f, 110f), Offset(178f, 110f))
        val order = listOf(0, 1, 2, 3, 4).take(heads)
        order.forEach { idx ->
            val h = spots[idx]; val sx = 100f + (h.x - 100f) * 0.4f
            val neck = path { moveTo(sx, 176f); cubicTo(sx - (h.x - 100f) * 0.9f - 30f, 150f, h.x + (h.x - 100f) * 0.8f + 30f * (if (h.x >= 100f) -1f else 1f), 130f, h.x, h.y + 16f) }
            ops += {
                drawPath(neck, dark, style = Stroke(26f, cap = StrokeCap.Round))
                drawPath(neck, mid, style = Stroke(21f, cap = StrokeCap.Round))
                drawPath(neck, light.copy(alpha = 0.45f), style = Stroke(5f, cap = StrokeCap.Round))
            }
            val scales = path { for (k in 1..7) { val f = k / 8f; val x = sx + (h.x - sx) * f; val y = 176f + (h.y + 16f - 176f) * f; moveTo(x - 8f, y); quadraticTo(x, y + 6f, x + 8f, y) } }
            line(scales, dark.copy(alpha = 0.7f), 1.4f)
            val headP = path { moveTo(h.x - 20f, h.y + 6f); cubicTo(h.x - 22f, h.y - 22f, h.x + 22f, h.y - 22f, h.x + 20f, h.y + 6f); cubicTo(h.x + 16f, h.y + 22f, h.x - 16f, h.y + 22f, h.x - 20f, h.y + 6f); close() }
            piece(headP, h.x - 20f, h.y - 20f, h.x + 20f, h.y + 22f)
            fill(path { moveTo(h.x - 12f, h.y + 10f); quadraticTo(h.x, h.y + 24f, h.x + 12f, h.y + 10f); quadraticTo(h.x, h.y + 14f, h.x - 12f, h.y + 10f); close() }, Color(0xFF220C10))
            teeth(Offset(h.x - 11f, h.y + 11f), Offset(h.x + 11f, h.y + 11f), 5, 4f + 7f * fear, 1f)
            ops += { t -> val f = pulse(t, 1.2f, idx.toFloat()); drawLine(Color(0xFFC0304A), Offset(h.x, h.y + 20f), Offset(h.x + (f - 0.5f) * 8f, h.y + 30f + 6f * f), 1.8f, StrokeCap.Round) }
            hornsAround(Offset(h.x, h.y - 6f), 11f)
            eyes(listOf(Offset(h.x - 9f, h.y - 2f), Offset(h.x + 9f, h.y - 2f)), 3.8f)
        }
        if (heads == 1 && extraEyes > 0) eyes(List(extraEyes.coerceAtMost(4)) { Offset(94f + 12f * (it % 2), 44f - 4f * (it / 2)) }, 2.4f)
        if (crack) glowCracks(Rect(40f, 162f, 160f, 196f), 2 + (fear * 3).toInt())
    }

    // ------------------------------------------------------------------ WRAITH
    private fun wraith() {
        val robe = path {
            moveTo(100f, 20f); cubicTo(70f, 22f, 56f, 56f, 52f, 96f); cubicTo(46f, 140f, 34f, 170f, 26f, 196f)
            var x = 26f
            val n = 9
            for (i in 1..n) { val nx = 26f + 148f * i / n; quadraticTo((x + nx) / 2f, if (i % 2 == 0) 206f else 184f, nx, 196f - (i % 3) * 6f); x = nx }
            cubicTo(166f, 170f, 154f, 140f, 148f, 96f); cubicTo(144f, 56f, 130f, 22f, 100f, 20f); close()
        }
        ops += { t ->
            withTransform({ translate(0f, -5f * sin(t * 1.6f)) }) {
                drawPath(robe, Brush.verticalGradient(0f to mid, 0.55f to dark, 1f to dark.copy(alpha = 0.0f), startY = 20f, endY = 204f))
                drawPath(robe, accent.copy(alpha = 0.3f), style = Stroke(1.8f))
            }
        }
        // inner folds
        line(path { for (i in 0..4) { moveTo(70f + i * 15f, 110f); quadraticTo(66f + i * 17f, 150f, 60f + i * 20f, 190f) } }, Color.Black.copy(alpha = 0.35f), 1.6f)
        val faceVoid = path { moveTo(76f, 78f); cubicTo(72f, 40f, 128f, 40f, 124f, 78f); cubicTo(122f, 96f, 110f, 104f, 100f, 104f); cubicTo(90f, 104f, 78f, 96f, 76f, 78f); close() }
        fill(faceVoid, Color(0xFF05030A))
        // skeletal hands reaching forward
        for (d in listOf(-1f, 1f)) {
            val hx = 100f + d * 52f; val hy = 148f
            val arm = path { moveTo(100f + d * 30f, 110f); quadraticTo(100f + d * 56f, 120f, hx, hy) }
            line(arm, light.copy(alpha = 0.85f), 4f)
            val fingers = path { for (k in -2..2) { moveTo(hx, hy); quadraticTo(hx + k * 6f, hy + 14f, hx + k * 8f + d * 4f, hy + 30f + (2 - kotlin.math.abs(k)) * 3f) } }
            line(fingers, light.copy(alpha = 0.9f), 2.2f)
        }
        if (fear > 0.4f) { // faint faces in the robe and hanging chains
            repeat(3) { i -> val c = Offset(78f + i * 22f, 150f + (i % 2) * 14f); fill(path { addOval(Rect(c.x - 7f, c.y - 8f, c.x + 7f, c.y + 8f)) }, Color.Black.copy(alpha = 0.35f)); fill(path { addOval(Rect(c.x - 4f, c.y - 3f, c.x - 1f, c.y)); addOval(Rect(c.x + 1f, c.y - 3f, c.x + 4f, c.y)) }, accent.copy(alpha = 0.7f)) }
            line(path { moveTo(60f, 120f); for (i in 1..6) lineTo(58f + (i % 2) * 6f, 120f + i * 11f) }, dark.copy(alpha = 0.9f), 3f)
        }
        teeth(Offset(88f, 94f), Offset(112f, 94f), fangCount.coerceAtMost(8), 4f + 6f * fear, -1f)
        hornsAround(Offset(100f, 34f), 16f)
        val e = listOf(Offset(90f, 74f), Offset(110f, 74f)) + List(extraEyes.coerceAtMost(4)) { Offset(94f + 12f * (it % 2), 62f - 6f * (it / 2)) }
        eyes(e, 4.6f - extraEyes * 0.25f, slit = false)
    }

    // ------------------------------------------------------------------ GOLEM
    private fun golem() {
        val legs = path { poly(60f, 144f, 96f, 144f, 98f, 198f, 54f, 198f); poly(104f, 144f, 140f, 144f, 146f, 198f, 102f, 198f) }
        piece(legs, 54f, 144f, 146f, 198f)
        val torso = path { poly(46f, 84f, 154f, 84f, 142f, 150f, 58f, 150f) }
        piece(torso, 46f, 84f, 154f, 150f)
        line(path { poly(58f, 108f, 142f, 112f, close = false); poly(66f, 130f, 134f, 128f, close = false) }, dark.copy(alpha = 0.8f), 2f)
        val armL = path { poly(14f, 88f, 50f, 84f, 52f, 150f, 36f, 176f, 10f, 172f, 8f, 120f) }
        val armR = path { poly(186f, 88f, 150f, 84f, 148f, 150f, 164f, 176f, 190f, 172f, 192f, 120f) }
        piece(armL, 8f, 84f, 52f, 176f); piece(armR, 148f, 84f, 192f, 176f)
        if (plates) {
            spikeRow(Offset(16f, 92f), Offset(48f, 86f), Offset(30f, 60f), 3, 14f + 12f * fear); spikeRow(Offset(184f, 92f), Offset(152f, 86f), Offset(170f, 60f), 3, 14f + 12f * fear)
        }
        val head = path { poly(80f, 46f, 120f, 46f, 126f, 82f, 74f, 82f) }
        piece(head, 74f, 46f, 126f, 82f)
        fill(path { poly(82f, 60f, 118f, 60f, 116f, 68f, 84f, 68f) }, Color(0xFF05030A))
        hornsAround(Offset(100f, 50f), 18f)
        if (fear > 0.25f) spikeRow(Offset(48f, 86f), Offset(152f, 86f), Offset(100f, 62f), 5 + (fear * 5).toInt(), 12f + 16f * fear)
        teeth(Offset(86f, 74f), Offset(114f, 74f), 6, 3f + 4f * fear, 1f)
        eyes(listOf(Offset(92f, 64f), Offset(108f, 64f)) + List(extraEyes.coerceAtMost(2)) { Offset(100f, 52f - it * 6f) }, 3.6f, slit = false)
        if (crack) glowCracks(Rect(50f, 90f, 150f, 150f), 3 + (fear * 4).toInt())
        // floating chunks
        if (fear > 0.5f) repeat(2 + (fear * 3).toInt()) { i ->
            val ph = i * 1.9f; val chunk = path { poly(-6f, -4f, 6f, -6f, 8f, 4f, -4f, 7f) }
            front += { t -> withTransform({ translate(100f + cos(t * 0.7f + ph) * (92f + i * 4f), 70f + sin(t * 0.7f + ph) * 24f + i * 16f); rotate(t * 20f + i * 40f, Offset.Zero) }) { drawPath(chunk, skin(-6f, -6f, 8f, 7f)); drawPath(chunk, dark, style = Stroke(1f)) } }
        }
    }
}
