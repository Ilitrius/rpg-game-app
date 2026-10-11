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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ==================================================================== Giftschleim
internal object ToxicSlimePainter {
    private val body = Path().apply {
        moveTo(18f, 190f)
        cubicTo(6f, 160f, 20f, 120f, 40f, 104f)
        cubicTo(36f, 80f, 56f, 60f, 74f, 66f)
        cubicTo(80f, 40f, 120f, 36f, 128f, 62f)
        cubicTo(148f, 56f, 168f, 80f, 160f, 104f)
        cubicTo(184f, 124f, 196f, 162f, 184f, 190f)
        // drips along the bottom
        cubicTo(176f, 198f, 168f, 196f, 160f, 192f)
        cubicTo(156f, 202f, 146f, 202f, 142f, 194f)
        cubicTo(110f, 200f, 90f, 200f, 62f, 194f)
        cubicTo(58f, 204f, 46f, 204f, 42f, 194f)
        cubicTo(32f, 198f, 24f, 196f, 18f, 190f)
        close()
    }
    private val bodyBrush = Brush.radialGradient(
        0f to Color(0xFFE6A8FF), 0.3f to Color(0xFFA858D8), 0.7f to Color(0xFF6A2A9A), 1f to Color(0xFF2E0A4A),
        center = Offset(84f, 84f), radius = 130f,
    )
    private val core = Brush.radialGradient(
        0f to Color(0xFFB8FF3A).copy(alpha = 0.75f), 0.5f to Color(0xFF7AE02A).copy(alpha = 0.35f), 1f to Color.Transparent,
        center = Offset(100f, 156f), radius = 56f,
    )
    private val skull = Path().apply {
        moveTo(100f, 140f)
        cubicTo(116f, 140f, 122f, 154f, 116f, 164f)
        lineTo(112f, 172f); lineTo(88f, 172f); lineTo(84f, 164f)
        cubicTo(78f, 154f, 84f, 140f, 100f, 140f)
        close()
    }
    private val drips = listOf(Triple(40f, 194f, 0f), Triple(150f, 196f, 0.4f), Triple(96f, 198f, 0.7f))

    fun draw(scope: DrawScope, time: Float) = with(scope) {
        drawOval(
            Brush.radialGradient(listOf(Color(0xFF9AE02A).copy(alpha = 0.55f), Color(0xFF5A9A1A).copy(alpha = 0.25f), Color.Transparent), center = Offset(100f, 196f), radius = 96f),
            Offset(4f, 186f), Size(192f, 20f),
        )
        drawPath(body, bodyBrush)
        clipPath(body) {
            drawCircle(core, 56f, Offset(100f, 156f))
            // a drowned skull in the goo
            drawPath(skull, Color(0xFFE8E0D0).copy(alpha = 0.35f))
            drawOval(Color(0xFF3A0A4A).copy(alpha = 0.5f), Offset(89f, 150f), Size(8f, 8f))
            drawOval(Color(0xFF3A0A4A).copy(alpha = 0.5f), Offset(103f, 150f), Size(8f, 8f))
            // rising bubbles
            for (i in 0 until 7) {
                val ph = ((time * 0.35f + i * 0.143f) % 1f)
                val x = 40f + i * 20f + sin(time + i) * 4f
                val y = 190f - ph * 130f
                val r = 3f + (i % 3) * 1.8f
                drawCircle(Color(0xFFD8FF8A).copy(alpha = 0.25f * (1f - ph)), r, Offset(x, y))
                drawCircle(Color(0xFFEFFFC8).copy(alpha = 0.7f * (1f - ph)), r, Offset(x, y), style = Stroke(0.9f))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF1A0428).copy(alpha = 0.55f)), startY = 120f, endY = 200f), topLeft = Offset(0f, 120f), size = Size(200f, 82f))
        }
        drawPath(body, Brush.linearGradient(listOf(Color.Transparent, Color(0xFFF4D8FF).copy(alpha = 0.8f)), Offset(60f, 190f), Offset(150f, 60f)), style = Stroke(3f))
        drawPath(body, Color(0xFF240640).copy(alpha = 0.7f), style = Stroke(1.4f))
        // highlights
        drawOval(Color.White.copy(alpha = 0.55f), Offset(84f, 50f), Size(22f, 9f))
        drawCircle(Color.White.copy(alpha = 0.8f), 3f, Offset(60f, 82f))
        // dripping goo
        drips.forEach { (x, y, ph) ->
            val t = ((time * 0.5f + ph) % 1f)
            val len = 4f + t * 12f
            drawOval(Color(0xFF8A40C0).copy(alpha = 1f - t * 0.6f), Offset(x - 3f, y - 2f), Size(6f, len))
        }

        // angry eyes
        val closed = blink(time, 3.3f, 1.4f)
        symmetric {
            val eye = Rect(66f, 96f, 92f, 118f)
            withTransform({ scale(1f, 1f - 0.85f * closed, eye.center) }) {
                drawOval(Brush.radialGradient(listOf(Color(0xFFFFF6A0), Color(0xFFE8D040)), center = eye.center, radius = 14f), eye.topLeft, eye.size)
                drawCircle(Color(0xFFC01010), 5.5f, eye.center + Offset(2f, 2f))
                drawCircle(Color.Black, 2.6f, eye.center + Offset(2f, 2f))
                drawCircle(Color.White, 1.5f, eye.center + Offset(-1f, -1f))
                drawOval(Color(0xFF240640), eye.topLeft, eye.size, style = Stroke(1.3f))
            }
            // slanted lid
            val lid = Path().apply { moveTo(62f, 92f); lineTo(96f, 104f); lineTo(96f, 96f); lineTo(64f, 86f); close() }
            drawPath(lid, Color(0xFF4A1A70))
        }
        // toothy grin
        val grin = Path().apply { moveTo(70f, 128f); quadraticTo(100f, 150f, 130f, 128f); quadraticTo(100f, 140f, 70f, 128f); close() }
        drawPath(grin, Color(0xFF1A0428))
        for (k in 0..5) {
            val x = 78f + k * 9f
            val y = 132f + sin((x - 70f) / 60f * PI.toFloat()) * 5f
            val tooth = Path().apply { moveTo(x - 3f, y - 1f); lineTo(x + 3f, y - 1f); lineTo(x, y + 5f); close() }
            drawPath(tooth, Color(0xFFF2F8D8))
        }
    }
}

