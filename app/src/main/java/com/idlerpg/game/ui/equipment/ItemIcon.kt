package com.idlerpg.game.ui.equipment

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.equipment.ItemBaseTypes
import com.idlerpg.game.ui.equipment.ItemPainters.drawAmulet
import com.idlerpg.game.ui.equipment.ItemPainters.drawAxeIcon
import com.idlerpg.game.ui.equipment.ItemPainters.drawBoots
import com.idlerpg.game.ui.equipment.ItemPainters.drawChainmail
import com.idlerpg.game.ui.equipment.ItemPainters.drawDaggerIcon
import com.idlerpg.game.ui.equipment.ItemPainters.drawGlove
import com.idlerpg.game.ui.equipment.ItemPainters.drawIronHelm
import com.idlerpg.game.ui.equipment.ItemPainters.drawLeatherCap
import com.idlerpg.game.ui.equipment.ItemPainters.drawLeatherVest
import com.idlerpg.game.ui.equipment.ItemPainters.drawMaceIcon
import com.idlerpg.game.ui.equipment.ItemPainters.drawRing
import com.idlerpg.game.ui.equipment.ItemPainters.drawSwordIcon
import com.idlerpg.game.ui.equipment.ItemPainters.drawTalisman
import kotlin.math.min

/** Rarity color: Gewöhnlich grau, Ungewöhnlich grün, Selten blau, Episch lila, Legendär orange. */
fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> Color(0xFFB8BBB4)
    Rarity.UNCOMMON -> Color(0xFF5FCB66)
    Rarity.RARE -> Color(0xFF4AA3F5)
    Rarity.EPIC -> Color(0xFFB45CD6)
    Rarity.LEGENDARY -> Color(0xFFFF9A2E)
}

/**
 * High-resolution item icon: a rarity-colored framed tile with a detailed vector graphic of the
 * item's base type (see [ItemPainters]). Square — give it e.g. `Modifier.size(64.dp)`.
 *
 * @param framed false draws only the item graphic (no tile/frame), e.g. for large detail views
 */
@Composable
fun ItemIcon(
    item: Item,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    contentDescription: String? = itemDisplayName(item),
) {
    val sem = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(sem)) {
        if (framed) drawRarityFrame(item.rarity)
        drawFitted(inset = if (framed) 0.1f else 0f) { drawItemGraphic(item) }
    }
}

