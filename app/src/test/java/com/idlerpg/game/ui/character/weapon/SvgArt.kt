package com.idlerpg.game.ui.character.weapon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Writes [VArt] as SVG (pure Kotlin — `java.awt` is not on the Android unit-test classpath), so JVM
 * tests can export the exact vector data of a graphic for a visual check without an emulator. Open
 * the file in any browser, or rasterize it, e.g.
 * `chrome --headless --screenshot=out.png --window-size=W,H file:///…/x.svg`.
 */
class SvgWriter(private val width: Int, private val height: Int) {
    private val defs = StringBuilder()
    private val body = StringBuilder()
    private var nextId = 0

    fun raw(svg: String) {
        body.append(svg).append('\n')
    }

    /** Opens a group with an SVG transform (e.g. `translate(…) rotate(…)`); close with [end]. */
    fun begin(transform: String) = raw("<g transform=\"$transform\">")
    fun end() = raw("</g>")

    internal fun art(art: VArt) = art.ops.forEach { op(it) }

    fun text(x: Float, y: Float, size: Float, text: String, color: String = "#fff") =
        raw("<text x=\"${n(x)}\" y=\"${n(y)}\" font-family=\"sans-serif\" font-size=\"${n(size)}\" fill=\"$color\">$text</text>")

    fun document(): String =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\">\n" +
            "<defs>\n$defs</defs>\n$body</svg>\n"

    private fun op(op: VOp) {
        when (op) {
            is VOp.Fill -> raw("<path d=\"${d(op.path)}\" ${paintAttr("fill", op.paint)}/>")
            is VOp.Stroke -> {
                val cap = when (op.cap) { StrokeCap.Round -> "round"; StrokeCap.Square -> "square"; else -> "butt" }
                val join = when (op.join) { StrokeJoin.Round -> "round"; StrokeJoin.Bevel -> "bevel"; else -> "miter" }
                val dash = op.dash?.joinToString(",") { n(it) }?.let { " stroke-dasharray=\"$it\"" } ?: ""
                raw(
                    "<path d=\"${d(op.path)}\" fill=\"none\" ${paintAttr("stroke", op.paint)} stroke-width=\"${n(op.width)}\" " +
                        "stroke-linecap=\"$cap\" stroke-linejoin=\"$join\" stroke-miterlimit=\"4\"$dash/>",
                )
            }
            is VOp.Clip -> {
                val id = "c${nextId++}"
                defs.append("<clipPath id=\"$id\"><path d=\"${d(op.clip)}\"/></clipPath>\n")
                raw("<g clip-path=\"url(#$id)\">")
                op.ops.forEach { op(it) }
                raw("</g>")
            }
        }
    }

    private fun paintAttr(attr: String, p: VPaint): String = when (p) {
        is VPaint.Solid -> "$attr=\"${rgb(p.color)}\" $attr-opacity=\"${n(p.color.alpha)}\""
        is VPaint.Linear -> {
            val id = "g${nextId++}"
            defs.append(
                "<linearGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" x1=\"${n(p.start.x)}\" y1=\"${n(p.start.y)}\" " +
                    "x2=\"${n(p.end.x)}\" y2=\"${n(p.end.y)}\">${stops(p.stops)}</linearGradient>\n",
            )
            "$attr=\"url(#$id)\""
        }
        is VPaint.Radial -> {
            val id = "g${nextId++}"
            defs.append(
                "<radialGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" cx=\"${n(p.center.x)}\" cy=\"${n(p.center.y)}\" " +
                    "r=\"${n(p.radius)}\">${stops(p.stops)}</radialGradient>\n",
            )
            "$attr=\"url(#$id)\""
        }
    }

    private fun stops(stops: List<Pair<Float, Color>>) = stops.joinToString("") { (f, c) ->
        "<stop offset=\"${n(f)}\" stop-color=\"${rgb(c)}\" stop-opacity=\"${n(c.alpha)}\"/>"
    }