// ==================================================================== Moorkröte
internal object ToadPainter {
    private val body = Path().apply {
        moveTo(14f, 182f)
        cubicTo(4f, 140f, 30f, 92f, 64f, 84f)
        quadraticTo(100f, 76f, 136f, 84f)
        cubicTo(170f, 92f, 196f, 140f, 186f, 182f)
        quadraticTo(100f, 200f, 14f, 182f)
        close()
    }
    private val bodyBrush = Brush.radialGradient(
        0f to Color(0xFFA8A85A), 0.4f to Color(0xFF6E7A34), 0.8f to Color(0xFF3E4A1C), 1f to Color(0xFF1E260C),
        center = Offset(84f, 100f), radius = 120f,
    )
    private val belly = Path().apply {
        moveTo(50f, 186f); cubicTo(50f, 150f, 70f, 136f, 100f, 136f); cubicTo(130f, 136f, 150f, 150f, 150f, 186f); quadraticTo(100f, 194f, 50f, 186f); close()
    }
    private val eyeDome = Rect(36f, 46f, 84f, 96f)
    private val warts = listOf(
        Triple(40f, 130f, 5f), Triple(58f, 112f, 4f), Triple(30f, 160f, 4.5f), Triple(150f, 116f, 4f), Triple(168f, 140f, 5f),
        Triple(160f, 166f, 4f), Triple(118f, 100f, 3.5f), Triple(80f, 102f, 3f), Triple(100f, 92f, 3.5f), Triple(140f, 96f, 3f),
    )
    private val hand = Path().apply {
        moveTo(36f, 170f)
        cubicTo(30f, 180f, 20f, 186f, 10f, 192f)
        lineTo(14f, 197f); lineTo(24f, 193f); lineTo(28f, 198f); lineTo(36f, 192f); lineTo(42f, 197f); lineTo(46f, 188f)
        cubicTo(50f, 182f, 50f, 176f, 48f, 170f)
        close()
    }

