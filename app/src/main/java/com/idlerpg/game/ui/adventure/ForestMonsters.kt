package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import com.idlerpg.game.ui.adventure.MonsterPainters.blink
import com.idlerpg.game.ui.adventure.MonsterPainters.furStrokes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Draws [block] once normally and once mirrored at x = 100 (symmetric monster parts). */
internal inline fun DrawScope.symmetric(block: DrawScope.() -> Unit) {
    block()
    withTransform({ scale(-1f, 1f, Offset(100f, 0f)) }) { block() }
}

/** Jagged fur outline: points between [a] and [b] with spikes of [depth] pointing to [outward] side. */
internal fun Path.furEdge(a: Offset, b: Offset, spikes: Int, depth: Float, r: Random) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val nx = dy / len
    val ny = -dx / len
    for (i in 1..spikes) {
        val t0 = (i - 0.5f) / spikes
        val t1 = i / spikes.toFloat()
        val d = depth * (0.6f + r.nextFloat() * 0.6f)
        lineTo(a.x + dx * t0 + nx * d, a.y + dy * t0 + ny * d)
        lineTo(a.x + dx * t1, a.y + dy * t1)
    }
}

// ==================================================================== Wolf / Schneewolf
internal object WolfPainter {
    private class Palette(
        val furLight: Color, val furMid: Color, val furDark: Color, val muzzle: Color,
        val eyeIn: Color, val eyeOut: Color, val strokeDark: Color,
    )

    private val grey = Palette(
        Color(0xFFB8B4AE), Color(0xFF6E6A66), Color(0xFF2E2C2A), Color(0xFFD8D2C8),
        Color(0xFFFFE27A), Color(0xFFD08A10), Color(0xFF141210),
    )
    private val snow = Palette(
        Color(0xFFFFFFFF), Color(0xFFC8D6E4), Color(0xFF6E86A4), Color(0xFFFFFFFF),
        Color(0xFFE6FBFF), Color(0xFF3AA6E0), Color(0xFF2A3A52),
    )

    private val haunch = Path().apply {
        moveTo(38f, 180f)
        cubicTo(30f, 130f, 60f, 100f, 100f, 100f)
        cubicTo(140f, 100f, 170f, 130f, 162f, 180f)
        quadraticTo(100f, 196f, 38f, 180f)
        close()
    }
    private val leg = Path().apply {
        moveTo(66f, 128f)
        cubicTo(64f, 150f, 70f, 170f, 70f, 188f)
        lineTo(90f, 188f)
        cubicTo(90f, 168f, 94f, 148f, 92f, 128f)
        close()
    }
    private val ruff: Path = run {
        val r = Random(4)
        Path().apply {
            moveTo(34f, 92f)
            furEdge(Offset(34f, 92f), Offset(60f, 150f), 6, 9f, r)
            furEdge(Offset(60f, 150f), Offset(100f, 178f), 6, 9f, r)
            furEdge(Offset(100f, 178f), Offset(140f, 150f), 6, 9f, r)
            furEdge(Offset(140f, 150f), Offset(166f, 92f), 6, 9f, r)
            lineTo(100f, 70f)
            close()
        }
    }
    private val head: Path = run {
        val r = Random(7)
        Path().apply {
            moveTo(56f, 52f)
            cubicTo(70f, 40f, 130f, 40f, 144f, 52f)
            lineTo(150f, 70f)
            furEdge(Offset(150f, 70f), Offset(166f, 100f), 4, 7f, r)
            furEdge(Offset(166f, 100f), Offset(132f, 126f), 4, 6f, r)
            quadraticTo(116f, 142f, 100f, 144f)
            quadraticTo(84f, 142f, 68f, 126f)
            furEdge(Offset(68f, 126f), Offset(34f, 100f), 4, 6f, r)
            furEdge(Offset(34f, 100f), Offset(50f, 70f), 4, 7f, r)
            close()
        }
    }
    private val ear = Path().apply {
        moveTo(56f, 58f); lineTo(46f, 8f); quadraticTo(70f, 22f, 86f, 46f); close()
    }
    private val earInner = Path().apply {
        moveTo(60f, 54f); lineTo(52f, 20f); quadraticTo(68f, 30f, 78f, 46f); close()
    }
    private val muzzle = Path().apply {
        moveTo(80f, 82f)
        cubicTo(82f, 100f, 76f, 118f, 84f, 132f)
        quadraticTo(100f, 142f, 116f, 132f)
        cubicTo(124f, 118f, 118f, 100f, 120f, 82f)
        quadraticTo(100f, 76f, 80f, 82f)
        close()
    }
    private val nose = Path().apply {
        moveTo(88f, 112f)
        cubicTo(88f, 104f, 112f, 104f, 112f, 112f)
        cubicTo(112f, 120f, 104f, 124f, 100f, 124f)
        cubicTo(96f, 124f, 88f, 120f, 88f, 112f)
        close()
    }
    private val mouth = Path().apply {
        moveTo(82f, 128f)
        quadraticTo(100f, 124f, 118f, 128f)
        quadraticTo(112f, 146f, 100f, 147f)
        quadraticTo(88f, 146f, 82f, 128f)
        close()
    }
    private val fang = Path().apply { moveTo(86f, 128f); lineTo(90f, 141f); lineTo(93f, 128f); close() }
    private val eye = Path().apply {
        moveTo(66f, 76f); quadraticTo(76f, 66f, 90f, 74f); quadraticTo(80f, 84f, 66f, 76f); close()
    }
    private val headFur = furStrokes(31, Rect(48f, 46f, 152f, 120f), 90, 6f, 90f, 60f)
    private val ruffFur = furStrokes(32, Rect(40f, 100f, 160f, 172f), 130, 9f, 95f, 30f)

