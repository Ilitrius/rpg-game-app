package com.idlerpg.game.ui.options

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.idlerpg.game.IdleRpgApp
import com.idlerpg.game.R

/** Pure backup-reminder rules; time is always a parameter. Nothing leaves the device. */
object BackupReminder {
    const val DAY_MILLIS = 24L * 60 * 60 * 1000
    const val STALE_DAYS = 14
    const val PROMPT_INTERVAL_DAYS = 7
    const val MIN_LEVEL = 10

    /** Hero is far enough and the last export is missing or older than [STALE_DAYS]. A last export in the future (other clock) counts as fresh. */
    fun needsBackup(level: Int, lastExport: Long, now: Long): Boolean {
        if (level < MIN_LEVEL) return false
        if (lastExport <= 0L) return true
        return now - lastExport > STALE_DAYS * DAY_MILLIS
    }

    /** Start-up dialog: enabled, backup needed, and not shown within the last [PROMPT_INTERVAL_DAYS] days. */
    fun shouldPrompt(enabled: Boolean, level: Int, lastExport: Long, lastPrompt: Long, now: Long): Boolean {
        if (!enabled || !needsBackup(level, lastExport, now)) return false
        if (lastPrompt <= 0L || lastPrompt > now) return true
        return now - lastPrompt >= PROMPT_INTERVAL_DAYS * DAY_MILLIS
    }
}

/** Subtle once-per-start dialog; records the prompt time as soon as it is shown. */
@Composable
fun BackupReminderDialog() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val settings = GameSettings.get(context)
        val level = runCatching { (context.applicationContext as IdleRpgApp).repository.current().character.level }.getOrNull() ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (BackupReminder.shouldPrompt(settings.remindersEnabled, level, settings.lastExportEpochMillis, settings.lastReminderPromptEpochMillis, now)) {
            settings.lastReminderPromptEpochMillis = now
            show = true
        }
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(stringResource(R.string.options_backup_dialog_title)) },
            text = { Text(stringResource(R.string.options_backup_dialog_text)) },
            confirmButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.options_backup_dialog_ok)) } },
        )
    }
}
