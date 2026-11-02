package com.idlerpg.game.ui.character.prestige

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import android.widget.Toast
import com.idlerpg.game.IdleRpgApp
import com.idlerpg.game.R
import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.prestige.Prestige
import com.idlerpg.game.domain.prestige.PrestigeConfig
import com.idlerpg.game.ui.options.GameSettings
import com.idlerpg.game.ui.options.SaveTransfer
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** "Wiedergeburt" card of the Charakter tab (visible from level 30, locked until the gate is reached). */
@Composable
fun PrestigeCard(state: GameState) {
    if (!Prestige.isVisible(state)) return
    val nf = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    var showDialog by remember { mutableStateOf(false) }
    var showTree by remember { mutableStateOf(false) }
    val gate = Prestige.gate(state)
    val open = gate.open

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoulIcon(size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.prestige_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.prestige_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.prestige_souls_have, nf.format(state.prestige.souls)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.prestige_rebirths, nf.format(state.prestige.rebirths)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                Text(
                    stringResource(R.string.prestige_preview, nf.format(Prestige.soulsPreview(state))),
                    style = MaterialTheme.typography.titleSmall,
                    color = SoulBlue,
                )
            } else {
                Text(
                    stringResource(R.string.prestige_locked, PrestigeConfig.GATE_LEVEL, PrestigeConfig.GATE_ZONE_ORDER),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GateBar(stringResource(R.string.prestige_gate_level, gate.level.coerceAtMost(PrestigeConfig.GATE_LEVEL), PrestigeConfig.GATE_LEVEL), gate.level, PrestigeConfig.GATE_LEVEL)
                GateBar(stringResource(R.string.prestige_gate_zone, gate.zoneOrder.coerceAtMost(PrestigeConfig.GATE_ZONE_ORDER), PrestigeConfig.GATE_ZONE_ORDER), gate.zoneOrder, PrestigeConfig.GATE_ZONE_ORDER)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showTree = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.prestige_open_talents)) }
                Button(enabled = open, onClick = { showDialog = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.prestige_open_rebirth)) }
            }
        }
    }
    if (showDialog) RebirthDialog(state, onDismiss = { showDialog = false })
    if (showTree) TalentTreeSheet(state, onDismiss = { showTree = false })
}

@Composable
private fun GateBar(label: String, value: Int, max: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { (value.toFloat() / max).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = SoulBlue)
    }
}

/** Two-step rebirth dialog: info + backup, then a final confirmation. The reset is ONE repository update. */
@Composable
private fun RebirthDialog(state: GameState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as IdleRpgApp).repository }
    val scope = rememberCoroutineScope()
    val nf = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    var step by remember { mutableIntStateOf(1) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = SaveTransfer.encode(repository.current())
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            if (ok) GameSettings.get(context).lastExportEpochMillis = System.currentTimeMillis()
            Toast.makeText(context, if (ok) R.string.prestige_dialog_backup_done else R.string.prestige_dialog_backup_failed, Toast.LENGTH_SHORT).show()
        }
    }

    val souls = Prestige.soulsPreview(state)
    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.prestige_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.prestige_dialog_souls, nf.format(souls)), style = MaterialTheme.typography.titleSmall, color = SoulBlue)
                    Text(stringResource(R.string.prestige_dialog_lose_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.prestige_dialog_lose), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.prestige_dialog_keep_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.prestige_dialog_keep), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.prestige_dialog_start, Prestige.startLevel(state.prestige)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { backup.launch(SaveTransfer.exportFileName()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.prestige_dialog_backup))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { step = 2 }) { Text(stringResource(R.string.prestige_dialog_continue)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.prestige_dialog_cancel)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.prestige_confirm_title)) },
            text = { Text(stringResource(R.string.prestige_confirm_text)) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        // Single atomic update; locked states stay unchanged (double tap cannot rebirth twice).
                        repository.update { Prestige.rebirth(it, System.currentTimeMillis()) }
                        Toast.makeText(context, context.getString(R.string.prestige_done, nf.format(souls)), Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                }) { Text(stringResource(R.string.prestige_confirm_go)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.prestige_dialog_cancel)) } },
        )
    }
}
