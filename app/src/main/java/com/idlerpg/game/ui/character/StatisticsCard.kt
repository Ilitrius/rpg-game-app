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
import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.world.Zones
import java.text.NumberFormat

/** "Statistiken" card of the Charakter tab: lifetime counters. */
@Composable
fun StatisticsCard(state: GameState, modifier: Modifier = Modifier) {
    val nf = NumberFormat.getIntegerInstance()
    val s = state.stats
    val minutes = s.playTimeMillis / 60_000
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.statistik_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            StatRow(stringResource(R.string.statistik_kills), nf.format(state.world.totalKills))
            StatRow(
                stringResource(R.string.statistik_play_time),
                stringResource(R.string.statistik_play_time_value, minutes / 60, minutes % 60),
            )
            StatRow(stringResource(R.string.statistik_highest_hit), nf.format(s.highestHit))
            StatRow(stringResource(R.string.statistik_gold_earned), nf.format(s.goldEarned))
            StatRow(stringResource(R.string.statistik_bosses), nf.format(state.world.defeatedBossIds.size))
            StatRow(
                stringResource(R.string.statistik_zones),
                stringResource(
                    R.string.statistik_zones_value,
                    state.world.unlockedZoneIds.count { id -> Zones.all.any { it.id == id } },
                    Zones.COUNT,
                ),
            )
            StatRow(stringResource(R.string.statistik_taps), nf.format(s.tapCount))
            StatRow(stringResource(R.string.statistik_goals), nf.format(state.progress.goalsCompleted))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
