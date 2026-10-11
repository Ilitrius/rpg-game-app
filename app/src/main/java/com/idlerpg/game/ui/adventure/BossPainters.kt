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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Golden crown used by bosses. Drawn with its base centered at [c], [w] wide. */
internal fun DrawScope.drawCrown(c: Offset, w: Float, time: Float) {
    val h = w * 0.55f
    val l = c.x - w / 2f
    val crown = Path().apply {
        moveTo(l, c.y)
        lineTo(l - w * 0.04f, c.y - h * 0.75f)
        lineTo(l + w * 0.2f, c.y - h * 0.35f)
        lineTo(l + w * 0.32f, c.y - h)
        lineTo(c.x, c.y - h * 0.45f)
        lineTo(l + w * 0.68f, c.y - h)
        lineTo(l + w * 0.8f, c.y - h * 0.35f)
        lineTo(l + w * 1.04f, c.y - h * 0.75f)
        lineTo(l + w, c.y)
        close()
    }
    drawPath(crown, Brush.linearGradient(listOf(Color(0xFFFFF4B0), Color(0xFFF2C230), Color(0xFFB07A10), Color(0xFFF8D860)), Offset(l, c.y - h), Offset(l + w, c.y)))
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFE680), Color(0xFFA06A08)), startY = c.y - h * 0.22f, endY = c.y), Offset(l, c.y - h * 0.22f), Size(w, h * 0.22f))
    drawPath(crown, Color(0xFF6A4204), style = Stroke(w * 0.02f, join = StrokeJoin.Round))
    // gems
    listOf(Triple(l + w * 0.2f, Color(0xFFE0203A), 0.07f), Triple(c.x, Color(0xFF2A7AE0), 0.09f), Triple(l + w * 0.8f, Color(0xFF20B060), 0.07f)).forEach { (x, col, s) ->
        val gc = Offset(x, c.y - h * 0.11f)
        drawCircle(Brush.radialGradient(listOf(Color.White, col, SceneKit.mix(col, Color.Black, 0.5f)), center = gc - Offset(w * 0.015f, w * 0.015f), radius = w * s), w * s * 0.8f, gc)
    }
    listOf(l - w * 0.04f to 0.75f, l + w * 0.32f to 1f, l + w * 0.68f to 1f, l + w * 1.04f to 0.75f).forEach { (x, f) ->
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFF2C230)), center = Offset(x - 1f, c.y - h * f - 1f), radius = w * 0.04f), w * 0.035f, Offset(x, c.y - h * f))
    }
    val shine = (time * 0.4f) % 1f
    drawLine(Color.White.copy(alpha = 0.6f * (1f - shine)), Offset(l + w * shine, c.y - h * 0.2f), Offset(l + w * shine + w * 0.06f, c.y - h * 0.02f), w * 0.02f)
}

// ==================================================================== Schleimkönig
internal object SlimeKingPainter {
    private val body = Path().apply {
        moveTo(4f, 190f)
        cubicTo(-10f, 150f, 8f, 100f, 40f, 78f)
        cubicTo(60f, 62f, 80f, 54f, 100f, 54f)
        cubicTo(120f, 54f, 140f, 62f, 160f, 78f)
        cubicTo(192f, 100f, 210f, 150f, 196f, 190f)
        cubicTo(150f, 204f, 50f, 204f, 4f, 190f)
        close()
    }
    private val bodyBrush = Brush.radialGradient(
        0f to Color(0xFFB8FFD8), 0.3f to Color(0xFF4ED8A0), 0.7f to Color(0xFF16906A), 1f to Color(0xFF064A36),
        center = Offset(76f, 90f), radius = 150f,
    )
    private val coins = listOf(Offset(60f, 168f), Offset(138f, 160f), Offset(108f, 180f), Offset(84f, 150f), Offset(152f, 178f), Offset(40f, 150f))