/** Empty equipment slot: neutral tile with a faint, desaturated silhouette of the slot's item type. */
@Composable
fun EmptySlotIcon(slot: EquipmentSlot, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val sem = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    val placeholder = Item(id = "slot_${slot.name}", name = "", slot = slot, baseType = ItemBaseTypes.forSlot(slot).first().id)
    Canvas(modifier.then(sem)) {
        drawEmptyFrame()
        val paint = Paint().apply {
            alpha = 0.22f
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
        drawFitted(inset = 0.16f) { drawItemGraphic(placeholder) }
        drawContext.canvas.restore()
    }
}

/** Scales the 100 × 100 unit icon space into the draw area (centered, with [inset] fraction margin). */
private fun DrawScope.drawFitted(inset: Float, block: DrawScope.() -> Unit) {
    val side = min(size.width, size.height) * (1f - 2f * inset)
    val s = side / 100f
    withTransform({
        translate((size.width - side) / 2f, (size.height - side) / 2f)
        scale(s, s, pivot = Offset.Zero)
    }) { block() }
}

/** Dispatches to the painter of the item's base type. */
private fun DrawScope.drawItemGraphic(item: Item) {
    val gem = ItemPainters.gemFor(item.rarity)
    when (ItemBaseTypes.of(item).id) {
        ItemBaseTypes.DAGGER -> drawDaggerIcon(rust = if (item.id == StarterItems.RUSTY_DAGGER_ID) 0.45f else 0f)
        ItemBaseTypes.SHORT_SWORD -> drawSwordIcon(gem)
        ItemBaseTypes.MACE -> drawMaceIcon(gem)
        ItemBaseTypes.AXE -> drawAxeIcon(gem)
        ItemBaseTypes.LEATHER_CAP -> drawLeatherCap()
        ItemBaseTypes.IRON_HELM -> drawIronHelm()
        ItemBaseTypes.LEATHER_VEST -> drawLeatherVest()
        ItemBaseTypes.CHAINMAIL -> drawChainmail()
        ItemBaseTypes.LEATHER_GLOVES -> drawGlove(plated = false)
        ItemBaseTypes.GAUNTLETS -> drawGlove(plated = true)
        ItemBaseTypes.LEATHER_BOOTS -> drawBoots(plated = false)
        ItemBaseTypes.PLATE_BOOTS -> drawBoots(plated = true)
        ItemBaseTypes.SILVER_RING -> drawRing(gold = false, gem = gem)
        ItemBaseTypes.GOLD_RING -> drawRing(gold = true, gem = gem)
        ItemBaseTypes.AMULET -> drawAmulet(gem)
        ItemBaseTypes.TALISMAN -> drawTalisman(gem)
        else -> drawDaggerIcon(0f)
    }
}

private fun DrawScope.drawRarityFrame(rarity: Rarity) {
    val c = rarityColor(rarity)
    val w = size.width
    val h = size.height
    val r = min(w, h) * 0.14f
    val stroke = min(w, h) * 0.035f
    // Tile background: dark with a soft rarity-tinted glow in the middle.
    drawRoundRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to lerpColor(Color(0xFF2A2F2A), c, if (rarity >= Rarity.EPIC) 0.42f else 0.26f),
                0.65f to Color(0xFF1C201C),
                1f to Color(0xFF111411),
            ),
            center = Offset(w / 2, h * 0.45f), radius = min(w, h) * 0.75f,
        ),
        cornerRadius = CornerRadius(r),
    )
    if (rarity == Rarity.LEGENDARY || rarity == Rarity.EPIC) {
        // Inner aura behind the item
        drawCircle(
            Brush.radialGradient(
                colorStops = arrayOf(0f to c.copy(alpha = 0.45f), 0.6f to c.copy(alpha = 0.12f), 1f to Color.Transparent),
                center = Offset(w / 2, h / 2), radius = min(w, h) * 0.46f,
            ),
            radius = min(w, h) * 0.46f,
        )
    }
    // Bevelled frame: dark outer line, rarity gradient border, light inner highlight.
    val inset = stroke / 2
    drawRoundRect(Color(0xFF0A0C0A), Offset(0f, 0f), Size(w, h), CornerRadius(r), style = Stroke(stroke * 0.6f))
    drawRoundRect(
        Brush.linearGradient(
            colorStops = arrayOf(0f to lerpColor(c, Color.White, 0.45f), 0.45f to c, 1f to lerpColor(c, Color.Black, 0.55f)),
            start = Offset.Zero, end = Offset(w, h),
        ),
        Offset(inset + stroke * 0.3f, inset + stroke * 0.3f), Size(w - stroke * 1.6f, h - stroke * 1.6f),
        CornerRadius(r * 0.85f), style = Stroke(stroke),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.10f),
        Offset(stroke * 1.6f, stroke * 1.6f), Size(w - stroke * 3.2f, h - stroke * 3.2f),
        CornerRadius(r * 0.7f), style = Stroke(stroke * 0.35f),
    )
    // Corner studs for rare and better
    if (rarity >= Rarity.RARE) {
        val sr = stroke * 0.75f
        val o = stroke * 1.9f
        for (p in listOf(Offset(o, o), Offset(w - o, o), Offset(o, h - o), Offset(w - o, h - o))) {
            drawCircle(Brush.radialGradient(listOf(Color.White, c, lerpColor(c, Color.Black, 0.6f)), center = p - Offset(sr * 0.3f, sr * 0.3f), radius = sr * 1.4f), sr, p)
        }
    }
}

private fun DrawScope.drawEmptyFrame() {
    val w = size.width
    val h = size.height
    val r = min(w, h) * 0.14f
    val stroke = min(w, h) * 0.03f
    drawRoundRect(
        Brush.radialGradient(listOf(Color(0xFF232823), Color(0xFF151815)), center = Offset(w / 2, h * 0.45f), radius = min(w, h) * 0.75f),
        cornerRadius = CornerRadius(r),
    )
    drawRoundRect(Color(0xFF4A5048).copy(alpha = 0.7f), Offset(stroke / 2, stroke / 2), Size(w - stroke, h - stroke), CornerRadius(r), style = Stroke(stroke))
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)
