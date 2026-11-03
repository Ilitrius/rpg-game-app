package com.idlerpg.game.ui.character.weapon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/** Draws [img] (or its top [topFraction]) at native pixel size at the origin of the current transform. */
private fun DrawScope.drawNative(img: ImageBitmap, topFraction: Float = 1f) {
    val h = (img.height * topFraction).toInt().coerceAtLeast(1)
    drawImage(
        img, IntOffset.Zero, IntSize(img.width, h), IntOffset.Zero, IntSize(img.width, h),
        filterQuality = FilterQuality.High,
    )
}

/** Draws the (square) item sprite fitted and centered into the whole draw area. */
internal fun DrawScope.drawItemSpriteFit(sprite: ImageBitmap) {
    val k = min(size.width / sprite.width, size.height / sprite.height)
    withTransform({
        translate((size.width - sprite.width * k) / 2f, (size.height - sprite.height * k) / 2f)
        scale(k, k, Offset.Zero)
    }) { drawNative(sprite) }
}

/** The bare fist: the top part of the first-person fist sprite (fist, cuff, start of the bracer), fitted and centered. */
internal fun DrawScope.drawFistCrop(held: Pair<ImageBitmap, HeldWeaponSprites.Meta>, rotationDegrees: Float = 0f) {
    val (img, _) = held
    val part = 0.46f
    val h = img.height * part
    val k = min(size.width / img.width, size.height / h) * 0.95f
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        rotate(rotationDegrees, Offset.Zero)
        translate(-img.width * k / 2f, -h * k / 2f)
        scale(k, k, Offset.Zero)
    }) { drawNative(img, part) }
}

/** Length of a weapon in hand space and how far its centre lies above the grip centre (fractions of the length). */
private class HandGeometry(val length: Float, val centerAboveGrip: Float, val uprightDegrees: Float)

private fun geometryOf(kind: WeaponKind): HandGeometry = when (kind) {
    WeaponKind.RUSTY_DAGGER, WeaponKind.DAGGER -> HandGeometry(320f, 0.29f, -46f)
    WeaponKind.SHORT_SWORD -> HandGeometry(392f, 0.31f, -46f)
    WeaponKind.MACE -> HandGeometry(395f, 0.32f, -42f)
    WeaponKind.AXE -> HandGeometry(382f, 0.36f, -32f)
    WeaponKind.FIST -> HandGeometry(120f, 0f, 0f)
}

/**
 * Draws the diagonal item sprite upright in a small figure's hand: grip at [hand], tilted by
 * [tiltDegrees], [scale] px per weapon (hand space) unit — the same convention as [drawWeaponInHand].
 */
internal fun DrawScope.drawItemSpriteInHand(
    sprite: ImageBitmap,
    kind: WeaponKind,
    hand: Offset,
    tiltDegrees: Float,
    scale: Float,
) {
    val g = geometryOf(kind)
    val side = g.length * scale / 1.37f      // a diagonal weapon fills its square sprite along the diagonal
    val k = side / sprite.width
    withTransform({
        translate(hand.x, hand.y)
        rotate(tiltDegrees, Offset.Zero)
        translate(0f, -g.centerAboveGrip * g.length * scale)
        rotate(g.uprightDegrees, Offset.Zero)
        translate(-side / 2f, -side / 2f)
        scale(k, k, Offset.Zero)
    }) { drawNative(sprite) }
}