    fun draw(scope: DrawScope, time: Float, snow: Boolean) = with(scope) {
        val p = if (snow) this@WolfPainter.snow else grey
        // haunches + legs
        drawPath(haunch, Brush.radialGradient(listOf(p.furMid, p.furDark), center = Offset(100f, 120f), radius = 80f))
        symmetric {
            drawPath(leg, Brush.horizontalGradient(listOf(p.furDark, p.furMid, p.furDark), startX = 64f, endX = 94f))
            drawPath(leg, p.strokeDark.copy(alpha = 0.6f), style = Stroke(1.2f))
            drawOval(Brush.radialGradient(listOf(p.furLight, p.furMid), center = Offset(80f, 186f), radius = 14f), Offset(66f, 182f), Size(28f, 14f))
            for (k in 0..2) drawLine(p.strokeDark.copy(alpha = 0.6f), Offset(73f + k * 7f, 186f), Offset(73f + k * 7f, 195f), 1.2f)
        }
        // chest ruff
        drawPath(ruff, Brush.radialGradient(listOf(p.furLight, p.furMid, p.furDark), center = Offset(100f, 120f), radius = 80f))
        clipPath(ruff) { drawPath(ruffFur, p.strokeDark.copy(alpha = 0.35f), style = Stroke(1.1f, cap = StrokeCap.Round)) }
        // ears
        symmetric {
            drawPath(ear, Brush.verticalGradient(listOf(p.furDark, p.furMid), startY = 8f, endY = 58f))
            drawPath(earInner, Brush.verticalGradient(listOf(Color(0xFF3A2A2A), Color(0xFFA07878)), startY = 20f, endY = 54f))
            drawPath(ear, p.strokeDark.copy(alpha = 0.7f), style = Stroke(1.3f, join = StrokeJoin.Round))
        }
        // head
        drawPath(head, Brush.radialGradient(listOf(p.furLight, p.furMid, p.furDark), center = Offset(100f, 70f), radius = 80f))
        clipPath(head) {
            drawPath(headFur, p.strokeDark.copy(alpha = 0.35f), style = Stroke(1f, cap = StrokeCap.Round))
            // darker saddle on the forehead
            drawOval(Brush.radialGradient(listOf(p.furDark.copy(alpha = 0.55f), Color.Transparent), center = Offset(100f, 48f), radius = 40f), Offset(60f, 30f), Size(80f, 40f))
        }
        drawPath(muzzle, Brush.verticalGradient(listOf(p.muzzle, SceneKit.mix(p.muzzle, p.furMid, 0.5f)), startY = 80f, endY = 140f))
        drawPath(head, p.strokeDark.copy(alpha = 0.6f), style = Stroke(1.4f, join = StrokeJoin.Round))

        // snarling mouth
        drawPath(mouth, Brush.verticalGradient(listOf(Color(0xFF3A0A0C), Color(0xFF8A2028)), startY = 126f, endY = 147f))
        symmetric { drawPath(fang, Color(0xFFF8F2E0)); drawPath(fang, Color(0xFF8A7A5A), style = Stroke(0.6f)) }
        drawPath(mouth, p.strokeDark, style = Stroke(1.4f, join = StrokeJoin.Round))
        // snout wrinkles
        symmetric { drawLine(p.strokeDark.copy(alpha = 0.5f), Offset(84f, 98f), Offset(92f, 101f), 1.2f, StrokeCap.Round) }
        drawPath(nose, Brush.radialGradient(listOf(Color(0xFF5A5A5A), Color(0xFF0A0A0A)), center = Offset(96f, 108f), radius = 14f))
        drawOval(Color.White.copy(alpha = 0.5f), Offset(93f, 107f), Size(7f, 3f))

        // eyes
        val closed = blink(time, 4.7f, 0.9f)
        symmetric {
            drawCircle(Brush.radialGradient(listOf(p.eyeOut.copy(alpha = 0.35f), Color.Transparent), Offset(78f, 75f), 16f), 16f, Offset(78f, 75f))
            withTransform({ scale(1f, 1f - 0.9f * closed, Offset(78f, 75f)) }) {
                drawPath(eye, Brush.radialGradient(listOf(p.eyeIn, p.eyeOut), center = Offset(78f, 75f), radius = 10f))
                drawOval(Color.Black, Offset(76f, 70f), Size(4f, 10f))
                drawCircle(Color.White.copy(alpha = 0.9f), 1.4f, Offset(74f, 73f))
                drawPath(eye, p.strokeDark, style = Stroke(1.4f))
            }
            // brow
            drawLine(p.strokeDark, Offset(62f, 66f), Offset(90f, 70f), 3f, StrokeCap.Round)
        }
        if (snow) {
            // frosty breath
            val puff = (time * 0.5f) % 1f
            if (puff < 0.6f) {
                val a = (0.6f - puff) * 0.6f
                val c = Offset(100f, 150f + puff * 20f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFE6F6FF).copy(alpha = a), Color.Transparent), c, 10f + puff * 24f), 10f + puff * 24f, c)
            }
        }
    }
}

