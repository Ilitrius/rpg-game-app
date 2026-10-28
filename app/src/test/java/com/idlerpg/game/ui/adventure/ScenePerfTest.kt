package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.idlerpg.game.domain.world.Zone
import com.idlerpg.game.domain.world.Zones
import com.idlerpg.game.ui.adventure.MonsterPainters.drawMonster
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/** Counts the draw calls of a frame (delegates everything to the real canvas). */
private class CountingCanvas(private val d: Canvas) : Canvas by d {
    var paths = 0; var shapes = 0; var lines = 0; var layers = 0; var shaderDraws = 0; var images = 0
    fun reset() { paths = 0; shapes = 0; lines = 0; layers = 0; shaderDraws = 0; images = 0 }
    val total get() = paths + shapes + lines + images
    private fun sh(p: Paint) { if (p.shader != null) shaderDraws++ }
    override fun drawPath(path: Path, paint: Paint) { paths++; sh(paint); d.drawPath(path, paint) }
    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) { shapes++; sh(paint); d.drawRect(left, top, right, bottom, paint) }
    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) { shapes++; sh(paint); d.drawOval(left, top, right, bottom, paint) }
    override fun drawCircle(center: Offset, radius: Float, paint: Paint) { shapes++; sh(paint); d.drawCircle(center, radius, paint) }
    override fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) { shapes++; sh(paint); d.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint) }
    override fun drawLine(p1: Offset, p2: Offset, paint: Paint) { lines++; sh(paint); d.drawLine(p1, p2, paint) }
    override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) { images++; d.drawImage(image, topLeftOffset, paint) }
    override fun drawImageRect(image: ImageBitmap, srcOffset: androidx.compose.ui.unit.IntOffset, srcSize: androidx.compose.ui.unit.IntSize, dstOffset: androidx.compose.ui.unit.IntOffset, dstSize: androidx.compose.ui.unit.IntSize, paint: Paint) { images++; d.drawImageRect(image, srcOffset, srcSize, dstOffset, dstSize, paint) }
    override fun saveLayer(bounds: Rect, paint: Paint) { layers++; d.saveLayer(bounds, paint) }
}

/**
 * Frame cost of the adventure view without a device: draw calls, shader draws and layers per frame
 * (counted) and CPU raster time (Robolectric native Skia; relative values only). Writes
 * `build/perf.txt`; the asserts are budgets that keep the per-frame work of every zone bounded.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScenePerfTest {
    private val w = 1080; private val h = 2400

    private data class Result(val zone: Zone, val calls: Int, val shaderDraws: Int, val layers: Int, val msPerFrame: Double, val bossMs: Double)

    private fun measure(z: Zone): Result {
        val bmp = ImageBitmap(w, h)
        val counting = CountingCanvas(Canvas(bmp))
        val scope = CanvasDrawScope()
        val scene = createZoneScene(z, Size(w.toFloat(), h.toFloat()))
        val base = minOf(w * 0.66f, h * 0.38f)
        fun frame(i: Int, boss: Boolean) {
            val t = i * 0.016f
            scope.draw(Density(1f), LayoutDirection.Ltr, counting, Size(w.toFloat(), h.toFloat())) {
                scene.drawBackground(this, 0.3f, t)
                drawMonster(if (boss) z.boss else z.monsters[1], Offset(w / 2f, scene.monsterGroundY), base * (if (boss) 1.38f else 1f), t, 0f, 0f, 1f, zone = z)
                scene.drawForeground(this, t)
            }
        }
        repeat(8) { frame(it, false) }
        counting.reset()
        frame(9, false)
        val calls = counting.total; val shaders = counting.shaderDraws; val layers = counting.layers
        val n = 40
        val t0 = System.nanoTime(); repeat(n) { frame(it + 10, false) }; val ms = (System.nanoTime() - t0) / 1e6 / n
        val t1 = System.nanoTime(); repeat(n) { frame(it + 10, true) }; val bms = (System.nanoTime() - t1) / 1e6 / n
        return Result(z, calls, shaders, layers, ms, bms)
    }

    @Test
    fun writePerf() {
        val picks = listOf(5, 6, 10, 25, 40, 70, 100).map { Zones.all[it - 1] } +
            listOf("vulkan_3", "uhrwerk_3", "glutpilz_5", "astral_5").map { Zones.byId(it) }
        val sb = StringBuilder("zone order | draw calls | shader draws | layers | ms/frame | boss ms/frame (1080x2400, CPU raster)\n")
        var maxCalls = 0; var maxLayers = 0; var maxShaders = 0
        picks.forEach { z ->
            val r = measure(z)
            if (z.biome != null) { maxCalls = maxOf(maxCalls, r.calls); maxLayers = maxOf(maxLayers, r.layers); maxShaders = maxOf(maxShaders, r.shaderDraws) }
            sb.appendLine(String.format(Locale.ROOT, "%-12s %3d | %5d | %5d | %d | %7.2f | %7.2f", z.id, z.order, r.calls, r.shaderDraws, r.layers, r.msPerFrame, r.bossMs))
        }
        File("build").mkdirs(); File("build/perf.txt").writeText(sb.toString())
        println(sb)
        assertTrue("saveLayers per frame $maxLayers", maxLayers <= 2)
        assertTrue("draw calls per frame $maxCalls", maxCalls <= PERF_BUDGET_CALLS)
        assertTrue("shader draws per frame $maxShaders", maxShaders <= PERF_BUDGET_SHADERS)
    }

    /** Creature draw ops are built once per (zone, type); their number stays bounded at every stage. */
    @Test
    fun creatureOpCountsStayBounded() {
        val worst = Zones.all.filter { it.biome != null }.flatMap { z -> (z.monsters + z.boss).map { ProceduralMonsters.opCount(z, it) } }.max()
        assertTrue("worst creature has $worst ops", worst <= 200)
    }

    private companion object { const val PERF_BUDGET_CALLS = 300
        const val PERF_BUDGET_SHADERS = 60 }
}
