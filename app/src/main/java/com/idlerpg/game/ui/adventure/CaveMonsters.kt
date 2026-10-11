package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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
import kotlin.math.sin

/** Polygon path from flat x,y pairs. */
internal fun poly(vararg xy: Float): Path = Path().apply {
    moveTo(xy[0], xy[1])
    var i = 2
    while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }
    close()
}

// ==================================================================== Stein-/Kristallgolem
internal object GolemPainter {
    private class Palette(val light: Color, val mid: Color, val dark: Color, val rune: Color, val eye: Color, val moss: Color?)

    private val stone = Palette(Color(0xFFB8B0A0), Color(0xFF7A7264), Color(0xFF3A342C), Color(0xFFFF9A2A), Color(0xFFFFC04A), Color(0xFF6E8A3A))
    private val crystal = Palette(Color(0xFF7A7690), Color(0xFF46425A), Color(0xFF1C1A26), Color(0xFF5AE8FF), Color(0xFF9AF6FF), null)

    // torso chunks (facets), legs, arms, head — in unit space
    private val torso = poly(52f, 70f, 148f, 70f, 162f, 96f, 154f, 140f, 132f, 156f, 68f, 156f, 46f, 140f, 38f, 96f)
    private val torsoFacetL = poly(52f, 70f, 100f, 74f, 92f, 118f, 46f, 140f, 38f, 96f)
    private val torsoFacetR = poly(148f, 70f, 162f, 96f, 154f, 140f, 108f, 118f, 100f, 74f)
    private val torsoFacetB = poly(46f, 140f, 92f, 118f, 108f, 118f, 154f, 140f, 132f, 156f, 68f, 156f)
    private val pelvis = poly(64f, 150f, 136f, 150f, 130f, 168f, 70f, 168f)
    private val leg = poly(66f, 162f, 94f, 162f, 96f, 198f, 60f, 198f)
    private val shoulder = poly(20f, 64f, 56f, 58f, 64f, 84f, 40f, 100f, 16f, 90f)
    private val upperArm = poly(18f, 90f, 42f, 96f, 38f, 136f, 14f, 132f)
    private val fist = poly(6f, 134f, 44f, 132f, 50f, 160f, 40f, 178f, 10f, 176f, 2f, 156f)
    private val head = poly(78f, 38f, 122f, 38f, 128f, 54f, 120f, 74f, 80f, 74f, 72f, 54f)
    private val cracks = Path().apply {
        moveTo(70f, 80f); lineTo(80f, 96f); lineTo(76f, 110f)
        moveTo(130f, 84f); lineTo(122f, 100f); lineTo(128f, 116f)
        moveTo(100f, 126f); lineTo(94f, 140f); lineTo(100f, 152f)
        moveTo(26f, 104f); lineTo(32f, 120f)
        moveTo(22f, 146f); lineTo(30f, 160f)
    }
    private val rune = Path().apply {
        // glowing chest rune
        moveTo(100f, 88f); lineTo(100f, 112f)
        moveTo(90f, 94f); lineTo(100f, 100f); lineTo(110f, 94f)
        moveTo(92f, 112f); lineTo(108f, 112f)
    }
    private val mossSpots = listOf(Rect(52f, 64f, 84f, 76f), Rect(124f, 62f, 154f, 74f), Rect(18f, 58f, 50f, 70f), Rect(70f, 152f, 96f, 162f))