// ==================================================================== Spider (Wald / Höhle)
internal object SpiderPainter {
    private class Palette(
        val bodyLight: Color, val bodyMid: Color, val bodyDark: Color, val mark: Color,
        val eye: Color, val eyeGlow: Color, val leg: Color, val legDark: Color,
    )

    private val forest = Palette(
        Color(0xFF8A6A4A), Color(0xFF4A3220), Color(0xFF1A100A), Color(0xFFE08A2A),
        Color(0xFFE02A20), Color(0xFFFF4A30), Color(0xFF5A3E28), Color(0xFF1E140C),
    )
    private val cave = Palette(
        Color(0xFFB8B0CC), Color(0xFF5E5476), Color(0xFF1E1A2A), Color(0xFF6AF0FF),
        Color(0xFF8AF8FF), Color(0xFF30E0FF), Color(0xFF6E6688), Color(0xFF221E30),
    )

    /** Leg segments (left side): hip on body, knee high, foot on the ground. */
    private val legs = listOf(
        Triple(Offset(84f, 120f), Offset(40f, 70f), Offset(18f, 150f)),
        Triple(Offset(84f, 128f), Offset(30f, 100f), Offset(8f, 190f)),
        Triple(Offset(86f, 136f), Offset(46f, 130f), Offset(30f, 196f)),
        Triple(Offset(90f, 142f), Offset(64f, 150f), Offset(56f, 197f)),
    )
    private val abdomen = Path().apply { addOval(Rect(46f, 30f, 154f, 128f)) }
    private val thorax = Path().apply {
        moveTo(100f, 104f)
        cubicTo(128f, 104f, 136f, 128f, 130f, 148f)
        cubicTo(124f, 166f, 76f, 166f, 70f, 148f)
        cubicTo(64f, 128f, 72f, 104f, 100f, 104f)
        close()
    }
    private val fang = Path().apply {
        moveTo(90f, 156f); cubicTo(84f, 166f, 86f, 176f, 94f, 180f); cubicTo(92f, 172f, 94f, 166f, 98f, 160f); close()
    }
    private val marking = Path().apply {
        // hourglass-like pattern on the abdomen
        moveTo(100f, 42f)
        cubicTo(116f, 52f, 112f, 70f, 104f, 78f)
        cubicTo(114f, 88f, 118f, 104f, 100f, 116f)
        cubicTo(82f, 104f, 86f, 88f, 96f, 78f)
        cubicTo(88f, 70f, 84f, 52f, 100f, 42f)
        close()
    }
    private val hairs = furStrokes(41, Rect(48f, 34f, 152f, 124f), 140, 6f, -90f, 80f)

