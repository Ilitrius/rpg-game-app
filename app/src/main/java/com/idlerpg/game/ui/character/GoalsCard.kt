package com.idlerpg.game.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlerpg.game.IdleRpgApp
import com.idlerpg.game.R
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.GoalState
import com.idlerpg.game.domain.progress.DailyReward
import com.idlerpg.game.domain.progress.GoalTypes
import com.idlerpg.game.domain.progress.Goals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val gold = Color(0xFFE6BC58)
private val flame = Color(0xFFFF8F3A)

/** "Tagesbelohnung & Ziele" card of the Charakter tab. Every claim is a single repository update. */
@Composable
fun GoalsCard(state: GameState) {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as IdleRpgApp).repository }
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Clock ticks once per second while the card is visible; the high-water mark is saved sparsely.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            repository.update { Goals.ensureActive(DailyReward.observe(it, now)) }
            delay(1000)
        }
    }

    val daily = DailyReward.status(state, now)
    val numbers = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CardGiftcard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            // ---- Daily reward
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.goals_daily_title), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalFireDepartment, null, tint = flame, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(
                                R.string.goals_daily_streak,
                                pluralStringResource(R.plurals.goals_days, state.progress.dailyStreak, state.progress.dailyStreak),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StreakDots(filled = state.progress.dailyStreak.coerceAtMost(DailyReward.MAX_STREAK_BONUS_DAY))
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RewardChip(stringResource(R.string.goals_daily_reward, numbers.format(daily.reward)))
                    Button(
                        enabled = daily.canClaim,
                        onClick = { scope.launch { repository.update { DailyReward.claim(it, System.currentTimeMillis()) } } },
                    ) { Text(stringResource(R.string.goals_daily_claim)) }
                }
            }
            if (daily.clockRewound) {
                Text(
                    stringResource(R.string.goals_daily_clock_rewound),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!daily.canClaim) {
                Text(
                    stringResource(R.string.goals_daily_next_in, formatDuration(daily.millisUntil)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (daily.nextStreak >= DailyReward.MAX_STREAK_BONUS_DAY) {
                Text(
                    stringResource(R.string.goals_daily_week_bonus),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ---- Goals
            Text(stringResource(R.string.goals_goals_title), style = MaterialTheme.typography.titleSmall)
            val active = Goals.active(state)
            if (active.isEmpty()) {
                Text(
                    stringResource(R.string.goals_none_left),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            active.forEach { goal ->
                GoalRow(state, goal, numbers) { scope.launch { repository.update { Goals.claim(it, goal.id) } } }
            }
        }
    }
}

@Composable
private fun GoalRow(state: GameState, goal: GoalState, numbers: NumberFormat, onClaim: () -> Unit) {
    val def = Goals.defOf(goal) ?: return
    val progress = Goals.progress(state, goal) ?: return
    val done = Goals.isComplete(state, goal)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(goalTitle(def.type, def.target), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.goals_progress, numbers.format(progress), numbers.format(def.target)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RewardChip(stringResource(R.string.goals_reward, numbers.format(Goals.reward(state, goal))))
                Button(enabled = done, onClick = onClaim) { Text(stringResource(R.string.goals_claim)) }
            }
        }
        LinearProgressIndicator(
            progress = { if (def.target > 0) progress.toFloat() / def.target else 1f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (done) gold else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

@Composable
private fun goalTitle(type: String, target: Long): String {
    val res = when (type) {
        GoalTypes.KILLS -> R.plurals.goals_kills
        GoalTypes.BOSSES -> R.plurals.goals_bosses
        GoalTypes.LEVEL -> R.plurals.goals_level
        else -> R.plurals.goals_zones
    }
    val n = target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return pluralStringResource(res, n, n)
}

@Composable
private fun RewardChip(text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.MonetizationOn, null, tint = gold, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Seven dots, one per streak day up to the weekly bonus. */
@Composable
private fun StreakDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(DailyReward.MAX_STREAK_BONUS_DAY) { i ->
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (i < filled) flame else MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val total = (millis.coerceAtLeast(0) + 999) / 1000
    return String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
}
