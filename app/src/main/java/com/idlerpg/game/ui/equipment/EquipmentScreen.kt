package com.idlerpg.game.ui.equipment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.idlerpg.game.ui.character.weapon.drawWeaponInHand
import com.idlerpg.game.ui.character.weapon.weaponLookOf
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val GoodColor = Color(0xFF81C784)
private val BadColor = Color(0xFFE57373)
private val GoldColor = Color(0xFFE6BC58)

/** "Ausrüstung" tab: paper doll with 7 slots, bonus summary, inventory grid and item details. */
@Composable
fun EquipmentScreen() {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as IdleRpgApp).repository }
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState(initial = null)
    // Selected item id (inventory or equipped).
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaperDollCard(current) { item -> selectedId = item.id }
        BonusCard(current)
        InventoryCard(current) { item ->
            selectedId = item.id
            if (item.isNew) scope.launch { repository.update { Inventory.markSeen(it, item.id) } }
        }
    }

    val id = selectedId
    if (id != null) {
        val equippedEntry = current.equipment.equipped.entries.firstOrNull { it.value.id == id }
        val invItem = current.equipment.inventory.firstOrNull { it.id == id }
        val item = equippedEntry?.value ?: invItem
        if (item == null) {
            LaunchedEffect(id) { selectedId = null }
        } else {
            ItemDetailSheet(
                state = current,
                item = item,
                isEquipped = equippedEntry != null,
                onDismiss = { selectedId = null },
                onEquip = {
                    selectedId = null
                    scope.launch { repository.update { Inventory.equip(it, item.id) } }
                },
                onUnequip = {
                    selectedId = null
                    scope.launch { repository.update { Inventory.unequip(it, item.slot) } }
                },
                onSell = {
                    selectedId = null
                    scope.launch { repository.update { Inventory.sell(it, item.id) } }
                },
            )
        }
    }
}

private val numberFormat: NumberFormat get() = NumberFormat.getIntegerInstance(Locale.GERMANY)

private fun fmt1(v: Double) = String.format(Locale.GERMANY, "%.1f", v)

@Composable
private fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

// ------------------------------------------------------------------ paper doll

@Composable
private fun PaperDollCard(state: GameState, onSlotClick: (Item) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.equipment_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                GoldPill(state.gold)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EquipmentSlot.HELMET, EquipmentSlot.ARMOR, EquipmentSlot.GLOVES, EquipmentSlot.BOOTS).forEach {
                        SlotView(it, state.equipment.equipped[it], onSlotClick)
                    }
                }
                HeroSilhouette(Modifier.weight(1f).height(330.dp), state.equipment.equipped[EquipmentSlot.WEAPON])
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EquipmentSlot.AMULET, EquipmentSlot.WEAPON, EquipmentSlot.RING).forEach {
                        SlotView(it, state.equipment.equipped[it], onSlotClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun GoldPill(gold: Long) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = GoldColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.equipment_gold, numberFormat.format(gold)), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SlotView(slot: EquipmentSlot, item: Item?, onClick: (Item) -> Unit) {
    val label = stringResource(slotLabel(slot))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        val m = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(9.dp))
            .let { if (item != null) it.clickable { onClick(item) } else it }
        if (item != null) {
            ItemIcon(item, m)
        } else {
            EmptySlotIcon(slot, m, contentDescription = stringResource(R.string.equipment_slot_empty, label))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = item?.let { rarityColor(it.rarity) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Soft vector silhouette of the hero between the slot columns, holding the equipped [weapon]. */
@Composable
private fun HeroSilhouette(modifier: Modifier, weapon: Item? = null) {
    val glow = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val s = min(size.width / 100f, size.height / 200f)
        val dx = (size.width - 100f * s) / 2f
        val dy = (size.height - 200f * s) / 2f
        withTransform({ translate(dx, dy); scale(s, s, pivot = Offset.Zero) }) {
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.16f), Color.Transparent), center = Offset(50f, 90f), radius = 90f),
                radius = 90f, center = Offset(50f, 90f),
            )
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), center = Offset(50f, 194f), radius = 34f),
                topLeft = Offset(16f, 187f), size = Size(68f, 14f),
            )
            val body = heroPath
            val fill = Brush.verticalGradient(listOf(Color(0xFF4A5247), Color(0xFF2C322B), Color(0xFF20251F)), startY = 8f, endY = 194f)
            drawOval(fill, Offset(39f, 9f), Size(22f, 26f))
            drawPath(body, fill)
            drawOval(glow.copy(alpha = 0.35f), Offset(39f, 9f), Size(22f, 26f), style = Stroke(0.9f))
            drawPath(body, glow.copy(alpha = 0.35f), style = Stroke(0.9f))
            // Subtle rim light on the left side
            drawArc(Color.White.copy(alpha = 0.18f), 150f, 90f, false, Offset(40f, 10f), Size(20f, 24f), style = Stroke(1.2f))
            // Equipped weapon in the right-hand side hand (graphic by the Charakter agent).
            if (weapon != null) drawWeaponInHand(weaponLookOf(weapon), Offset(82.5f, 103.5f), 8f, 0.2f, Color(0xFF2E342D))
        }
    }
}