    private fun legPath(hip: Offset, knee: Offset, foot: Offset): Pair<Path, Path> {
        val upper = Path().apply { moveTo(hip.x, hip.y); quadraticTo((hip.x + knee.x) / 2f, knee.y - 8f, knee.x, knee.y) }
        val lower = Path().apply { moveTo(knee.x, knee.y); quadraticTo(knee.x - 6f, (knee.y + foot.y) / 2f, foot.x, foot.y) }
        return upper to lower
    }

    fun draw(scope: DrawScope, time: Float, cave: Boolean) = with(scope) {
        val p = if (cave) this@SpiderPainter.cave else forest
        val legScale = if (cave) 1.08f else 1f
        // legs (behind), slight idle twitch
        symmetric {
            legs.forEachIndexed { i, (hip, knee0, foot0) ->
                val tw = sin(time * 4f + i * 1.3f) * 2f
                val knee = Offset(100f + (knee0.x - 100f) * legScale, knee0.y - (legScale - 1f) * 40f + tw)
                val foot = Offset(100f + (foot0.x - 100f) * legScale, foot0.y)
                val (u, l) = legPath(hip, knee, foot)
                drawPath(u, p.legDark, style = Stroke(9f, cap = StrokeCap.Round))
                drawPath(u, p.leg, style = Stroke(5.5f, cap = StrokeCap.Round))
                drawPath(l, p.legDark, style = Stroke(6.5f, cap = StrokeCap.Round))
                drawPath(l, p.leg, style = Stroke(3.5f, cap = StrokeCap.Round))
                drawCircle(p.legDark, 5f, knee)
                drawCircle(p.leg.copy(alpha = 0.9f), 2.5f, knee - Offset(1f, 1f))
                // bristles along the lower leg
                for (k in 1..4) {
                    val t = k / 5f
                    val bx = knee.x + (foot.x - knee.x) * t
                    val by = knee.y + (foot.y - knee.y) * t
                    drawLine(p.legDark, Offset(bx, by), Offset(bx - 4f, by - 3f), 0.9f, StrokeCap.Round)
                }
                if (cave && i % 2 == 0) drawCircle(p.mark.copy(alpha = 0.6f), 1.8f, knee)
            }
        }
        // abdomen
        drawPath(abdomen, Brush.radialGradient(listOf(p.bodyLight, p.bodyMid, p.bodyDark), center = Offset(88f, 60f), radius = 80f))
        clipPath(abdomen) {
            drawPath(hairs, p.bodyDark.copy(alpha = 0.5f), style = Stroke(1f, cap = StrokeCap.Round))
            if (cave) {
                val glow = 0.6f + 0.4f * sin(time * PI.toFloat())
                listOf(Offset(76f, 60f), Offset(124f, 60f), Offset(84f, 96f), Offset(116f, 96f), Offset(100f, 50f)).forEach {
                    drawCircle(Brush.radialGradient(listOf(p.mark.copy(alpha = 0.8f * glow), Color.Transparent), it, 10f), 10f, it)
                    drawCircle(Color.White.copy(alpha = 0.8f * glow), 2f, it)
                }
            } else {
                drawPath(marking, Brush.verticalGradient(listOf(Color(0xFFFFB050), p.mark, Color(0xFFA0501A)), startY = 42f, endY = 116f))
                drawPath(marking, p.bodyDark.copy(alpha = 0.6f), style = Stroke(1.2f))
                listOf(Offset(70f, 70f), Offset(130f, 70f)).forEach { drawCircle(p.mark.copy(alpha = 0.8f), 4.5f, it) }
            }
            drawOval(Color.White.copy(alpha = 0.18f), Offset(62f, 40f), Size(40f, 22f))
        }
        drawPath(abdomen, p.bodyDark, style = Stroke(1.5f))
        // thorax/head
        drawPath(thorax, Brush.radialGradient(listOf(p.bodyLight, p.bodyMid, p.bodyDark), center = Offset(94f, 120f), radius = 44f))
        drawPath(thorax, p.bodyDark, style = Stroke(1.4f))
        // fangs (chelicerae)
        val chew = sin(time * 6f) * 1.5f
        withTransform({ rotate(chew, Offset(94f, 158f)) }) {
            drawPath(fang, Brush.verticalGradient(listOf(p.bodyMid, Color(0xFF0A0606)), startY = 156f, endY = 180f))
        }
        withTransform({ scale(-1f, 1f, Offset(100f, 0f)); rotate(chew, Offset(94f, 158f)) }) {
            drawPath(fang, Brush.verticalGradient(listOf(p.bodyMid, Color(0xFF0A0606)), startY = 156f, endY = 180f))
        }
        // eyes: 2 big, 4 small (+2 tiny)
        val big = listOf(Offset(91f, 128f), Offset(109f, 128f))
        val small = listOf(Offset(80f, 120f), Offset(120f, 120f), Offset(86f, 140f), Offset(114f, 140f), Offset(95f, 116f), Offset(105f, 116f))
        (big + small).forEach { c ->
            val rr = if (c in big) 7f else if (c.y == 116f) 2.6f else 4f
            drawCircle(Brush.radialGradient(listOf(p.eyeGlow.copy(alpha = 0.45f), Color.Transparent), c, rr * 2.4f), rr * 2.4f, c)
            drawCircle(Brush.radialGradient(listOf(p.eye, SceneKit.mix(p.eye, Color.Black, 0.7f)), center = c - Offset(rr * 0.3f, rr * 0.3f), radius = rr * 1.2f), rr, c)
            drawCircle(Color.White.copy(alpha = 0.9f), rr * 0.32f, c - Offset(rr * 0.35f, rr * 0.35f))
        }
    }
}