    fun draw(scope: DrawScope, time: Float) = with(scope) {
        drawOval(Brush.radialGradient(listOf(Color(0xFF4ED8A0).copy(alpha = 0.55f), Color.Transparent), center = Offset(100f, 196f), radius = 110f), Offset(-10f, 186f), Size(220f, 22f))
        drawPath(body, bodyBrush)
        clipPath(body) {
            // dense royal core glowing through the goo
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0A6A4A).copy(alpha = 0.55f), Color.Transparent), Offset(100f, 160f), 70f), 70f, Offset(100f, 160f))
            // swallowed treasure floating inside
            coins.forEachIndexed { i, c ->
                val p = c + Offset(0f, sin(time * 1.2f + i) * 2.5f)
                drawOval(Brush.radialGradient(listOf(Color(0xFFFFF0A0), Color(0xFFD0A020), Color(0xFF8A6008)), center = p - Offset(2f, 2f), radius = 8f), p - Offset(7f, 5f), Size(14f, 10f))
                drawOval(Color(0xFF6A4804).copy(alpha = 0.6f), p - Offset(7f, 5f), Size(14f, 10f), style = Stroke(0.8f))
            }
            // bubbles
            for (i in 0 until 9) {
                val ph = ((time * 0.25f + i * 0.111f) % 1f)
                val x = 24f + i * 19f
                val y = 190f - ph * 120f
                val r = 2.5f + (i % 4) * 1.5f
                drawCircle(Color.White.copy(alpha = 0.55f * (1f - ph)), r, Offset(x, y), style = Stroke(0.9f))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF023A28).copy(alpha = 0.55f)), startY = 120f, endY = 200f), topLeft = Offset(-20f, 120f), size = Size(240f, 82f))
        }
        drawPath(body, Brush.linearGradient(listOf(Color.Transparent, Color(0xFFE8FFF2).copy(alpha = 0.9f)), Offset(40f, 190f), Offset(170f, 60f)), style = Stroke(3.5f))
        drawPath(body, Color(0xFF033A28).copy(alpha = 0.7f), style = Stroke(1.6f))
        drawOval(Color.White.copy(alpha = 0.6f), Offset(46f, 76f), Size(34f, 14f))
        drawCircle(Color.White.copy(alpha = 0.9f), 4f, Offset(88f, 64f))

        // face: heavy royal brows, big eyes, mustache of goo
        val closed = blink(time, 4.2f, 0.6f)
        symmetric {
            val eye = Rect(60f, 92f, 90f, 126f)
            drawOval(Color(0xFF033A28).copy(alpha = 0.35f), eye.topLeft + Offset(0f, 2f), eye.size)
            withTransform({ scale(1f, 1f - 0.85f * closed, eye.center) }) {
                drawOval(Brush.radialGradient(listOf(Color.White, Color(0xFFD8F4E8)), center = eye.center - Offset(3f, 4f), radius = 18f), eye.topLeft, eye.size)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF3A6A9A), Color(0xFF0A1A2A)), center = eye.center, radius = 10f), 9f, eye.center + Offset(2f, 3f))
                drawCircle(Color.White, 3f, eye.center + Offset(-1f, 0f))
                drawOval(Color(0xFF033A28), eye.topLeft, eye.size, style = Stroke(1.4f))
            }
            val brow = Path().apply { moveTo(54f, 88f); quadraticTo(74f, 76f, 96f, 90f); quadraticTo(74f, 84f, 54f, 92f); close() }
            drawPath(brow, Color(0xFF064A36))
            val stache = Path().apply {
                moveTo(100f, 136f); cubicTo(90f, 128f, 72f, 130f, 62f, 142f); cubicTo(56f, 150f, 48f, 148f, 46f, 140f)
                cubicTo(50f, 158f, 72f, 156f, 84f, 146f); quadraticTo(94f, 142f, 100f, 146f); close()
            }
            drawPath(stache, Brush.verticalGradient(listOf(Color(0xFF0A7050), Color(0xFF033A28)), startY = 128f, endY = 156f))
        }
        val mouth = Path().apply { moveTo(82f, 152f); quadraticTo(100f, 168f, 118f, 152f); quadraticTo(100f, 160f, 82f, 152f); close() }
        drawPath(mouth, Color(0xFF02281C))
        drawCrown(Offset(100f, 60f), 64f, time)
    }
}

// ==================================================================== Uralter Baumhüter
internal object TreantPainter {
    private val trunk = Path().apply {
        moveTo(56f, 196f)
        cubicTo(64f, 170f, 66f, 140f, 62f, 110f)
        cubicTo(58f, 84f, 64f, 60f, 74f, 40f)
        lineTo(126f, 40f)
        cubicTo(136f, 60f, 142f, 84f, 138f, 110f)
        cubicTo(134f, 140f, 136f, 170f, 144f, 196f)
        close()
    }
    private val trunkBrush = Brush.horizontalGradient(
        0f to Color(0xFF1E140A), 0.3f to Color(0xFF6A4E30), 0.55f to Color(0xFF8A6A44), 0.8f to Color(0xFF4A3420), 1f to Color(0xFF140C06),
        startX = 56f, endX = 144f,
    )
    private val bark: Path = run {
        val r = Random(71)
        Path().apply {
            repeat(46) {
                val x = 62f + r.nextFloat() * 76f
                val y = 40f + r.nextFloat() * 150f
                val len = 10f + r.nextFloat() * 24f
                moveTo(x, y)
                cubicTo(x + 3f, y + len * 0.3f, x - 3f, y + len * 0.6f, x + (r.nextFloat() - 0.5f) * 4f, y + len)
            }
        }
    }
    private val root = Path().apply {
        moveTo(60f, 184f); cubicTo(44f, 186f, 26f, 192f, 4f, 198f); lineTo(30f, 198f); cubicTo(44f, 196f, 56f, 196f, 66f, 196f); close()
        moveTo(70f, 188f); cubicTo(64f, 194f, 56f, 198f, 46f, 200f); lineTo(70f, 199f); close()
    }
    private val arm = Path().apply {
        moveTo(66f, 92f)
        cubicTo(46f, 86f, 28f, 74f, 14f, 50f)
        lineTo(2f, 34f); lineTo(14f, 40f); lineTo(10f, 22f); lineTo(22f, 38f); lineTo(28f, 26f); lineTo(28f, 48f)
        cubicTo(40f, 66f, 54f, 74f, 68f, 76f)
        close()
    }
    private class Leaf(val c: Offset, val r: Float, val brush: Brush)

