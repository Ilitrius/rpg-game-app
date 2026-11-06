package com.idlerpg.game.ui.character.rank

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.idlerpg.game.R
import com.idlerpg.game.ui.equipment.ItemSprites
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Metal tone of a rank: bronze, silver, gold, then violet for the highest ranks (light, dark). */
internal fun rankTones(rank: Int): Pair<Color, Color> = when {
    rank <= 0 -> Color(0xFF5B5B63) to Color(0xFF2C2C32)
    rank <= 3 -> Color(0xFFE0A877) to Color(0xFF7A4A22)
    rank <= 6 -> Color(0xFFEFF2F5) to Color(0xFF7C8793)
    rank <= 10 -> Color(0xFFFFE08A) to Color(0xFFB07A12)
    else -> Color(0xFFC9A6FF) to Color(0xFF5A2FB0)
}

/**
 * Vector badge of a rank: a shield in the rank's metal with one to five chevrons (rank 1-5); ranks 6-10 keep
 * five chevrons and add stars - the higher the rank, the richer the decoration.
 */
@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val r = rank.coerceIn(0, 15)
    val res = badgeRes(r)
    val resources = LocalContext.current.resources
    var bitmap by remember(res) { mutableStateOf<ImageBitmap?>(ItemSprites.cached(res)) }
    LaunchedEffect(res) { if (bitmap == null) bitmap = ItemSprites.load(resources, res) }
    val img = bitmap
    if (img != null) {
        Image(img, contentDescription = null, modifier = modifier.size(size), filterQuality = FilterQuality.High)
    } else {
        Canvas(modifier.size(size)) { drawRankBadge(r) }
    }
}

/** Raytraced badge of a rank (`tools/render_items/badges.py`). */
private fun badgeRes(rank: Int): Int = when (rank) {
    0 -> R.drawable.item_rank_badge_0
    1 -> R.drawable.item_rank_badge_1
    2 -> R.drawable.item_rank_badge_2
    3 -> R.drawable.item_rank_badge_3
    4 -> R.drawable.item_rank_badge_4
    5 -> R.drawable.item_rank_badge_5
    6 -> R.drawable.item_rank_badge_6
    7 -> R.drawable.item_rank_badge_7
    8 -> R.drawable.item_rank_badge_8
    9 -> R.drawable.item_rank_badge_9
    10 -> R.drawable.item_rank_badge_10
    11 -> R.drawable.item_rank_badge_11
    12 -> R.drawable.item_rank_badge_12
    13 -> R.drawable.item_rank_badge_13
    14 -> R.drawable.item_rank_badge_14
    else -> R.drawable.item_rank_badge_15
}

private fun DrawScope.drawRankBadge(rank: Int) {
    val w = size.width
    val h = size.height
    val (light, dark) = rankTones(rank)
    val shield = Path().apply {
        moveTo(w * 0.50f, h * 0.04f)
        cubicTo(w * 0.68f, h * 0.14f, w * 0.84f, h * 0.14f, w * 0.92f, h * 0.12f)
        cubicTo(w * 0.94f, h * 0.55f, w * 0.80f, h * 0.82f, w * 0.50f, h * 0.97f)
        cubicTo(w * 0.20f, h * 0.82f, w * 0.06f, h * 0.55f, w * 0.08f, h * 0.12f)
        cubicTo(w * 0.16f, h * 0.14f, w * 0.32f, h * 0.14f, w * 0.50f, h * 0.04f)
        close()
    }
    drawPath(shield, Brush.linearGradient(listOf(light, dark), Offset(w * 0.2f, 0f), Offset(w * 0.8f, h)))
    drawPath(shield, Color.Black.copy(alpha = 0.55f), style = Stroke(w * 0.045f, join = StrokeJoin.Round))
    if (rank == 0) return
    val chevrons = rank.coerceAtMost(5)
    val ink = Color(0xFF221A12)
    val gap = h * 0.095f
    val top = h * (if (rank <= 5) 0.50f - 0.04f * chevrons else 0.30f)
    for (i in 0 until chevrons) {
        val y = top + i * gap
        val p = Path().apply {
            moveTo(w * 0.27f, y)
            lineTo(w * 0.50f, y + h * 0.10f)
            lineTo(w * 0.73f, y)
        }
        drawPath(p, ink, style = Stroke(w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val stars = (rank - 5).coerceIn(0, 5)
    for (i in 0 until stars) {
        val cx = w * (0.50f + (i - (stars - 1) / 2f) * 0.13f)
        star(Offset(cx, h * 0.20f), w * 0.055f, ink)
    }
}

private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val ang = -PI / 2 + i * PI / 5
        val rad = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (cos(ang) * rad).toFloat()
        val y = c.y + (sin(ang) * rad).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}
