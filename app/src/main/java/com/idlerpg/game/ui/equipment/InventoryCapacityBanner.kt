package com.idlerpg.game.ui.equipment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlerpg.game.R
import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.equipment.Inventory
import com.idlerpg.game.domain.equipment.InventoryFill
import kotlin.math.max
import kotlin.math.min

/** Accent for "fast voll" (amber) and "voll" (warm red). */
private val NearlyFullColor = Color(0xFFFFB547)
private val FullColor = Color(0xFFFF6B5B)

/** Accent color of a fill level; [normal] is used while there is plenty of room. */
internal fun fillAccent(fill: InventoryFill, normal: Color): Color = when (fill) {
    InventoryFill.OK -> normal
    InventoryFill.NEARLY_FULL -> NearlyFullColor
    InventoryFill.FULL -> FullColor
}

/**
 * Warning banner for the equipment tab: appears (animated) when the inventory is nearly full or
 * full and explains that new loot is then sold automatically, with a hint on how to sell items.
 * Hidden while there is plenty of room. Announced politely to screen readers when it appears.
 */
@Composable
fun InventoryCapacityBanner(state: GameState, onGoToInventory: () -> Unit, modifier: Modifier = Modifier) {
    val fill = Inventory.fill(state)
    val visible = fill != InventoryFill.OK
    // Keep showing the last warning level while the banner animates out.
    var lastWarning by remember { mutableStateOf(if (visible) fill else InventoryFill.NEARLY_FULL) }
    LaunchedEffect(fill) { if (fill != InventoryFill.OK) lastWarning = fill }
    val shown = if (visible) fill else lastWarning

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        BannerContent(
            full = shown == InventoryFill.FULL,
            used = state.equipment.inventory.size,
            free = Inventory.freeSlots(state),
            fraction = Inventory.fillFraction(state),
            onGoToInventory = onGoToInventory,
        )
    }
}

@Composable
private fun BannerContent(full: Boolean, used: Int, free: Int, fraction: Float, onGoToInventory: () -> Unit) {
    val accent = if (full) FullColor else NearlyFullColor
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = base,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.35f), accent.copy(alpha = 0.15f))),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to lerp(base, accent, if (full) 0.26f else 0.18f),
                        0.55f to lerp(base, accent, 0.05f),
                        1f to base,
                    ),
                    start = Offset.Zero,
                    end = Offset(900f, 500f),
                ),
            ),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InventoryGauge(
                        fraction = fraction,
                        accent = accent,
                        full = full,
                        modifier = Modifier.size(if (full) 68.dp else 56.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(if (full) R.string.equipment_capacity_full_title else R.string.equipment_capacity_nearly_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = lerp(accent, Color.White, 0.12f),
                                modifier = Modifier.weight(1f),
                            )
                            CountChip(used, accent)
                        }
                        if (!full) {
                            Text(
                                pluralStringResource(R.plurals.equipment_capacity_free_slots, free, free),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            stringResource(if (full) R.string.equipment_capacity_full_body else R.string.equipment_capacity_nearly_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        )
                    }
                }
                if (full) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.22f)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Icon(
                                Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFE6BC58),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.equipment_capacity_sell_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onGoToInventory,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accent.copy(alpha = 0.24f),
                            contentColor = lerp(accent, Color.White, 0.35f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.equipment_capacity_go_to_inventory), maxLines = 1)
                    }
                } else {
                    TextButton(
                        onClick = onGoToInventory,
                        colors = ButtonDefaults.textButtonColors(contentColor = lerp(accent, Color.White, 0.2f)),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.equipment_capacity_go_to_inventory), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountChip(used: Int, accent: Color) {
    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.2f), border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))) {
        Text(
            stringResource(R.string.equipment_inventory_count, used, Inventory.CAPACITY),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = lerp(accent, Color.White, 0.3f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/**
 * Thin segmented capacity bar for the inventory header (gradient fill with gloss, one segment
 * per 10 slots). Animates when the fill changes.
 */
@Composable
internal fun InventoryCapacityBar(used: Int, fraction: Float, accent: Color, modifier: Modifier = Modifier) {
    val animated = animateFloatAsState(fraction, tween(500), label = "capacityBar")
    val description = stringResource(R.string.equipment_inventory_capacity_description, used, Inventory.CAPACITY)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(7.dp)
            .semantics { contentDescription = description },
    ) {
        val h = size.height
        val r = CornerRadius(h / 2f)
        drawRoundRect(Color.Black.copy(alpha = 0.4f), cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.06f), topLeft = Offset(0f, h * 0.55f), size = Size(size.width, h * 0.45f), cornerRadius = r)
        val w = size.width * animated.value
        if (w > 0.5f) {
            val fillW = max(w, h)
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(lerp(accent, Color.Black, 0.35f), accent, lerp(accent, Color.White, 0.25f)),
                    startX = 0f,
                    endX = fillW,
                ),
                size = Size(fillW, h),
                cornerRadius = r,
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.22f),
                topLeft = Offset(h * 0.35f, h * 0.14f),
                size = Size(max(fillW - h * 0.7f, 0f), h * 0.28f),
                cornerRadius = CornerRadius(h * 0.14f),
            )
        }
        // Segment separators (every 10 slots).
        val segments = (Inventory.CAPACITY / 10).coerceAtLeast(1)
        for (i in 1 until segments) {
            val x = size.width * i / segments
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.5.dp.toPx())
        }
    }
}

