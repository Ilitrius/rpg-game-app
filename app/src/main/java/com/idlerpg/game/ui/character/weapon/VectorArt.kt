package com.idlerpg.game.ui.character.weapon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath

/*
 * Platform-neutral vector art: paths, gradients and draw ops as plain Kotlin data. The app draws it
 * with Compose ([drawArt]); JVM unit tests can render the very same data offscreen (Java2D) to
 * check the graphic without an emulator. Compose objects (Path, Brush, PathEffect) are created
 * lazily on first draw, so building the art never touches Android classes.
 */

/** One command of a [VPath]. */
internal sealed interface PathCmd {
    data class MoveTo(val p: Offset) : PathCmd
    data class LineTo(val p: Offset) : PathCmd
    data class CubicTo(val c1: Offset, val c2: Offset, val p: Offset) : PathCmd

    /** Separate closed oval sub-path. */
    data class Oval(val rect: Rect) : PathCmd
    data object Close : PathCmd
}

/** Immutable vector path in art units. */
internal class VPath(val commands: List<PathCmd>) {

    /** Bounding box of all points incl. bezier control points (conservative). */
    val controlBounds: Rect by lazy(LazyThreadSafetyMode.PUBLICATION) {
        var l = Float.POSITIVE_INFINITY
        var t = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY
        var b = Float.NEGATIVE_INFINITY
        fun add(p: Offset) {
            l = minOf(l, p.x); t = minOf(t, p.y); r = maxOf(r, p.x); b = maxOf(b, p.y)
        }
        commands.forEach { c ->
            when (c) {
                is PathCmd.MoveTo -> add(c.p)
                is PathCmd.LineTo -> add(c.p)
                is PathCmd.CubicTo -> { add(c.c1); add(c.c2); add(c.p) }
                is PathCmd.Oval -> { add(c.rect.topLeft); add(c.rect.bottomRight) }
                PathCmd.Close -> Unit
            }
        }
        if (l > r) Rect.Zero else Rect(l, t, r, b)
    }

    /** Compose path (built on first draw). */
    val composePath: Path by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Path().apply {
            commands.forEach { c ->
                when (c) {
                    is PathCmd.MoveTo -> moveTo(c.p.x, c.p.y)
                    is PathCmd.LineTo -> lineTo(c.p.x, c.p.y)
                    is PathCmd.CubicTo -> cubicTo(c.c1.x, c.c1.y, c.c2.x, c.c2.y, c.p.x, c.p.y)
                    is PathCmd.Oval -> addOval(c.rect)
                    PathCmd.Close -> close()
                }
            }
        }
    }
}

internal class VPathBuilder {
    private val cmds = ArrayList<PathCmd>()
    fun moveTo(x: Float, y: Float) { cmds += PathCmd.MoveTo(Offset(x, y)) }
    fun lineTo(x: Float, y: Float) { cmds += PathCmd.LineTo(Offset(x, y)) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        cmds += PathCmd.CubicTo(Offset(x1, y1), Offset(x2, y2), Offset(x3, y3))
    }
    fun oval(rect: Rect) { cmds += PathCmd.Oval(rect) }
    fun circle(center: Offset, radius: Float) = oval(Rect(center, radius))
    fun close() { cmds += PathCmd.Close }
    fun build() = VPath(cmds.toList())
}

internal inline fun vpath(block: VPathBuilder.() -> Unit): VPath = VPathBuilder().apply(block).build()

/** Straight segment (for strokes). */
internal fun vline(a: Offset, b: Offset): VPath = vpath { moveTo(a.x, a.y); lineTo(b.x, b.y) }

/** Fill/stroke paint. Gradient stops are (fraction 0..1, color), ascending. */
internal sealed interface VPaint {
    data class Solid(val color: Color) : VPaint

    class Linear(val stops: List<Pair<Float, Color>>, val start: Offset, val end: Offset) : VPaint {
        val brush: Brush by lazy(LazyThreadSafetyMode.PUBLICATION) {
            Brush.linearGradient(colorStops = stops.toTypedArray(), start = start, end = end)
        }
    }

    class Radial(val stops: List<Pair<Float, Color>>, val center: Offset, val radius: Float) : VPaint {
        val brush: Brush by lazy(LazyThreadSafetyMode.PUBLICATION) {
            Brush.radialGradient(colorStops = stops.toTypedArray(), center = center, radius = radius)
        }
    }
}

internal fun solid(color: Color) = VPaint.Solid(color)

internal fun linear(start: Offset, end: Offset, vararg stops: Pair<Float, Color>) =
    VPaint.Linear(stops.toList(), start, end)

/** Colors evenly spread from [start] to [end] (like `Brush.linearGradient(colors)`). */
internal fun linearOf(start: Offset, end: Offset, colors: List<Color>) =
    VPaint.Linear(evenStops(colors), start, end)

internal fun horizontal(startX: Float, endX: Float, vararg stops: Pair<Float, Color>) =
    linear(Offset(startX, 0f), Offset(endX, 0f), *stops)

internal fun vertical(startY: Float, endY: Float, vararg stops: Pair<Float, Color>) =
    linear(Offset(0f, startY), Offset(0f, endY), *stops)