    companion object {
        fun n(v: Float): String = String.format(Locale.ROOT, "%.3f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

        fun rgb(c: Color): String = String.format(
            Locale.ROOT, "#%02x%02x%02x",
            (c.red * 255f + 0.5f).toInt().coerceIn(0, 255),
            (c.green * 255f + 0.5f).toInt().coerceIn(0, 255),
            (c.blue * 255f + 0.5f).toInt().coerceIn(0, 255),
        )

        internal fun d(p: VPath): String = buildString {
            p.commands.forEach { c ->
                when (c) {
                    is PathCmd.MoveTo -> append("M${n(c.p.x)} ${n(c.p.y)}")
                    is PathCmd.LineTo -> append("L${n(c.p.x)} ${n(c.p.y)}")
                    is PathCmd.CubicTo -> append("C${n(c.c1.x)} ${n(c.c1.y)} ${n(c.c2.x)} ${n(c.c2.y)} ${n(c.p.x)} ${n(c.p.y)}")
                    is PathCmd.Oval -> {
                        val r = c.rect
                        val rx = r.width / 2f
                        val ry = r.height / 2f
                        append("M${n(r.left)} ${n(r.center.y)}")
                        append("A${n(rx)} ${n(ry)} 0 1 0 ${n(r.right)} ${n(r.center.y)}")
                        append("A${n(rx)} ${n(ry)} 0 1 0 ${n(r.left)} ${n(r.center.y)}Z")
                    }
                    PathCmd.Close -> append("Z")
                }
            }
        }
    }
}

/** Flattened polygons of a [VPath] (cubics subdivided) — for point-in-shape checks in tests. */
internal fun VPath.polygons(steps: Int = 16): List<List<Offset>> {
    val result = ArrayList<List<Offset>>()
    var cur = ArrayList<Offset>()
    var last = Offset.Zero
    fun flush() {
        if (cur.size >= 3) result += cur
        cur = ArrayList()
    }
    commands.forEach { c ->
        when (c) {
            is PathCmd.MoveTo -> { flush(); cur += c.p; last = c.p }
            is PathCmd.LineTo -> { cur += c.p; last = c.p }
            is PathCmd.CubicTo -> {
                val p0 = last
                for (i in 1..steps) {
                    val t = i.toFloat() / steps
                    val u = 1f - t
                    cur += p0 * (u * u * u) + c.c1 * (3 * u * u * t) + c.c2 * (3 * u * t * t) + c.p * (t * t * t)
                }
                last = c.p
            }
            is PathCmd.Oval -> {
                flush()
                val r = c.rect
                for (i in 0 until 48) {
                    val a = i / 48.0 * 2 * Math.PI
                    cur += Offset(r.center.x + r.width / 2f * cos(a).toFloat(), r.center.y + r.height / 2f * sin(a).toFloat())
                }
                flush()
            }
            PathCmd.Close -> { flush() }
        }
    }
    flush()
    return result
}

/** Non-zero winding test of [p] against the flattened [VPath]. */
internal fun VPath.containsPoint(p: Offset): Boolean {
    var winding = 0
    polygons().forEach { poly ->
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            if (a.y <= p.y) {
                if (b.y > p.y && cross(a, b, p) > 0) winding++
            } else if (b.y <= p.y && cross(a, b, p) < 0) {
                winding--
            }
        }
    }
    return winding != 0
}

private fun cross(a: Offset, b: Offset, p: Offset) = (b.x - a.x) * (p.y - a.y) - (p.x - a.x) * (b.y - a.y)

/** True if any fill of [art] (respecting clips loosely: clipped fills count too) covers [p]. */
internal fun VArt.coversPoint(p: Offset): Boolean {
    fun any(ops: List<VOp>): Boolean = ops.any { op ->
        when (op) {
            is VOp.Fill -> op.paint.isOpaque() && op.path.containsPoint(p)
            is VOp.Stroke -> false
            is VOp.Clip -> op.clip.containsPoint(p) && any(op.ops)
        }
    }
    return any(ops)
}

private fun VPaint.isOpaque(): Boolean = when (this) {
    is VPaint.Solid -> color.alpha > 0.99f
    is VPaint.Linear -> stops.all { it.second.alpha > 0.99f }
    is VPaint.Radial -> stops.all { it.second.alpha > 0.99f }
}
