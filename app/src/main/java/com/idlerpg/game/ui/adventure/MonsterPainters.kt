package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import com.idlerpg.game.domain.world.MonsterType
import com.idlerpg.game.domain.world.Zone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * High-resolution vector monsters. Every monster is painted in a local unit space of
 * [UNIT] × [UNIT] (front view, facing the hero), standing on the ground line at y = [GROUND],
 * horizontally centered at x = 100. Everything is paths + gradients, so it stays sharp at any scale.
 */
object MonsterPainters {
    const val UNIT = 200f
    const val GROUND = 198f

    /**
     * Draws [type] with its ground center at [ground] (screen px) and [heightPx] as size of the unit box.
     *
     * @param time running seconds (drives blinking etc.)
     * @param breath -1..1 idle breathing phase
     * @param flash 0..1 white hit flash
     * @param alpha overall opacity (death fade / spawn fade-in)
     */
    fun DrawScope.drawMonster(
        type: MonsterType,
        ground: Offset,
        heightPx: Float,
        time: Float,
        breath: Float,
        flash: Float,
        alpha: Float,
        squash: Float = 0f,
        zone: Zone? = null,
    ) {
        if (alpha <= 0.01f) return
        val s = heightPx / UNIT
        withTransform({
            translate(ground.x - 100f * s, ground.y - GROUND * s)
            scale(s, s, Offset.Zero)
        }) {
            drawGroundShadow(alpha * (1f - 0.5f * squash))
            val bounds = Rect(-90f, -90f, 290f, 215f)
            layerPaint.alpha = alpha
            drawIntoCanvas { it.saveLayer(bounds, layerPaint) }
            withTransform({
                scale(1f - 0.025f * breath + 0.12f * squash, 1f + 0.035f * breath - 0.35f * squash, Offset(100f, GROUND))
            }) {
                when (type) {
                    MonsterType.SLIME -> Slime.draw(this, time)
                    MonsterType.GIANT_RAT -> Rat.draw(this, time)
                    MonsterType.BOAR -> Boar.draw(this, time)
                    MonsterType.WOLF -> WolfPainter.draw(this, time, snow = false)
                    MonsterType.SNOW_WOLF -> WolfPainter.draw(this, time, snow = true)
                    MonsterType.FOREST_SPIDER -> SpiderPainter.draw(this, time, cave = false)
                    MonsterType.CAVE_SPIDER -> SpiderPainter.draw(this, time, cave = true)
                    MonsterType.MUSHROOM -> MushroomPainter.draw(this, time)
                    MonsterType.TOXIC_SLIME -> ToxicSlimePainter.draw(this, time)
                    MonsterType.MIRE_TOAD -> ToadPainter.draw(this, time)
                    MonsterType.WISP -> WispPainter.draw(this, time, ice = false)
                    MonsterType.ICE_WISP -> WispPainter.draw(this, time, ice = true)
                    MonsterType.STONE_GOLEM -> GolemPainter.draw(this, time, crystal = false)
                    MonsterType.CRYSTAL_GOLEM -> GolemPainter.draw(this, time, crystal = true)
                    MonsterType.BAT -> BatPainter.draw(this, time)
                    MonsterType.SLIME_KING -> SlimeKingPainter.draw(this, time)
                    MonsterType.TREANT -> TreantPainter.draw(this, time)
                    MonsterType.HYDRA -> HydraPainter.draw(this, time)
                    MonsterType.YETI -> YetiPainter.draw(this, time)
                    MonsterType.DRAGON -> DragonPainter.draw(this, time)
                    MonsterType.GEN_SWARM, MonsterType.GEN_BEAST, MonsterType.GEN_BRUTE, MonsterType.GEN_BOSS ->
                        if (zone?.biome != null) ProceduralMonsters.draw(this, zone, type, time)
                }
                if (flash > 0f) {
                    drawRect(
                        Color(0xFFFFF6EE).copy(alpha = (flash * 0.8f).coerceIn(0f, 1f)),
                        topLeft = bounds.topLeft, size = bounds.size, blendMode = BlendMode.SrcAtop,
                    )
                }
            }
            drawIntoCanvas { it.restore() }
        }
    }

    /** Reused paint of the per-monster layer (draw runs on the main thread only). */
    private val layerPaint = Paint()

