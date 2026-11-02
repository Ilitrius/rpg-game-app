package com.idlerpg.game.ui.equipment

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.idlerpg.game.R
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.equipment.ItemBaseTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pre-rendered item graphics (raytraced offline by `tools/render_items`, 512 px WebP in
 * `drawable-nodpi`). Decoded once off the main thread and kept in a size-bounded LRU cache; while a
 * sprite is not ready (or the base type has none) [ItemIcon] falls back to the vector painters.
 */
internal object ItemSprites {

    /** Cache budget in bytes (one 512 x 512 sprite is 1 MiB decoded). */
    private const val CACHE_BYTES = 24 * 1024 * 1024

    private val cache = object : LruCache<Int, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** Drawable resource of the sprite for [item], or null if there is none. */
    fun resFor(item: Item): Int? = when (ItemBaseTypes.of(item).id) {
        ItemBaseTypes.DAGGER ->
            if (item.id == StarterItems.RUSTY_DAGGER_ID) R.drawable.item_dagger_rusty else R.drawable.item_dagger
        ItemBaseTypes.SHORT_SWORD -> byRarity(item.rarity, SHORT_SWORD)
        ItemBaseTypes.MACE -> byRarity(item.rarity, MACE)
        ItemBaseTypes.AXE -> byRarity(item.rarity, AXE)
        ItemBaseTypes.LEATHER_CAP -> R.drawable.item_leather_cap
        ItemBaseTypes.IRON_HELM -> R.drawable.item_iron_helm
        ItemBaseTypes.LEATHER_VEST -> R.drawable.item_leather_vest
        ItemBaseTypes.CHAINMAIL -> R.drawable.item_chainmail
        ItemBaseTypes.LEATHER_GLOVES -> R.drawable.item_leather_gloves
        ItemBaseTypes.GAUNTLETS -> R.drawable.item_gauntlets
        ItemBaseTypes.LEATHER_BOOTS -> R.drawable.item_leather_boots
        ItemBaseTypes.PLATE_BOOTS -> R.drawable.item_plate_boots
        ItemBaseTypes.SILVER_RING -> byRarity(item.rarity, SILVER_RING)
        ItemBaseTypes.GOLD_RING -> byRarity(item.rarity, GOLD_RING)
        ItemBaseTypes.AMULET -> byRarity(item.rarity, AMULET)
        ItemBaseTypes.TALISMAN -> byRarity(item.rarity, TALISMAN)
        else -> null
    }

    fun cached(res: Int): ImageBitmap? = cache.get(res)

    /** Returns the decoded sprite, decoding on a background thread on a cache miss. */
    suspend fun load(resources: Resources, res: Int): ImageBitmap? {
        cache.get(res)?.let { return it }
        return withContext(Dispatchers.Default) {
            val bmp = runCatching { BitmapFactory.decodeResource(resources, res) }.getOrNull()
                ?: return@withContext null
            bmp.asImageBitmap().also { cache.put(res, it) }
        }
    }

    private fun byRarity(rarity: Rarity, ids: IntArray): Int = ids[rarity.ordinal]

    private val SHORT_SWORD = intArrayOf(
        R.drawable.item_short_sword_common, R.drawable.item_short_sword_uncommon, R.drawable.item_short_sword_rare,
        R.drawable.item_short_sword_epic, R.drawable.item_short_sword_legendary,
    )
    private val MACE = intArrayOf(
        R.drawable.item_mace_common, R.drawable.item_mace_uncommon, R.drawable.item_mace_rare,
        R.drawable.item_mace_epic, R.drawable.item_mace_legendary,
    )
    private val AXE = intArrayOf(
        R.drawable.item_axe_common, R.drawable.item_axe_uncommon, R.drawable.item_axe_rare,
        R.drawable.item_axe_epic, R.drawable.item_axe_legendary,
    )
    private val SILVER_RING = intArrayOf(
        R.drawable.item_silver_ring_common, R.drawable.item_silver_ring_uncommon, R.drawable.item_silver_ring_rare,
        R.drawable.item_silver_ring_epic, R.drawable.item_silver_ring_legendary,
    )
    private val GOLD_RING = intArrayOf(
        R.drawable.item_gold_ring_common, R.drawable.item_gold_ring_uncommon, R.drawable.item_gold_ring_rare,
        R.drawable.item_gold_ring_epic, R.drawable.item_gold_ring_legendary,
    )
    private val AMULET = intArrayOf(
        R.drawable.item_amulet_common, R.drawable.item_amulet_uncommon, R.drawable.item_amulet_rare,
        R.drawable.item_amulet_epic, R.drawable.item_amulet_legendary,
    )
    private val TALISMAN = intArrayOf(
        R.drawable.item_talisman_common, R.drawable.item_talisman_uncommon, R.drawable.item_talisman_rare,
        R.drawable.item_talisman_epic, R.drawable.item_talisman_legendary,
    )
}

/** The sprite of [item] once decoded; null while loading or if the item has no sprite. */
@Composable
internal fun rememberItemSprite(item: Item): ImageBitmap? {
    val res = remember(item.baseType, item.rarity, item.id) { ItemSprites.resFor(item) } ?: return null
    val resources = LocalContext.current.resources
    ItemSprites.cached(res)?.let { return it }
    var sprite by remember(res) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(res) { sprite = ItemSprites.load(resources, res) }
    return sprite
}
