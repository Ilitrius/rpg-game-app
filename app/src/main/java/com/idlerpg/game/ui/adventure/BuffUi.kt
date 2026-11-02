package com.idlerpg.game.ui.adventure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlerpg.game.R
import com.idlerpg.game.data.ActiveBuff
import com.idlerpg.game.domain.buffs.Buffs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Reference duration for the remaining-time ring (a full ring = 15 min or more). */
private const val RING_REFERENCE_MILLIS = 15L * 60 * 1000

private fun buffAccent(type: String): Color = when (type) {
    Buffs.DAMAGE -> Color(0xFFE5533D)
    Buffs.XP -> Color(0xFF4FC3F7)
    Buffs.GOLD -> Color(0xFFFFC53D)
    Buffs.DROP -> Color(0xFFB46CFF)
    Buffs.REGEN -> Color(0xFF58D68D)
    else -> Color(0xFFFF9F43)
}

private fun buffNameRes(type: String): Int = when (type) {
    Buffs.DAMAGE -> R.string.buff_damage
    Buffs.XP -> R.string.buff_xp
    Buffs.GOLD -> R.string.buff_gold
    Buffs.DROP -> R.string.buff_drop
    Buffs.REGEN -> R.string.buff_regen
    else -> R.string.buff_click
}

private fun buffSourceRes(source: String): Int? = when (source) {
    Buffs.SOURCE_ELIXIR -> R.string.buff_source_elixir
    Buffs.SOURCE_DAILY -> R.string.buff_source_daily
    Buffs.SOURCE_GOAL -> R.string.buff_source_goal
    Buffs.SOURCE_BOSS -> R.string.buff_source_boss
    else -> null
}

/** Left edge column of the HUD (below the zone label): one icon with time ring per active buff. */
@Composable
internal fun BuffBar(buffs: List<ActiveBuff>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (buffs.isEmpty()) return
    val desc = stringResource(R.string.buff_bar_desc)
    Column(
        modifier
            .semantics { contentDescription = desc }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x99141A14))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        buffs.forEach { b ->
            BuffIcon(b, Modifier.size(34.dp))
            Text(
                Buffs.clock(b.remainingMillis),
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEDE6D3), shadow = hudShadow),
            )
        }
    }
}

/** Vector icon of a buff type with a remaining-time ring around it. */
@Composable
internal fun BuffIcon(buff: ActiveBuff, modifier: Modifier = Modifier) {
    val accent = buffAccent(buff.type)
    val fraction = (buff.remainingMillis.toFloat() / RING_REFERENCE_MILLIS).coerceIn(0.03f, 1f)
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.08f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF10150F)), center, s / 2f))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = s / 2f - stroke / 2f, style = Stroke(stroke))
        drawArc(
            accent, -90f, 360f * fraction, useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f), size = Size(s - stroke, s - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawBuffGlyph(buff.type, s)
    }
}