    private val shadowBrush = Brush.radialGradient(
        0f to Color.Black.copy(alpha = 0.55f),
        0.6f to Color.Black.copy(alpha = 0.3f),
        1f to Color.Transparent,
        center = Offset(100f, GROUND), radius = 82f,
    )

    private fun DrawScope.drawGroundShadow(alpha: Float) {
        withTransform({ scale(1f, 0.16f, Offset(100f, GROUND)) }) {
            drawCircle(shadowBrush, radius = 82f, center = Offset(100f, GROUND), alpha = alpha)
        }
    }

    /** 0 = open, 1 = closed; a short blink every few seconds (offset per monster). */
    internal fun blink(time: Float, period: Float, offset: Float): Float {
        val p = ((time + offset) % period + period) % period
        return if (p < 0.16f) sin(p / 0.16f * PI.toFloat()) else 0f
    }

    /** Short fur/bristle strokes clipped inside a body, built once from a seeded random. */
    internal fun furStrokes(seed: Int, area: Rect, count: Int, length: Float, angleDeg: Float, spread: Float): Path {
        val r = Random(seed)
        return Path().apply {
            repeat(count) {
                val x = area.left + r.nextFloat() * area.width
                val y = area.top + r.nextFloat() * area.height
                // strokes fan outward from the vertical center line
                val side = if (x < area.center.x) -1f else 1f
                val a = ((angleDeg + side * spread * r.nextFloat()) * PI / 180.0).toFloat()
                val len = length * (0.6f + r.nextFloat() * 0.8f)
                moveTo(x, y)
                quadraticTo(x + cos(a) * len * 0.5f + side * 1.5f, y + sin(a) * len * 0.5f, x + cos(a) * len, y + sin(a) * len)
            }
        }
    }