    fun draw(scope: DrawScope, time: Float) = with(scope) {
        drawPath(body, bodyBrush)
        clipPath(body) {
            // dark mottled spots
            listOf(Offset(60f, 120f), Offset(140f, 124f), Offset(100f, 106f), Offset(40f, 150f), Offset(164f, 152f)).forEach {
                drawOval(Color(0xFF2A3410).copy(alpha = 0.45f), it - Offset(10f, 6f), Size(20f, 12f))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = 150f, endY = 196f), topLeft = Offset(0f, 150f), size = Size(200f, 50f))
        }
        // throat sac pulsing
        val sac = 1f + 0.12f * (0.5f + 0.5f * sin(time * PI.toFloat() * 1.5f))
        withTransform({ scale(sac, sac, Offset(100f, 160f)) }) {
            drawPath(belly, Brush.radialGradient(listOf(Color(0xFFF2E0A0), Color(0xFFC8A860), Color(0xFF8A7038)), center = Offset(100f, 150f), radius = 54f))
            for (k in 0..3) drawLine(Color(0xFF8A7038).copy(alpha = 0.5f), Offset(70f + k * 3f, 150f + k * 9f), Offset(130f - k * 3f, 150f + k * 9f), 1f)
        }
        warts.forEach { (x, y, r) ->
            drawCircle(Brush.radialGradient(listOf(Color(0xFFD8D080), Color(0xFF6E7030)), center = Offset(x - r * 0.3f, y - r * 0.3f), radius = r * 1.3f), r, Offset(x, y))
            drawCircle(Color(0xFF2A3410).copy(alpha = 0.5f), r, Offset(x, y), style = Stroke(0.7f))
        }
        drawPath(body, Color(0xFF141A06).copy(alpha = 0.7f), style = Stroke(1.6f))
        // mouth: very wide line with a smug curl
        val mouth = Path().apply { moveTo(34f, 120f); cubicTo(60f, 134f, 140f, 134f, 166f, 120f) }
        drawPath(mouth, Color(0xFF141A06), style = Stroke(3f, cap = StrokeCap.Round))
        drawPath(mouth, Color(0xFFC86A5A).copy(alpha = 0.5f), style = Stroke(1f, cap = StrokeCap.Round))
        // nostrils
        drawOval(Color(0xFF1A2008), Offset(90f, 104f), Size(5f, 3f))
        drawOval(Color(0xFF1A2008), Offset(105f, 104f), Size(5f, 3f))
        // webbed hands
        symmetric {
            drawPath(hand, Brush.verticalGradient(listOf(Color(0xFF7A8A3E), Color(0xFF3E4A1C)), startY = 170f, endY = 198f))
            drawPath(hand, Color(0xFF141A06).copy(alpha = 0.7f), style = Stroke(1.1f, join = StrokeJoin.Round))
        }
        // bulging eyes on top
        val closed = blink(time, 5.5f, 0.3f)
        symmetric {
            drawOval(Brush.radialGradient(listOf(Color(0xFFA8A85A), Color(0xFF4E5A22)), center = Offset(56f, 60f), radius = 34f), eyeDome.topLeft, eyeDome.size)
            val c = eyeDome.center + Offset(0f, 2f)
            withTransform({ scale(1f, 1f - 0.9f * closed, c) }) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE08A), Color(0xFFD0901A), Color(0xFF6A3A08)), center = c - Offset(3f, 3f), radius = 18f), 17f, c)
                drawOval(Color.Black, c - Offset(10f, 3.5f), Size(20f, 7f))
                drawCircle(Color.White.copy(alpha = 0.9f), 3f, c - Offset(6f, 7f))
            }
            if (closed > 0f) drawCircle(Color(0xFF6E7A34), 17f * closed, c)
            drawOval(Color(0xFF141A06).copy(alpha = 0.7f), eyeDome.topLeft, eyeDome.size, style = Stroke(1.4f))
        }
    }
}

// ==================================================================== Irrlicht / Eisgeist
internal object WispPainter {
    private fun flame(seed: Float, time: Float): Path = Path().apply {
        // flickering flame tongue above the orb
        val s = sin(time * 5f + seed) * 6f
        moveTo(70f, 104f)
        cubicTo(62f, 70f, 84f, 60f, 90f + s, 20f)
        cubicTo(96f, 46f, 104f, 40f, 112f - s, 28f)
        cubicTo(116f, 50f, 138f, 64f, 130f, 104f)
        close()
    }

