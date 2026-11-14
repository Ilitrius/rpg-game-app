package com.idlerpg.game.ui.adventure

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.idlerpg.game.R
import com.idlerpg.game.domain.world.MonsterType
import com.idlerpg.game.domain.world.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One finished picture per monster of the generated zones (6..100). If a drawable with the matching name
 * exists, it is drawn instead of the tinted body-plan layers of [GenMonsterSprites]; otherwise the old
 * rendering stays in place, so pictures can be added one by one without a code change.
 *
 * File names (in `res/drawable-nodpi/`, WebP or PNG, transparent, front view, feet at the bottom):
 * - regular monsters: `mob_<biome>_<1..3>` (the three roles of a biome, shared by its five stages), e.g. `mob_heide_2`
 * - bosses: `mob_<biome>_boss_<1..5>` (one per stage), e.g. `mob_vulkan_boss_3`
 *
 * The picture is fitted into the 200-unit monster box like [MonsterSprites] (max. 190 units high, 270 wide).
 * Colours are used as delivered (no biome tint, no aura).
 */
internal object MonsterArt {

    private const val CACHE_BYTES = 24 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** Drawable name -> resource id (0 = no such picture). Filled by [preload] on the main thread. */
    private val ids = HashMap<String, Int>()

    /** Drawable name of [type] in [zone], or null for the hand-made zones (those use [MonsterSprites]). */
    fun name(zone: Zone, type: MonsterType): String? {
        val biome = zone.biome ?: return null
        if (type.isBoss) return "mob_${biome.id}_boss_${zone.stage + 1}"
        val role = zone.monsters.indexOf(type)
        return if (role in 0..2) "mob_${biome.id}_${role + 1}" else null
    }

    /** The cached picture, or null if there is none (yet). Cheap, safe to call while drawing. */
    fun peek(zone: Zone, type: MonsterType): ImageBitmap? = name(zone, type)?.let { cache.get(it) }

    /** Looks up and decodes the pictures of [types] in [zone] that exist and are not cached yet. */
    suspend fun preload(resources: Resources, zone: Zone, types: Collection<MonsterType>) {
        val pkg = resources.getResourcePackageName(R.drawable.scene_meadow)
        for (type in types) {
            val name = name(zone, type) ?: continue
            if (cache.get(name) != null) continue
            val id = ids.getOrPut(name) { resources.getIdentifier(name, "drawable", pkg) }
            if (id == 0) continue
            val bmp = withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeResource(resources, id) }.getOrNull()
            } ?: continue
            cache.put(name, bmp.asImageBitmap())
        }
    }
}
