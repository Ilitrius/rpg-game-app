package com.idlerpg.game.ui.adventure

import android.content.res.Resources
import android.graphics.Bitmap
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
import kotlin.math.sin

/**
 * Raytraced monsters of the generated zones (`tools/render_items/render_gen.py`): one neutral render per
 * [Body] and fear variant, split into four layers that are coloured per biome at draw time: skin (grey,
 * gradient-mapped to the biome skin palette), eye (eye colour), glow (accent colour, pulsing) and fixed
 * (teeth, claws, horns as rendered). The glow layer is kept as an alpha mask to save memory.
 * [ProceduralMonsters] stays the fallback and still paints the aura around the creature.
 */
internal object GenMonsterSprites {

    private class Layers(val skin: Int, val eye: Int, val glow: Int, val fixed: Int)

    /** Index = body.ordinal * 2 + fear variant (0 calm, 1 fearsome). */
    private val table = listOf(
        Layers(R.drawable.gen_blob_0_skin, R.drawable.gen_blob_0_eye, R.drawable.gen_blob_0_glow, R.drawable.gen_blob_0_fixed),
        Layers(R.drawable.gen_blob_1_skin, R.drawable.gen_blob_1_eye, R.drawable.gen_blob_1_glow, R.drawable.gen_blob_1_fixed),
        Layers(R.drawable.gen_hound_0_skin, R.drawable.gen_hound_0_eye, R.drawable.gen_hound_0_glow, R.drawable.gen_hound_0_fixed),
        Layers(R.drawable.gen_hound_1_skin, R.drawable.gen_hound_1_eye, R.drawable.gen_hound_1_glow, R.drawable.gen_hound_1_fixed),
        Layers(R.drawable.gen_humanoid_0_skin, R.drawable.gen_humanoid_0_eye, R.drawable.gen_humanoid_0_glow, R.drawable.gen_humanoid_0_fixed),
        Layers(R.drawable.gen_humanoid_1_skin, R.drawable.gen_humanoid_1_eye, R.drawable.gen_humanoid_1_glow, R.drawable.gen_humanoid_1_fixed),
        Layers(R.drawable.gen_insect_0_skin, R.drawable.gen_insect_0_eye, R.drawable.gen_insect_0_glow, R.drawable.gen_insect_0_fixed),
        Layers(R.drawable.gen_insect_1_skin, R.drawable.gen_insect_1_eye, R.drawable.gen_insect_1_glow, R.drawable.gen_insect_1_fixed),
        Layers(R.drawable.gen_winged_0_skin, R.drawable.gen_winged_0_eye, R.drawable.gen_winged_0_glow, R.drawable.gen_winged_0_fixed),
        Layers(R.drawable.gen_winged_1_skin, R.drawable.gen_winged_1_eye, R.drawable.gen_winged_1_glow, R.drawable.gen_winged_1_fixed),
        Layers(R.drawable.gen_serpent_0_skin, R.drawable.gen_serpent_0_eye, R.drawable.gen_serpent_0_glow, R.drawable.gen_serpent_0_fixed),
        Layers(R.drawable.gen_serpent_1_skin, R.drawable.gen_serpent_1_eye, R.drawable.gen_serpent_1_glow, R.drawable.gen_serpent_1_fixed),
        Layers(R.drawable.gen_wraith_0_skin, R.drawable.gen_wraith_0_eye, R.drawable.gen_wraith_0_glow, R.drawable.gen_wraith_0_fixed),
        Layers(R.drawable.gen_wraith_1_skin, R.drawable.gen_wraith_1_eye, R.drawable.gen_wraith_1_glow, R.drawable.gen_wraith_1_fixed),
        Layers(R.drawable.gen_golem_0_skin, R.drawable.gen_golem_0_eye, R.drawable.gen_golem_0_glow, R.drawable.gen_golem_0_fixed),
        Layers(R.drawable.gen_golem_1_skin, R.drawable.gen_golem_1_eye, R.drawable.gen_golem_1_glow, R.drawable.gen_golem_1_fixed),
    )

    private class Decoded(val skin: ImageBitmap, val eye: ImageBitmap, val glow: ImageBitmap, val fixed: ImageBitmap) {
        val bytes: Int = skin.width * skin.height * 13
    }

