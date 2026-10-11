package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Small geometry helpers shared by the painted zone scenes. All results are cached by the scenes. */
internal object SceneKit {

    /** Adds a pointed leaf (two quadratic arcs) centered at [cx],[cy], [len] long, rotated by [angle] (rad). */
    fun addLeaf(p: Path, cx: Float, cy: Float, len: Float, width: Float, angle: Float) {
        val dx = cos(angle) * len / 2f
        val dy = sin(angle) * len / 2f
        val nx = -sin(angle) * width
        val ny = cos(angle) * width
        p.moveTo(cx - dx, cy - dy)
        p.quadraticTo(cx + nx, cy + ny, cx + dx, cy + dy)
        p.quadraticTo(cx - nx, cy - ny, cx - dx, cy - dy)
        p.close()
    }

    /**
     * Adds a fern frond rooted at [x],[y]: a curved stem of [height] leaning by [lean] with leaflets
     * on both sides that shrink towards the tip.
     */
    fun addFern(p: Path, x: Float, y: Float, height: Float, lean: Float, r: Random) {
        val steps = 9
        var px = x
        var py = y
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val nx = x + lean * t * t
            val ny = y - height * t
            val ang = kotlin.math.atan2(ny - py, nx - px)
            val size = height * 0.22f * (1f - t * 0.75f)
            listOf(-1f, 1f).forEach { side ->
                val a = ang + side * (1.15f + r.nextFloat() * 0.25f)
                val cx = (px + nx) / 2f + cos(a) * size * 0.5f
                val cy = (py + ny) / 2f + sin(a) * size * 0.5f
                addLeaf(p, cx, cy, size, size * 0.22f, a)
            }
            px = nx
            py = ny
        }
        // stem
        p.moveTo(x - height * 0.012f, y)
        p.quadraticTo(x + lean * 0.3f, y - height * 0.5f, x + lean, y - height)
        p.quadraticTo(x + lean * 0.3f + height * 0.01f, y - height * 0.5f, x + height * 0.012f, y)
        p.close()
    }

    /** Tapered, slightly bent trunk from ground ([x],[yBottom]) up to [yTop]. */
    fun trunk(x: Float, yBottom: Float, yTop: Float, wBottom: Float, wTop: Float, bend: Float): Path = Path().apply {
        val h = yBottom - yTop
        moveTo(x - wBottom / 2f - wBottom * 0.35f, yBottom)
        cubicTo(x - wBottom / 2f, yBottom - h * 0.08f, x - wBottom / 2f + bend * 0.3f, yBottom - h * 0.4f, x - wTop / 2f + bend, yTop)
        lineTo(x + wTop / 2f + bend, yTop)
        cubicTo(x + wBottom / 2f + bend * 0.3f, yBottom - h * 0.4f, x + wBottom / 2f, yBottom - h * 0.08f, x + wBottom / 2f + wBottom * 0.35f, yBottom)
        close()
    }

    /** Irregular rock outline around [c] with radius [rx]×[ry] (flat bottom), [n] vertices. */
    fun rock(c: Offset, rx: Float, ry: Float, r: Random, n: Int = 9): Path = Path().apply {
        for (i in 0..n) {
            val a = PI.toFloat() + i / n.toFloat() * PI.toFloat()
            val k = 0.75f + r.nextFloat() * 0.35f
            val x = c.x + cos(a) * rx * k
            val y = c.y + sin(a) * ry * k
            if (i == 0) moveTo(c.x - rx, c.y) else lineTo(x, y)
        }
        lineTo(c.x + rx, c.y)
        quadraticTo(c.x, c.y + ry * 0.18f, c.x - rx, c.y)
        close()
    }

    /** Standard dark vignette. */
    fun vignette(w: Float, h: Float, strength: Float, color: Color = Color.Black, centerY: Float = 0.46f) = Brush.radialGradient(
        0f to Color.Transparent,
        0.6f to Color.Transparent,
        1f to color.copy(alpha = strength),
        center = Offset(w * 0.5f, h * centerY), radius = maxOf(w, h) * 0.78f,
    )

    /** Linear blend of two colors. */
    fun mix(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t,
        a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t,
        a.alpha + (b.alpha - a.alpha) * t,
    )

    /** Smooth periodic wave with whole cycles per 60 s (seam-free with the looping scene time). */
    fun wave(time: Float, cyclesPerMinute: Int, phase: Float = 0f): Float =
        sin(time / 60f * 2f * PI.toFloat() * cyclesPerMinute + phase)
}