    private val canopy: List<Leaf> = run {
        val r = Random(73)
        val list = ArrayList<Leaf>()
        repeat(26) {
            val a = PI.toFloat() + r.nextFloat() * PI.toFloat()
            val d = 30f + r.nextFloat() * 50f
            val c = Offset(100f + cos(a) * d * 1.4f, 34f + sin(a) * d * 0.7f)
            val rad = 16f + r.nextFloat() * 14f
            val light = SceneKit.mix(Color(0xFF5A9A3A), Color(0xFFA8D060), r.nextFloat() * 0.6f)
            list += Leaf(c, rad, Brush.radialGradient(listOf(light, Color(0xFF2E5A1E), Color(0xFF14300C)), center = c - Offset(rad * 0.3f, rad * 0.35f), radius = rad * 1.3f))
        }
        list.sortedBy { -it.c.y }
    }
    private val leafDetail: Path = run {
        val r = Random(74)
        Path().apply { canopy.forEach { l -> repeat(5) { val a = r.nextFloat() * 6.28f; SceneKit.addLeaf(this, l.c.x + cos(a) * l.r * 0.85f, l.c.y + sin(a) * l.r * 0.85f, l.r * 0.42f, l.r * 0.1f, a) } } }
    }
    private val mouth = Path().apply {
        moveTo(80f, 128f); cubicTo(88f, 120f, 112f, 120f, 120f, 128f); cubicTo(116f, 146f, 84f, 146f, 80f, 128f); close()
    }

    fun draw(scope: DrawScope, time: Float) = with(scope) {
        val creak = sin(time * PI.toFloat() * 0.6f) * 3f
        symmetric {
            drawPath(root, Brush.verticalGradient(listOf(Color(0xFF5A4028), Color(0xFF2A1C10)), startY = 184f, endY = 200f))
            drawPath(root, Color(0xFF140C06), style = Stroke(1f))
        }
        symmetric {
            withTransform({ rotate(creak, Offset(66f, 84f)) }) {
                drawPath(arm, Brush.linearGradient(listOf(Color(0xFF7A5A38), Color(0xFF3A2814)), Offset(66f, 80f), Offset(10f, 30f)))
                drawPath(arm, Color(0xFF140C06), style = Stroke(1.3f, join = StrokeJoin.Round))
                // leaf tufts on the arm
                listOf(Offset(20f, 40f), Offset(34f, 58f)).forEach {
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF8AC050), Color(0xFF2E5A1E)), center = it - Offset(3f, 3f), radius = 12f), 10f, it)
                }
            }
        }
        drawPath(trunk, trunkBrush)
        clipPath(trunk) {
            drawPath(bark, Color(0xFF140C06).copy(alpha = 0.6f), style = Stroke(1.6f, cap = StrokeCap.Round))
            // moss streaks
            drawOval(Brush.radialGradient(listOf(Color(0xFF6E9A3A).copy(alpha = 0.7f), Color.Transparent), center = Offset(76f, 170f), radius = 30f), Offset(46f, 150f), Size(60f, 40f))
            drawOval(Brush.radialGradient(listOf(Color(0xFF6E9A3A).copy(alpha = 0.55f), Color.Transparent), center = Offset(130f, 60f), radius = 24f), Offset(106f, 44f), Size(48f, 32f))
        }
        drawPath(trunk, Color(0xFF0E0804), style = Stroke(1.6f))
        // carved face: hollow eye sockets with green glow
        val glow = 0.7f + 0.3f * sin(time * PI.toFloat())
        val closed = blink(time, 6.3f, 2.5f)
        symmetric {
            val socket = Path().apply { moveTo(72f, 90f); quadraticTo(84f, 78f, 96f, 90f); quadraticTo(86f, 106f, 72f, 96f); close() }
            drawPath(socket, Color(0xFF0A0602))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB8FF6A).copy(alpha = 0.7f * glow), Color.Transparent), Offset(84f, 92f), 16f), 16f, Offset(84f, 92f), blendMode = BlendMode.Screen)
            withTransform({ scale(1f, 1f - 0.9f * closed, Offset(84f, 92f)) }) {
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9AF04A)), center = Offset(84f, 92f), radius = 5f), 4.5f, Offset(84f, 92f))
            }
            // knotted brow
            val brow = Path().apply { moveTo(66f, 82f); quadraticTo(82f, 70f, 98f, 84f); lineTo(96f, 88f); quadraticTo(82f, 78f, 68f, 88f); close() }
            drawPath(brow, Color(0xFF3A2814))
        }
        drawPath(mouth, Color(0xFF0A0602))
        drawPath(mouth, Brush.radialGradient(listOf(Color(0xFF9AF04A).copy(alpha = 0.35f * glow), Color.Transparent), center = Offset(100f, 134f), radius = 20f))
        for (k in 0..3) drawPath(poly(86f + k * 8f, 126f, 92f + k * 8f, 126f, 89f + k * 8f, 133f), Color(0xFF8A6A44))
        // nose knot
        drawOval(Brush.radialGradient(listOf(Color(0xFF8A6A44), Color(0xFF3A2814)), center = Offset(98f, 106f), radius = 10f), Offset(92f, 100f), Size(16f, 14f))
        // canopy crown on top
        canopy.forEach { l ->
            drawCircle(Color.Black.copy(alpha = 0.2f), l.r, l.c + Offset(2f, 4f))
            drawCircle(l.brush, l.r, l.c)
        }
        drawPath(leafDetail, Color(0xFF1E400E).copy(alpha = 0.7f))
        // glowing fireflies around the crown
        for (k in 0 until 5) {
            val a = time * 0.9f + k * 1.26f
            val p = Offset(100f + cos(a) * 80f, 30f + sin(a * 1.3f) * 30f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFE8FF8A), Color.Transparent), p, 6f), 6f, p, blendMode = BlendMode.Screen)
        }
        // small mushrooms at the roots
        listOf(Offset(48f, 190f), Offset(152f, 192f)).forEach { c ->
            drawRect(Color(0xFFEDE4CC), Offset(c.x - 2f, c.y - 8f), Size(4f, 8f))
            drawOval(Brush.radialGradient(listOf(Color(0xFFFF7A5A), Color(0xFF8E1A0E)), center = c - Offset(2f, 12f), radius = 9f), Offset(c.x - 8f, c.y - 14f), Size(16f, 9f))
        }
    }
}