    private fun crystalCluster(base: Offset, scale: Float, tilt: Float, c1: Color, c2: Color): List<Pair<Path, Brush>> {
        val list = ArrayList<Pair<Path, Brush>>()
        listOf(-16f to 1f, 0f to 1.35f, 14f to 0.9f).forEach { (a, s) ->
            val d = dir(-90f + a + tilt)
            val n = Offset(-d.y, d.x)
            val len = 26f * s * scale
            val wdt = 5f * scale
            val tip = base + d * len
            val sh = base + d * (len * 0.75f)
            val p = Path().apply {
                moveTo(base.x + n.x * wdt, base.y + n.y * wdt)
                lineTo(sh.x + n.x * wdt, sh.y + n.y * wdt)
                lineTo(tip.x, tip.y)
                lineTo(sh.x - n.x * wdt, sh.y - n.y * wdt)
                lineTo(base.x - n.x * wdt, base.y - n.y * wdt)
                close()
            }
            list += p to Brush.linearGradient(listOf(c2, c1, SceneKit.mix(c1, Color.Black, 0.5f)), base + n * wdt, base - n * wdt)
        }
        return list
    }
    private val crystals: List<Pair<Path, Brush>> =
        crystalCluster(Offset(36f, 62f), 1.1f, -25f, Color(0xFF8A4AD0), Color(0xFFE8C8FF)) +
            crystalCluster(Offset(164f, 62f), 1.1f, 25f, Color(0xFF2AA8C8), Color(0xFFC8F8FF)) +
            crystalCluster(Offset(100f, 40f), 0.8f, 0f, Color(0xFF2AA8C8), Color(0xFFC8F8FF)) +
            crystalCluster(Offset(60f, 72f), 0.6f, -10f, Color(0xFF2AA8C8), Color(0xFFC8F8FF)) +
            crystalCluster(Offset(140f, 72f), 0.6f, 12f, Color(0xFF8A4AD0), Color(0xFFE8C8FF))

    private fun DrawScope.chunk(p: Path, pal: Palette, light: Offset, r: Float) {
        drawPath(p, Brush.radialGradient(listOf(pal.light, pal.mid, pal.dark), center = light, radius = r))
        drawPath(p, pal.dark, style = Stroke(1.4f, join = StrokeJoin.Round))
    }

    fun draw(scope: DrawScope, time: Float, crystal: Boolean) = with(scope) {
        val pal = if (crystal) this@GolemPainter.crystal else stone
        val glow = 0.65f + 0.35f * sin(time * PI.toFloat() * 0.8f)
        val sway = sin(time * PI.toFloat() * 0.5f) * 2f
        symmetric { chunk(leg, pal, Offset(72f, 168f), 40f) }
        chunk(pelvis, pal, Offset(90f, 152f), 50f)
        // arms (slow pendulum)
        symmetric {
            withTransform({ rotate(sway, Offset(36f, 76f)) }) {
                chunk(upperArm, pal, Offset(22f, 96f), 40f)
                chunk(fist, pal, Offset(14f, 140f), 44f)
                drawLine(pal.dark, Offset(14f, 150f), Offset(44f, 148f), 1.2f)
                drawLine(pal.dark, Offset(12f, 162f), Offset(46f, 160f), 1.2f)
            }
            chunk(shoulder, pal, Offset(26f, 64f), 44f)
        }
        // torso facets for a chiselled look
        drawPath(torso, pal.mid)
        drawPath(torsoFacetL, Brush.radialGradient(listOf(pal.light, pal.mid), center = Offset(60f, 80f), radius = 70f))
        drawPath(torsoFacetR, Brush.radialGradient(listOf(pal.mid, pal.dark), center = Offset(130f, 90f), radius = 70f))
        drawPath(torsoFacetB, Brush.verticalGradient(listOf(pal.mid, pal.dark), startY = 118f, endY = 156f))
        listOf(torsoFacetL, torsoFacetR, torsoFacetB).forEach { drawPath(it, pal.dark.copy(alpha = 0.6f), style = Stroke(1f)) }
        drawPath(torso, pal.dark, style = Stroke(1.6f, join = StrokeJoin.Round))
        drawPath(cracks, pal.dark.copy(alpha = 0.8f), style = Stroke(1.2f, cap = StrokeCap.Round))
        // glowing rune
        drawPath(rune, pal.rune.copy(alpha = 0.35f * glow), style = Stroke(7f, cap = StrokeCap.Round), blendMode = BlendMode.Screen)
        drawPath(rune, SceneKit.mix(pal.rune, Color.White, 0.4f).copy(alpha = glow), style = Stroke(2f, cap = StrokeCap.Round))
        pal.moss?.let { moss ->
            mossSpots.forEach { drawOval(Brush.radialGradient(listOf(moss, moss.copy(alpha = 0f)), center = it.center, radius = it.width / 2f), it.topLeft, it.size) }
        }
        // head
        chunk(head, pal, Offset(92f, 44f), 40f)
        val brow = poly(74f, 50f, 126f, 50f, 122f, 58f, 78f, 58f)
        drawPath(brow, pal.dark.copy(alpha = 0.7f))
        val closed = blink(time, 6.1f, 1.1f)
        symmetric {
            val eye = Rect(82f, 58f, 96f, 64f - 4f * closed)
            drawOval(Brush.radialGradient(listOf(pal.eye.copy(alpha = 0.6f * glow), Color.Transparent), eye.center, 14f), Offset(eye.center.x - 14f, eye.center.y - 14f), Size(28f, 28f))
            drawRect(Color.White, eye.topLeft, eye.size)
            drawRect(pal.eye, eye.topLeft + Offset(1f, 1f), Size(eye.width - 2f, (eye.height - 2f).coerceAtLeast(0f)))
        }
        drawLine(pal.dark, Offset(90f, 70f), Offset(110f, 70f), 2f, StrokeCap.Round)
        if (crystal) crystals.forEach { (p, b) ->
            drawPath(p, b)
            drawPath(p, Color.White.copy(alpha = 0.5f), style = Stroke(0.8f))
        }
        if (crystal) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8AF0FF).copy(alpha = 0.25f * glow), Color.Transparent), Offset(100f, 60f), 90f), 90f, Offset(100f, 60f), blendMode = BlendMode.Screen)
        }
    }
}