    fun draw(scope: DrawScope, time: Float, ice: Boolean) = with(scope) {
        val bob = sin(time * PI.toFloat() * 0.8f) * 6f
        val core = if (ice) Color(0xFFF2FCFF) else Color(0xFFF2FFE0)
        val mid = if (ice) Color(0xFF7ACCFF) else Color(0xFF6AF0B0)
        val outer = if (ice) Color(0xFF2A6AC8) else Color(0xFF1AA070)
        withTransform({ translate(0f, bob - 30f) }) {
            val c = Offset(100f, 116f)
            // large soft halo
            drawCircle(Brush.radialGradient(listOf(mid.copy(alpha = 0.45f), outer.copy(alpha = 0.15f), Color.Transparent), c, 96f), 96f, c, blendMode = BlendMode.Screen)
            // trailing wisps below
            for (k in 0..2) {
                val sway = sin(time * 2.5f + k) * 10f
                val tail = Path().apply {
                    moveTo(80f + k * 10f, 140f)
                    cubicTo(70f + k * 14f + sway, 170f, 96f + k * 6f - sway, 190f, 86f + k * 14f + sway * 1.5f, 214f)
                    cubicTo(100f + k * 8f, 190f, 116f - k * 4f, 168f, 124f - k * 6f, 140f)
                    close()
                }
                drawPath(tail, Brush.verticalGradient(listOf(mid.copy(alpha = 0.7f), Color.Transparent), startY = 140f, endY = 214f))
            }
            // flame crown
            drawPath(flame(0f, time), Brush.verticalGradient(listOf(Color.Transparent, mid.copy(alpha = 0.8f), core), startY = 20f, endY = 104f))
            withTransform({ scale(0.6f, 0.7f, Offset(100f, 104f)) }) {
                drawPath(flame(1.7f, time), Brush.verticalGradient(listOf(Color.Transparent, core), startY = 20f, endY = 104f))
            }
            // orb body
            drawCircle(Brush.radialGradient(0f to core, 0.45f to mid, 0.85f to outer, 1f to outer.copy(alpha = 0.6f), center = c - Offset(8f, 10f), radius = 48f), 44f, c)
            drawCircle(core.copy(alpha = 0.6f), 44f, c, style = Stroke(2f))
            // hollow face
            val closed = blink(time, 4.1f, 3.3f)
            symmetric {
                withTransform({ scale(1f, 1f - 0.85f * closed, Offset(86f, 110f)) }) {
                    val eye = Path().apply { moveTo(76f, 104f); quadraticTo(86f, 96f, 96f, 106f); quadraticTo(88f, 120f, 78f, 114f); close() }
                    drawPath(eye, SceneKit.mix(outer, Color.Black, 0.6f))
                    drawCircle(core, 2f, Offset(86f, 108f))
                }
            }
            val mouth = Path().apply { moveTo(88f, 132f); quadraticTo(100f, 124f, 112f, 132f); quadraticTo(100f, 146f, 88f, 132f); close() }
            drawPath(mouth, SceneKit.mix(outer, Color.Black, 0.6f))
            drawOval(Color.White.copy(alpha = 0.7f), Offset(76f, 84f), Size(18f, 8f))

            // orbiting sparks / ice shards
            for (k in 0 until 6) {
                val a = time * 1.6f + k * PI.toFloat() / 3f
                val p = Offset(100f + cos(a) * 66f, 116f + sin(a) * 22f)
                val front = sin(a) > 0f
                val alpha = if (front) 1f else 0.45f
                if (ice) {
                    val shard = Path().apply {
                        moveTo(p.x, p.y - 9f); lineTo(p.x + 3.5f, p.y); lineTo(p.x, p.y + 9f); lineTo(p.x - 3.5f, p.y); close()
                    }
                    drawPath(shard, Brush.verticalGradient(listOf(Color.White, Color(0xFF8AD0FF)), startY = p.y - 9f, endY = p.y + 9f), alpha = alpha)
                    drawPath(shard, Color(0xFF2A6AC8), style = Stroke(0.8f), alpha = alpha)
                } else {
                    drawCircle(Brush.radialGradient(listOf(core, mid.copy(alpha = 0.5f), Color.Transparent), p, 7f), 7f, p, alpha = alpha)
                }
            }
            if (ice) {
                // frosty crown of spikes
                for (k in -2..2) {
                    val x = 100f + k * 14f
                    val hgt = 22f - abs(k) * 5f
                    val sp = Path().apply { moveTo(x - 5f, 80f); lineTo(x, 80f - hgt); lineTo(x + 5f, 80f); close() }
                    drawPath(sp, Brush.verticalGradient(listOf(Color.White, Color(0xFF9AD8FF)), startY = 80f - hgt, endY = 80f))
                    drawPath(sp, Color(0xFF3A7AC8), style = Stroke(0.8f))
                }
            }
        }
    }
}
