package com.idlerpg.game.ui.shop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.idlerpg.game.R
import com.idlerpg.game.data.ActiveBuff
import com.idlerpg.game.domain.shop.Elixirs
import com.idlerpg.game.domain.shop.Elixirs.Elixir
import com.idlerpg.game.ui.adventure.BuffIcon
import com.idlerpg.game.ui.equipment.ItemSprites

/** Raytraced bottle per elixir (`tools/render_items/items_potions.py`). */
private fun bottleRes(e: Elixir): Int = when (e) {
    Elixir.XP -> R.drawable.item_elixir_xp
    Elixir.GOLD -> R.drawable.item_elixir_gold
    Elixir.DAMAGE -> R.drawable.item_elixir_damage
    Elixir.DROP -> R.drawable.item_elixir_drop
    Elixir.REGEN -> R.drawable.item_elixir_regen
}

private fun glowColor(e: Elixir): Color = when (e) {
    Elixir.XP -> Color(0xFF4FC3F7)
    Elixir.GOLD -> Color(0xFFFFC53D)
    Elixir.DAMAGE -> Color(0xFFE5533D)
    Elixir.DROP -> Color(0xFFB46CFF)
    Elixir.REGEN -> Color(0xFF58D68D)
}

/**
 * Shop picture of an elixir: the bottle on a soft glow in its buff colour. Until the bitmap is decoded
 * (off the main thread, shared cache with the item sprites) the vector buff icon of the HUD stands in.
 */
@Composable
internal fun ElixirIcon(elixir: Elixir, modifier: Modifier = Modifier) {
    val res = bottleRes(elixir)
    val resources = LocalContext.current.resources
    var bottle by remember(res) { mutableStateOf<ImageBitmap?>(ItemSprites.cached(res)) }
    LaunchedEffect(res) { if (bottle == null) bottle = ItemSprites.load(resources, res) }
    val img = bottle
    if (img == null) {
        BuffIcon(ActiveBuff(elixir.type, Elixirs.MAGNITUDE, Elixirs.DURATION_MILLIS), modifier)
        return
    }
    val glow = glowColor(elixir)
    run {
        Canvas(modifier) {
            val s = size.minDimension
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.38f), glow.copy(alpha = 0.10f), Color.Transparent), center, s * 0.52f),
                s * 0.52f, center,
            )
            val side = (s * 1.08f).toInt()
            val o = Offset(center.x - side / 2f, center.y - side / 2f)
            drawImage(
                img, IntOffset.Zero, IntSize(img.width, img.height),
                IntOffset(o.x.toInt(), o.y.toInt()), IntSize(side, side), filterQuality = FilterQuality.High,
            )
        }
    }
}