// ==================================================================== Riesenfledermaus
internal object BatPainter {
    private val wing = Path().apply {
        // left wing: from the shoulder out over three finger bones, scalloped trailing edge
        moveTo(88f, 92f)
        cubicTo(70f, 70f, 40f, 54f, 6f, 50f)   // leading edge to the wing tip
        quadraticTo(10f, 80f, 2f, 104f)       // tip down
        quadraticTo(20f, 98f, 28f, 120f)       // scallop 1
        quadraticTo(44f, 108f, 56f, 128f)      // scallop 2
        quadraticTo(70f, 116f, 86f, 130f)      // scallop 3
        close()
    }
    private val bones = Path().apply {
        moveTo(88f, 92f); lineTo(48f, 62f); lineTo(6f, 50f)
        moveTo(48f, 62f); lineTo(2f, 104f)
        moveTo(48f, 62f); lineTo(28f, 120f)
        moveTo(48f, 62f); lineTo(56f, 128f)
    }
    private val body = Path().apply {
        moveTo(100f, 70f)
        cubicTo(122f, 70f, 128f, 100f, 124f, 124f)
        cubicTo(120f, 148f, 110f, 156f, 100f, 156f)
        cubicTo(90f, 156f, 80f, 148f, 76f, 124f)
        cubicTo(72f, 100f, 78f, 70f, 100f, 70f)
        close()
    }
    private val ear = Path().apply { moveTo(84f, 58f); lineTo(70f, 14f); quadraticTo(90f, 30f, 96f, 52f); close() }
    private val earInner = Path().apply { moveTo(85f, 54f); lineTo(76f, 26f); quadraticTo(88f, 36f, 92f, 52f); close() }
    private val head = Path().apply { addOval(Rect(76f, 44f, 124f, 92f)) }
    private val fur = furStrokes(61, Rect(78f, 72f, 122f, 152f), 70, 6f, 90f, 40f)

