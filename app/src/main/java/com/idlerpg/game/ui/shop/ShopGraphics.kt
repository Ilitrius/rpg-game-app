package com.idlerpg.game.ui.shop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Vector gold coin (layered gradients, rim, embossed star, specular highlight). Square. */
@Composable
fun GoldCoin(modifier: Modifier = Modifier, dimmed: Boolean = false) {
    Canvas(modifier) { drawGoldCoin(Offset(size.width / 2, size.height / 2), min(size.width, size.height) / 2, dimmed) }
}

internal fun DrawScope.drawGoldCoin(center: Offset, radius: Float, dimmed: Boolean = false) {
    val a = if (dimmed) 0.55f else 1f
    // Soft drop shadow
    drawCircle(Color.Black.copy(alpha = 0.35f * a), radius * 0.98f, center + Offset(radius * 0.06f, radius * 0.1f))
    // Rim
    drawCircle(
        Brush.linearGradient(
            listOf(Color(0xFFFFEFB0), Color(0xFFD9A21E), Color(0xFF7A4E08)),
            start = center - Offset(radius, radius), end = center + Offset(radius, radius),
        ),
        radius, center, alpha = a,
    )
    // Face
    val face = radius * 0.8f
    drawCircle(
        Brush.radialGradient(
            colorStops = arrayOf(0f to Color(0xFFFFE89A), 0.55f to Color(0xFFF0B832), 1f to Color(0xFFB07812)),
            center = center - Offset(face * 0.3f, face * 0.35f), radius = face * 1.5f,
        ),
        face, center, alpha = a,
    )
    drawCircle(Color(0xFF8A5A0A).copy(alpha = 0.6f * a), face, center, style = Stroke(radius * 0.06f))
    // Embossed star: dark offset copy + light face
    val star = starPath(center, face * 0.55f, face * 0.24f)
    val shadow = starPath(center + Offset(radius * 0.04f, radius * 0.05f), face * 0.55f, face * 0.24f)
    drawPath(shadow, Color(0xFF8A5A0A).copy(alpha = 0.75f * a))
    drawPath(
        star,
        Brush.linearGradient(listOf(Color(0xFFFFF4C8), Color(0xFFE8A82A)), start = center - Offset(face, face), end = center + Offset(face, face)),
        alpha = a,
    )
    // Specular highlight arc
    drawArc(
        Color.White.copy(alpha = 0.55f * a), startAngle = 200f, sweepAngle = 70f, useCenter = false,
        topLeft = center - Offset(radius * 0.86f, radius * 0.86f), size = Size(radius * 1.72f, radius * 1.72f),
        style = Stroke(radius * 0.09f, cap = StrokeCap.Round),
    )
}

