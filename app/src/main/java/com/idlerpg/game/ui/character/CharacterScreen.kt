package com.idlerpg.game.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.idlerpg.game.IdleRpgApp
import com.idlerpg.game.R
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.character.StatType
import com.idlerpg.game.domain.character.WeaponAction
import com.idlerpg.game.domain.character.get
import com.idlerpg.game.ui.character.weapon.WeaponImage
import com.idlerpg.game.ui.equipment.itemDisplayName
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** "Charakter" tab: level/XP/gold, equipped weapon, stats with point spending, combat values. */
@Composable
fun CharacterScreen() {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as IdleRpgApp).repository }
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsStateWithLifecycle(initialValue = null)

    // Saves imported at runtime may still lack the starter weapon.
    LaunchedEffect(state?.starterWeaponGranted) {
        if (state?.starterWeaponGranted == false) repository.update(StarterItems::ensureStarterWeapon)
    }

    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val combat = remember(current) { CombatStats.of(current) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeaderCard(current)
        WeaponCard(current.equipment.equipped[EquipmentSlot.WEAPON], combat)
        StatsCard(current, combat) { type ->
            scope.launch { repository.update { Progression.spendPoint(it, type) } }
        }
        DerivedCard(combat)
    }
}

private val numberFormat: NumberFormat get() = NumberFormat.getIntegerInstance(Locale.getDefault())

@Composable
private fun HeaderCard(state: GameState) {
    val c = state.character
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.character_level, c.level),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = Color(0xFFE6BC58), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.character_gold, numberFormat.format(state.gold)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            val atMax = c.level >= Progression.MAX_LEVEL
            LinearProgressIndicator(
                progress = { Progression.levelProgress(c) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Text(
                if (atMax) {
                    stringResource(R.string.character_xp_max)
                } else {
                    stringResource(
                        R.string.character_xp,
                        numberFormat.format(c.xp),
                        numberFormat.format(Progression.xpForNextLevel(c.level)),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeaponCard(weapon: Item?, combat: CombatStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                weapon?.rarity?.takeIf { it >= Rarity.RARE }
                                    ?.let { lerp(Color(0xFF4A4F45), rarityColor(it), 0.35f) }
                                    ?: Color(0xFF4A4F45),
                                Color(0xFF262B25),
                                Color(0xFF171A16),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (weapon != null) {
                    WeaponImage(
                        item = weapon,
                        rotationDegrees = 90f,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 30.dp),
                        contentDescription = stringResource(R.string.character_weapon_image),
                    )
                } else {
                    WeaponImage(
                        item = null,
                        rotationDegrees = -8f,
                        modifier = Modifier.fillMaxSize().padding(top = 26.dp, bottom = 30.dp),
                    )
                    Text(
                        stringResource(R.string.character_weapon_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    )
                }
                Text(
                    stringResource(R.string.character_weapon_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        weapon?.let { itemDisplayName(it) } ?: stringResource(R.string.character_weapon_fists),
                        style = MaterialTheme.typography.titleLarge,
                        color = weapon?.rarity?.let { rarityColor(it) } ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (weapon != null) {
                        Text(
                            rarityLabel(weapon.rarity),
                            style = MaterialTheme.typography.labelMedium,
                            color = rarityColor(weapon.rarity),
                        )
                    }
                }
                val baseMin = weapon?.damageMin?.takeIf { weapon.damageMax > 0 } ?: CombatStats.FIST_DAMAGE_MIN
                val baseMax = weapon?.damageMax?.takeIf { it > 0 } ?: CombatStats.FIST_DAMAGE_MAX
                Text(
                    stringResource(R.string.character_weapon_damage, baseMin, baseMax),
                    style = MaterialTheme.typography.bodyLarge,
                )
                weapon?.let { bonusLine(it) }?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF81C784))
                }
                Spacer(Modifier.height(8.dp))
                ActionAffinities(combat)
            }
        }
    }
}

@Composable
private fun bonusLine(item: Item): String {
    val parts = buildList {
        StatType.entries.forEach { t ->
            val v = item.bonus[t]
            if (v != 0) add("${if (v > 0) "+" else ""}$v ${stringResource(statLabel(t))}")
        }
    }
    return parts.joinToString("  ·  ")
}

@Composable
private fun StatsCard(state: GameState, combat: CombatStats, onSpend: (StatType) -> Unit) {
    val c = state.character
    val bonus = CombatStats.equipmentBonus(state.equipment.equipped.values)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(Icons.Outlined.BarChart, stringResource(R.string.character_stats_title))
            if (c.unspentPoints > 0) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) {
                    Text(
                        stringResource(R.string.character_unspent_points, c.unspentPoints),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            } else {
                Text(
                    stringResource(R.string.character_no_points, Progression.STAT_POINTS_PER_LEVEL),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatType.entries.forEachIndexed { i, type ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                StatRow(
                    type = type,
                    base = c.stats[type],
                    bonus = bonus[type],
                    total = combat.totalStats[type],
                    canSpend = c.unspentPoints > 0,
                    onSpend = { onSpend(type) },
                )
            }
        }
    }
}