private val heroPath: Path by lazy {
    Path().apply {
        moveTo(45f, 33f); lineTo(55f, 33f); lineTo(56f, 39f)
        cubicTo(65f, 40f, 75f, 42f, 78.5f, 49f)
        cubicTo(81.5f, 58f, 83.5f, 78f, 85.5f, 98f)
        quadraticTo(87f, 107f, 82.5f, 109f)
        quadraticTo(79f, 107f, 78f, 100f)
        cubicTo(76f, 84f, 73.5f, 70f, 70.5f, 61f)
        cubicTo(69f, 78f, 69.5f, 92f, 66.5f, 104f)
        cubicTo(67.5f, 132f, 66f, 160f, 63.5f, 186f)
        lineTo(66.5f, 193f); lineTo(53.5f, 193f); lineTo(53.5f, 186f)
        cubicTo(53f, 160f, 52.5f, 135f, 50f, 116f)
        cubicTo(47.5f, 135f, 47f, 160f, 46.5f, 186f)
        lineTo(46.5f, 193f); lineTo(33.5f, 193f); lineTo(36.5f, 186f)
        cubicTo(34f, 160f, 32.5f, 132f, 33.5f, 104f)
        cubicTo(30.5f, 92f, 31f, 78f, 29.5f, 61f)
        cubicTo(26.5f, 70f, 24f, 84f, 22f, 100f)
        quadraticTo(21f, 107f, 17.5f, 109f)
        quadraticTo(13f, 107f, 14.5f, 98f)
        cubicTo(16.5f, 78f, 18.5f, 58f, 21.5f, 49f)
        cubicTo(25f, 42f, 35f, 40f, 44f, 39f)
        close()
    }
}

// ------------------------------------------------------------------ bonus summary

@Composable
private fun BonusCard(state: GameState) {
    val equipped = state.equipment.equipped.values
    val bonus = CombatStats.equipmentBonus(equipped)
    val combat = remember(state) { CombatStats.of(state) }
    val weapon = state.equipment.equipped[EquipmentSlot.WEAPON]
    Card(Modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.equipment_bonus_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatType.entries.forEach { t ->
                    val v = bonus[t]
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (v > 0) "+$v" else v.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (v > 0) GoodColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(stringResource(statLabel(t)), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SummaryRow(
                stringResource(R.string.equipment_bonus_weapon_damage),
                if (weapon != null && weapon.damageMax > 0) "${weapon.damageMin}–${weapon.damageMax}"
                else "${stringResource(R.string.equipment_fists)} ${CombatStats.FIST_DAMAGE_MIN}–${CombatStats.FIST_DAMAGE_MAX}",
            )
            SummaryRow(stringResource(R.string.equipment_bonus_dps), fmt1(combat.dps), emphasize = true)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ------------------------------------------------------------------ inventory

@Composable
private fun InventoryCard(state: GameState, onItemClick: (Item) -> Unit) {
    val items = remember(state.equipment.inventory) { Inventory.sorted(state.equipment.inventory) }
    val full = Inventory.isFull(state)
    Card(Modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.equipment_inventory_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.equipment_inventory_count, items.size, Inventory.CAPACITY),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (full) BadColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (full) {
                Text(stringResource(R.string.equipment_inventory_full), style = MaterialTheme.typography.bodySmall, color = BadColor)
            }
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.equipment_inventory_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InventoryGrid(items, onItemClick)
        }
    }
}

@Composable
private fun InventoryGrid(items: List<Item>, onItemClick: (Item) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap: Dp = 8.dp
        val columns = max(4, ((maxWidth + gap) / (64.dp + gap)).toInt())
        // Show all items plus empty cells to complete the last row (at least one row).
        val cells = max(columns, ((items.size + columns - 1) / columns) * columns).coerceAtMost(
            ((Inventory.CAPACITY + columns - 1) / columns) * columns,
        )
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            (0 until cells).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { i ->
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            val item = items.getOrNull(i)
                            if (item != null) InventoryCell(item) { onItemClick(item) } else EmptyCell()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryCell(item: Item, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick)) {
        ItemIcon(item, Modifier.fillMaxSize())
        Text(
            stringResource(R.string.equipment_level_short, item.itemLevel),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp),
        )
        if (item.isNew) {
            Text(
                stringResource(R.string.equipment_new_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp),
            )
        }
    }
}

@Composable
private fun EmptyCell() {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF20241F), Color(0xFF171A16)))),
    )
}

