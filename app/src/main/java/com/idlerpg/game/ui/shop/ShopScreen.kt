package com.idlerpg.game.ui.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.idlerpg.game.IdleRpgApp
import com.idlerpg.game.R
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.StatType
import com.idlerpg.game.domain.character.get
import com.idlerpg.game.domain.equipment.Inventory
import com.idlerpg.game.domain.shop.Shop
import com.idlerpg.game.audio.Sfx
import com.idlerpg.game.audio.SoundManager
import com.idlerpg.game.ui.equipment.ItemIcon
import com.idlerpg.game.ui.equipment.itemDisplayName
import com.idlerpg.game.ui.equipment.localizedName
import com.idlerpg.game.ui.equipment.rarityColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val GoodColor = Color(0xFF81C784)
private val BadColor = Color(0xFFE57373)
private val GoldColor = Color(0xFFE6BC58)

private enum class ShopMode { BUY, SELL }

private const val SELL_NONE = -1
/** Pending bulk-sale request: [SELL_ALL] or a [Rarity] ordinal. */
private const val SELL_ALL = -2

private fun sellRarities(code: Int): Set<Rarity> =
    if (code == SELL_ALL) Rarity.entries.toSet() else setOfNotNull(Rarity.entries.getOrNull(code))

/** "Shop" tab: merchant banner, buy (timed/paid restock) and sell (single + bulk) lists, item details. */
@Composable
fun ShopScreen() {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as IdleRpgApp).repository }
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    var mode by rememberSaveable { mutableIntStateOf(ShopMode.BUY.ordinal) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingSell by rememberSaveable { mutableIntStateOf(SELL_NONE) }

    // Clock for the restock countdown: only ticks while the screen is visible (battery).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Free restock (first visit, timer expired, hero outgrew the goods) — atomic in one update.
    val restockDue = Shop.isRestockDue(current, now)
    LaunchedEffect(restockDue) {
        if (restockDue) repository.update { Shop.ensureStock(it, System.currentTimeMillis()) }
    }

    val res = context.resources
    val sound = remember { SoundManager.get(context) }
    fun toast(text: String) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(text)
    }

    fun buy(item: Item) {
        selectedId = null
        scope.launch {
            var result: Shop.BuyResult? = null
            repository.update { s -> Shop.buy(s, item.id).also { result = it }.state }
            val r = result ?: return@launch
            if (r.outcome == Shop.BuyOutcome.BOUGHT) sound.play(Sfx.COIN)
            toast(
                when (r.outcome) {
                    Shop.BuyOutcome.BOUGHT -> res.getString(R.string.shop_bought, item.localizedName(res))
                    Shop.BuyOutcome.INVENTORY_FULL -> res.getString(R.string.shop_inventory_full)
                    Shop.BuyOutcome.NOT_ENOUGH_GOLD -> res.getString(R.string.shop_not_enough_gold, fmt(r.price - r.state.gold))
                    Shop.BuyOutcome.SOLD_OUT -> res.getString(R.string.shop_offer_gone)
                },
            )
        }
    }

    fun sell(item: Item) {
        selectedId = null
        scope.launch {
            var gold = 0L
            repository.update { s -> Shop.sell(s, item.id).also { gold = it.gold - s.gold } }
            if (gold > 0) sound.play(Sfx.COIN)
            if (gold > 0 || item.price == 0L) toast(res.getString(R.string.shop_sold_one, item.localizedName(res), fmt(gold)))
        }
    }

    // Recomputes the candidates inside the update (loot may have dropped since the dialog opened).
    fun bulkSell(rarities: Set<Rarity>) {
        scope.launch {
            var result: Shop.SellResult? = null
            repository.update { s -> Shop.bulkSell(s, rarities).also { result = it }.state }
            val r = result ?: return@launch
            if (r.count > 0) sound.play(Sfx.COIN)
            if (r.count > 0) toast(res.getQuantityString(R.plurals.shop_sold_many, r.count, r.count, fmt(r.gold)))
        }
    }

    fun restock() {
        scope.launch {
            var result: Shop.RestockResult? = null
            repository.update { s -> Shop.paidRestock(s, System.currentTimeMillis()).also { result = it }.state }
            val r = result ?: return@launch
            toast(res.getString(if (r.success) R.string.shop_restock_done else R.string.shop_restock_no_gold))
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { ShopHeader(current) }
            item(key = "mode") {
                ModeSelector(ShopMode.entries[mode]) { mode = it.ordinal }
            }
            if (mode == ShopMode.BUY.ordinal) {
                item(key = "restock") { RestockCard(current, now, onRestock = ::restock) }
                if (Inventory.isFull(current)) {
                    item(key = "full") { InventoryFullCard { mode = ShopMode.SELL.ordinal } }
                }
                val offers = current.shop.offers
                if (offers.isEmpty() && !restockDue) {
                    item(key = "soldout") { HintText(stringResource(R.string.shop_sold_out)) }
                }
                items(offers, key = { "offer_${it.id}" }) { offer ->
                    OfferRow(current, offer) { selectedId = offer.id }
                }
            } else {
                val inventory = Inventory.sorted(current.equipment.inventory)
                item(key = "invhead") { SellHeader(current, onSell = { pendingSell = it }) }
                if (inventory.isEmpty()) {
                    item(key = "invempty") { HintText(stringResource(R.string.shop_sell_empty)) }
                }
                items(inventory, key = { "inv_${it.id}" }) { item ->
                    SellRow(item) { selectedId = item.id }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    if (pendingSell != SELL_NONE) {
        val rarities = sellRarities(pendingSell)
        val count = Shop.bulkSellCandidates(current, rarities).size
        val gold = Shop.bulkSellGold(current, rarities)
        val kept = Shop.keptUpgradeCount(current, rarities)
        val everything = pendingSell == SELL_ALL
        val single = rarities.singleOrNull()
        AlertDialog(
            onDismissRequest = { pendingSell = SELL_NONE },
            title = {
                Text(
                    if (everything) stringResource(R.string.shop_sell_everything_confirm_title)
                    else stringResource(R.string.shop_sell_rarity_confirm_title, rarityLabelText(single ?: Rarity.COMMON)),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        pluralStringResource(
                            if (everything) R.plurals.shop_sell_everything_confirm else R.plurals.shop_sell_rarity_confirm,
                            count, count, fmt(gold),
                        ),
                    )
                    Text(stringResource(R.string.shop_sell_keeps_equipped))
                    if (kept > 0) Text(pluralStringResource(R.plurals.shop_sell_keeps_upgrades, kept, kept))
                    if (everything || (single != null && single >= Rarity.EPIC)) {
                        Text(stringResource(R.string.shop_sell_irreversible), color = BadColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = count > 0,
                    onClick = {
                        pendingSell = SELL_NONE
                        bulkSell(rarities)
                    },
                ) { Text(stringResource(R.string.shop_tab_sell), color = if (count > 0) BadColor else Color.Unspecified) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSell = SELL_NONE }) { Text(stringResource(R.string.shop_cancel)) }
            },
        )
    }

    val id = selectedId
    if (id != null) {
        val offer = current.shop.offers.firstOrNull { it.id == id }
        val owned = current.equipment.inventory.firstOrNull { it.id == id }
        when {
            offer != null -> ItemSheet(current, offer, ShopMode.BUY, onDismiss = { selectedId = null }, onAction = { buy(offer) })
            owned != null -> ItemSheet(current, owned, ShopMode.SELL, onDismiss = { selectedId = null }, onAction = { sell(owned) })
            else -> LaunchedEffect(id) { selectedId = null }
        }
    }
}


private val numberFormat: NumberFormat get() = NumberFormat.getIntegerInstance(Locale.getDefault())
private fun fmt(v: Long): String = numberFormat.format(v)
private fun fmt1(v: Double) = String.format(Locale.getDefault(), "%.1f", v)

private fun formatCountdown(millis: Long): String {
    val total = (millis + 999) / 1000
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}

@Composable
private fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

// ------------------------------------------------------------------ header

@Composable
private fun ShopHeader(state: GameState) {
    Box(Modifier.fillMaxWidth().height(150.dp)) {
        ShopBanner(Modifier.fillMaxSize())
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 52.dp, end = 50.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.shop_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF6E7C1),
                )
                Text(
                    stringResource(R.string.shop_subtitle, Shop.shopLevel(state)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD9C49A),
                )
            }
            GoldPill(state.gold)
        }
    }
}

