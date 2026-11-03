package com.idlerpg.game.ui.adventure

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.idlerpg.game.R
import com.idlerpg.game.domain.world.MonsterType
import com.idlerpg.game.domain.world.Zone
import com.idlerpg.game.domain.world.Zones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Pre-rendered, realistic monster sprites (raytraced offline by `tools/render_items/render_monsters.py`).
 * Decoded off the main thread by [preload] into a size-bounded cache; [MonsterPainters] draws a sprite
 * when one is cached and falls back to the vector painters otherwise.
 */
internal object MonsterSprites {

    private const val CACHE_BYTES = 20 * 1024 * 1024

    private val cache = object : LruCache<Int, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun resFor(type: MonsterType): Int? = when (type) {
        MonsterType.SLIME -> R.drawable.monster_slime
        MonsterType.GIANT_RAT -> R.drawable.monster_giant_rat
        MonsterType.BOAR -> R.drawable.monster_boar
        MonsterType.SLIME_KING -> R.drawable.monster_slime_king
        else -> null
    }

    /** The cached sprite of [type], or null if there is none (yet). Cheap, safe to call while drawing. */
    fun peek(type: MonsterType): ImageBitmap? = resFor(type)?.let { cache.get(it) }

    /** Decodes the sprites of [types] that are not cached yet, on a background thread. */
    suspend fun preload(resources: Resources, types: Collection<MonsterType>) {
        for (type in types) {
            val res = resFor(type) ?: continue
            if (cache.get(res) != null) continue
            val bmp = withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeResource(resources, res) }.getOrNull()
            } ?: continue
            cache.put(res, bmp.asImageBitmap())
        }
    }

    /**
     * Draws [sprite] inside the 200-unit monster box of [MonsterPainters] (center x = 100, feet on
     * [MonsterPainters.GROUND]): full box height, narrowed if the sprite is wider than the box.
     */
    fun draw(scope: DrawScope, sprite: ImageBitmap) = with(scope) {
        val maxW = 270f
        val aspect = sprite.width.toFloat() / sprite.height
        val hUnits = min(190f, maxW / aspect)
        val wUnits = hUnits * aspect
        val k = hUnits / sprite.height
        withTransform({
            translate(100f - wUnits / 2f, MonsterPainters.GROUND - hUnits)
            scale(k, k, Offset.Zero)
        }) {
            drawFull(sprite, FilterQuality.High)
        }
    }
}

/** Pre-rendered zone backdrops (portrait images whose horizon sits at [HORIZON_FRACTION] of the height). */
internal object SceneBackdrops {

    const val HORIZON_FRACTION = 0.40f

    private const val CACHE_BYTES = 20 * 1024 * 1024

    private val cache = object : LruCache<Int, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun resFor(zoneId: String): Int? = when (zoneId) {
        Zones.WIESE_ID -> R.drawable.scene_meadow
        else -> null
    }

    fun peek(zoneId: String): ImageBitmap? = resFor(zoneId)?.let { cache.get(it) }

    suspend fun preload(resources: Resources, zone: Zone) {
        val res = resFor(zone.id) ?: return
        if (cache.get(res) != null) return
        val bmp = withContext(Dispatchers.Default) {
            runCatching { BitmapFactory.decodeResource(resources, res) }.getOrNull()
        } ?: return
        cache.put(res, bmp.asImageBitmap())
    }

    /** Covers the canvas ([w] x [h]) with [img], keeping its horizon on [horizonY]. */
    fun draw(scope: DrawScope, img: ImageBitmap, w: Float, h: Float, horizonY: Float) = with(scope) {
        val s = max(w / img.width, h / img.height)
        val yOff = horizonY - HORIZON_FRACTION * img.height * s
        val xOff = (w - img.width * s) / 2f
        withTransform({
            translate(xOff, yOff)
            scale(s, s, Offset.Zero)
        }) {
            drawFull(img, if (s < 0.7f) FilterQuality.Medium else FilterQuality.High)
        }
    }
}

/** Draws [img] at its native size at the origin of the current (possibly scaled) transform. */
private fun DrawScope.drawFull(img: ImageBitmap, quality: FilterQuality) {
    val size = IntSize(img.width, img.height)
    drawImage(img, IntOffset.Zero, size, IntOffset.Zero, size, filterQuality = quality)
}
