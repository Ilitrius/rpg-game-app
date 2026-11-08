package com.idlerpg.game.ui.character.rank

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlerpg.game.IdleRpgApp
import com.idlerpg.game.R
import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.rank.RankBonuses
import com.idlerpg.game.domain.rank.RankBuyResult
import com.idlerpg.game.domain.rank.RankConfig
import com.idlerpg.game.domain.rank.Ranks
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

internal fun rankNameRes(rank: Int): Int = when (rank.coerceIn(0, RankConfig.MAX_RANK)) {
    1 -> R.string.rank_name_1
    2 -> R.string.rank_name_2
    3 -> R.string.rank_name_3
    4 -> R.string.rank_name_4
    5 -> R.string.rank_name_5
    6 -> R.string.rank_name_6
    7 -> R.string.rank_name_7
    8 -> R.string.rank_name_8
    9 -> R.string.rank_name_9
    10 -> R.string.rank_name_10
    11 -> R.string.rank_name_11
    12 -> R.string.rank_name_12
    13 -> R.string.rank_name_13
    14 -> R.string.rank_name_14
    15 -> R.string.rank_name_15
    else -> R.string.rank_name_0
}

private const val VISIBLE_LEVEL = 30

/** "Rang" card of the Charakter tab: shown from hero level 30 (or once a rank was bought). */
@Composable
fun RankCard(state: GameState) {
    val rank = Ranks.rank(state)
    if (rank == 0 && Ranks.heroLevel(state) < VISIBLE_LEVEL) return
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as IdleRpgApp).repository }
    val scope = rememberCoroutineScope()
    val nf = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    var confirm by remember { mutableStateOf(false) }
    val next = rank + 1
    val result = Ranks.check(state)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankBadge(rank)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.rank_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (rank == 0) stringResource(R.string.rank_none) else stringResource(R.string.rank_current, stringResource(rankNameRes(rank))),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(stringResource(R.string.rank_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!Ranks.isActive(state)) {
                Text(
                    stringResource(R.string.rank_locked_endlevel, RankConfig.UNLOCK_LEVEL, Ranks.heroLevel(state)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
            }
            Text(stringResource(R.string.rank_bonus_now, bonusText(RankBonuses.of(rank))), style = MaterialTheme.typography.bodyMedium)
            if (result == RankBuyResult.MAX_RANK) {
                Text(stringResource(R.string.rank_max), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    stringResource(R.string.rank_next, stringResource(rankNameRes(next))) + " · " +
                        if (Ranks.requiredRebirths(next) > 0) {
                            stringResource(R.string.rank_requires_level_rebirths, Ranks.requiredLevel(next), Ranks.requiredRebirths(next))
                        } else {
                            stringResource(R.string.rank_requires_level, Ranks.requiredLevel(next))
                        },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.rank_bonus_next, bonusText(RankBonuses.of(next))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (result) {
                    RankBuyResult.LEVEL_TOO_LOW -> Text(
                        stringResource(R.string.rank_level_missing, Ranks.requiredLevel(next), Ranks.heroLevel(state)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                    RankBuyResult.REBIRTHS_TOO_LOW -> Text(
                        stringResource(R.string.rank_rebirths_missing, Ranks.requiredRebirths(next), state.prestige.rebirths),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                    RankBuyResult.NOT_ENOUGH_GOLD -> Text(
                        stringResource(R.string.rank_gold_missing, nf.format(Ranks.price(next) - state.gold)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
                Button(enabled = result == RankBuyResult.OK, onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rank_buy_price, nf.format(Ranks.price(next))))
                }
            }
        }
    }
    if (confirm && result != RankBuyResult.MAX_RANK) {
        val name = stringResource(rankNameRes(next))
        val bought = stringResource(R.string.rank_bought, name)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.rank_dialog_title, name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.rank_dialog_text, nf.format(Ranks.price(next))))
                    Text(bonusText(RankBonuses.of(next)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        var ok = false
                        repository.update { s -> Ranks.buy(s).also { ok = it !== s } }
                        if (ok) Toast.makeText(context, bought, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.rank_dialog_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.rank_dialog_cancel)) } },
        )
    }
}

@Composable
private fun bonusText(b: RankBonuses): String {
    fun pct(m: Double) = ((m - 1.0) * 100.0).roundToInt()
    val parts = buildList {
        if (b.damage > 1.0) add(stringResource(R.string.rank_bonus_damage, pct(b.damage)))
        if (b.hp > 1.0) add(stringResource(R.string.rank_bonus_hp, pct(b.hp)))
        if (b.speed > 1.0) add(stringResource(R.string.rank_bonus_speed, pct(b.speed)))
        if (b.gold > 1.0) add(stringResource(R.string.rank_bonus_gold, pct(b.gold)))
        if (b.extraSlots > 0) add(stringResource(R.string.rank_bonus_slots, b.extraSlots))
    }
    return if (parts.isEmpty()) stringResource(R.string.rank_bonus_none) else parts.joinToString(" · ")
}