// ------------------------------------------------------------------ detail sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetailSheet(
    state: GameState,
    item: Item,
    isEquipped: Boolean,
    onDismiss: () -> Unit,
    onEquip: () -> Unit,
    onUnequip: () -> Unit,
    onSell: () -> Unit,
) {
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
                    Text(item.name, style = MaterialTheme.typography.titleLarge, color = rarityColor(item.rarity), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${rarityLabel(item.rarity)} · ${stringResource(slotLabel(item.slot))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = rarityColor(item.rarity).copy(alpha = 0.85f),
                    )
                    Text(stringResource(R.string.equipment_detail_level, item.itemLevel), style = MaterialTheme.typography.bodyMedium)
                    if (isEquipped) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                            Text(
                                stringResource(R.string.equipment_detail_equipped),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Own values
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.damageMax > 0) {
                        Text(
                            stringResource(R.string.equipment_detail_damage, item.damageMin, item.damageMax),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    val lines = StatType.entries.filter { item.bonus[it] != 0 }
                    if (lines.isEmpty()) {
                        Text(stringResource(R.string.equipment_detail_no_bonus), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    lines.forEach { t ->
                        val v = item.bonus[t]
                        Text(
                            "${if (v > 0) "+" else ""}$v ${stringResource(statLabel(t))}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (v > 0) GoodColor else BadColor,
                        )
                    }
                    Text(
                        stringResource(R.string.equipment_detail_value, numberFormat.format(item.price)),
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldColor,
                    )
                }
            }

            if (!isEquipped) ComparisonBlock(state, item)

            // Actions
            if (isEquipped) {
                val canUnequip = Inventory.canUnequip(state)
                Button(onClick = onUnequip, enabled = canUnequip, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.equipment_action_unequip))
                }
                if (!canUnequip) {
                    Text(stringResource(R.string.equipment_unequip_full), style = MaterialTheme.typography.bodySmall, color = BadColor)
                }
            } else {
                Button(onClick = onEquip, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.equipment_action_equip))
                }
                OutlinedButton(onClick = onSell, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.equipment_action_sell, numberFormat.format(item.price)), maxLines = 1)
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
                    Text(
                        stringResource(R.string.equipment_compare_title, equipped.name),
                        style = MaterialTheme.typography.titleSmall,
                        color = rarityColor(equipped.rarity),
                    )
                }
            } else {
                Text(stringResource(R.string.equipment_compare_empty_slot), style = MaterialTheme.typography.titleSmall)
            }
            val statLines = StatType.entries.filter { diff.stats[it] != 0 }
            if (statLines.isEmpty() && !diff.hasDamage) {
                Text(stringResource(R.string.equipment_compare_same), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (diff.hasDamage) {
                DeltaText(
                    "${stringResource(R.string.equipment_compare_damage)} ${signed(diff.damageMin)} / ${signed(diff.damageMax)}",
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
                Text(
                    stringResource(R.string.equipment_compare_dps, fmt1(dpsNow), fmt1(dpsNew)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (kotlin.math.abs(d) >= 0.05) {
                    Text(
                        (if (d > 0) "+" else "−") + fmt1(kotlin.math.abs(d)),
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
private fun rarityLabel(r: Rarity) = stringResource(
    when (r) {
        Rarity.COMMON -> R.string.equipment_rarity_common
        Rarity.UNCOMMON -> R.string.equipment_rarity_uncommon
        Rarity.RARE -> R.string.equipment_rarity_rare
        Rarity.EPIC -> R.string.equipment_rarity_epic
        Rarity.LEGENDARY -> R.string.equipment_rarity_legendary
    },
)
