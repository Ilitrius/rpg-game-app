package com.idlerpg.game.ui.character.prestige

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.idlerpg.game.domain.prestige.Talents
import com.idlerpg.game.domain.prestige.TalentBranch

internal val SoulBlue = Color(0xFF7FD6FF)
internal val SoulDeep = Color(0xFF3A6BD8)

internal fun branchColor(b: TalentBranch) = when (b) {
    TalentBranch.PROGRESS -> Color(0xFF8BD17C)
    TalentBranch.COMBAT -> Color(0xFFFF7A66)
    TalentBranch.LOOT -> Color(0xFFE6BC58)
}

/** Vector soul: flame-like drop with inner glow and two eyes of light. Pure vector, resolution independent. */
@Composable
fun SoulIcon(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawSoul() }
}

private fun DrawScope.drawSoul() {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(w * 0.5f, h * 0.04f)
        cubicTo(w * 0.62f, h * 0.30f, w * 0.90f, h * 0.42f, w * 0.88f, h * 0.68f)
        cubicTo(w * 0.86f, h * 0.90f, w * 0.66f, h * 0.97f, w * 0.5f, h * 0.97f)
        cubicTo(w * 0.34f, h * 0.97f, w * 0.14f, h * 0.90f, w * 0.12f, h * 0.68f)
        cubicTo(w * 0.10f, h * 0.42f, w * 0.38f, h * 0.30f, w * 0.5f, h * 0.04f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color.White, SoulBlue, SoulDeep), startY = 0f, endY = h))
    drawPath(body, SoulDeep.copy(alpha = 0.9f), style = Stroke(width = w * 0.045f))
    drawCircle(Color(0xFF12305E), radius = w * 0.065f, center = Offset(w * 0.38f, h * 0.62f))
    drawCircle(Color(0xFF12305E), radius = w * 0.065f, center = Offset(w * 0.62f, h * 0.62f))
}

/** Talent glyph on a round plate in the branch colour. */
@Composable
fun TalentIcon(id: String, branch: TalentBranch, modifier: Modifier = Modifier, size: Dp = 40.dp, dim: Boolean = false) {
    Canvas(modifier.size(size)) {
        val c = branchColor(branch).let { if (dim) it.copy(alpha = 0.45f) else it }
        val w = this.size.width
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0.10f))), radius = w / 2f)
        drawCircle(c, radius = w / 2f - w * 0.03f, style = Stroke(width = w * 0.05f))
        drawGlyph(id, c, Color.White.copy(alpha = if (dim) 0.6f else 0.95f))
    }
}

private fun DrawScope.drawGlyph(id: String, accent: Color, ink: Color) {
    val w = size.width
    fun p(x: Float, y: Float) = Offset(w * x, w * y)
    val sw = w * 0.07f
    val line = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    fun poly(vararg pts: Pair<Float, Float>, close: Boolean = false, fill: Boolean = false, color: Color = ink) {
        val path = Path().apply {
            moveTo(w * pts[0].first, w * pts[0].second)
            for (i in 1 until pts.size) lineTo(w * pts[i].first, w * pts[i].second)
            if (close) close()
        }
        drawPath(path, color, style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else line)
    }
    when (id) {
        Talents.XP -> poly(0.5f to 0.22f, 0.58f to 0.42f, 0.78f to 0.44f, 0.63f to 0.57f, 0.68f to 0.78f, 0.5f to 0.66f, 0.32f to 0.78f, 0.37f to 0.57f, 0.22f to 0.44f, 0.42f to 0.42f, close = true, fill = true)
        Talents.GOLD -> { drawCircle(ink, w * 0.2f, p(0.5f, 0.5f), style = line); poly(0.5f to 0.4f, 0.5f to 0.6f) }
        Talents.START_LEVEL -> { poly(0.5f to 0.76f, 0.5f to 0.26f); poly(0.32f to 0.44f, 0.5f to 0.26f, 0.68f to 0.44f) }
        Talents.REST -> poly(0.5f to 0.22f, 0.74f to 0.32f, 0.72f to 0.58f, 0.5f to 0.78f, 0.28f to 0.58f, 0.26f to 0.32f, close = true)
        Talents.DAMAGE -> { poly(0.3f to 0.72f, 0.7f to 0.28f); poly(0.58f to 0.28f, 0.7f to 0.28f, 0.7f to 0.4f); poly(0.26f to 0.6f, 0.4f to 0.74f) }
        Talents.SPEED -> { poly(0.56f to 0.2f, 0.34f to 0.52f, 0.5f to 0.52f, 0.44f to 0.8f, 0.68f to 0.46f, 0.52f to 0.46f, close = true, fill = true) }
        Talents.HP -> poly(0.5f to 0.78f, 0.24f to 0.5f, 0.3f to 0.3f, 0.5f to 0.36f, 0.7f to 0.3f, 0.76f to 0.5f, close = true, fill = true)
        Talents.CRIT -> { drawCircle(ink, w * 0.2f, p(0.5f, 0.5f), style = line); poly(0.5f to 0.2f, 0.5f to 0.34f); poly(0.5f to 0.66f, 0.5f to 0.8f); poly(0.2f to 0.5f, 0.34f to 0.5f); poly(0.66f to 0.5f, 0.8f to 0.5f) }
        Talents.DROP -> { drawRoundedBox(ink, line); poly(0.5f to 0.4f, 0.5f to 0.4f) }
        Talents.SLOTS -> { poly(0.3f to 0.42f, 0.7f to 0.42f, 0.74f to 0.78f, 0.26f to 0.78f, close = true); poly(0.4f to 0.42f, 0.4f to 0.3f, 0.6f to 0.3f, 0.6f to 0.42f) }
        Talents.CLICK -> { poly(0.42f to 0.22f, 0.42f to 0.58f, 0.34f to 0.5f, 0.3f to 0.56f, 0.46f to 0.78f, 0.7f to 0.74f, 0.72f to 0.5f, 0.58f to 0.46f, 0.58f to 0.3f) }
        Talents.COMBO -> { poly(0.26f to 0.62f, 0.4f to 0.4f, 0.54f to 0.62f, 0.68f to 0.3f); drawCircle(accent, w * 0.05f, p(0.74f, 0.26f)) }
        else -> drawCircle(ink, w * 0.12f, p(0.5f, 0.5f))
    }
}

private fun DrawScope.drawRoundedBox(ink: Color, line: Stroke) {
    val w = size.width
    drawRoundRect(ink, topLeft = Offset(w * 0.28f, w * 0.34f), size = Size(w * 0.44f, w * 0.34f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f), style = line)
    drawLine(ink, Offset(w * 0.28f, w * 0.48f), Offset(w * 0.72f, w * 0.48f), strokeWidth = line.width)
}