@Composable
private fun GoldPill(gold: Long) {
    Surface(shape = RoundedCornerShape(50), color = Color(0xCC14100B), border = BorderStroke(1.dp, GoldColor.copy(alpha = 0.5f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GoldCoin(Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.shop_gold, fmt(gold)), style = MaterialTheme.typography.titleSmall, color = GoldColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(mode: ShopMode, onSelect: (ShopMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ShopMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onSelect(m) },
                shape = SegmentedButtonDefaults.itemShape(i, ShopMode.entries.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = mode == m) {
                        Icon(
                            if (m == ShopMode.BUY) Icons.Filled.ShoppingCart else Icons.Filled.Sell,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
            ) {
                Text(stringResource(if (m == ShopMode.BUY) R.string.shop_tab_buy else R.string.shop_tab_sell))
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
    )
}

// ------------------------------------------------------------------ buy

@Composable
private fun RestockCard(state: GameState, now: Long, onRestock: () -> Unit) {
    val cost = Shop.restockCost(state)
    val affordable = state.gold >= cost
    Card(Modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                val left = Shop.millisUntilRestock(state, now)
                Text(
                    if (state.shop.stockLevel <= 0 || left <= 0) stringResource(R.string.shop_restock_arriving)
                    else stringResource(R.string.shop_restock_in, formatCountdown(left)),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            FilledTonalButton(onClick = onRestock, enabled = affordable, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shop_restock_paid, fmt(cost)), maxLines = 1)
            }
            if (state.shop.paidRestocks > 0) {
                Text(stringResource(R.string.shop_restock_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InventoryFullCard(onGoToSell: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BadColor.copy(alpha = 0.14f)),
        border = BorderStroke(1.dp, BadColor.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Inventory2, contentDescription = null, tint = BadColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shop_inventory_full), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onGoToSell, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.shop_go_to_sell), maxLines = 1)
            }
        }
    }
}

@Composable
private fun OfferRow(state: GameState, item: Item, onClick: () -> Unit) {
    val price = Shop.buyPrice(item)
    val affordable = state.gold >= price
    val dpsDelta = remember(state, item) { Inventory.combatWith(state, item).dps - CombatStats.of(state).dps }
    val upgrade = remember(state, item) { Shop.isUpgrade(state, item) }
    ItemRowCard(item, onClick, trailing = { PriceTag(price, affordable) }) {
        when {
            abs(dpsDelta) >= 0.05 -> Text(
                stringResource(R.string.shop_dps_delta, (if (dpsDelta > 0) "+" else "−") + fmt1(abs(dpsDelta))),
                style = MaterialTheme.typography.labelMedium,
                color = if (dpsDelta > 0) GoodColor else BadColor,
            )
            upgrade -> Text(stringResource(R.string.shop_upgrade), style = MaterialTheme.typography.labelMedium, color = GoodColor)
        }
    }
}

@Composable
private fun PriceTag(price: Long, affordable: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GoldCoin(Modifier.size(20.dp), dimmed = !affordable)
        Spacer(Modifier.width(5.dp))
        Text(
            fmt(price),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (affordable) GoldColor else BadColor,
        )
    }
}

/** Common row layout: icon, name (rarity color), meta line, extra [details], [trailing] price. */
@Composable
private fun ItemRowCard(
    item: Item,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = cardColors(),
        border = BorderStroke(1.dp, rarityColor(item.rarity).copy(alpha = if (item.rarity >= Rarity.RARE) 0.45f else 0.15f)),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemIcon(item, Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    itemDisplayName(item),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = rarityColor(item.rarity),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${rarityLabel(item.rarity)} · ${stringResource(slotLabel(item.slot))} · ${stringResource(R.string.shop_level, item.itemLevel)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                details()
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

// ------------------------------------------------------------------ sell

@Composable
private fun SellHeader(state: GameState, onSell: (Int) -> Unit) {
    val groups = remember(state) { Shop.sellGroups(state) }
    Card(Modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.shop_sell_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.shop_sell_count, state.equipment.inventory.size, Inventory.CAPACITY),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (Inventory.isFull(state)) BadColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (groups.isNotEmpty()) {
                Text(stringResource(R.string.shop_quicksell_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                groups.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(rarityColor(g.rarity), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rarityLabelText(g.rarity), style = MaterialTheme.typography.titleSmall, color = rarityColor(g.rarity), maxLines = 1)
                            Text(
                                pluralStringResource(R.plurals.shop_sell_detail, g.count, g.count, fmt(g.gold)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        FilledTonalButton(onClick = { onSell(g.rarity.ordinal) }) { Text(stringResource(R.string.shop_tab_sell), maxLines = 1) }
                    }
                }
                val total = groups.sumOf { it.count }
                val gold = groups.sumOf { it.gold }
                Button(
                    onClick = { onSell(SELL_ALL) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BadColor.copy(alpha = 0.85f), contentColor = Color.White),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.shop_sell_everything), maxLines = 1)
                        Text(
                            pluralStringResource(R.plurals.shop_sell_detail, total, total, fmt(gold)),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SellRow(item: Item, onClick: () -> Unit) {
    ItemRowCard(item, onClick, trailing = { PriceTag(item.price, affordable = true) }) {
        if (item.isNew) {
            Text(stringResource(R.string.shop_new), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ------------------------------------------------------------------ detail sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(state: GameState, item: Item, mode: ShopMode, onDismiss: () -> Unit, onAction: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(item, Modifier.size(112.dp))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(itemDisplayName(item), style = MaterialTheme.typography.titleLarge, color = rarityColor(item.rarity), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${rarityLabel(item.rarity)} · ${stringResource(slotLabel(item.slot))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = rarityColor(item.rarity).copy(alpha = 0.85f),
                    )
                    Text(stringResource(R.string.shop_level, item.itemLevel), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.damageMax > 0) {
                        Text(stringResource(R.string.shop_damage, item.damageMin, item.damageMax), style = MaterialTheme.typography.titleMedium)
                    }
                    val lines = StatType.entries.filter { item.bonus[it] != 0 }
                    if (lines.isEmpty()) Text(stringResource(R.string.shop_no_bonus), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    lines.forEach { t ->
                        val v = item.bonus[t]
                        Text("${signed(v)} ${stringResource(statLabel(t))}", style = MaterialTheme.typography.bodyLarge, color = if (v > 0) GoodColor else BadColor)
                    }
                    Text(stringResource(R.string.shop_sell_value, fmt(item.price)), style = MaterialTheme.typography.bodySmall, color = GoldColor)
                }
            }

            ComparisonBlock(state, item)

            if (mode == ShopMode.BUY) {
                val price = Shop.buyPrice(item)
                val blocker = Shop.buyBlocker(state, item)
                Button(onClick = onAction, enabled = blocker == null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shop_buy, fmt(price)), maxLines = 1)
                }
                val reason = when (blocker) {
                    Shop.BuyOutcome.INVENTORY_FULL -> stringResource(R.string.shop_inventory_full)
                    Shop.BuyOutcome.NOT_ENOUGH_GOLD -> stringResource(R.string.shop_not_enough_gold, fmt(price - state.gold))
                    Shop.BuyOutcome.SOLD_OUT -> stringResource(R.string.shop_offer_gone)
                    else -> null
                }
                if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall, color = BadColor)
            } else {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Sell, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shop_sell, fmt(item.price)), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ComparisonBlock(state: GameState, item: Item) {
    val equipped = state.equipment.equipped[item.slot]
    val diff = Inventory.compare(item, equipped)
    val dpsNow = remember(state) { CombatStats.of(state).dps }
    val dpsNew = remember(state, item) { Inventory.combatWith(state, item).dps }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (equipped != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(equipped, Modifier.size(36.dp), contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.shop_compare_title, itemDisplayName(equipped)), style = MaterialTheme.typography.titleSmall, color = rarityColor(equipped.rarity))
                }
            } else {
                Text(stringResource(R.string.shop_compare_empty), style = MaterialTheme.typography.titleSmall)
            }
            val statLines = StatType.entries.filter { diff.stats[it] != 0 }
            if (statLines.isEmpty() && !diff.hasDamage) {
                Text(stringResource(R.string.shop_compare_same), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (diff.hasDamage) {
                DeltaText(
                    stringResource(R.string.shop_compare_damage, signed(diff.damageMin), signed(diff.damageMax)),
                    positive = diff.damageMin + diff.damageMax >= 0,
                )
            }
            statLines.forEach { t ->
                val v = diff.stats[t]
                DeltaText("${signed(v)} ${stringResource(statLabel(t))}", positive = v > 0)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            val d = dpsNew - dpsNow
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.shop_compare_dps, fmt1(dpsNow), fmt1(dpsNew)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (abs(d) >= 0.05) {
                    Text(
                        (if (d > 0) "+" else "−") + fmt1(abs(d)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (d > 0) GoodColor else BadColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeltaText(text: String, positive: Boolean) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = if (positive) GoodColor else BadColor)
}

private fun signed(v: Int) = when {
    v > 0 -> "+$v"
    v < 0 -> "−${-v}"
    else -> "±0"
}

// Labels: shared vocabulary of the Ausrüstung tab (strings_ausruestung.xml), read-only reuse.
private fun slotLabel(slot: EquipmentSlot) = when (slot) {
    EquipmentSlot.WEAPON -> R.string.equipment_slot_weapon
    EquipmentSlot.HELMET -> R.string.equipment_slot_helmet
    EquipmentSlot.ARMOR -> R.string.equipment_slot_armor
    EquipmentSlot.GLOVES -> R.string.equipment_slot_gloves
    EquipmentSlot.BOOTS -> R.string.equipment_slot_boots
    EquipmentSlot.RING -> R.string.equipment_slot_ring
    EquipmentSlot.AMULET -> R.string.equipment_slot_amulet
}

private fun statLabel(t: StatType) = when (t) {
    StatType.STRENGTH -> R.string.equipment_stat_strength
    StatType.DEXTERITY -> R.string.equipment_stat_dexterity
    StatType.VITALITY -> R.string.equipment_stat_vitality
    StatType.LUCK -> R.string.equipment_stat_luck
}

@Composable
private fun rarityLabel(r: Rarity) = rarityLabelText(r)

@Composable
private fun rarityLabelText(r: Rarity) = stringResource(
    when (r) {
        Rarity.COMMON -> R.string.equipment_rarity_common
        Rarity.UNCOMMON -> R.string.equipment_rarity_uncommon
        Rarity.RARE -> R.string.equipment_rarity_rare
        Rarity.EPIC -> R.string.equipment_rarity_epic
        Rarity.LEGENDARY -> R.string.equipment_rarity_legendary
    },
)