private fun DrawScope.drawBuffGlyph(type: String, s: Float) {
    val c = Offset(s / 2f, s / 2f)
    val u = s / 100f
    fun p(x: Float, y: Float) = Offset(c.x + x * u, c.y + y * u)
    val light = Color(0xFFFFF3D0)
    when (type) {
        Buffs.DAMAGE -> { // sword
            val blade = Path().apply {
                moveTo(p(0f, -30f).x, p(0f, -30f).y); lineTo(p(6f, -20f).x, p(6f, -20f).y)
                lineTo(p(6f, 12f).x, p(6f, 12f).y); lineTo(p(-6f, 12f).x, p(-6f, 12f).y)
                lineTo(p(-6f, -20f).x, p(-6f, -20f).y); close()
            }
            drawPath(blade, Brush.horizontalGradient(listOf(Color(0xFFB9C3CC), Color.White, Color(0xFF8793A0)), p(-6f, 0f).x, p(6f, 0f).x))
            drawRoundRect(Color(0xFFD9A441), p(-16f, 12f), Size(32f * u, 6f * u), androidx.compose.ui.geometry.CornerRadius(3f * u))
            drawRoundRect(Color(0xFF7A4A24), p(-3.5f, 18f), Size(7f * u, 14f * u), androidx.compose.ui.geometry.CornerRadius(3f * u))
        }
        Buffs.XP -> { // star
            val star = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 32f else 14f
                val a = -PI / 2 + i * PI / 5
                val o = p((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
                if (i == 0) star.moveTo(o.x, o.y) else star.lineTo(o.x, o.y)
            }
            star.close()
            drawPath(star, Brush.verticalGradient(listOf(Color.White, Color(0xFF4FC3F7)), p(0f, -32f).y, p(0f, 32f).y))
        }
        Buffs.GOLD -> { // coin
            drawCircle(Brush.linearGradient(listOf(Color(0xFFFFE69A), Color(0xFFD9962B)), p(-26f, -26f), p(26f, 26f)), 28f * u, c)
            drawCircle(Color(0xFF8A5A12), 20f * u, c, style = Stroke(3f * u))
            drawRoundRect(Color(0xFF8A5A12), p(-3f, -12f), Size(6f * u, 24f * u), androidx.compose.ui.geometry.CornerRadius(2f * u))
        }
        Buffs.DROP -> { // gem
            val gem = Path().apply {
                moveTo(p(-20f, -12f).x, p(-20f, -12f).y); lineTo(p(-10f, -28f).x, p(-10f, -28f).y)
                lineTo(p(10f, -28f).x, p(10f, -28f).y); lineTo(p(20f, -12f).x, p(20f, -12f).y)
                lineTo(p(0f, 30f).x, p(0f, 30f).y); close()
            }
            drawPath(gem, Brush.linearGradient(listOf(Color(0xFFE2C4FF), Color(0xFF8A3FE0)), p(-20f, -28f), p(20f, 30f)))
            drawLine(light.copy(alpha = 0.7f), p(-20f, -12f), p(20f, -12f), 2f * u)
            drawLine(light.copy(alpha = 0.5f), p(-10f, -28f), p(0f, -12f), 2f * u)
            drawLine(light.copy(alpha = 0.5f), p(10f, -28f), p(0f, -12f), 2f * u)
        }
        Buffs.REGEN -> { // heart
            val h = Path().apply {
                moveTo(p(0f, 28f).x, p(0f, 28f).y)
                cubicTo(p(-40f, 0f).x, p(-40f, 0f).y, p(-24f, -34f).x, p(-24f, -34f).y, p(0f, -14f).x, p(0f, -14f).y)
                cubicTo(p(24f, -34f).x, p(24f, -34f).y, p(40f, 0f).x, p(40f, 0f).y, p(0f, 28f).x, p(0f, 28f).y)
                close()
            }
            drawPath(h, Brush.verticalGradient(listOf(Color(0xFF9BF0C0), Color(0xFF1E9E5A)), p(0f, -30f).y, p(0f, 28f).y))
        }
        else -> { // lightning (faster clicking)
            val z = Path().apply {
                moveTo(p(6f, -32f).x, p(6f, -32f).y); lineTo(p(-16f, 4f).x, p(-16f, 4f).y)
                lineTo(p(-2f, 4f).x, p(-2f, 4f).y); lineTo(p(-8f, 32f).x, p(-8f, 32f).y)
                lineTo(p(18f, -8f).x, p(18f, -8f).y); lineTo(p(3f, -8f).x, p(3f, -8f).y); close()
            }
            drawPath(z, Brush.verticalGradient(listOf(Color(0xFFFFF1B8), Color(0xFFFF9F43)), p(0f, -32f).y, p(0f, 32f).y))
        }
    }
}

/** Detail sheet: every active effect with strength, source and remaining time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuffSheet(buffs: List<ActiveBuff>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.buff_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.buff_sheet_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (buffs.isEmpty()) {
                Text(stringResource(R.string.buff_sheet_empty), style = MaterialTheme.typography.bodyMedium)
            }
            buffs.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BuffIcon(b, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.buff_line, (b.magnitude * 100).roundToInt(), stringResource(buffNameRes(b.type))),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        )
                        buffSourceRes(b.source)?.let {
                            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(Buffs.clock(b.remainingMillis), style = MaterialTheme.typography.titleMedium, color = buffAccent(b.type))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
