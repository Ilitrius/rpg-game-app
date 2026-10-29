package com.idlerpg.game.ui.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.idlerpg.game.R
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.TapStats
import java.util.Locale

/** Tap damage overview: damage range, click multiplier, max combo. */
@Composable
fun TapCard(combat: CombatStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.character_tap_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TapRow(
                stringResource(R.string.character_tap_damage),
                stringResource(
                    R.string.character_tap_damage_value,
                    TapStats.minTapDamage(combat),
                    TapStats.maxTapDamage(combat),
                ),
            )
            TapRow(
                stringResource(R.string.character_tap_multiplier),
                String.format(Locale.getDefault(), "×%.2f", TapStats.clickMultiplier(combat.totalStats)),
            )
            TapRow(
                stringResource(R.string.character_tap_combo),
                String.format(
                    Locale.getDefault(), "×%.2f (%d)",
                    TapStats.MAX_COMBO_MULTIPLIER, TapStats.comboCap,
                ),
            )
        }
    }
}

@Composable
private fun TapRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