internal fun radial(center: Offset, radius: Float, vararg stops: Pair<Float, Color>) =
    VPaint.Radial(stops.toList(), center, radius)

/** Colors evenly spread from the center (like `Brush.radialGradient(colors)`). */
internal fun radialOf(center: Offset, radius: Float, colors: List<Color>) =
    VPaint.Radial(evenStops(colors), center, radius)

private fun evenStops(colors: List<Color>): List<Pair<Float, Color>> =
    colors.mapIndexed { i, c -> (if (colors.size == 1) 0f else i.toFloat() / (colors.size - 1)) to c }

/** One draw operation. */
internal sealed interface VOp {
    class Fill(val path: VPath, val paint: VPaint) : VOp

    class Stroke(
        val path: VPath,
        val paint: VPaint,
        val width: Float,
        val cap: StrokeCap = StrokeCap.Butt,
        val join: StrokeJoin = StrokeJoin.Miter,
        /** Dash pattern (on, off, …) in art units; null = solid line. */
        val dash: FloatArray? = null,
    ) : VOp {
        val composeStyle: androidx.compose.ui.graphics.drawscope.Stroke by lazy(LazyThreadSafetyMode.PUBLICATION) {
            androidx.compose.ui.graphics.drawscope.Stroke(
                width = width,
                cap = cap,
                join = join,
                pathEffect = dash?.let { PathEffect.dashPathEffect(it) },
            )
        }
    }

    /** Draws [ops] clipped to [clip]. */
    class Clip(val clip: VPath, val ops: List<VOp>) : VOp
}

/** A finished graphic: ordered draw ops (back to front). */
internal class VArt(val ops: List<VOp>) {

    /** Conservative bounds of everything drawn (fills, strokes incl. half width; clips limit their content). */
    val bounds: Rect by lazy(LazyThreadSafetyMode.PUBLICATION) { boundsOf(ops) ?: Rect.Zero }

    private fun boundsOf(list: List<VOp>): Rect? {
        var acc: Rect? = null
        list.forEach { op ->
            val r = when (op) {
                is VOp.Fill -> op.path.controlBounds
                is VOp.Stroke -> op.path.controlBounds.inflate(op.width / 2f)
                is VOp.Clip -> boundsOf(op.ops)?.intersect(op.clip.controlBounds)
            }
            if (r != null) acc = acc?.let { a -> Rect(minOf(a.left, r.left), minOf(a.top, r.top), maxOf(a.right, r.right), maxOf(a.bottom, r.bottom)) } ?: r
        }
        return acc
    }
}

internal class VArtBuilder {
    val ops = ArrayList<VOp>()

    fun fill(path: VPath, paint: VPaint) { ops += VOp.Fill(path, paint) }
    fun fill(path: VPath, color: Color) = fill(path, solid(color))

    fun stroke(
        path: VPath,
        paint: VPaint,
        width: Float,
        cap: StrokeCap = StrokeCap.Butt,
        join: StrokeJoin = StrokeJoin.Miter,
        dash: FloatArray? = null,
    ) { ops += VOp.Stroke(path, paint, width, cap, join, dash) }

    fun stroke(
        path: VPath,
        color: Color,
        width: Float,
        cap: StrokeCap = StrokeCap.Butt,
        join: StrokeJoin = StrokeJoin.Miter,
        dash: FloatArray? = null,
    ) = stroke(path, solid(color), width, cap, join, dash)

    fun line(color: Color, a: Offset, b: Offset, width: Float, cap: StrokeCap = StrokeCap.Butt) =
        stroke(vline(a, b), color, width, cap)

    fun circle(paint: VPaint, center: Offset, radius: Float) = fill(vpath { circle(center, radius) }, paint)
    fun circle(color: Color, center: Offset, radius: Float) = circle(solid(color), center, radius)

    fun clip(path: VPath, block: VArtBuilder.() -> Unit) { ops += VOp.Clip(path, VArtBuilder().apply(block).ops.toList()) }

    fun art(art: VArt) { ops += art.ops }
}

internal inline fun vart(block: VArtBuilder.() -> Unit): VArt = VArt(VArtBuilder().apply(block).ops.toList())

// ============================================================================ Compose renderer

/** Draws [art] in the current transform (art units = current units). */
internal fun DrawScope.drawArt(art: VArt) = art.ops.forEach { drawOp(it) }

private fun DrawScope.drawOp(op: VOp) {
    when (op) {
        is VOp.Fill -> paintPath(op.path.composePath, op.paint, Fill)
        is VOp.Stroke -> paintPath(op.path.composePath, op.paint, op.composeStyle)
        is VOp.Clip -> clipPath(op.clip.composePath) { op.ops.forEach { drawOp(it) } }
    }
}

private fun DrawScope.paintPath(path: Path, paint: VPaint, style: androidx.compose.ui.graphics.drawscope.DrawStyle) {
    when (paint) {
        is VPaint.Solid -> drawPath(path, paint.color, style = style)
        is VPaint.Linear -> drawPath(path, paint.brush, style = style)
        is VPaint.Radial -> drawPath(path, paint.brush, style = style)
    }
}