/**
 * Round capacity gauge: gradient ring showing the fill level around a detailed vector loot bag.
 * When [full], the bag overflows with coins, a warning badge sits on the ring and a soft glow pulses.
 */
@Composable
private fun InventoryGauge(fraction: Float, accent: Color, full: Boolean, modifier: Modifier = Modifier) {
    val animated = animateFloatAsState(fraction, tween(700, easing = FastOutSlowInEasing), label = "gauge")
    val pulse: State<Float>? = if (full) {
        rememberInfiniteTransition(label = "gaugePulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "gaugePulseValue",
        )
    } else {
        null
    }
    Canvas(modifier) {
        val s = min(size.width, size.height) / 100f
        withTransform({
            translate((size.width - 100f * s) / 2f, (size.height - 100f * s) / 2f)
            scale(s, s, pivot = Offset.Zero)
        }) {
            drawGauge(animated.value, accent, full, pulse?.value ?: 0f)
        }
    }
}

/** Draws the gauge into a 100 × 100 unit square. */
private fun DrawScope.drawGauge(fraction: Float, accent: Color, full: Boolean, pulse: Float) {
    val c = Offset(50f, 50f)
    // Layout (units): ring radius 40 (stroke 7 → 36.5..43.5), disc 37, glow halo 43..50 outside the ring.
    // Outer glow (pulses when full).
    val glow = if (full) 0.28f + 0.32f * pulse else 0.2f
    drawCircle(
        Brush.radialGradient(
            colorStops = arrayOf(0.7f to accent.copy(alpha = 0f), 0.86f to accent.copy(alpha = glow), 1f to accent.copy(alpha = 0f)),
            center = c, radius = 50f,
        ),
        radius = 50f, center = c,
    )
    // Inner disc with a soft light from the top left.
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF353B33), Color(0xFF1E221D), Color(0xFF151814)), center = Offset(42f, 38f), radius = 46f),
        radius = 37f, center = c,
    )
    drawCircle(Color.White.copy(alpha = 0.07f), radius = 36f, center = c, style = Stroke(1f))

    // Ring track and progress arc (starts at 12 o'clock).
    val ringTopLeft = Offset(10f, 10f)
    val ringSize = Size(80f, 80f)
    drawArc(Color.Black.copy(alpha = 0.5f), 0f, 360f, false, ringTopLeft, ringSize, style = Stroke(7f))
    val sweep = 360f * fraction.coerceIn(0f, 1f)
    if (sweep > 0.5f) {
        val f = fraction.coerceIn(0.02f, 1f)
        val dark = lerp(accent, Color.Black, 0.3f)
        val light = lerp(accent, Color.White, 0.3f)
        // Dark → accent → light along the arc; wraps back to dark behind the arc's end so the
        // round start cap (which lies just "before" 0°) picks up the dark start tone.
        val stops = if (f < 0.97f) {
            arrayOf(0f to dark, f * 0.6f to accent, f to light, f + 0.03f to dark, 1f to dark)
        } else {
            arrayOf(0f to dark, f * 0.6f to accent, 1f to light)
        }
        rotate(-90f, pivot = c) {
            drawArc(
                Brush.sweepGradient(colorStops = stops, center = c),
                0f, sweep, false, ringTopLeft, ringSize,
                style = Stroke(7f, cap = if (sweep >= 359.5f) StrokeCap.Butt else StrokeCap.Round),
            )
        }
        // Gloss on the outer half of the ring (upper left).
        drawArc(Color.White.copy(alpha = 0.2f), 200f, 110f, false, Offset(8.8f, 8.8f), Size(82.4f, 82.4f), style = Stroke(1.3f, cap = StrokeCap.Round))
    }

    // Loot bag, scaled into the disc (bag spans ~7..97 units vertically incl. coins and shadow).
    val k = 0.68f
    withTransform({
        translate(50f - 50f * k, 52f - 56f * k)
        scale(k, k, pivot = Offset.Zero)
    }) {
        drawLootBag(overflowing = full)
    }

    // Badge sits on the ring at 45° (top right).
    if (full) drawWarningBadge(Offset(78.3f, 21.7f), accent, pulse)
}