// ==================================================================== Sumpfhydra
internal object HydraPainter {
    private val body = Path().apply {
        moveTo(10f, 196f)
        cubicTo(14f, 150f, 50f, 120f, 100f, 120f)
        cubicTo(150f, 120f, 186f, 150f, 190f, 196f)
        close()
    }
    private val bodyBrush = Brush.radialGradient(
        0f to Color(0xFF6E9A6A), 0.5f to Color(0xFF2E5A3E), 1f to Color(0xFF0E2418),
        center = Offset(90f, 140f), radius = 100f,
    )
    private val scales: Path = run {
        Path().apply {
            var row = 0
            var y = 128f
            while (y < 196f) {
                var x = 20f + (row % 2) * 7f
                while (x < 184f) {
                    moveTo(x - 7f, y); quadraticTo(x, y + 8f, x + 7f, y)
                    x += 14f
                }
                y += 9f
                row++
            }
        }
    }

    /** One serpent head + neck; [base] on the body, head at [head], [lean] -1..1 turns it sideways. */
    private fun DrawScope.drawHead(base: Offset, head: Offset, lean: Float, time: Float, phase: Float, scale: Float) {
        val neck = Path().apply {
            moveTo(base.x - 14f * scale, base.y)
            cubicTo(base.x - 18f * scale, base.y - 40f, head.x - 26f * scale + lean * 10f, head.y + 50f, head.x - 11f * scale, head.y + 12f)
            lineTo(head.x + 11f * scale, head.y + 12f)
            cubicTo(head.x + 26f * scale + lean * 10f, head.y + 50f, base.x + 18f * scale, base.y - 40f, base.x + 14f * scale, base.y)
            close()
        }
        drawPath(neck, Brush.horizontalGradient(listOf(Color(0xFF0E2418), Color(0xFF4E7A52), Color(0xFF0E2418)), startX = base.x - 20f * scale, endX = base.x + 20f * scale))
        // ventral plates down the neck
        val belly = Path().apply {
            moveTo(base.x - 6f * scale, base.y)
            cubicTo(base.x - 8f * scale, base.y - 40f, head.x - 10f * scale + lean * 6f, head.y + 50f, head.x - 5f * scale, head.y + 14f)
            lineTo(head.x + 5f * scale, head.y + 14f)
            cubicTo(head.x + 10f * scale + lean * 6f, head.y + 50f, base.x + 8f * scale, base.y - 40f, base.x + 6f * scale, base.y)
            close()
        }
        drawPath(belly, Brush.verticalGradient(listOf(Color(0xFFD8D08A), Color(0xFF8A8A4A)), startY = head.y, endY = base.y))
        clipPath(belly) {
            var y = head.y + 18f
            while (y < base.y) { drawLine(Color(0xFF5A5A2A).copy(alpha = 0.7f), Offset(head.x - 30f, y), Offset(head.x + 30f, y), 0.9f); y += 6f }
        }
        drawPath(neck, Color(0xFF061208), style = Stroke(1.2f))

        withTransform({
            translate(head.x, head.y)
            scale(scale, scale, Offset.Zero)
            rotate(lean * 14f, Offset.Zero)
        }) {
            // fins / horns
            listOf(-1f, 1f).forEach { s ->
                val fin = Path().apply { moveTo(s * 12f, -12f); lineTo(s * 34f, -34f); lineTo(s * 26f, -14f); lineTo(s * 36f, -16f); lineTo(s * 18f, -2f); close() }
                drawPath(fin, Brush.linearGradient(listOf(Color(0xFFB04A3A), Color(0xFF5A1A12)), Offset(s * 12f, -12f), Offset(s * 34f, -34f)))
                drawPath(fin, Color(0xFF2A0A06), style = Stroke(1f))
            }
            val skull = Path().apply {
                moveTo(-20f, -10f); cubicTo(-22f, -26f, 22f, -26f, 20f, -10f)
                cubicTo(18f, 4f, 12f, 18f, 0f, 22f); cubicTo(-12f, 18f, -18f, 4f, -20f, -10f); close()
            }
            drawPath(skull, Brush.radialGradient(listOf(Color(0xFF8AB07A), Color(0xFF2E5A3E), Color(0xFF0E2418)), center = Offset(-6f, -12f), radius = 32f))
            drawPath(skull, Color(0xFF061208), style = Stroke(1.2f))
            // jaws open & close
            val open = 0.5f + 0.5f * sin(time * 2.2f + phase)
            val jaw = Path().apply { moveTo(-12f, 12f); quadraticTo(0f, 22f + open * 10f, 12f, 12f); quadraticTo(0f, 16f, -12f, 12f); close() }
            drawPath(jaw, Color(0xFF4A0A0A))
            for (k in -2..2) drawPath(poly(k * 4.5f - 1.5f, 13.5f, k * 4.5f + 1.5f, 13.5f, k * 4.5f, 18f), Color.White)
            // forked tongue flicks
            if (open > 0.75f) {
                drawPath(Path().apply { moveTo(0f, 18f); lineTo(0f, 32f); moveTo(0f, 32f); lineTo(-3f, 37f); moveTo(0f, 32f); lineTo(3f, 37f) }, Color(0xFFE0304A), style = Stroke(1.6f, cap = StrokeCap.Round))
            }
            // nostrils + eyes
            drawCircle(Color(0xFF061208), 1.3f, Offset(-4f, 6f)); drawCircle(Color(0xFF061208), 1.3f, Offset(4f, 6f))
            val closed = blink(time, 3.7f, phase)
            listOf(-1f, 1f).forEach { s ->
                val c = Offset(s * 10f, -8f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE04A).copy(alpha = 0.6f), Color.Transparent), c, 9f), 9f, c)
                withTransform({ scale(1f, 1f - 0.9f * closed, c) }) {
                    drawOval(Color(0xFFFFD020), c - Offset(5f, 3.5f), Size(10f, 7f))
                    drawOval(Color.Black, c - Offset(1f, 3.5f), Size(2f, 7f))
                }
                drawLine(Color(0xFF061208), c + Offset(-s * 7f, -6f), c + Offset(s * 6f, -3f), 2f, StrokeCap.Round)
            }
        }
    }

    fun draw(scope: DrawScope, time: Float) = with(scope) {
        // murky water ring around the body
        drawOval(Brush.radialGradient(listOf(Color(0xFF3A4A2A).copy(alpha = 0.8f), Color(0xFF1A2414).copy(alpha = 0.4f), Color.Transparent), center = Offset(100f, 196f), radius = 120f), Offset(-20f, 184f), Size(240f, 26f))
        val sway = sin(time * PI.toFloat() * 0.7f)
        drawHead(Offset(46f, 160f), Offset(18f + sway * 4f, 54f), -1f, time, 1.1f, 0.9f)
        drawHead(Offset(154f, 160f), Offset(182f - sway * 4f, 58f), 1f, time, 2.3f, 0.9f)
        drawPath(body, bodyBrush)
        clipPath(body) {
            drawPath(scales, Color(0xFF061208).copy(alpha = 0.45f), style = Stroke(1f))
            // dorsal spikes
            for (k in -3..3) {
                val x = 100f + k * 18f
                val y = 124f + k * k * 1.6f
                drawPath(poly(x - 6f, y + 6f, x, y - 10f, x + 6f, y + 6f), Color(0xFF8A2A1E))
            }
        }
        drawPath(body, Color(0xFF061208), style = Stroke(1.5f))
        drawHead(Offset(100f, 150f), Offset(100f + sway * 3f, 30f), 0f, time, 0f, 1.15f)
        // ripples
        for (k in 0..1) {
            val p = ((time * 0.3f + k * 0.5f) % 1f)
            val rx = 90f + p * 40f
            drawOval(Color(0xFFB8C8A0).copy(alpha = 0.4f * (1f - p)), Offset(100f - rx, 192f - rx * 0.07f), Size(rx * 2f, rx * 0.14f), style = Stroke(1.2f))
        }
    }
}

