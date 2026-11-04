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
import com.idlerpg.game.domain.world.Biome
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
        MonsterType.WOLF -> R.drawable.monster_wolf
        MonsterType.FOREST_SPIDER -> R.drawable.monster_forest_spider
        MonsterType.MUSHROOM -> R.drawable.monster_mushroom
        MonsterType.TREANT -> R.drawable.monster_treant
        MonsterType.TOXIC_SLIME -> R.drawable.monster_toxic_slime
        MonsterType.MIRE_TOAD -> R.drawable.monster_mire_toad
        MonsterType.WISP -> R.drawable.monster_wisp
        MonsterType.HYDRA -> R.drawable.monster_hydra
        MonsterType.STONE_GOLEM -> R.drawable.monster_stone_golem
        MonsterType.SNOW_WOLF -> R.drawable.monster_snow_wolf
        MonsterType.ICE_WISP -> R.drawable.monster_ice_wisp
        MonsterType.YETI -> R.drawable.monster_yeti
        MonsterType.BAT -> R.drawable.monster_bat
        MonsterType.CAVE_SPIDER -> R.drawable.monster_cave_spider
        MonsterType.CRYSTAL_GOLEM -> R.drawable.monster_crystal_golem
        MonsterType.DRAGON -> R.drawable.monster_dragon
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
        Zones.WALD_ID -> R.drawable.scene_forest
        Zones.SUMPF_ID -> R.drawable.scene_swamp
        Zones.GEBIRGE_ID -> R.drawable.scene_mountain
        Zones.HOEHLE_ID -> R.drawable.scene_cave
        else -> null
    }

    /** One backdrop per biome; its five stages share it (the scene adds darkness and particles per stage). */
    fun resFor(biome: Biome): Int = when (biome) {
        Biome.HEIDE -> R.drawable.scene_biome_heide
        Biome.WUESTE -> R.drawable.scene_biome_wueste
        Biome.VULKAN -> R.drawable.scene_biome_vulkan
        Biome.GRUFT -> R.drawable.scene_biome_gruft
        Biome.FROST -> R.drawable.scene_biome_frost
        Biome.DSCHUNGEL -> R.drawable.scene_biome_dschungel
        Biome.RUINEN -> R.drawable.scene_biome_ruinen
        Biome.GLUTPILZ -> R.drawable.scene_biome_glutpilz
        Biome.KUESTE -> R.drawable.scene_biome_kueste
        Biome.KNOCHEN -> R.drawable.scene_biome_knochen
        Biome.BLUTGARTEN -> R.drawable.scene_biome_blutgarten
        Biome.HIMMEL -> R.drawable.scene_biome_himmel
        Biome.UNTERWELT -> R.drawable.scene_biome_unterwelt
        Biome.SCHATTEN -> R.drawable.scene_biome_schatten
        Biome.UHRWERK -> R.drawable.scene_biome_uhrwerk
        Biome.TRAUM -> R.drawable.scene_biome_traum
        Biome.ASTRAL -> R.drawable.scene_biome_astral
        Biome.LEERE -> R.drawable.scene_biome_leere
        Biome.ENDE -> R.drawable.scene_biome_ende
    }

    fun resFor(zone: Zone): Int? = zone.biome?.let { resFor(it) } ?: resFor(zone.id)

    fun peek(zoneId: String): ImageBitmap? = resFor(zoneId)?.let { cache.get(it) }

    fun peek(zone: Zone): ImageBitmap? = resFor(zone)?.let { cache.get(it) }

    suspend fun preload(resources: Resources, zone: Zone) {
        val res = resFor(zone) ?: return
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
