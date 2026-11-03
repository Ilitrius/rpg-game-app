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

    // ---- handmade zones 2-5: a body plan + a fixed palette per monster
    private class Named(val plan: Plan, val dark: Color, val light: Color, val eye: Color)

    private val MUSHROOM = Plan(R.drawable.monster_gen_mushroom, R.drawable.monster_gen_mushroom_glow)
    private val TOAD = Plan(R.drawable.monster_gen_toad, R.drawable.monster_gen_toad_glow)
    private val WISP = Plan(R.drawable.monster_gen_wisp, R.drawable.monster_gen_wisp_glow)
    private val BAT = Plan(R.drawable.monster_gen_bat, R.drawable.monster_gen_bat_glow)
    private val TREANT = Plan(R.drawable.monster_gen_treant, R.drawable.monster_gen_treant_glow)
    private val HYDRA = Plan(R.drawable.monster_gen_hydra, R.drawable.monster_gen_hydra_glow)
    private val YETI = Plan(R.drawable.monster_gen_yeti, R.drawable.monster_gen_yeti_glow)
    private val DRAGON = Plan(R.drawable.monster_gen_dragon, R.drawable.monster_gen_dragon_glow)

    private fun c(rgb: Long) = Color(0xFF000000 or rgb)

    private fun named(type: MonsterType): Named? = when (type) {
        MonsterType.WOLF -> Named(plan(Body.HOUND, false), c(0x2B241C), c(0xB5A58E), c(0xFFC447))
        MonsterType.SNOW_WOLF -> Named(plan(Body.HOUND, false), c(0x5A6A7C), c(0xF4FAFF), c(0x8CE6FF))
        MonsterType.FOREST_SPIDER -> Named(plan(Body.INSECT, false), c(0x1E2A12), c(0x8FA060), c(0xFF3A2A))
        MonsterType.CAVE_SPIDER -> Named(plan(Body.INSECT, false), c(0x1A1422), c(0x7A6A8C), c(0xC77DFF))
        MonsterType.TOXIC_SLIME -> Named(plan(Body.BLOB, false), c(0x1C3A0E), c(0xB8F060), c(0xFFF04A))
        MonsterType.STONE_GOLEM -> Named(plan(Body.GOLEM, false), c(0x2B2B2B), c(0xA8A39A), c(0xFF8A2E))
        MonsterType.CRYSTAL_GOLEM -> Named(plan(Body.GOLEM, false), c(0x1A2A55), c(0xA8D8FF), c(0x6AF0FF))
        MonsterType.MUSHROOM -> Named(MUSHROOM, c(0x3A1A18), c(0xE09A80), c(0xFFE070))
        MonsterType.MIRE_TOAD -> Named(TOAD, c(0x1E2A14), c(0xA0B860), c(0xFFD84A))
        MonsterType.WISP -> Named(WISP, c(0x6A3008), c(0xFFE090), c(0xFFF0B0))
        MonsterType.ICE_WISP -> Named(WISP, c(0x1A3A5A), c(0xD8F8FF), c(0xFFFFFF))
        MonsterType.BAT -> Named(BAT, c(0x1E1820), c(0x7A6A80), c(0xFF4040))
        MonsterType.TREANT -> Named(TREANT, c(0x2A2414), c(0xA89870), c(0xC8FF50))
        MonsterType.HYDRA -> Named(HYDRA, c(0x143018), c(0x7CC878), c(0xFFE040))
        MonsterType.YETI -> Named(YETI, c(0x4A5A6A), c(0xF4F9FF), c(0x7FE0FF))
        MonsterType.DRAGON -> Named(DRAGON, c(0x4A1008), c(0xE8A060), c(0xFFD030))
        else -> null
    }

    /** Decodes the sprites of the named monsters of [types] (zones 2-5) on a background thread. */
    suspend fun preloadNamed(resources: Resources, types: Collection<MonsterType>) {
        for (type in types) {
            val pl = named(type)?.plan ?: continue
            loadPlan(resources, pl)
        }
    }

    private suspend fun loadPlan(resources: Resources, pl: Plan) {
        for (res in intArrayOf(pl.base, pl.glow)) {
            if (cache.get(res) != null) continue
            val opts = BitmapFactory.Options().apply { if (res == pl.glow) inSampleSize = 2 }
            val bmp = withContext(Dispatchers.Default) { runCatching { BitmapFactory.decodeResource(resources, res, opts) }.getOrNull() } ?: continue
            cache.put(res, bmp.asImageBitmap())
        }
    }

    /** Draws a named monster of the zones 2-5 (body plan + fixed palette); false if it is not loaded or not named. */
    fun drawNamed(scope: DrawScope, type: MonsterType): Boolean {
        val n = named(type) ?: return false
        val base = cache.get(n.plan.base) ?: return false
        val (tint, glowFilter) = filtersFor("named/" + type.id, n.dark, n.light, n.eye)
        drawPlan(scope, base, cache.get(n.plan.glow), tint, glowFilter)
        return true
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
        val st = BiomeStyles.of(zone.biome!!)
        val fear = ProceduralMonsters.fear(zone, type)
        val dark = SceneKit.mix(st.skinDark, Color.Black, 0.30f + 0.25f * fear)
        val light = SceneKit.mix(st.skinLight, st.skinMid, 0.15f * fear)
        return filtersFor(zone.id + "/" + type.id, dark, light, st.eye)
    }

    /** Gradient map dark -> light (by luminance) and the eye-colour tint of the glow layer; cached for the last creature. */
    private fun filtersFor(key: String, dark: Color, light: Color, eye: Color): Pair<ColorFilter, ColorFilter> {
        if (key != lastKey || lastTint == null || lastGlow == null) {
            val gain = 1.12f
            fun row(lo: Float, hi: Float): FloatArray {
                val k = gain * (hi - lo)
                return floatArrayOf(k * 0.299f, k * 0.587f, k * 0.114f, 0f, lo * 255f)
            }
            val r = row(dark.red, light.red)
            val g = row(dark.green, light.green)
            val b = row(dark.blue, light.blue)
            lastTint = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(*r, *g, *b, 0f, 0f, 0f, 1f, 0f)))
            lastGlow = ColorFilter.tint(eye, BlendMode.Modulate)
            lastKey = key
        }
        return lastTint!! to lastGlow!!
    }

    /** Draws the tinted body plan of [type] in [zone] into the 200-unit monster box; false if not loaded yet. */
    fun draw(scope: DrawScope, zone: Zone, type: MonsterType): Boolean {
        val pl = planOf(zone, type) ?: return false
        val base = cache.get(pl.base) ?: return false
        val (tint, glowFilter) = filtersFor(zone, type)
        drawPlan(scope, base, cache.get(pl.glow), tint, glowFilter)
        return true
    }

    private fun drawPlan(scope: DrawScope, base: ImageBitmap, glow: ImageBitmap?, tint: ColorFilter, glowFilter: ColorFilter) {
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
    }
}