// ==================================================================== Pilzling
internal object MushroomPainter {
    private val cap = Path().apply {
        moveTo(16f, 92f)
        cubicTo(14f, 40f, 56f, 12f, 100f, 12f)
        cubicTo(144f, 12f, 186f, 40f, 184f, 92f)
        cubicTo(170f, 104f, 30f, 104f, 16f, 92f)
        close()
    }
    private val capBrush = Brush.radialGradient(
        0f to Color(0xFFFF8A6A), 0.35f to Color(0xFFE0301E), 0.8f to Color(0xFFA0180E), 1f to Color(0xFF5E0C06),
        center = Offset(76f, 34f), radius = 130f,
    )
    private val gills = Rect(30f, 82f, 170f, 106f)
    private val stem = Path().apply {
        moveTo(58f, 96f)
        cubicTo(48f, 130f, 48f, 168f, 60f, 186f)
        quadraticTo(100f, 198f, 140f, 186f)
        cubicTo(152f, 168f, 152f, 130f, 142f, 96f)
        close()
    }
    private val stemBrush = Brush.horizontalGradient(
        0f to Color(0xFFB8A888), 0.3f to Color(0xFFF8F0DC), 0.6f to Color(0xFFE8DCC0), 1f to Color(0xFF9A8A6A),
        startX = 50f, endX = 150f,
    )
    private val spots = listOf(
        Triple(60f, 40f, 11f), Triple(100f, 26f, 9f), Triple(140f, 42f, 12f), Triple(36f, 72f, 8f),
        Triple(164f, 72f, 8f), Triple(84f, 62f, 7f), Triple(120f, 66f, 8f), Triple(102f, 48f, 5f),
    )
    private val arm = Path().apply {
        moveTo(54f, 132f); cubicTo(40f, 134f, 30f, 146f, 28f, 160f); lineTo(36f, 162f); cubicTo(40f, 150f, 48f, 144f, 56f, 144f); close()
    }
    private val foot = Rect(60f, 182f, 92f, 198f)