    fun draw(scope: DrawScope, time: Float) = with(scope) {
        val flap = sin(time * PI.toFloat() * 3f)
        val hover = -40f + sin(time * PI.toFloat() * 3f + 1f) * 5f
        withTransform({ translate(0f, hover) }) {
            symmetric {
                withTransform({ scale(1f, 1f - 0.28f * flap, Offset(88f, 92f)); rotate(-10f * flap, Offset(88f, 92f)) }) {
                    drawPath(wing, Brush.radialGradient(listOf(Color(0xFF6A4A5A), Color(0xFF3A2430), Color(0xFF1A0E14)), center = Offset(60f, 80f), radius = 80f))
                    clipPath(wing) {
                        // veins in the membrane
                        drawPath(bones, Color(0xFFA07888).copy(alpha = 0.25f), style = Stroke(5f, cap = StrokeCap.Round))
                    }
                    drawPath(bones, Color(0xFF241018), style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(wing, Color(0xFF0E0608), style = Stroke(1.3f, join = StrokeJoin.Round))
                    drawCircle(Color(0xFF2A1820), 2.6f, Offset(48f, 62f))
                    // thumb claw
                    drawPath(poly(48f, 62f, 44f, 54f, 50f, 58f), Color(0xFFE8DCC8))
                }
            }
            // body + feet
            drawPath(body, Brush.radialGradient(listOf(Color(0xFF8A6A6A), Color(0xFF4A3036), Color(0xFF1E1014)), center = Offset(94f, 96f), radius = 60f))
            clipPath(body) { drawPath(fur, Color(0xFF140A0E).copy(alpha = 0.5f), style = Stroke(1f, cap = StrokeCap.Round)) }
            drawPath(body, Color(0xFF0E0608), style = Stroke(1.3f))
            symmetric {
                drawLine(Color(0xFF2A1820), Offset(92f, 154f), Offset(88f, 166f), 3f, StrokeCap.Round)
                for (k in -1..1) drawLine(Color(0xFFE8DCC8), Offset(88f, 166f), Offset(86f + k * 3f, 171f), 1.2f, StrokeCap.Round)
            }
            // ears + head
            symmetric {
                drawPath(ear, Brush.verticalGradient(listOf(Color(0xFF2A1820), Color(0xFF5A3A44)), startY = 14f, endY = 58f))
                drawPath(earInner, Brush.verticalGradient(listOf(Color(0xFF6A3A44), Color(0xFFC08890)), startY = 26f, endY = 54f))
                drawPath(ear, Color(0xFF0E0608), style = Stroke(1.2f, join = StrokeJoin.Round))
            }
            drawPath(head, Brush.radialGradient(listOf(Color(0xFF9A7A7A), Color(0xFF5A3A40), Color(0xFF241418)), center = Offset(94f, 60f), radius = 34f))
            drawPath(head, Color(0xFF0E0608), style = Stroke(1.3f))
            // pug nose
            val nose = Path().apply { moveTo(92f, 74f); quadraticTo(100f, 64f, 108f, 74f); quadraticTo(100f, 80f, 92f, 74f); close() }
            drawPath(nose, Color(0xFFB07880))
            drawOval(Color(0xFF2A1218), Offset(95f, 72f), Size(3f, 3f))
            drawOval(Color(0xFF2A1218), Offset(102f, 72f), Size(3f, 3f))
            // glowing eyes
            val closed = blink(time, 4.4f, 2.7f)
            symmetric {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF3020).copy(alpha = 0.5f), Color.Transparent), Offset(88f, 62f), 12f), 12f, Offset(88f, 62f))
                withTransform({ scale(1f, 1f - 0.9f * closed, Offset(88f, 62f)) }) {
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD0A0), Color(0xFFE02010), Color(0xFF600000)), center = Offset(87f, 61f), radius = 6f), 5f, Offset(88f, 62f))
                    drawCircle(Color.White, 1.3f, Offset(86.5f, 60.5f))
                }
            }
            // open mouth with fangs
            val mouth = Path().apply { moveTo(90f, 82f); quadraticTo(100f, 80f, 110f, 82f); quadraticTo(100f, 94f, 90f, 82f); close() }
            drawPath(mouth, Color(0xFF3A0A10))
            symmetric { drawPath(poly(92f, 82f, 96f, 82f, 94f, 90f), Color.White) }
        }
    }
}