// ==================================================================== Frostyeti
internal object YetiPainter {
    private val body: Path = run {
        val r = Random(81)
        Path().apply {
            moveTo(46f, 190f)
            furEdge(Offset(46f, 190f), Offset(26f, 110f), 8, 6f, r)
            cubicTo(30f, 70f, 70f, 54f, 100f, 54f)
            cubicTo(130f, 54f, 170f, 70f, 174f, 110f)
            furEdge(Offset(174f, 110f), Offset(154f, 190f), 8, 6f, r)
            lineTo(46f, 190f)
            close()
        }
    }
    private val arm: Path = run {
        val r = Random(82)
        Path().apply {
            moveTo(40f, 80f)
            furEdge(Offset(40f, 80f), Offset(8f, 120f), 5, 5f, r)
            furEdge(Offset(8f, 120f), Offset(4f, 170f), 5, 5f, r)
            lineTo(30f, 172f)
            furEdge(Offset(30f, 172f), Offset(36f, 120f), 4, 4f, r)
            lineTo(52f, 96f)
            close()
        }
    }
    private val hand = Path().apply {
        moveTo(0f, 166f); cubicTo(-4f, 180f, 2f, 194f, 16f, 196f); cubicTo(28f, 198f, 38f, 190f, 36f, 168f); close()
    }
    private val face = Path().apply {
        moveTo(74f, 64f); cubicTo(74f, 48f, 126f, 48f, 126f, 64f)
        cubicTo(130f, 90f, 120f, 116f, 100f, 118f); cubicTo(80f, 116f, 70f, 90f, 74f, 64f); close()
    }
    private val horn = Path().apply {
        moveTo(74f, 52f); cubicTo(56f, 30f, 32f, 34f, 30f, 56f); cubicTo(30f, 70f, 42f, 74f, 48f, 66f)
        cubicTo(42f, 64f, 40f, 54f, 48f, 48f); cubicTo(58f, 44f, 66f, 54f, 70f, 62f); close()
    }
    private val fur = furStrokes(83, Rect(30f, 60f, 170f, 190f), 240, 9f, 95f, 40f)
    private val headFur: Path = run {
        val r = Random(84)
        Path().apply {
            moveTo(60f, 70f)
            furEdge(Offset(60f, 70f), Offset(74f, 34f), 5, 6f, r)
            furEdge(Offset(74f, 34f), Offset(126f, 34f), 7, 6f, r)
            furEdge(Offset(126f, 34f), Offset(140f, 70f), 5, 6f, r)
            lineTo(100f, 80f)
            close()
        }
    }

