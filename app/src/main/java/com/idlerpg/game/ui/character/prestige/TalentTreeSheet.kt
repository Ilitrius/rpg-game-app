package com.idlerpg.game.ui.character.prestige

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.idlerpg.game.IdleRpgApp
import com.idlerpg.game.R
import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.prestige.BuyResult
import com.idlerpg.game.domain.prestige.PayWith
import com.idlerpg.game.domain.prestige.Prestige
import com.idlerpg.game.domain.prestige.Talent
import com.idlerpg.game.domain.prestige.TalentBranch
import com.idlerpg.game.domain.prestige.Talents
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Talent tree: three branches; each talent buyable with souls or (up to half its levels) gold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalentTreeSheet(state: GameState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as IdleRpgApp).repository }
    val scope = rememberCoroutineScope()
    val nf = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoulIcon(size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.prestige_tree_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.prestige_tree_souls, nf.format(state.prestige.souls)), color = SoulBlue, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.prestige_tree_gold, nf.format(state.gold)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            for (branch in TalentBranch.entries) {
                Text(
                    stringResource(branchTitle(branch)),
                    style = MaterialTheme.typography.titleSmall,
                    color = branchColor(branch),
                    modifier = Modifier.padding(top = 6.dp),
                )
                for (t in Talents.catalog.filter { it.branch == branch }) {
                    TalentRow(state, t, nf) { with -> scope.launch { repository.update { Prestige.buyTalent(it, t.id, with) } } }
                }
            }
            OutlinedButton(
                onClick = { scope.launch { repository.update { Prestige.respec(it) } } },
                enabled = state.prestige.talents.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.prestige_tree_respec)) }
            Text(stringResource(R.string.prestige_tree_respec_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun TalentRow(state: GameState, t: Talent, nf: NumberFormat, onBuy: (PayWith) -> Unit) {
    val lvl = Prestige.level(state.prestige, t.id)
    val maxed = lvl >= t.maxLevel
    val soulCheck = Prestige.check(state, t.id, PayWith.SOULS)
    val goldCheck = Prestige.check(state, t.id, PayWith.GOLD)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TalentIcon(t.id, t.branch, dim = lvl == 0)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(talentName(t.id)), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(talentDesc(t.id), effectText(t)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.prestige_tree_level, lvl, t.maxLevel), style = MaterialTheme.typography.labelMedium, color = branchColor(t.branch))
            }
            LinearProgressIndicator(progress = { lvl.toFloat() / t.maxLevel }, modifier = Modifier.fillMaxWidth(), color = branchColor(t.branch))
            if (maxed) {
                Text(stringResource(R.string.prestige_buy_max), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = soulCheck == BuyResult.OK, onClick = { onBuy(PayWith.SOULS) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.prestige_buy_souls, nf.format(Prestige.soulCost(t, lvl))))
                    }
                    OutlinedButton(enabled = goldCheck == BuyResult.OK, onClick = { onBuy(PayWith.GOLD) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.prestige_buy_gold, nf.format(Prestige.goldCost(t, lvl, state.prestige.rebirths))))
                    }
                }
                if (goldCheck == BuyResult.GOLD_SHARE_REACHED) {
                    Text(stringResource(R.string.prestige_buy_gold_limit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Effect per level as display text ("5 %", "2", "0,05"). */
private fun effectText(t: Talent): String = when (t.id) {
    Talents.START_LEVEL, Talents.SLOTS -> String.format(Locale.getDefault(), "%d", t.perLevel.toInt())
    Talents.CRIT -> String.format(Locale.getDefault(), "%.2f", t.perLevel)
    else -> String.format(Locale.getDefault(), "%d %%", Math.round(t.perLevel * 100).toInt())
}

private fun branchTitle(b: TalentBranch) = when (b) {
    TalentBranch.PROGRESS -> R.string.prestige_branch_progress
    TalentBranch.COMBAT -> R.string.prestige_branch_combat
    TalentBranch.LOOT -> R.string.prestige_branch_loot
}

private fun talentName(id: String) = when (id) {
    Talents.XP -> R.string.talent_xp
    Talents.GOLD -> R.string.talent_gold
    Talents.START_LEVEL -> R.string.talent_start_level
    Talents.REST -> R.string.talent_rest
    Talents.DAMAGE -> R.string.talent_damage
    Talents.SPEED -> R.string.talent_speed
    Talents.HP -> R.string.talent_hp
    Talents.CRIT -> R.string.talent_crit
    Talents.DROP -> R.string.talent_drop
    Talents.SLOTS -> R.string.talent_slots
    Talents.CLICK -> R.string.talent_click
    else -> R.string.talent_combo
}

private fun talentDesc(id: String) = when (id) {
    Talents.XP -> R.string.talent_xp_desc
    Talents.GOLD -> R.string.talent_gold_desc
    Talents.START_LEVEL -> R.string.talent_start_level_desc
    Talents.REST -> R.string.talent_rest_desc
    Talents.DAMAGE -> R.string.talent_damage_desc
    Talents.SPEED -> R.string.talent_speed_desc
    Talents.HP -> R.string.talent_hp_desc
    Talents.CRIT -> R.string.talent_crit_desc
    Talents.DROP -> R.string.talent_drop_desc
    Talents.SLOTS -> R.string.talent_slots_desc
    Talents.CLICK -> R.string.talent_click_desc
    else -> R.string.talent_combo_desc
}