@Composable
private fun StatRow(type: StatType, base: Int, bonus: Int, total: Int, canSpend: Boolean, onSpend: () -> Unit) {
    val label = stringResource(statLabel(type))
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(statColor(type).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(statIcon(type), contentDescription = null, tint = statColor(type), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(statHint(type)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(total.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row {
                Text(base.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (bonus != 0) {
                    Text(
                        " " + stringResource(R.string.character_stat_bonus, bonus),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF81C784),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        FilledIconButton(
            onClick = onSpend,
            enabled = canSpend,
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.character_stat_spend, label))
        }
    }
}

@Composable
private fun DerivedCard(combat: CombatStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(Icons.Outlined.Analytics, stringResource(R.string.character_derived_title))
            DerivedRow(
                stringResource(R.string.character_derived_damage),
                stringResource(R.string.character_derived_damage_value, combat.damageMin, combat.damageMax),
            )
            DerivedRow(stringResource(R.string.character_derived_aps), format("%.2f", combat.attacksPerSecond))
            DerivedRow(stringResource(R.string.character_derived_hp), numberFormat.format(combat.maxHp))
            DerivedRow(stringResource(R.string.character_derived_crit), format("%.1f %%", combat.critChance * 100))
            DerivedRow(stringResource(R.string.character_derived_crit_mult), format("×%.2f", combat.critMultiplier))
            DerivedRow(stringResource(R.string.character_derived_parry), format("%.0f %%", combat.parryChance * 100))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            DerivedRow(stringResource(R.string.character_derived_dps), format("%.1f", combat.dps), emphasize = true)
        }
    }
}

/** Rows "Schwingen / Stechen / Schlagen / Parieren" with a 5-step rating for the equipped weapon. */
@Composable
private fun ActionAffinities(combat: CombatStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.character_actions_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        WeaponAction.entries.forEach { action ->
            val (label, hint) = when (action) {
                WeaponAction.SWING -> R.string.character_action_swing to R.string.character_action_swing_hint
                WeaponAction.STAB -> R.string.character_action_stab to R.string.character_action_stab_hint
                WeaponAction.STRIKE -> R.string.character_action_strike to R.string.character_action_strike_hint
                WeaponAction.PARRY -> R.string.character_action_parry to R.string.character_action_parry_hint
            }
            val mod = combat.modifierFor(action)
            val rating: Int
            val value: String
            if (action == WeaponAction.PARRY) {
                rating = actionRating(combat.parryChance, 0.15, 0.80)
                value = stringResource(R.string.character_action_parry_value, format("%.0f", combat.parryChance * 100))
            } else {
                rating = actionRating(mod.expectedFactor(combat.critChance, combat.critMultiplier), 0.62, 1.42)
                val dmg = stringResource(R.string.character_action_damage, format("%.2f", mod.damageMultiplier))
                value = if (mod.critChanceBonus > 0.0) {
                    dmg + " · " + stringResource(R.string.character_action_crit, (mod.critChanceBonus * 100).roundToInt())
                } else dmg
            }
            ActionRow(stringResource(label), stringResource(hint), rating, value)
        }
    }
}

/** Maps [v] linearly from [lo]..[hi] to a rating of 1..5. */
private fun actionRating(v: Double, lo: Double, hi: Double): Int =
    (1 + ((v - lo) / (hi - lo) * 4.0).roundToInt()).coerceIn(1, 5)

@Composable
private fun ActionRow(label: String, hint: String, rating: Int, value: String) {
    val ratingText = stringResource(R.string.character_action_rating, rating)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.semantics { contentDescription = ratingText },
            ) {
                val on = lerp(Color(0xFFB0874A), Color(0xFFFFC857), rating / 5f)
                repeat(5) { i ->
                    Box(
                        Modifier
                            .size(width = 16.dp, height = 7.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < rating) on else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun format(pattern: String, value: Double) = String.format(Locale.getDefault(), pattern, value)

@Composable
private fun DerivedRow(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

private fun statLabel(t: StatType) = when (t) {
    StatType.STRENGTH -> R.string.character_stat_strength
    StatType.DEXTERITY -> R.string.character_stat_dexterity
    StatType.VITALITY -> R.string.character_stat_vitality
    StatType.LUCK -> R.string.character_stat_luck
}

private fun statHint(t: StatType) = when (t) {
    StatType.STRENGTH -> R.string.character_stat_strength_hint
    StatType.DEXTERITY -> R.string.character_stat_dexterity_hint
    StatType.VITALITY -> R.string.character_stat_vitality_hint
    StatType.LUCK -> R.string.character_stat_luck_hint
}

private fun statIcon(t: StatType) = when (t) {
    StatType.STRENGTH -> Icons.Filled.FitnessCenter
    StatType.DEXTERITY -> Icons.Filled.Speed
    StatType.VITALITY -> Icons.Filled.Favorite
    StatType.LUCK -> Icons.Filled.AutoAwesome
}

private fun statColor(t: StatType) = when (t) {
    StatType.STRENGTH -> Color(0xFFE57373)
    StatType.DEXTERITY -> Color(0xFF81C784)
    StatType.VITALITY -> Color(0xFFF06292)
    StatType.LUCK -> Color(0xFFFFD54F)
}

private fun rarityColor(r: Rarity) = when (r) {
    Rarity.COMMON -> Color(0xFFD7D7D2)
    Rarity.UNCOMMON -> Color(0xFF66BB6A)
    Rarity.RARE -> Color(0xFF42A5F5)
    Rarity.EPIC -> Color(0xFFAB47BC)
    Rarity.LEGENDARY -> Color(0xFFFFA726)
}

@Composable
private fun rarityLabel(r: Rarity) = stringResource(
    when (r) {
        Rarity.COMMON -> R.string.character_rarity_common
        Rarity.UNCOMMON -> R.string.character_rarity_uncommon
        Rarity.RARE -> R.string.character_rarity_rare
        Rarity.EPIC -> R.string.character_rarity_epic
        Rarity.LEGENDARY -> R.string.character_rarity_legendary
    },
)
