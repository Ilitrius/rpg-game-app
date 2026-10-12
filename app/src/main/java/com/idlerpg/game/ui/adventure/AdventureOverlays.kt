package com.idlerpg.game.ui.adventure

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlerpg.game.R
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.world.BossOutcome
import com.idlerpg.game.domain.world.LootDrop
import com.idlerpg.game.domain.world.WorldProgress
import com.idlerpg.game.domain.world.Zone
import com.idlerpg.game.domain.world.Zones
import com.idlerpg.game.ui.equipment.ItemIcon
import com.idlerpg.game.ui.equipment.rarityColor
import java.util.Locale
import kotlin.math.ceil

/** Big centered message after a boss fight. */
sealed interface BannerMessage {
    data class Victory(val zone: Zone, val unlocked: Zone?) : BannerMessage
    data class Failed(val outcome: BossOutcome) : BannerMessage
}

private val goldGradient = listOf(Color(0xFFFFE69A), Color(0xFFE0A82E), Color(0xFFA8741A))

// ------------------------------------------------------------------ boss HUD

@Composable
internal fun BossChallengeButton(onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "bossPulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )
    Row(
        Modifier
            .drawBehind {
                // soft golden glow behind the button
                drawRoundRect(
                    Color(0xFFFFC94A).copy(alpha = 0.18f + 0.22f * pulse),
                    topLeft = Offset(-6.dp.toPx(), -6.dp.toPx()),
                    size = size.copy(width = size.width + 12.dp.toPx(), height = size.height + 12.dp.toPx()),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                )
            }
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(goldGradient))
            .border(1.dp, Color(0xFFFFF4C8).copy(alpha = 0.8f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Whatshot, contentDescription = null, tint = Color(0xFF5A1A05), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.boss_challenge),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2B1D05)),
        )
    }
}

@Composable
internal fun BossTimer(timeLeftMillis: Long, modifier: Modifier = Modifier) {
    val fraction = (timeLeftMillis.toFloat() / WorldProgress.BOSS_TIME_LIMIT_MILLIS).coerceIn(0f, 1f)
    val urgent = timeLeftMillis <= 10_000L
    val color = if (urgent) Color(0xFFFF6A50) else Color(0xFFFFE08A)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Canvas(Modifier.weight(1f).height(6.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(Color(0xCC120E0C), cornerRadius = r)
            drawRoundRect(
                Brush.horizontalGradient(if (urgent) listOf(Color(0xFFFF8A65), Color(0xFFD32F2F)) else listOf(Color(0xFFFFF1B8), Color(0xFFE0A82E))),
                size = size.copy(width = size.width * fraction), cornerRadius = r,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.boss_time_left, ceil(timeLeftMillis / 1000.0).toInt()),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, shadow = hudShadow),
        )
    }
}

// ------------------------------------------------------------------ loot popup

/**
 * Compact card with the dropped item: grows in from its bottom-left corner, stays ~2.5 s and fades
 * out upwards; then [onDone]. Meant for the bottom-left HUD slot above the hero block — the caller
 * limits the width (see LOOT_MAX_WIDTH_FRACTION) so the card stays left of the resting weapon.
 */
@Composable
internal fun LootPopup(drop: LootDrop, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(2900, easing = LinearEasing))
        onDone()
    }
    val t = p.value
    val enter = (t / 0.1f).coerceIn(0f, 1f)
    val exit = ((t - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val color = rarityColor(drop.item.rarity)
    val fmt = { n: Long -> String.format(Locale.GERMANY, "%,d", n) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .graphicsLayer {
                val e = FastOutSlowInEasing.transform(enter)
                alpha = enter * (1f - exit)
                translationX = (1f - e) * -14.dp.toPx()
                translationY = (1f - e) * 10.dp.toPx() - exit * 10.dp.toPx()
                val s = 0.9f + 0.1f * e
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 1f)
            }
            .drawBehind {
                // soft rarity glow behind the card
                drawRoundRect(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.height * 0.5f, size.height * 0.5f), radius = size.height * 1.1f,
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF02A2620), Color(0xF0171411))))
            .border(BorderStroke(1.5.dp, Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))), shape)
            .padding(start = 8.dp, top = 7.dp, end = 10.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemIcon(drop.item, Modifier.size(44.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                stringResource(R.string.loot_header, drop.item.itemLevel),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCFC6B4), letterSpacing = 0.4.sp),
                maxLines = 1,
            )
            Text(
                drop.item.name,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, lineHeight = 17.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (!drop.added) {
                Text(
                    stringResource(R.string.loot_autosold, fmt(drop.autoSoldGold)),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFC66B), lineHeight = 14.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ banners / travel

@Composable
internal fun BossBanner(message: BannerMessage, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(3200, easing = LinearEasing))
        onDone()
    }
    val t = p.value
    val enter = (t / 0.08f).coerceIn(0f, 1f)
    val exit = ((t - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val pop = if (enter < 1f) 1.4f - 0.4f * FastOutSlowInEasing.transform(enter) else 1f
    val (title, sub, color) = when (message) {
        is BannerMessage.Victory -> Triple(
            stringResource(R.string.boss_victory_title),
            message.unlocked?.let { stringResource(R.string.boss_victory_unlocked, stringResource(it.nameRes())) },
            Color(0xFFFFD54F),
        )
        is BannerMessage.Failed -> Triple(
            stringResource(if (message.outcome == BossOutcome.TIMEOUT) R.string.boss_failed_timeout else R.string.boss_failed_defeat),
            stringResource(R.string.boss_failed_hint),
            Color(0xFFFF7A5C),
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = BiasAlignment(0f, -0.25f)) {
        Column(
            Modifier
                .graphicsLayer {
                    alpha = enter * (1f - exit)
                    scaleX = pop
                    scaleY = pop
                }
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent, 0.2f to Color(0xB0100C08), 0.8f to Color(0xB0100C08), 1f to Color.Transparent,
                        ),
                    )
                }
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, color = color, shadow = hudShadow),
                textAlign = TextAlign.Center,
            )
            if (sub != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    sub,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF2E8D2), shadow = hudShadow),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

