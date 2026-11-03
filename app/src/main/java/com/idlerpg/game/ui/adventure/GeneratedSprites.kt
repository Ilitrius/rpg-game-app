package com.idlerpg.game.ui.adventure

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Raytraced, neutral grey body plans of the generated zones (`tools/render_items/monsters_gen*.py`):
 * [Body] x (regular, boss), plus a white glow layer (eyes, cracks). The zone's biome palette colours them
 * at draw time: a gradient map from the biome's dark to light skin colour (darker for stronger zones, like
 * the vector creatures' fear level) and the biome's eye colour on the glow layer. Falls back to the vector
 * [ProceduralMonsters] while a sprite is not loaded.
 */
internal object GeneratedSprites {

    private class Plan(val base: Int, val glow: Int)

    private const val CACHE_BYTES = 48 * 1024 * 1024
    private val cache = object : LruCache<Int, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun plan(body: Body, boss: Boolean): Plan = when (body) {
        Body.BLOB -> if (boss) Plan(R.drawable.monster_gen_blob_boss, R.drawable.monster_gen_blob_boss_glow) else Plan(R.drawable.monster_gen_blob, R.drawable.monster_gen_blob_glow)
        Body.HOUND -> if (boss) Plan(R.drawable.monster_gen_hound_boss, R.drawable.monster_gen_hound_boss_glow) else Plan(R.drawable.monster_gen_hound, R.drawable.monster_gen_hound_glow)
        Body.HUMANOID -> if (boss) Plan(R.drawable.monster_gen_humanoid_boss, R.drawable.monster_gen_humanoid_boss_glow) else Plan(R.drawable.monster_gen_humanoid, R.drawable.monster_gen_humanoid_glow)
        Body.INSECT -> if (boss) Plan(R.drawable.monster_gen_insect_boss, R.drawable.monster_gen_insect_boss_glow) else Plan(R.drawable.monster_gen_insect, R.drawable.monster_gen_insect_glow)
        Body.WINGED -> if (boss) Plan(R.drawable.monster_gen_winged_boss, R.drawable.monster_gen_winged_boss_glow) else Plan(R.drawable.monster_gen_winged, R.drawable.monster_gen_winged_glow)
        Body.SERPENT -> if (boss) Plan(R.drawable.monster_gen_serpent_boss, R.drawable.monster_gen_serpent_boss_glow) else Plan(R.drawable.monster_gen_serpent, R.drawable.monster_gen_serpent_glow)
        Body.WRAITH -> if (boss) Plan(R.drawable.monster_gen_wraith_boss, R.drawable.monster_gen_wraith_boss_glow) else Plan(R.drawable.monster_gen_wraith, R.drawable.monster_gen_wraith_glow)
        Body.GOLEM -> if (boss) Plan(R.drawable.monster_gen_golem_boss, R.drawable.monster_gen_golem_boss_glow) else Plan(R.drawable.monster_gen_golem, R.drawable.monster_gen_golem_glow)
    }

    private fun planOf(zone: Zone, type: MonsterType): Plan? {
        val biome = zone.biome ?: return null
        val role = zone.monsters.indexOf(type)
        return plan(BiomeStyles.of(biome).bodyOf(role, type.isBoss), type.isBoss)
    }

    /** Decodes the body plans a [zone] shows (three roles + boss) on a background thread. */
    suspend fun preload(resources: Resources, zone: Zone) {
        val biome = zone.biome ?: return
        val st = BiomeStyles.of(biome)
        val plans = (0..2).map { plan(st.bodyOf(it, false), false) } + plan(st.bodyOf(0, true), true)
        for (pl in plans) for (res in intArrayOf(pl.base, pl.glow)) {
            if (cache.get(res) != null) continue
            // the glow layer (eyes, cracks) is almost empty: half resolution, scaled up when drawn (saves memory)
            val opts = BitmapFactory.Options().apply { if (res == pl.glow) inSampleSize = 2 }
            val bmp = withContext(Dispatchers.Default) { runCatching { BitmapFactory.decodeResource(resources, res, opts) }.getOrNull() } ?: continue
            cache.put(res, bmp.asImageBitmap())
        }
    }

    // colour filters of the last drawn creature (no per-frame allocation)
    private var lastKey: String? = null
    private var lastTint: ColorFilter? = null
    private var lastGlow: ColorFilter? = null

    private fun filtersFor(zone: Zone, type: MonsterType): Pair<ColorFilter, ColorFilter> {
        val key = zone.id + "/" + type.id
        if (key != lastKey || lastTint == null || lastGlow == null) {
            val st = BiomeStyles.of(zone.biome!!)
            val fear = ProceduralMonsters.fear(zone, type)
            val dark = SceneKit.mix(st.skinDark, Color.Black, 0.30f + 0.25f * fear)
            val light = SceneKit.mix(st.skinLight, st.skinMid, 0.15f * fear)
            val gain = 1.12f
            fun row(lo: Float, hi: Float): FloatArray {
                val k = gain * (hi - lo)
                return floatArrayOf(k * 0.299f, k * 0.587f, k * 0.114f, 0f, lo * 255f)
            }
            val r = row(dark.red, light.red)
            val g = row(dark.green, light.green)
            val b = row(dark.blue, light.blue)
            lastTint = ColorFilter.colorMatrix(
                ColorMatrix(floatArrayOf(*r, *g, *b, 0f, 0f, 0f, 1f, 0f)),
            )
            lastGlow = ColorFilter.tint(st.eye, BlendMode.Modulate)
            lastKey = key
        }
        return lastTint!! to lastGlow!!
    }

    /** Draws the tinted body plan of [type] in [zone] into the 200-unit monster box; false if not loaded yet. */
    fun draw(scope: DrawScope, zone: Zone, type: MonsterType): Boolean {
        val pl = planOf(zone, type) ?: return false
        val base = cache.get(pl.base) ?: return false
        val glow = cache.get(pl.glow)
        val (tint, glowFilter) = filtersFor(zone, type)
        val aspect = base.width.toFloat() / base.height
        val hUnits = min(190f, 270f / aspect)
        val wUnits = hUnits * aspect
        val k = hUnits / base.height
        with(scope) {
            withTransform({
                translate(100f - wUnits / 2f, MonsterPainters.GROUND - hUnits)
                scale(k, k, Offset.Zero)
            }) {
                val size = IntSize(base.width, base.height)
                drawImage(base, IntOffset.Zero, size, IntOffset.Zero, size, colorFilter = tint, filterQuality = FilterQuality.High)
                if (glow != null) {
                    drawImage(glow, IntOffset.Zero, IntSize(glow.width, glow.height), IntOffset.Zero, size, colorFilter = glowFilter, filterQuality = FilterQuality.High)
                }
            }
        }
        return true
    }
}