private fun starPath(c: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val ang = -PI / 2 + i * PI / 5
        val p = Offset(c.x + (r * cos(ang)).toFloat(), c.y + (r * sin(ang)).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/**
 * Merchant stall header: wooden beam, striped canopy with scalloped edge, hanging lanterns and a
 * dark wooden counter (text goes on the counter, lower ~45 %).
 */
@Composable
fun ShopBanner(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawShopBanner() }
}

private val Wine = Color(0xFF9C2A24)
private val WineDark = Color(0xFF5E1512)
private val Cream = Color(0xFFEFE2BE)
private val CreamDark = Color(0xFFB9A57A)

private fun DrawScope.drawShopBanner() {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(h * 0.12f)
    val canopyTop = h * 0.13f
    val canopyBottom = h * 0.46f

    // Counter / back wall: dark planks
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF3B2A1C), Color(0xFF2A1D13), Color(0xFF1C140D))),
        cornerRadius = corner,
    )
    val plank = h * 0.14f
    var y = canopyBottom + plank
    while (y < h) {
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), strokeWidth = h * 0.012f)
        drawLine(Color(0xFF6B4A2E).copy(alpha = 0.25f), Offset(0f, y + h * 0.012f), Offset(w, y + h * 0.012f), strokeWidth = h * 0.006f)
        y += plank
    }
    // Warm glow from the lanterns on the wall
    for (lx in listOf(w * 0.05f, w * 0.95f)) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFB347).copy(alpha = 0.32f), Color.Transparent), center = Offset(lx, h * 0.62f), radius = h * 0.55f),
            radius = h * 0.55f, center = Offset(lx, h * 0.62f),
        )
    }

    // Canopy stripes with scalloped bottom edge
    val stripes = 10
    val sw = w / stripes
    val scallopR = sw / 2f
    for (i in 0 until stripes) {
        val x0 = i * sw
        val red = i % 2 == 0
        val brush = Brush.verticalGradient(
            if (red) listOf(Color(0xFFC0443A), Wine, WineDark) else listOf(Color(0xFFFFF6DC), Cream, CreamDark),
            startY = canopyTop, endY = canopyBottom + scallopR,
        )
        val p = Path().apply {
            moveTo(x0, canopyTop)
            lineTo(x0 + sw, canopyTop)
            lineTo(x0 + sw, canopyBottom)
            arcTo(Rect(x0, canopyBottom - scallopR, x0 + sw, canopyBottom + scallopR), 0f, 180f, false)
            close()
        }
        // Scallop shadow on the wall
        drawOval(Color.Black.copy(alpha = 0.28f), Offset(x0 + sw * 0.08f, canopyBottom + scallopR * 0.15f), Size(sw * 0.84f, scallopR * 1.25f))
        drawPath(p, brush)
        // Fold shading at the stripe edges
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.12f)),
                startX = x0, endX = x0 + sw,
            ),
            Offset(x0, canopyTop), Size(sw, canopyBottom - canopyTop),
        )
        // Hem stitching
        drawArc(
            if (red) Color(0xFFFFD27A).copy(alpha = 0.55f) else Color(0xFF8E6B3A).copy(alpha = 0.55f),
            0f, 180f, false,
            topLeft = Offset(x0 + sw * 0.12f, canopyBottom - scallopR * 0.76f), size = Size(sw * 0.76f, scallopR * 1.52f),
            style = Stroke(h * 0.008f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(h * 0.02f, h * 0.014f))),
        )
    }
    // Light from above on the canopy
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), startY = canopyTop, endY = canopyTop + (canopyBottom - canopyTop) * 0.5f),
        Offset(0f, canopyTop), Size(w, (canopyBottom - canopyTop) * 0.5f),
    )

    // Wooden beam
    val beamH = canopyTop * 1.15f
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF9A6B3F), Color(0xFF6E4524), Color(0xFF3E2512)), startY = 0f, endY = beamH),
        topLeft = Offset.Zero, size = Size(w, beamH), cornerRadius = CornerRadius(h * 0.12f, h * 0.05f),
    )
    for (k in 0..4) {
        val gy = beamH * (0.25f + k * 0.14f)
        drawLine(Color(0xFF3A220F).copy(alpha = 0.35f), Offset(w * 0.02f, gy), Offset(w * 0.98f, gy + (k - 2) * h * 0.004f), strokeWidth = h * 0.005f)
    }
    drawLine(Color.White.copy(alpha = 0.18f), Offset(w * 0.01f, beamH * 0.12f), Offset(w * 0.99f, beamH * 0.12f), strokeWidth = h * 0.008f)
    // Nails
    for (nx in listOf(0.03f, 0.25f, 0.5f, 0.75f, 0.97f)) {
        val c = Offset(w * nx, beamH * 0.5f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE0E0D8), Color(0xFF6A6A60), Color(0xFF2A2A26)), center = c - Offset(1f, 1f), radius = h * 0.022f), h * 0.018f, c)
    }

    // Hanging lanterns
    for (lx in listOf(w * 0.05f, w * 0.95f)) drawLantern(Offset(lx, canopyBottom + scallopR * 0.9f), h)

    // Outer frame
    drawRoundRect(Color.Black.copy(alpha = 0.5f), style = Stroke(h * 0.012f), cornerRadius = corner)
}

private fun DrawScope.drawLantern(top: Offset, h: Float) {
    val lw = h * 0.1f
    val lh = h * 0.16f
    drawLine(Color(0xFF2B2B2B), top - Offset(0f, h * 0.06f), top, strokeWidth = h * 0.008f)
    val body = Rect(top.x - lw / 2, top.y + h * 0.03f, top.x + lw / 2, top.y + h * 0.03f + lh)
    // Glow
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFD27A).copy(alpha = 0.55f), Color.Transparent), center = body.center, radius = lh * 1.3f),
        lh * 1.3f, body.center,
    )
    // Glass
    drawRoundRect(
        Brush.radialGradient(listOf(Color(0xFFFFF3C4), Color(0xFFFFB238), Color(0xFFB3561A)), center = body.center, radius = lw),
        body.topLeft, body.size, CornerRadius(lw * 0.25f),
    )
    // Iron frame
    drawRoundRect(Color(0xFF1E1E1E), body.topLeft, body.size, CornerRadius(lw * 0.25f), style = Stroke(h * 0.01f))
    drawLine(Color(0xFF1E1E1E), Offset(body.center.x, body.top), Offset(body.center.x, body.bottom), strokeWidth = h * 0.006f)
    // Cap and base
    val cap = Path().apply {
        moveTo(body.left - lw * 0.15f, body.top)
        lineTo(body.right + lw * 0.15f, body.top)
        lineTo(body.center.x, body.top - lh * 0.28f)
        close()
    }
    drawPath(cap, Brush.verticalGradient(listOf(Color(0xFF5A5A55), Color(0xFF1E1E1C)), startY = body.top - lh * 0.28f, endY = body.top))
    drawRoundRect(Color(0xFF2A2A28), Offset(body.left - lw * 0.1f, body.bottom), Size(lw * 1.2f, lh * 0.12f), CornerRadius(lw * 0.1f))
}