    fun draw(scope: DrawScope, time: Float) = with(scope) {
        val roar = (0.5f + 0.5f * sin(time * PI.toFloat() * 0.5f)).let { it * it }
        val furLight = Color(0xFFFFFFFF)
        val furMid = Color(0xFFD6E2EE)
        val furDark = Color(0xFF8A9CB4)
        // arms behind
        symmetric {
            drawPath(arm, Brush.radialGradient(listOf(furLight, furMid, furDark), center = Offset(24f, 110f), radius = 70f))
            drawPath(arm, Color(0xFF4A5A74).copy(alpha = 0.7f), style = Stroke(1.2f, join = StrokeJoin.Round))
            drawPath(hand, Brush.radialGradient(listOf(Color(0xFF8AA0C0), Color(0xFF3A4A66)), center = Offset(14f, 176f), radius = 26f))
            for (k in 0..3) {
                val x = 4f + k * 8f
                drawPath(poly(x, 192f, x + 4f, 192f, x + 2f, 200f), Color(0xFF2A2A30))
            }
        }
        drawPath(body, Brush.radialGradient(listOf(furLight, furMid, furDark), center = Offset(90f, 90f), radius = 120f))
        clipPath(body) {
            drawPath(fur, Color(0xFF6A7C98).copy(alpha = 0.35f), style = Stroke(1.2f, cap = StrokeCap.Round))
            // blue-grey chest skin
            drawOval(Brush.radialGradient(listOf(Color(0xFF9AB0CC), Color(0xFF6A80A0).copy(alpha = 0f)), center = Offset(100f, 140f), radius = 40f), Offset(60f, 108f), Size(80f, 64f))
            // icicles frozen in the fur
            listOf(Offset(48f, 150f), Offset(150f, 140f), Offset(70f, 176f), Offset(130f, 172f)).forEach {
                drawPath(poly(it.x - 3f, it.y, it.x + 3f, it.y, it.x, it.y + 12f), Brush.verticalGradient(listOf(Color.White, Color(0xFF8AD0FF)), startY = it.y, endY = it.y + 12f))
            }
        }
        drawPath(body, Color(0xFF4A5A74).copy(alpha = 0.7f), style = Stroke(1.4f, join = StrokeJoin.Round))
        // legs/feet
        symmetric {
            drawOval(Brush.radialGradient(listOf(Color(0xFF8AA0C0), Color(0xFF3A4A66)), center = Offset(70f, 190f), radius = 22f), Offset(50f, 184f), Size(40f, 14f))
        }
        // horns
        symmetric {
            drawPath(horn, Brush.linearGradient(listOf(Color(0xFFF2E8D0), Color(0xFFA8987A), Color(0xFF5E5240)), Offset(74f, 52f), Offset(30f, 56f)))
            drawPath(horn, Color(0xFF3A3020), style = Stroke(1.1f))
            for (k in 0..3) drawLine(Color(0xFF6A5C44).copy(alpha = 0.7f), Offset(64f - k * 8f, 42f + k), Offset(60f - k * 8f, 50f + k * 2f), 1f)
        }
        drawPath(headFur, Brush.radialGradient(listOf(furLight, furMid), center = Offset(100f, 50f), radius = 50f))
        // face
        drawPath(face, Brush.radialGradient(listOf(Color(0xFFA8BCD6), Color(0xFF6A80A0), Color(0xFF3A4A66)), center = Offset(96f, 76f), radius = 46f))
        drawPath(face, Color(0xFF2A3448), style = Stroke(1.3f))
        // heavy brow + eyes
        val brow = Path().apply { moveTo(74f, 70f); quadraticTo(100f, 60f, 126f, 70f); lineTo(124f, 78f); quadraticTo(100f, 70f, 76f, 78f); close() }
        drawPath(brow, Color(0xFF3A4A66))
        val closed = blink(time, 5.2f, 1.9f)
        symmetric {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8AE8FF).copy(alpha = 0.6f), Color.Transparent), Offset(88f, 82f), 10f), 10f, Offset(88f, 82f))
            withTransform({ scale(1f, 1f - 0.9f * closed, Offset(88f, 82f)) }) {
                drawOval(Color(0xFFE8FBFF), Offset(82f, 79f), Size(12f, 7f))
                drawCircle(Color(0xFF1A6AA0), 2.5f, Offset(89f, 82.5f))
            }
        }
        // nose
        drawOval(Color(0xFF2A3448), Offset(93f, 90f), Size(5f, 4f))
        drawOval(Color(0xFF2A3448), Offset(102f, 90f), Size(5f, 4f))
        // roaring mouth
        val mh = 8f + roar * 12f
        val mouth = Path().apply { moveTo(84f, 100f); quadraticTo(100f, 96f, 116f, 100f); quadraticTo(112f, 100f + mh * 1.6f, 100f, 100f + mh * 1.7f); quadraticTo(88f, 100f + mh * 1.6f, 84f, 100f); close() }
        drawPath(mouth, Brush.verticalGradient(listOf(Color(0xFF3A0A14), Color(0xFF8A2A3A)), startY = 100f, endY = 100f + mh * 1.7f))
        symmetric {
            drawPath(poly(86f, 100f, 91f, 100f, 88.5f, 110f), Color.White)
            drawPath(poly(88f, 100f + mh * 1.5f, 92f, 100f + mh * 1.5f, 90f, 100f + mh * 1.5f - 7f), Color(0xFFF2F2E8))
        }
        drawPath(mouth, Color(0xFF1A2030), style = Stroke(1.2f))
        // frost breath while roaring
        if (roar > 0.4f) {
            val a = (roar - 0.4f) * 1.2f
            val c = Offset(100f, 132f + roar * 10f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFE6F8FF).copy(alpha = a), Color.Transparent), c, 30f), 30f, c)
        }
    }
}