private val BagOutline = Color(0xFF1A110A)
private val BagStitch = Color(0xFFE2C79C)

/** Leather loot bag with rope tie in a 100 × 100 unit square; [overflowing] adds coins spilling out of the top. */
private fun DrawScope.drawLootBag(overflowing: Boolean) {
    // Ground shadow
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), center = Offset(50f, 91f), radius = 32f),
        topLeft = Offset(18f, 85f), size = Size(64f, 12f),
    )

    val body = Path().apply {
        moveTo(40f, 37f)
        cubicTo(24f, 43f, 12f, 59f, 15.5f, 74f)
        cubicTo(18.5f, 87f, 33f, 92.5f, 50f, 92.5f)
        cubicTo(67f, 92.5f, 81.5f, 87f, 84.5f, 74f)
        cubicTo(88f, 59f, 76f, 43f, 60f, 37f)
        close()
    }
    drawPath(
        body,
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFFC99662), 0.4f to Color(0xFF94623A), 0.78f to Color(0xFF5A381C), 1f to Color(0xFF2E1C0E),
            ),
            center = Offset(40f, 58f), radius = 48f,
        ),
    )
    clipPath(body) {
        // Core shadow on the right/bottom
        drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent), center = Offset(78f, 84f), radius = 34f),
            topLeft = Offset(44f, 50f), size = Size(68f, 68f),
        )
        // Specular highlight on the upper left belly
        drawOval(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.38f), Color.Transparent), center = Offset(32f, 57f), radius = 13f),
            topLeft = Offset(19f, 44f), size = Size(26f, 26f),
        )
        // Fabric folds running down from the tie
        val folds = Path().apply {
            moveTo(44f, 40f); quadraticTo(38f, 54f, 33f, 64f)
            moveTo(52f, 41f); quadraticTo(53f, 52f, 50f, 61f)
            moveTo(58f, 40f); quadraticTo(66f, 52f, 70f, 60f)
        }
        drawPath(folds, Color.Black.copy(alpha = 0.3f), style = Stroke(2.2f, cap = StrokeCap.Round))
        drawPath(folds, Color.White.copy(alpha = 0.10f), style = Stroke(0.8f, cap = StrokeCap.Round))
        // Leather patch with stitching
        val patch = Path().apply {
            moveTo(38.5f, 71f)
            quadraticTo(50f, 66f, 61.5f, 71f)
            lineTo(60f, 82.5f)
            quadraticTo(50f, 86f, 40f, 82.5f)
            close()
        }
        drawPath(patch, Brush.verticalGradient(listOf(Color(0xFF6E4627), Color(0xFF4A2D16)), startY = 66f, endY = 86f))
        drawPath(patch, BagOutline.copy(alpha = 0.6f), style = Stroke(0.9f))
        drawPath(
            patch,
            BagStitch.copy(alpha = 0.75f),
            style = Stroke(0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4f, 1.8f))),
        )
    }
    drawPath(body, BagOutline, style = Stroke(1.8f))

    if (overflowing) {
        drawCoin(Offset(43f, 17f), 6.2f, tilt = 0.85f)
        drawCoin(Offset(57f, 13f), 6.8f, tilt = 0.7f)
        drawCoin(Offset(50f, 21f), 6.0f, tilt = 1f)
    }

    // Ruffled neck above the tie
    val neck = Path().apply {
        moveTo(41.5f, 38f)
        lineTo(35f, 24f)
        quadraticTo(40f, 27.5f, 44.5f, 22.5f)
        quadraticTo(50f, 27f, 55.5f, 22f)
        quadraticTo(60f, 27.5f, 65f, 24f)
        lineTo(58.5f, 38f)
        close()
    }
    drawPath(neck, Brush.verticalGradient(listOf(Color(0xFFB98754), Color(0xFF6E4627)), startY = 22f, endY = 38f))
    drawPath(neck, Color.Black.copy(alpha = 0.25f), style = Stroke(0.9f))
    drawLine(Color.Black.copy(alpha = 0.28f), Offset(44.5f, 24f), Offset(46f, 36f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawLine(Color.Black.copy(alpha = 0.28f), Offset(55.5f, 23.5f), Offset(54f, 36f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawPath(neck, BagOutline, style = Stroke(1.5f))

    // Rope tie with twist marks and a hanging end
    val ropeTop = 34f
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFEBD39C), Color(0xFFB08A48), Color(0xFF654A22)), startY = ropeTop, endY = ropeTop + 7f),
        topLeft = Offset(36.5f, ropeTop), size = Size(27f, 7f), cornerRadius = CornerRadius(3.5f),
    )
    for (i in 0 until 6) {
        val x = 38.5f + i * 4.3f
        drawLine(Color(0xFF5A4020), Offset(x, ropeTop + 0.8f), Offset(x + 2.4f, ropeTop + 6.2f), strokeWidth = 0.9f)
    }
    drawRoundRect(BagOutline, Offset(36.5f, ropeTop), Size(27f, 7f), CornerRadius(3.5f), style = Stroke(1.2f))
    val ropeEnd = Path().apply {
        moveTo(61f, ropeTop + 5f)
        cubicTo(66f, ropeTop + 9f, 64f, ropeTop + 15f, 68f, ropeTop + 19f)
    }
    drawPath(ropeEnd, BagOutline, style = Stroke(3.6f, cap = StrokeCap.Round))
    drawPath(ropeEnd, Color(0xFFC9A464), style = Stroke(2.2f, cap = StrokeCap.Round))
    drawCircle(Color(0xFFB08A48), radius = 2.2f, center = Offset(68.2f, ropeTop + 19.5f))
    drawCircle(BagOutline, radius = 2.2f, center = Offset(68.2f, ropeTop + 19.5f), style = Stroke(0.9f))
}