/** Black fade with the destination name while travelling to another zone. */
@Composable
internal fun TravelOverlay(target: Zone?) {
    val a by animateFloatAsState(
        if (target != null) 1f else 0f,
        tween((TRAVEL_TRANSITION_MILLIS / 2).toInt(), easing = FastOutSlowInEasing),
        label = "travel",
    )
    if (a <= 0.01f) return
    val name = target?.let { stringResource(it.nameRes()) }
    Box(
        Modifier
            .fillMaxSize()
            .alpha(a)
            .background(Brush.verticalGradient(listOf(Color(0xFF07090A), Color(0xFF14110C)))),
        contentAlignment = Alignment.Center,
    ) {
        if (name != null) {
            Text(
                stringResource(R.string.zone_travel, name),
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE8D9AE), shadow = hudShadow),
            )
        }
    }
}

// ------------------------------------------------------------------ zone sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZoneSheet(world: WorldState, current: Zone, onSelect: (Zone) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.zone_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.zone_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Zones.all.forEach { zone -> ZoneRow(zone, world, zone.id == current.id) { onSelect(zone) } }
        }
    }
}

@Composable
private fun ZoneRow(zone: Zone, world: WorldState, isCurrent: Boolean, onClick: () -> Unit) {
    val unlocked = WorldProgress.isUnlocked(world, zone)
    val bossDefeated = WorldProgress.isBossDefeated(world, zone)
    val accent = zoneAccent(zone)
    val shape = RoundedCornerShape(12.dp)
    val lockedCd = stringResource(R.string.zone_locked_cd)
    Surface(
        shape = shape,
        color = if (isCurrent) Color(0xFF353A2C) else Color(0xFF2E332C),
        border = BorderStroke(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else Color(0xFF454B42)),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (unlocked) 1f else 0.62f)
            .clip(shape)
            .clickable(enabled = unlocked && !isCurrent, onClick = onClick)
            .semantics { if (!unlocked) contentDescription = lockedCd },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.45f)))),
                contentAlignment = Alignment.Center,
            ) {
                if (unlocked) {
                    Text(
                        zone.order.toString(),
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White, shadow = hudShadow),
                    )
                } else {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(zone.nameRes()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.zone_levels, zone.minLevel, zone.maxLevel),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFFE08A),
                    )
                }
                val status = when {
                    !unlocked -> {
                        val prev = Zones.previous(zone)
                        if (prev != null) {
                            stringResource(R.string.zone_locked_requirement, stringResource(prev.boss.nameRes()), stringResource(prev.nameRes()))
                        } else {
                            lockedCd
                        }
                    }
                    bossDefeated -> stringResource(R.string.zone_boss_defeated)
                    else -> stringResource(R.string.zone_boss_open, stringResource(zone.boss.nameRes()))
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bossDefeated) Color(0xFF8FD483) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isCurrent) {
                    Text(
                        stringResource(R.string.zone_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            when {
                isCurrent -> Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                bossDefeated -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF8FD483))
                !unlocked -> Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF9A9A90))
            }
        }
    }
}

private fun zoneAccent(zone: Zone): Color = when (zone.id) {
    Zones.WALD_ID -> Color(0xFF2E7D32)
    Zones.SUMPF_ID -> Color(0xFF5B6B2A)
    Zones.GEBIRGE_ID -> Color(0xFF5C7A99)
    Zones.HOEHLE_ID -> Color(0xFF6A4C8C)
    else -> Color(0xFF7CB342)
}