    private val cache = object : LruCache<Int, Decoded>(28 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Decoded): Int = value.bytes
    }

    private val filters = HashMap<Long, ColorFilter>()

    /** The fearsome variant from the middle of the game on, and always for bosses. */
    fun variant(zone: Zone, type: MonsterType): Int = if (type.isBoss || ProceduralMonsters.fear(zone, type) >= 0.5f) 1 else 0

    private fun key(zone: Zone, type: MonsterType): Int? {
        val biome = zone.biome ?: return null
        val body = BiomeStyles.of(biome).bodyOf(zone.monsters.indexOf(type), type.isBoss)
        return body.ordinal * 2 + variant(zone, type)
    }

    /** Decodes the layers of [types] in [zone] that are not cached yet, on a background thread. */
    suspend fun preload(resources: Resources, zone: Zone, types: Collection<MonsterType>) {
        for (type in types) {
            val k = key(zone, type) ?: continue
            if (cache.get(k) != null) continue
            val l = table[k]
            val d = withContext(Dispatchers.Default) {
                runCatching {
                    Decoded(
                        decode(resources, l.skin, mask = false), decode(resources, l.eye, mask = false),
                        decode(resources, l.glow, mask = true), decode(resources, l.fixed, mask = false),
                    )
                }.getOrNull()
            } ?: continue
            cache.put(k, d)
        }
    }

    private fun decode(resources: Resources, res: Int, mask: Boolean): ImageBitmap {
        val bmp = BitmapFactory.decodeResource(resources, res) ?: error("decode $res")
        if (!mask) return bmp.asImageBitmap()
        val a: Bitmap = bmp.extractAlpha()
        bmp.recycle()
        return a.asImageBitmap()
    }

    /**
     * Draws the creature into the 200-unit monster box (feet on [MonsterPainters.GROUND]) if its layers are
     * cached. Returns false otherwise, so the caller paints the procedural fallback.
     */
    fun draw(scope: DrawScope, zone: Zone, type: MonsterType, time: Float): Boolean {
        val k = key(zone, type) ?: return false
        val d = cache.get(k) ?: return false
        val st = BiomeStyles.of(zone.biome!!)
        val skinFilter = filters.getOrPut(k.toLong() * 1000 + zone.order) {
            val fear = ProceduralMonsters.fear(zone, type)
            // every zone and role gets its own shade of the biome palette, so the same body plan does not
            // look identical in all five stages; bosses lean towards the accent colour
            val role = zone.monsters.indexOf(type).coerceAtLeast(0)
            val vary = ((zone.order * 37 + role * 53) % 9) / 8f
            val tone = if (type.isBoss) 0.14f + 0.06f * zone.stage else 0.06f + 0.16f * vary
            val dark = SceneKit.mix(SceneKit.mix(st.skinDark, Color.Black, 0.25f * fear), st.accent, tone * 0.35f)
            val light = SceneKit.mix(SceneKit.mix(st.skinLight, st.skinMid, 0.45f * vary), st.accent, tone)
            gradientMap(dark, light)
        }
        with(scope) {
            val aspect = d.skin.width.toFloat() / d.skin.height
            val hUnits = min(190f, 270f / aspect)
            val wUnits = hUnits * aspect
            val s = hUnits / d.skin.height
            withTransform({
                translate(100f - wUnits / 2f, MonsterPainters.GROUND - hUnits)
                scale(s, s, Offset.Zero)
            }) {
                layer(d.skin, skinFilter)
                layer(d.fixed, null)
                layer(d.eye, ColorFilter.tint(st.eye, BlendMode.Modulate))
                layer(d.glow, ColorFilter.tint(st.accent), alpha = 0.65f + 0.35f * sin(time * 2.4f))
            }
        }
        return true
    }

    /** Maps the grey value g (0..1) to dark + (light - dark) * g; alpha unchanged. */
    private fun gradientMap(dark: Color, light: Color): ColorFilter {
        fun row(d: Float, l: Float) = floatArrayOf((l - d) * 0.299f, (l - d) * 0.587f, (l - d) * 0.114f, 0f, d * 255f)
        val m = row(dark.red, light.red) + row(dark.green, light.green) + row(dark.blue, light.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
        return ColorFilter.colorMatrix(ColorMatrix(m))
    }

    private fun DrawScope.layer(img: ImageBitmap, filter: ColorFilter?, alpha: Float = 1f) {
        val size = IntSize(img.width, img.height)
        drawImage(img, IntOffset.Zero, size, IntOffset.Zero, size, alpha = alpha, colorFilter = filter, filterQuality = FilterQuality.High)
    }
}