/** Gold coin seen slightly from the side ([tilt] = vertical squash, 1 = round). */
private fun DrawScope.drawCoin(center: Offset, radius: Float, tilt: Float) {
    val rx = radius
    val ry = radius * tilt
    val edge = radius * 0.28f
    // Coin edge (thickness)
    drawOval(Color(0xFF6B4A0E), topLeft = Offset(center.x - rx, center.y - ry + edge), size = Size(rx * 2f, ry * 2f))
    drawOval(
        Brush.radialGradient(
            listOf(Color(0xFFFFF1BF), Color(0xFFE8BE52), Color(0xFFA9781F)),
            center = Offset(center.x - rx * 0.35f, center.y - ry * 0.4f), radius = radius * 1.5f,
        ),
        topLeft = Offset(center.x - rx, center.y - ry), size = Size(rx * 2f, ry * 2f),
    )
    drawOval(
        Color(0xFF8A5F14).copy(alpha = 0.8f),
        topLeft = Offset(center.x - rx * 0.62f, center.y - ry * 0.62f), size = Size(rx * 1.24f, ry * 1.24f),
        style = Stroke(0.8f),
    )
    drawOval(Color(0xFF4A3008), topLeft = Offset(center.x - rx, center.y - ry), size = Size(rx * 2f, ry * 2f), style = Stroke(0.9f))
    drawCircle(Color.White.copy(alpha = 0.7f), radius = radius * 0.16f, center = Offset(center.x - rx * 0.38f, center.y - ry * 0.42f))
}

/** Round warning badge with an exclamation mark, sitting on the gauge ring. */
private fun DrawScope.drawWarningBadge(center: Offset, accent: Color, pulse: Float) {
    val r = 11f + 0.6f * pulse
    drawCircle(Color.Black.copy(alpha = 0.45f), radius = r + 2.2f, center = center + Offset(0.6f, 1.2f))
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(accent, Color.White, 0.35f), accent, lerp(accent, Color.Black, 0.35f)),
            center = center - Offset(r * 0.35f, r * 0.4f), radius = r * 1.5f,
        ),
        radius = r, center = center,
    )
    drawCircle(Color(0xFF2A0D08), radius = r, center = center, style = Stroke(1.3f))
    drawCircle(Color.White.copy(alpha = 0.3f), radius = r - 2f, center = center, style = Stroke(0.8f))
    // Exclamation mark
    drawLine(Color.White, center + Offset(0f, -6f), center + Offset(0f, 1.6f), strokeWidth = 3.2f, cap = StrokeCap.Round)
    drawCircle(Color.White, radius = 1.8f, center = center + Offset(0f, 5.6f))
}