    fun draw(scope: DrawScope, time: Float) = with(scope) {
        val wave = sin(time * 3f) * 4f
        symmetric {
            drawOval(Brush.radialGradient(listOf(Color(0xFFE8DCC0), Color(0xFF8A7A5A)), center = Offset(76f, 188f), radius = 18f), foot.topLeft, foot.size)
        }
        drawPath(stem, stemBrush)
        clipPath(stem) {
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF3A2A18).copy(alpha = 0.55f), Color.Transparent), startY = 96f, endY = 130f),
                topLeft = Offset(40f, 96f), size = Size(120f, 40f),
            )
            // moss at the base
            drawOval(Brush.radialGradient(listOf(Color(0xFF6E9A3A).copy(alpha = 0.8f), Color.Transparent), center = Offset(100f, 190f), radius = 40f), Offset(50f, 170f), Size(100f, 40f))
        }
        drawPath(stem, Color(0xFF5E4E32).copy(alpha = 0.6f), style = Stroke(1.4f))
        // twig arms
        withTransform({ rotate(wave, Offset(54f, 138f)) }) {
            drawPath(arm, Brush.linearGradient(listOf(Color(0xFFD8C8A8), Color(0xFF8A7A5A)), Offset(54f, 132f), Offset(28f, 160f)))
            drawPath(arm, Color(0xFF5E4E32), style = Stroke(1f))
        }
        withTransform({ scale(-1f, 1f, Offset(100f, 0f)); rotate(-wave, Offset(54f, 138f)) }) {
            drawPath(arm, Brush.linearGradient(listOf(Color(0xFFD8C8A8), Color(0xFF8A7A5A)), Offset(54f, 132f), Offset(28f, 160f)))
            drawPath(arm, Color(0xFF5E4E32), style = Stroke(1f))
        }
        // gills under the cap
        drawOval(Brush.verticalGradient(listOf(Color(0xFFE8D8B8), Color(0xFF9A8460)), startY = gills.top, endY = gills.bottom), gills.topLeft, gills.size)
        clipPath(Path().apply { addOval(gills) }) {
            for (k in 0..26) {
                val x = gills.left + k * (gills.width / 26f)
                drawLine(Color(0xFF7A6448).copy(alpha = 0.6f), Offset(100f, gills.top - 6f), Offset(x, gills.bottom), 0.9f)
            }
        }
        drawPath(cap, capBrush)
        clipPath(cap) {
            spots.forEach { (x, y, r) ->
                drawOval(Brush.radialGradient(listOf(Color.White, Color(0xFFF0E6D8)), center = Offset(x - r * 0.3f, y - r * 0.3f), radius = r * 1.2f), Offset(x - r, y - r * 0.8f), Size(r * 2f, r * 1.6f))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF3A0602).copy(alpha = 0.5f)), startY = 70f, endY = 100f), topLeft = Offset(0f, 70f), size = Size(200f, 32f))
        }
        drawOval(Color.White.copy(alpha = 0.35f), Offset(52f, 22f), Size(40f, 14f))
        drawPath(cap, Color(0xFF4A0804).copy(alpha = 0.8f), style = Stroke(1.6f))

        // face on the stem
        val closed = blink(time, 3.9f, 2.2f)
        symmetric {
            drawLine(Color(0xFF3A2A18), Offset(72f, 120f), Offset(92f, 128f), 3f, StrokeCap.Round)
            withTransform({ scale(1f, 1f - 0.9f * closed, Offset(84f, 136f)) }) {
                drawOval(Color(0xFF1A120A), Offset(76f, 128f), Size(15f, 16f))
                drawCircle(Color(0xFFFFE070), 3.4f, Offset(84f, 137f))
                drawCircle(Color.White, 1.4f, Offset(82f, 134f))
            }
        }
        val mouth = Path().apply { moveTo(88f, 160f); quadraticTo(100f, 152f, 112f, 160f); quadraticTo(100f, 166f, 88f, 160f); close() }
        drawPath(mouth, Color(0xFF2A1A0E))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE88A7A).copy(alpha = 0.35f), Color.Transparent), Offset(70f, 150f), 10f), 10f, Offset(70f, 150f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE88A7A).copy(alpha = 0.35f), Color.Transparent), Offset(130f, 150f), 10f), 10f, Offset(130f, 150f))
    }
}

/** Unit vector helper for angle in degrees. */
internal fun dir(deg: Float) = Offset(cos(deg * PI.toFloat() / 180f), sin(deg * PI.toFloat() / 180f))
