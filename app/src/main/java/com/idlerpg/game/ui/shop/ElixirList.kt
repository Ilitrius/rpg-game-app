package com.idlerpg.game.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.idlerpg.game.R
import com.idlerpg.game.data.ActiveBuff
import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.buffs.Buffs
import com.idlerpg.game.domain.shop.Elixirs
import com.idlerpg.game.domain.shop.Elixirs.Elixir
import com.idlerpg.game.ui.adventure.BuffIcon
import java.text.NumberFormat
import java.util.Locale

private fun nameRes(e: Elixir) = when (e) {
    Elixir.XP -> R.string.shop_elixir_xp
    Elixir.GOLD -> R.string.shop_elixir_gold
    Elixir.DAMAGE -> R.string.shop_elixir_damage
    Elixir.DROP -> R.string.shop_elixir_drop
    Elixir.REGEN -> R.string.shop_elixir_regen
}

private fun effectRes(e: Elixir) = when (e) {
    Elixir.XP -> R.string.shop_elixir_xp_effect
    Elixir.GOLD -> R.string.shop_elixir_gold_effect
    Elixir.DAMAGE -> R.string.shop_elixir_damage_effect
    Elixir.DROP -> R.string.shop_elixir_drop_effect
    Elixir.REGEN -> R.string.shop_elixir_regen_effect
}

/** One elixir offer: vector icon (shared with the HUD buff bar), effect, remaining time, price and buy button. */
@Composable
internal fun ElixirRow(state: GameState, elixir: Elixir, onBuy: () -> Unit) {
    val price = Elixirs.price(state, elixir)
    val blocker = Elixirs.blocker(state, elixir)
    val running = state.buffs.active.firstOrNull { it.type == elixir.type && it.remainingMillis > 0 }
    val affordable = state.gold >= price
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BuffIcon(ActiveBuff(elixir.type, Elixirs.MAGNITUDE, Elixirs.DURATION_MILLIS), Modifier.size(52.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(nameRes(elixir)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(effectRes(elixir)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (running != null) {
                    Text(
                        stringResource(R.string.shop_elixir_running, Buffs.clock(running.remainingMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (blocker != null) {
                    Text(
                        stringResource(
                            when (blocker) {
                                Elixirs.Outcome.STRONGER_ACTIVE -> R.string.shop_elixir_blocked_stronger
                                Elixirs.Outcome.MAX_DURATION -> R.string.shop_elixir_blocked_max
                                else -> R.string.shop_elixir_blocked_full
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Button(onClick = onBuy, enabled = blocker == null && affordable) {
                Text(stringResource(R.string.shop_elixir_buy, NumberFormat.getIntegerInstance(Locale.getDefault()).format(price)), maxLines = 1)
            }
        }
    }
}