    // ================================================================ Schleim
    private object Slime {
        val puddleBrush = Brush.radialGradient(
            0f to Color(0xFF5BC23E).copy(alpha = 0.55f), 0.7f to Color(0xFF3B9A2C).copy(alpha = 0.3f), 1f to Color.Transparent,
            center = Offset(100f, 195f), radius = 92f,
        )
        val body = Path().apply {
            moveTo(20f, 192f)
            cubicTo(8f, 168f, 22f, 124f, 50f, 98f)
            cubicTo(68f, 72f, 82f, 44f, 100f, 42f)
            cubicTo(120f, 42f, 134f, 70f, 152f, 98f)
            cubicTo(180f, 124f, 194f, 168f, 182f, 192f)
            cubicTo(150f, 202f, 52f, 202f, 20f, 192f)
            close()
        }
        val bodyBrush = Brush.radialGradient(
            0f to Color(0xFFD2FA98), 0.3f to Color(0xFF8EE163), 0.68f to Color(0xFF45AE38), 1f to Color(0xFF1C6A1E),
            center = Offset(80f, 86f), radius = 130f,
        )
        val depthShade = Brush.verticalGradient(
            0f to Color.Transparent, 0.55f to Color(0xFF0E4A12).copy(alpha = 0.18f), 1f to Color(0xFF0B3A0E).copy(alpha = 0.6f),
            startY = 100f, endY = 200f,
        )
        val core = Path().apply {
            moveTo(62f, 176f)
            cubicTo(56f, 150f, 76f, 128f, 102f, 130f)
            cubicTo(130f, 132f, 146f, 152f, 140f, 176f)
            cubicTo(120f, 186f, 82f, 186f, 62f, 176f)
            close()
        }
        val coreBrush = Brush.radialGradient(
            0f to Color(0xFF1F7A22).copy(alpha = 0.45f), 1f to Color(0xFF1F7A22).copy(alpha = 0f),
            center = Offset(100f, 160f), radius = 48f,
        )
        val rim = Brush.linearGradient(
            0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0xFFEFFFD8).copy(alpha = 0.85f),
            start = Offset(40f, 190f), end = Offset(170f, 70f),
        )
        val crescent = Path().apply {
            moveTo(58f, 104f)
            cubicTo(62f, 80f, 78f, 58f, 98f, 50f)
            cubicTo(86f, 62f, 76f, 80f, 70f, 106f)
            cubicTo(66f, 110f, 60f, 110f, 58f, 104f)
            close()
        }
        val crescentBrush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.85f), 1f to Color.White.copy(alpha = 0.1f),
            start = Offset(90f, 52f), end = Offset(60f, 108f),
        )
        val bubbles = listOf(
            Triple(62f, 152f, 6f), Triple(136f, 162f, 4.5f), Triple(120f, 132f, 3f),
            Triple(76f, 128f, 3.5f), Triple(148f, 138f, 5f), Triple(96f, 172f, 3.2f),
        )
        val mouth = Path().apply {
            moveTo(86f, 142f)
            cubicTo(92f, 156f, 108f, 156f, 114f, 142f)
            cubicTo(106f, 148f, 94f, 148f, 86f, 142f)
            close()
        }
        val eyeCenters = listOf(Offset(79f, 116f), Offset(121f, 116f))
        val scleraBrushes = eyeCenters.map {
            Brush.radialGradient(
                0f to Color.White, 0.7f to Color(0xFFEFF7E8), 1f to Color(0xFFBFD8B4),
                center = it - Offset(3f, 4f), radius = 17f,
            )
        }
        val pupilBrushes = eyeCenters.map {
            Brush.radialGradient(
                0f to Color(0xFF3A4E3A), 0.6f to Color(0xFF142014), 1f to Color(0xFF050A05),
                center = it + Offset(1f, 2f), radius = 8f,
            )
        }

        fun draw(scope: DrawScope, time: Float) = with(scope) {
            drawOval(puddleBrush, Offset(10f, 184f), Size(180f, 20f))
            drawPath(body, bodyBrush)
            drawPath(core, coreBrush)
            drawPath(body, depthShade)
            clipPath(body) {
                bubbles.forEach { (x, y, r) ->
                    val c = Offset(x, y + sin(time * 1.3f + x) * 1.5f)
                    drawCircle(Color(0xFFE6FFD0).copy(alpha = 0.16f), r, c)
                    drawCircle(Color(0xFFE6FFD0).copy(alpha = 0.55f), r, c, style = Stroke(1f))
                    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.32f, c - Offset(r * 0.35f, r * 0.35f))
                }
            }
            drawPath(body, rim, style = Stroke(width = 3.5f))
            drawPath(body, Color(0xFF124A14).copy(alpha = 0.55f), style = Stroke(width = 1.4f))
            drawPath(crescent, crescentBrush)
            drawCircle(Color.White.copy(alpha = 0.9f), 4f, Offset(106f, 54f))
            drawCircle(Color.White.copy(alpha = 0.5f), 2f, Offset(114f, 60f))

            // cheeks
            listOf(Offset(62f, 136f), Offset(138f, 136f)).forEach {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9A8A).copy(alpha = 0.35f), Color.Transparent), it, 11f), 11f, it)
            }
            val closed = blink(time, 3.6f, 0.4f)
            eyeCenters.forEachIndexed { i, c ->
                val eye = Rect(c.x - 12f, c.y - 15f, c.x + 12f, c.y + 15f)
                drawOval(Color(0xFF0E3A10).copy(alpha = 0.35f), eye.topLeft + Offset(0f, 2f), eye.size)
                drawOval(scleraBrushes[i], eye.topLeft, eye.size)
                drawCircle(pupilBrushes[i], 8f, c + Offset(1f, 3f))
                drawCircle(Color.White, 2.8f, c + Offset(-2f, -1f))
                drawCircle(Color.White.copy(alpha = 0.8f), 1.2f, c + Offset(4f, 6f))
                drawOval(Color(0xFF124A14).copy(alpha = 0.7f), eye.topLeft, eye.size, style = Stroke(1.3f))
                if (closed > 0f) {
                    val lid = Path().apply { addOval(eye) }
                    clipPath(lid) {
                        drawRect(Color(0xFF63C447), topLeft = eye.topLeft, size = Size(eye.width, eye.height * closed))
                        drawLine(Color(0xFF124A14), Offset(eye.left, eye.top + eye.height * closed), Offset(eye.right, eye.top + eye.height * closed), 1.6f)
                    }
                }
            }
            drawPath(mouth, Color(0xFF0D3B10))
            drawPath(mouth, Color(0xFF0A2C0C), style = Stroke(1.2f, join = StrokeJoin.Round))
        }
    }

    // ================================================================ Riesenratte
    private object Rat {
        val furDark = Color(0xFF2B211B)
        val tail = Path().apply {
            moveTo(150f, 182f)
            cubicTo(182f, 186f, 196f, 160f, 188f, 130f)
            cubicTo(182f, 108f, 192f, 88f, 198f, 80f)
        }
        val tailBrush = Brush.linearGradient(
            0f to Color(0xFFB58A84), 1f to Color(0xFFE0B5AE), start = Offset(150f, 182f), end = Offset(198f, 80f),
        )
        val body = Path().apply {
            moveTo(100f, 66f)
            cubicTo(152f, 66f, 176f, 118f, 172f, 166f)
            cubicTo(170f, 192f, 142f, 197f, 100f, 197f)
            cubicTo(58f, 197f, 30f, 192f, 28f, 166f)
            cubicTo(24f, 118f, 48f, 66f, 100f, 66f)
            close()
        }
        val bodyBrush = Brush.radialGradient(
            0f to Color(0xFF9C8B7C), 0.45f to Color(0xFF6A5A4D), 0.85f to Color(0xFF3E3129), 1f to Color(0xFF231A15),
            center = Offset(88f, 110f), radius = 110f,
        )
        val belly = Brush.radialGradient(
            0f to Color(0xFFC9B7A2).copy(alpha = 0.85f), 0.6f to Color(0xFFA89580).copy(alpha = 0.4f), 1f to Color.Transparent,
            center = Offset(100f, 158f), radius = 46f,
        )
        val bodyFurDark = furStrokes(3, Rect(30f, 70f, 170f, 195f), 150, 9f, 100f, 40f)
        val bodyFurLight = furStrokes(4, Rect(40f, 75f, 160f, 170f), 80, 7f, 95f, 35f)

        val head = Path().apply {
            moveTo(58f, 70f)
            cubicTo(56f, 40f, 78f, 26f, 100f, 26f)
            cubicTo(122f, 26f, 144f, 40f, 142f, 70f)
            cubicTo(140f, 96f, 120f, 120f, 100f, 126f)
            cubicTo(80f, 120f, 60f, 96f, 58f, 70f)
            close()
        }
        val headBrush = Brush.radialGradient(
            0f to Color(0xFFAE9D8E), 0.5f to Color(0xFF75655A), 1f to Color(0xFF3A2E27),
            center = Offset(92f, 54f), radius = 72f,
        )
        val headFur = furStrokes(6, Rect(60f, 30f, 140f, 100f), 70, 6f, 80f, 70f)
        val muzzle = Rect(76f, 88f, 124f, 122f)
        val muzzleBrush = Brush.radialGradient(
            0f to Color(0xFFD8C6B4), 0.6f to Color(0xFFAE9A88), 1f to Color(0xFF7A685A).copy(alpha = 0f),
            center = Offset(100f, 100f), radius = 30f,
        )
        val ears = listOf(Offset(56f, 38f), Offset(144f, 38f))
        val earOuter = ears.map {
            Brush.radialGradient(0f to Color(0xFF8C7B6C), 1f to Color(0xFF3A2E27), center = it - Offset(4f, 4f), radius = 26f)
        }
        val earInner = ears.map {
            Brush.radialGradient(
                0f to Color(0xFFF6BDB8), 0.6f to Color(0xFFD98C8C), 1f to Color(0xFF9A5560),
                center = it + Offset(0f, 2f), radius = 17f,
            )
        }
        val eyes = listOf(Offset(83f, 68f), Offset(117f, 68f))
        val eyeBrush = eyes.map {
            Brush.radialGradient(
                0f to Color(0xFFFF7A5A), 0.35f to Color(0xFFD01E1E), 0.8f to Color(0xFF5A0606), 1f to Color(0xFF1A0000),
                center = it - Offset(1.5f, 1.5f), radius = 7.5f,
            )
        }
        val eyeGlow = eyes.map { Brush.radialGradient(listOf(Color(0xFFFF3020).copy(alpha = 0.35f), Color.Transparent), it, 16f) }
        val nose = Path().apply {
            moveTo(92f, 108f)
            cubicTo(92f, 102f, 108f, 102f, 108f, 108f)
            cubicTo(108f, 114f, 102f, 118f, 100f, 118f)
            cubicTo(98f, 118f, 92f, 114f, 92f, 108f)
            close()
        }
        val noseBrush = Brush.radialGradient(
            0f to Color(0xFFFFC6C6), 0.5f to Color(0xFFE08A92), 1f to Color(0xFF8A3F4A), center = Offset(97f, 106f), radius = 12f,
        )
        val toothBrush = Brush.verticalGradient(
            0f to Color(0xFFFFF6D6), 0.7f to Color(0xFFE8D29A), 1f to Color(0xFFB79A5C), startY = 118f, endY = 132f,
        )
        val pawBrush = Brush.radialGradient(
            0f to Color(0xFFF2C0BA), 0.7f to Color(0xFFCC8E8A), 1f to Color(0xFF8E5A58), center = Offset(0f, -2f), radius = 12f,
        )

        fun draw(scope: DrawScope, time: Float) = with(scope) {
            // tail (behind body) with a thin taper: thick outline + inner color
            drawPath(tail, Color(0xFF6E4C48), style = Stroke(width = 8f, cap = StrokeCap.Round))
            drawPath(tail, tailBrush, style = Stroke(width = 5.5f, cap = StrokeCap.Round))
            // hind feet
            listOf(Offset(52f, 194f), Offset(148f, 194f)).forEach { c ->
                withTransform({ translate(c.x, c.y); scale(1.6f, 0.75f, Offset.Zero) }) {
                    drawCircle(pawBrush, 11f, Offset.Zero)
                }
            }
            drawPath(body, bodyBrush)
            drawOval(belly, Offset(58f, 112f), Size(84f, 86f))
            clipPath(body) {
                drawPath(bodyFurDark, furDark.copy(alpha = 0.55f), style = Stroke(1.3f, cap = StrokeCap.Round))
                drawPath(bodyFurLight, Color(0xFFCDBBA8).copy(alpha = 0.3f), style = Stroke(1f, cap = StrokeCap.Round))
            }
            drawPath(body, Color(0xFF1A1310).copy(alpha = 0.7f), style = Stroke(1.5f))

            // front paws with little toes
            listOf(Offset(80f, 188f), Offset(120f, 188f)).forEach { c ->
                withTransform({ translate(c.x, c.y) }) {
                    drawOval(Color.Black.copy(alpha = 0.3f), Offset(-11f, -2f), Size(22f, 12f))
                    drawOval(pawBrush, Offset(-10f, -7f), Size(20f, 13f))
                    for (k in -1..1) drawCircle(Color(0xFFF7CFCA), 2.6f, Offset(k * 5.5f, 4f))
                }
            }

            // ears
            ears.forEachIndexed { i, c ->
                drawCircle(earOuter[i], 24f, c)
                drawCircle(earInner[i], 16f, c + Offset(0f, 2f))
                drawCircle(Color(0xFF1A1310).copy(alpha = 0.6f), 24f, c, style = Stroke(1.3f))
            }

            drawPath(head, headBrush)
            clipPath(head) {
                drawPath(headFur, furDark.copy(alpha = 0.45f), style = Stroke(1.1f, cap = StrokeCap.Round))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF241A14).copy(alpha = 0.5f), Color.Transparent), Offset(100f, 130f), 50f), 50f, Offset(100f, 130f))
            }
            drawOval(muzzleBrush, muzzle.topLeft, muzzle.size)
            drawPath(head, Color(0xFF1A1310).copy(alpha = 0.7f), style = Stroke(1.4f))

            // angry brows
            drawLine(furDark, Offset(70f, 56f), Offset(90f, 62f), 3.2f, StrokeCap.Round)
            drawLine(furDark, Offset(130f, 56f), Offset(110f, 62f), 3.2f, StrokeCap.Round)

            val closed = blink(time, 4.3f, 1.7f)
            eyes.forEachIndexed { i, c ->
                drawCircle(eyeGlow[i], 16f, c)
                withTransform({ scale(1f, 1f - 0.9f * closed, c) }) {
                    drawCircle(eyeBrush[i], 7f, c)
                    drawCircle(Color.White.copy(alpha = 0.9f), 1.8f, c - Offset(2.2f, 2.4f))
                }
            }

            // teeth
            drawRoundRect(toothBrush, Offset(94.5f, 117f), Size(5.2f, 14f), androidx.compose.ui.geometry.CornerRadius(1.5f))
            drawRoundRect(toothBrush, Offset(100.3f, 117f), Size(5.2f, 14f), androidx.compose.ui.geometry.CornerRadius(1.5f))
            drawLine(Color(0xFF6E5A3A), Offset(100f, 118f), Offset(100f, 130f), 0.8f)

            drawPath(nose, noseBrush)
            drawCircle(Color(0xFF5A2A30), 1.6f, Offset(96.5f, 110f))
            drawCircle(Color(0xFF5A2A30), 1.6f, Offset(103.5f, 110f))
            drawCircle(Color.White.copy(alpha = 0.7f), 1.5f, Offset(96f, 105f))

            // whiskers
            val wiggle = sin(time * 3f) * 1.2f
            for (k in 0..2) {
                val y = 104f + k * 5f
                val wl = Path().apply { moveTo(84f, y); quadraticTo(66f, y - 4f + k * 2f + wiggle, 44f, y - 6f + k * 6f + wiggle) }
                val wr = Path().apply { moveTo(116f, y); quadraticTo(134f, y - 4f + k * 2f + wiggle, 156f, y - 6f + k * 6f + wiggle) }
                drawPath(wl, Color(0xFFE8E0D6).copy(alpha = 0.75f), style = Stroke(0.9f, cap = StrokeCap.Round))
                drawPath(wr, Color(0xFFE8E0D6).copy(alpha = 0.75f), style = Stroke(0.9f, cap = StrokeCap.Round))
            }
        }
    }

    // ================================================================ Wildschwein
    private object Boar {
        val hoofBrush = Brush.verticalGradient(0f to Color(0xFF3A3430), 1f to Color(0xFF0E0C0A), startY = 40f, endY = 52f)
        val legBrush = Brush.horizontalGradient(
            0f to Color(0xFF1E140D), 0.5f to Color(0xFF4A3322), 1f to Color(0xFF1E140D), startX = 0f, endX = 24f,
        )
        val body = Path().apply {
            moveTo(100f, 54f)
            cubicTo(160f, 50f, 196f, 92f, 194f, 140f)
            cubicTo(192f, 166f, 160f, 174f, 100f, 174f)
            cubicTo(40f, 174f, 8f, 166f, 6f, 140f)
            cubicTo(4f, 92f, 40f, 50f, 100f, 54f)
            close()
        }
        val bodyBrush = Brush.radialGradient(
            0f to Color(0xFF7A5639), 0.45f to Color(0xFF4E3524), 0.85f to Color(0xFF2A1C12), 1f to Color(0xFF160E09),
            center = Offset(86f, 92f), radius = 120f,
        )
        val bodyFur = furStrokes(12, Rect(10f, 60f, 190f, 184f), 220, 11f, 100f, 50f)
        val bodyFurLight = furStrokes(13, Rect(30f, 62f, 170f, 140f), 90, 8f, 95f, 50f)
        val mane = Path().apply {
            val r = Random(21)
            var x = 52f
            moveTo(x, 64f)
            while (x < 148f) {
                val t = (x - 100f) / 48f
                val top = 40f + t * t * 18f
                lineTo(x + 3f, top - r.nextFloat() * 8f)
                x += 6f + r.nextFloat() * 3f
                lineTo(x, top + 12f)
            }
            lineTo(148f, 64f)
            close()
        }
        val maneBrush = Brush.verticalGradient(0f to Color(0xFF3A2A1E), 1f to Color(0xFF120B07), startY = 30f, endY = 70f)
        val head = Path().apply {
            moveTo(56f, 72f)
            cubicTo(56f, 50f, 80f, 44f, 100f, 44f)
            cubicTo(120f, 44f, 144f, 50f, 144f, 72f)
            cubicTo(146f, 100f, 132f, 128f, 124f, 146f)
            cubicTo(116f, 160f, 84f, 160f, 76f, 146f)
            cubicTo(68f, 128f, 54f, 100f, 56f, 72f)
            close()
        }
        val headBrush = Brush.radialGradient(
            0f to Color(0xFF8E6A4C), 0.5f to Color(0xFF5A3F2B), 1f to Color(0xFF241810),
            center = Offset(92f, 78f), radius = 80f,
        )
        val headFur = furStrokes(15, Rect(58f, 50f, 142f, 140f), 90, 7f, 90f, 60f)
        val ears = listOf(
            Path().apply { moveTo(60f, 66f); lineTo(40f, 34f); quadraticTo(62f, 40f, 76f, 54f); close() },
            Path().apply { moveTo(140f, 66f); lineTo(160f, 34f); quadraticTo(138f, 40f, 124f, 54f); close() },
        )
        val earsInner = listOf(
            Path().apply { moveTo(60f, 62f); lineTo(46f, 40f); quadraticTo(62f, 45f, 70f, 54f); close() },
            Path().apply { moveTo(140f, 62f); lineTo(154f, 40f); quadraticTo(138f, 45f, 130f, 54f); close() },
        )
        val earBrush = Brush.verticalGradient(0f to Color(0xFF3A2719), 1f to Color(0xFF6A4A33), startY = 34f, endY = 66f)
        val earInnerBrush = Brush.verticalGradient(0f to Color(0xFF6E3F38), 1f to Color(0xFFB07A6A), startY = 40f, endY = 62f)
        val snoutRect = Rect(76f, 136f, 124f, 164f)
        val snoutBrush = Brush.radialGradient(
            0f to Color(0xFFD8A08E), 0.55f to Color(0xFFA9705F), 1f to Color(0xFF5E3A2E),
            center = Offset(96f, 144f), radius = 30f,
        )
        val tusks = listOf(
            Path().apply { moveTo(80f, 150f); cubicTo(70f, 150f, 60f, 140f, 58f, 120f); cubicTo(64f, 134f, 72f, 142f, 84f, 143f); close() },
            Path().apply { moveTo(120f, 150f); cubicTo(130f, 150f, 140f, 140f, 142f, 120f); cubicTo(136f, 134f, 128f, 142f, 116f, 143f); close() },
        )
        val tuskBrush = Brush.linearGradient(
            0f to Color(0xFFFFFBEE), 0.5f to Color(0xFFE6D6AE), 1f to Color(0xFF9C8858),
            start = Offset(60f, 120f), end = Offset(84f, 150f),
        )
        val tuskBrushR = Brush.linearGradient(
            0f to Color(0xFFFFFBEE), 0.5f to Color(0xFFE6D6AE), 1f to Color(0xFF9C8858),
            start = Offset(140f, 120f), end = Offset(116f, 150f),
        )
        val eyes = listOf(Offset(82f, 90f), Offset(118f, 90f))
        val irisBrush = eyes.map {
            Brush.radialGradient(
                0f to Color(0xFFFFD27A), 0.5f to Color(0xFFE08A1E), 1f to Color(0xFF6A3008),
                center = it - Offset(1f, 1f), radius = 5.5f,
            )
        }

        fun draw(scope: DrawScope, time: Float) = with(scope) {
            // hind legs (darker, behind) and front legs
            listOf(30f to true, 146f to true, 62f to false, 114f to false).forEach { (x, hind) ->
                withTransform({ translate(x, 146f) }) {
                    val legShape = Path().apply {
                        moveTo(0f, 0f); lineTo(24f, 0f)
                        cubicTo(24f, 18f, 21f, 30f, 20f, 42f)
                        lineTo(4f, 42f)
                        cubicTo(3f, 30f, 0f, 18f, 0f, 0f)
                        close()
                    }
                    drawPath(legShape, legBrush)
                    drawPath(legShape, Color(0xFF0E0805).copy(alpha = if (hind) 0.45f else 0.15f))
                    drawPath(legShape, Color(0xFF0E0805).copy(alpha = 0.7f), style = Stroke(1.2f))
                    drawRoundRect(hoofBrush, Offset(2f, 40f), Size(20f, 12f), androidx.compose.ui.geometry.CornerRadius(4f))
                    drawLine(Color.Black.copy(alpha = 0.7f), Offset(12f, 41f), Offset(12f, 52f), 1.4f)
                    drawLine(Color.White.copy(alpha = 0.18f), Offset(5f, 42f), Offset(9f, 42f), 1.2f, StrokeCap.Round)
                }
            }
            drawPath(body, bodyBrush)
            clipPath(body) {
                drawPath(bodyFur, Color(0xFF120B07).copy(alpha = 0.55f), style = Stroke(1.4f, cap = StrokeCap.Round))
                drawPath(bodyFurLight, Color(0xFFB08A66).copy(alpha = 0.28f), style = Stroke(1.1f, cap = StrokeCap.Round))
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f), startY = 120f, endY = 176f),
                    topLeft = Offset(0f, 120f), size = Size(200f, 60f),
                )
            }
            drawPath(body, Color(0xFF0E0805).copy(alpha = 0.7f), style = Stroke(1.6f))
            drawPath(mane, maneBrush)

            ears.forEachIndexed { i, e ->
                drawPath(e, earBrush)
                drawPath(earsInner[i], earInnerBrush)
                drawPath(e, Color(0xFF0E0805).copy(alpha = 0.7f), style = Stroke(1.2f, join = StrokeJoin.Round))
            }

            drawPath(head, headBrush)
            clipPath(head) {
                drawPath(headFur, Color(0xFF140D08).copy(alpha = 0.5f), style = Stroke(1.2f, cap = StrokeCap.Round))
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFC09070).copy(alpha = 0.35f), Color.Transparent), Offset(100f, 70f), 30f),
                    30f, Offset(100f, 70f),
                )
            }
            drawPath(head, Color(0xFF0E0805).copy(alpha = 0.75f), style = Stroke(1.5f))

            // brow ridges (angry)
            val browL = Path().apply { moveTo(68f, 80f); quadraticTo(80f, 80f, 92f, 88f); quadraticTo(80f, 84f, 68f, 84f); close() }
            val browR = Path().apply { moveTo(132f, 80f); quadraticTo(120f, 80f, 108f, 88f); quadraticTo(120f, 84f, 132f, 84f); close() }
            drawPath(browL, Color(0xFF1A110A))
            drawPath(browR, Color(0xFF1A110A))

            val closed = blink(time, 5.1f, 2.9f)
            eyes.forEachIndexed { i, c ->
                withTransform({ scale(1f, 1f - 0.9f * closed, c) }) {
                    drawOval(Color(0xFF1A0E06), c - Offset(7f, 5f), Size(14f, 10f))
                    drawCircle(irisBrush[i], 4.6f, c)
                    drawCircle(Color.Black, 2.1f, c)
                    drawCircle(Color.White.copy(alpha = 0.9f), 1.2f, c - Offset(1.6f, 1.6f))
                }
            }

            // snout
            drawOval(Color.Black.copy(alpha = 0.35f), snoutRect.topLeft + Offset(0f, 3f), snoutRect.size)
            drawOval(snoutBrush, snoutRect.topLeft, snoutRect.size)
            drawOval(Color(0xFF3E2219).copy(alpha = 0.8f), snoutRect.topLeft, snoutRect.size, style = Stroke(1.3f))
            drawOval(Color(0xFF2A120C), Offset(88f, 146f), Size(7f, 10f))
            drawOval(Color(0xFF2A120C), Offset(105f, 146f), Size(7f, 10f))
            drawOval(Color.White.copy(alpha = 0.35f), Offset(86f, 139f), Size(16f, 5f))
            // mouth line + tusks
            val mouth = Path().apply { moveTo(80f, 166f); quadraticTo(100f, 174f, 120f, 166f) }
            drawPath(mouth, Color(0xFF1A0E08), style = Stroke(2f, cap = StrokeCap.Round))
            drawPath(tusks[0], tuskBrush)
            drawPath(tusks[1], tuskBrushR)
            tusks.forEach { drawPath(it, Color(0xFF5E5030).copy(alpha = 0.8f), style = Stroke(1f, join = StrokeJoin.Round)) }

            // breath puffs from the snout
            val puff = ((time * 0.6f) % 1f)
            if (puff < 0.5f) {
                val a = (0.5f - puff) * 0.5f
                listOf(-1f, 1f).forEach { side ->
                    val c = Offset(100f + side * (14f + puff * 30f), 160f + puff * 6f)
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), c, 6f + puff * 14f),
                        6f + puff * 14f, c,
                    )
                }
            }
        }
    }
}