// ==================================================================== Höhlendrache
internal object DragonPainter {
    private val wing = Path().apply {
        moveTo(80f, 90f)
        cubicTo(60f, 40f, 20f, 10f, -40f, -20f)      // arm to the wing claw
        lineTo(-60f, 20f)
        quadraticTo(-48f, 30f, -56f, 60f)
        quadraticTo(-30f, 54f, -30f, 92f)
        quadraticTo(-6f, 82f, 4f, 118f)
        quadraticTo(26f, 104f, 40f, 132f)
        quadraticTo(56f, 118f, 72f, 130f)
        close()
    }
    private val wingBones = Path().apply {
        moveTo(80f, 90f); lineTo(20f, 20f); lineTo(-40f, -20f)
        moveTo(20f, 20f); lineTo(-60f, 20f)
        moveTo(20f, 20f); lineTo(-56f, 60f)
        moveTo(20f, 20f); lineTo(-30f, 92f)
        moveTo(20f, 20f); lineTo(4f, 118f)
        moveTo(20f, 20f); lineTo(40f, 132f)
    }
    private val tail = Path().apply {
        moveTo(130f, 180f); cubicTo(170f, 190f, 200f, 176f, 210f, 150f); cubicTo(214f, 140f, 222f, 136f, 232f, 134f)
        lineTo(226f, 146f); cubicTo(214f, 172f, 186f, 198f, 130f, 194f); close()
    }
    private val body = Path().apply {
        moveTo(100f, 92f)
        cubicTo(136f, 92f, 158f, 130f, 152f, 168f)
        cubicTo(148f, 190f, 128f, 198f, 100f, 198f)
        cubicTo(72f, 198f, 52f, 190f, 48f, 168f)
        cubicTo(42f, 130f, 64f, 92f, 100f, 92f)
        close()
    }
    private val chest = Path().apply {
        moveTo(100f, 96f); cubicTo(118f, 100f, 126f, 140f, 120f, 190f); quadraticTo(100f, 196f, 80f, 190f); cubicTo(74f, 140f, 82f, 100f, 100f, 96f); close()
    }
    private val neck = Path().apply {
        moveTo(82f, 110f); cubicTo(80f, 90f, 84f, 74f, 86f, 60f); lineTo(114f, 60f); cubicTo(116f, 74f, 120f, 90f, 118f, 110f); close()
    }
    private val head = Path().apply {
        moveTo(70f, 40f); cubicTo(72f, 20f, 128f, 20f, 130f, 40f)
        cubicTo(132f, 54f, 124f, 62f, 120f, 74f)
        cubicTo(116f, 88f, 84f, 88f, 80f, 74f)
        cubicTo(76f, 62f, 68f, 54f, 70f, 40f); close()
    }
    private val hornL = Path().apply { moveTo(76f, 30f); cubicTo(64f, 14f, 56f, 0f, 58f, -16f); cubicTo(66f, 0f, 78f, 12f, 88f, 24f); close() }
    private val leg = Path().apply {
        moveTo(56f, 150f); cubicTo(44f, 160f, 44f, 180f, 50f, 192f); lineTo(76f, 192f); cubicTo(74f, 176f, 74f, 164f, 76f, 150f); close()
    }
    private val scales: Path = run {
        Path().apply {
            var y = 100f
            var row = 0
            while (y < 196f) {
                var x = 46f + (row % 2) * 6f
                while (x < 156f) { moveTo(x - 6f, y); quadraticTo(x, y + 7f, x + 6f, y); x += 12f }
                y += 8f; row++
            }
        }
    }

