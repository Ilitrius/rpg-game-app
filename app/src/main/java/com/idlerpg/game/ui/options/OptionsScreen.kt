package com.idlerpg.game.ui.options

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.idlerpg.game.IdleRpgApp
import com.idlerpg.game.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** "Optionen" tab: save-game export/import, room for future settings. */
@Composable
fun OptionsScreen() {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as IdleRpgApp).repository }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Validated JSON waiting for user confirmation (saveable across rotation).
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }

    fun notify(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun handleImportText(text: String?) {
        when (val result = SaveTransfer.parse(text)) {
            is ImportResult.Valid -> pendingImport = text
            is ImportResult.Invalid -> notify(errorMessage(context, result.error))
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = SaveTransfer.encode(repository.current())
                writeText(context, uri, json)
            }.isSuccess
            notify(context.getString(if (ok) R.string.options_export_file_success else R.string.options_export_file_error))
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { readText(context, uri) }
            text.fold(
                onSuccess = { handleImportText(it) },
                onFailure = { notify(context.getString(R.string.options_import_read_error)) },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.options_title), style = MaterialTheme.typography.headlineMedium)

            SaveGameCard(
                onExportFile = {
                    try {
                        createDocument.launch(SaveTransfer.exportFileName())
                    } catch (e: ActivityNotFoundException) {
                        notify(context.getString(R.string.options_no_file_app))
                    }
                },
                onExportClipboard = {
                    scope.launch {
                        clipboard.setText(AnnotatedString(SaveTransfer.encode(repository.current())))
                        notify(context.getString(R.string.options_export_clipboard_success))
                    }
                },
                onImportFile = {
                    try {
                        openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    } catch (e: ActivityNotFoundException) {
                        notify(context.getString(R.string.options_no_file_app))
                    }
                },
                onImportClipboard = {
                    val text = clipboard.getText()?.text
                    if (text.isNullOrBlank()) {
                        notify(context.getString(R.string.options_import_clipboard_empty))
                    } else {
                        handleImportText(text)
                    }
                },
            )

            FutureSettingsCard()
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }

    pendingImport?.let { json ->
        val valid = SaveTransfer.parse(json) as? ImportResult.Valid
        if (valid == null) {
            pendingImport = null
        } else {
            ConfirmImportDialog(
                summary = valid.summary,
                onConfirm = {
                    pendingImport = null
                    scope.launch {
                        val ok = runCatching { repository.replace(valid.state) }.isSuccess
                        notify(context.getString(if (ok) R.string.options_import_success else R.string.options_import_write_error))
                    }
                },
                onDismiss = { pendingImport = null },
            )
        }
    }
}

@Composable
private fun SaveGameCard(
    onExportFile: () -> Unit,
    onExportClipboard: () -> Unit,
    onImportFile: () -> Unit,
    onImportClipboard: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Save, stringResource(R.string.options_save_title))
            Text(
                stringResource(R.string.options_save_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SubHeader(stringResource(R.string.options_export_header))
            ActionButton(Icons.Outlined.SaveAlt, stringResource(R.string.options_export_file), primary = true, onClick = onExportFile)
            ActionButton(Icons.Outlined.ContentCopy, stringResource(R.string.options_export_clipboard), primary = false, onClick = onExportClipboard)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SubHeader(stringResource(R.string.options_import_header))
            ActionButton(Icons.Outlined.FileOpen, stringResource(R.string.options_import_file), primary = true, onClick = onImportFile)
            ActionButton(Icons.Outlined.ContentPaste, stringResource(R.string.options_import_clipboard), primary = false, onClick = onImportClipboard)
        }
    }
}

@Composable
private fun FutureSettingsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(Icons.Outlined.Tune, stringResource(R.string.options_more_title))
            Text(
                stringResource(R.string.options_more_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, primary: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
    val modifier = Modifier.fillMaxWidth()
    if (primary) {
        Button(onClick = onClick, modifier = modifier, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) { content() }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) { content() }
    }
}

@Composable
private fun ConfirmImportDialog(summary: SaveSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val gold = NumberFormat.getIntegerInstance(Locale.GERMANY).format(summary.gold)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.options_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.options_confirm_summary, summary.name, summary.level, gold, summary.itemCount),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.options_confirm_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.options_confirm_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.options_confirm_cancel)) }
        },
    )
}

private fun errorMessage(context: Context, error: SaveError): String = when (error) {
    SaveError.Empty -> context.getString(R.string.options_error_empty)
    SaveError.TooLarge -> context.getString(R.string.options_error_too_large)
    SaveError.Malformed -> context.getString(R.string.options_error_malformed)
    is SaveError.NewerVersion -> context.getString(R.string.options_error_newer_version, error.found, error.supported)
    SaveError.InvalidVersion -> context.getString(R.string.options_error_invalid_version)
    SaveError.LevelTooLow -> context.getString(R.string.options_error_level)
    is SaveError.NegativeValue -> context.getString(R.string.options_error_negative, error.field)
    is SaveError.SlotMismatch -> context.getString(R.string.options_error_slot, error.slotKey, error.itemSlot)
}

private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("no output stream")
    stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
}

/** Reads at most [SaveTransfer.MAX_IMPORT_CHARS] + 1 chars so oversized files are rejected cheaply. */
private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openInputStream(uri) ?: error("no input stream")
    stream.bufferedReader(Charsets.UTF_8).use { reader ->
        val limit = SaveTransfer.MAX_IMPORT_CHARS + 1
        val buffer = CharArray(8192)
        val sb = StringBuilder()
        while (sb.length < limit) {
            val n = reader.read(buffer, 0, minOf(buffer.size, limit - sb.length))
            if (n < 0) break
            sb.appendRange(buffer, 0, n)
        }
        sb.toString()
    }
}