    fun draw(scope: DrawScope, time: Float) = with(scope) {
        val beat = sin(time * PI.toFloat() * 0.8f)
        val glow = 0.6f + 0.4f * sin(time * PI.toFloat() * 1.5f)
        // wings behind
        symmetric {
            withTransform({ rotate(-6f * beat, Offset(80f, 90f)); scale(1f, 1f - 0.06f * beat, Offset(80f, 90f)) }) {
                drawPath(wing, Brush.radialGradient(listOf(Color(0xFFB0402A), Color(0xFF6A1A12), Color(0xFF2A0806)), center = Offset(30f, 60f), radius = 120f))
                clipPath(wing) { drawPath(wingBones, Color(0xFFE07A4A).copy(alpha = 0.18f), style = Stroke(7f, cap = StrokeCap.Round)) }
                drawPath(wingBones, Color(0xFF2A0806), style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(wing, Color(0xFF1A0402), style = Stroke(1.4f, join = StrokeJoin.Round))
                drawPath(poly(-40f, -20f, -48f, -32f, -34f, -24f), Color(0xFFE8DCC8))
            }
        }
        drawPath(tail, Brush.verticalGradient(listOf(Color(0xFF7A2418), Color(0xFF2A0806)), startY = 134f, endY = 196f))
        drawPath(poly(226f, 146f, 232f, 134f, 244f, 126f, 236f, 146f), Color(0xFF3A0A06))
        // legs
        symmetric {
            drawPath(leg, Brush.horizontalGradient(listOf(Color(0xFF3A0A06), Color(0xFF8A2A1A), Color(0xFF3A0A06)), startX = 44f, endX = 78f))
            drawPath(leg, Color(0xFF1A0402), style = Stroke(1.2f))
            for (k in 0..2) {
                val x = 50f + k * 10f
                drawPath(Path().apply { moveTo(x, 190f); quadraticTo(x - 2f, 198f, x - 6f, 200f); lineTo(x + 4f, 192f); close() }, Color(0xFFF2E8D0))
            }
        }
        drawPath(body, Brush.radialGradient(listOf(Color(0xFFC8503A), Color(0xFF7A2418), Color(0xFF2A0806)), center = Offset(90f, 120f), radius = 90f))
        clipPath(body) { drawPath(scales, Color(0xFF1A0402).copy(alpha = 0.4f), style = Stroke(1f)) }
        // golden ventral plates with inner fire glow
        drawPath(chest, Brush.verticalGradient(listOf(Color(0xFFF2D27A), Color(0xFFC08A30), Color(0xFF7A5010)), startY = 96f, endY = 196f))
        clipPath(chest) {
            var y = 104f
            while (y < 196f) { drawLine(Color(0xFF5A3A08).copy(alpha = 0.7f), Offset(70f, y), Offset(130f, y + 2f), 1.2f); y += 9f }
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A20).copy(alpha = 0.6f * glow), Color.Transparent), Offset(100f, 130f), 30f), 30f, Offset(100f, 130f), blendMode = BlendMode.Screen)
        }
        drawPath(body, Color(0xFF1A0402), style = Stroke(1.5f))
        // neck
        drawPath(neck, Brush.horizontalGradient(listOf(Color(0xFF3A0A06), Color(0xFFA83A26), Color(0xFF3A0A06)), startX = 80f, endX = 120f))
        drawPath(Path().apply { moveTo(92f, 62f); lineTo(108f, 62f); lineTo(112f, 108f); lineTo(88f, 108f); close() },
            Brush.verticalGradient(listOf(Color(0xFFE2BE62), Color(0xFFA87A28)), startY = 62f, endY = 108f))
        // dorsal spines peeking over the shoulders
        for (k in -2..2) drawPath(poly(100f + k * 16f - 5f, 96f + k * k * 2f, 100f + k * 16f, 84f + k * k * 2f, 100f + k * 16f + 5f, 96f + k * k * 2f), Color(0xFF2A0806))
        // horns
        symmetric {
            drawPath(hornL, Brush.linearGradient(listOf(Color(0xFFF2E8D0), Color(0xFF8A7A5A), Color(0xFF3A3020)), Offset(80f, 28f), Offset(58f, -16f)))
            drawPath(hornL, Color(0xFF2A2010), style = Stroke(1f))
            drawPath(poly(70f, 50f, 54f, 46f, 70f, 58f), Color(0xFF5A140C))
        }
        drawPath(head, Brush.radialGradient(listOf(Color(0xFFD86048), Color(0xFF8A2A1A), Color(0xFF2A0806)), center = Offset(94f, 40f), radius = 56f))
        drawPath(head, Color(0xFF1A0402), style = Stroke(1.4f))
        // brow ridges + glowing eyes
        val closed = blink(time, 5.8f, 0.2f)
        symmetric {
            val ridge = Path().apply { moveTo(74f, 38f); quadraticTo(86f, 34f, 96f, 44f); lineTo(94f, 48f); quadraticTo(86f, 42f, 76f, 44f); close() }
            drawPath(ridge, Color(0xFF3A0A06))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC040).copy(alpha = 0.7f * glow), Color.Transparent), Offset(86f, 50f), 13f), 13f, Offset(86f, 50f), blendMode = BlendMode.Screen)
            withTransform({ scale(1f, 1f - 0.9f * closed, Offset(86f, 50f)) }) {
                drawOval(Brush.radialGradient(listOf(Color(0xFFFFF4A0), Color(0xFFFF9A10)), center = Offset(86f, 50f), radius = 7f), Offset(80f, 46f), Size(12f, 8f))
                drawOval(Color.Black, Offset(85f, 46f), Size(2f, 8f))
            }
        }
        // snout, nostrils with smoke
        drawOval(Color(0xFF2A0806), Offset(91f, 72f), Size(5f, 4f))
        drawOval(Color(0xFF2A0806), Offset(104f, 72f), Size(5f, 4f))
        val smoke = (time * 0.6f) % 1f
        listOf(93f, 107f).forEach { x ->
            val c = Offset(x + (x - 100f) * smoke, 70f - smoke * 26f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8A8A8A).copy(alpha = 0.5f * (1f - smoke)), Color.Transparent), c, 4f + smoke * 10f), 4f + smoke * 10f, c)
        }
        // jaw with teeth and throat fire
        val mouth = Path().apply { moveTo(84f, 78f); quadraticTo(100f, 76f, 116f, 78f); quadraticTo(110f, 92f, 100f, 93f); quadraticTo(90f, 92f, 84f, 78f); close() }
        drawPath(mouth, Brush.radialGradient(listOf(Color(0xFFFFE070), Color(0xFFFF6A10), Color(0xFF5A0A04)), center = Offset(100f, 86f), radius = 14f * (0.8f + 0.2f * glow)))
        symmetric {
            for (k in 0..2) drawPath(poly(86f + k * 4.5f, 79f, 89f + k * 4.5f, 79f, 87.5f + k * 4.5f, 84f), Color.White)
        }
        drawPath(mouth, Color(0xFF1A0402), style = Stroke(1.1f))
    }
}
