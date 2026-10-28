package com.idlerpg.game.ui.options

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PrivacyTip
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
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.outlined.Language
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.domain.world.Zones
import com.idlerpg.game.ui.adventure.nameRes
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
    // Why the last import was rejected; shown in a dialog (the messages are too long for a snackbar).
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    var showPrivacySummary by rememberSaveable { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun handleImportText(text: String?) {
        when (val result = SaveTransfer.parse(text)) {
            is ImportResult.Valid -> pendingImport = text
            is ImportResult.Invalid -> importError = errorMessage(context, result.error)
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

            val settings = remember { GameSettings.get(context) }
            val soundOn by settings.soundEnabledFlow.collectAsState()
            SoundCard(soundOn = soundOn, onChange = { settings.soundEnabled = it })

            val autoBoss by settings.autoBossFlow.collectAsState()
            AutoBossCard(autoBoss = autoBoss, onChange = { settings.autoBoss = it })

            val language by settings.languageFlow.collectAsState()
            LanguageCard(selected = language, onSelect = {
                if (it != settings.language) {
                    settings.language = it
                    (context as? android.app.Activity)?.recreate()
                }
            })
            DonateCard(
                onDonate = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(DonateLink.URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (e: ActivityNotFoundException) {
                        notify(context.getString(R.string.options_donate_no_browser))
                    } catch (e: SecurityException) {
                        notify(context.getString(R.string.options_donate_no_browser))
                    }
                },
            )
            PrivacyCard(
                onShowSummary = { showPrivacySummary = true },
                onOpenPolicy = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.options_privacy_url))),
                        )
                    } catch (e: ActivityNotFoundException) {
                        notify(context.getString(R.string.options_privacy_no_browser))
                    }
                },
            )
            FutureSettingsCard()
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }

    pendingImport?.let { json ->
        val valid = remember(json) { SaveTransfer.parse(json) as? ImportResult.Valid }
        if (valid == null) {
            pendingImport = null
        } else {
            ConfirmImportDialog(
                summary = valid.summary,
                notes = valid.notes.map { noteMessage(context, it) },
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

    importError?.let { message ->
        ImportErrorDialog(message = message, onDismiss = { importError = null })
    }

    if (showPrivacySummary) {
        AlertDialog(
            onDismissRequest = { showPrivacySummary = false },
            icon = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
            title = { Text(stringResource(R.string.options_privacy_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.options_privacy_summary), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacySummary = false }) { Text(stringResource(R.string.options_error_ok)) }
            },
        )
    }
}

@Composable
private fun DonateCard(onDonate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Favorite, stringResource(R.string.options_donate_title))
            Text(
                stringResource(R.string.options_donate_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionButton(Icons.Outlined.Favorite, stringResource(R.string.options_donate_button), primary = true, onClick = onDonate)
        }
    }
}

@Composable
private fun PrivacyCard(onShowSummary: () -> Unit, onOpenPolicy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(Icons.Outlined.PrivacyTip, stringResource(R.string.options_privacy_title))
            Text(
                stringResource(R.string.options_privacy_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionButton(Icons.Outlined.Info, stringResource(R.string.options_privacy_summary_button), primary = false, onClick = onShowSummary)
            ActionButton(Icons.Outlined.OpenInBrowser, stringResource(R.string.options_privacy_open), primary = false, onClick = onOpenPolicy)
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
private fun SoundCard(soundOn: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                if (soundOn) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                stringResource(R.string.options_sound_title),
            )
            Text(
                stringResource(R.string.options_sound_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.options_sound_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = soundOn, onCheckedChange = onChange)
            }
        }
    }
}

@Composable
private fun AutoBossCard(autoBoss: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(Icons.Outlined.Tune, stringResource(R.string.options_autoboss_title))
            Text(
                stringResource(R.string.options_autoboss_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.options_autoboss_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoBoss, onCheckedChange = onChange)
            }
        }
    }
}

@Composable
private fun LanguageCard(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(Icons.Outlined.Language, stringResource(R.string.options_language_title))
            Text(
                stringResource(R.string.options_language_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { lang ->
                    val label = when (lang) {
                        AppLanguage.SYSTEM -> stringResource(R.string.options_language_system)
                        AppLanguage.GERMAN -> stringResource(R.string.options_language_german)
                        AppLanguage.ENGLISH -> stringResource(R.string.options_language_english)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = lang == selected, role = Role.RadioButton, onClick = { onSelect(lang) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = lang == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
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
private fun ConfirmImportDialog(summary: SaveSummary, notes: List<String>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
                notes.forEach { note ->
                    Row(verticalAlignment = Alignment.Top) {
                        // Neutral accent (gold), not tertiary: in the dark scheme tertiary is the same
                        // rose tone as the error warning below, which made harmless notes look like errors.
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp).size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
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

@Composable
private fun ImportErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.options_error_title)) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.options_error_ok)) }
        },
    )
}

/** German, concrete message for a rejected save: where the problem sits and what is wrong. */
private fun errorMessage(context: Context, error: SaveError): String = when (error) {
    SaveError.Empty -> context.getString(R.string.options_error_empty)
    SaveError.TooLarge -> context.getString(R.string.options_error_too_large)
    SaveError.Malformed -> context.getString(R.string.options_error_malformed)
    is SaveError.NewerVersion -> context.getString(R.string.options_error_newer_version, error.found, error.supported)
    SaveError.InvalidVersion -> context.getString(R.string.options_error_invalid_version)
    SaveError.LevelTooLow -> context.getString(R.string.options_error_level)
    is SaveError.SlotMismatch -> context.getString(
        R.string.options_error_slot,
        slotName(context, error.slotKey),
        slotName(context, error.itemSlot),
    )
    is SaveError.NegativeValue -> invalidAt(
        context,
        error.location,
        context.getString(R.string.options_error_negative, error.field.key, fieldLabel(context, error.field)),
    )
    is SaveError.BelowMinimum -> invalidAt(
        context,
        error.location,
        context.getString(R.string.options_error_below_min, error.field.key, fieldLabel(context, error.field), error.min, error.found),
    )
    is SaveError.OutOfRange -> invalidAt(
        context,
        error.location,
        context.getString(
            R.string.options_error_out_of_range,
            error.field.key, fieldLabel(context, error.field), error.min, error.max, error.found,
        ),
    )
    is SaveError.DamageRangeReversed -> invalidAt(
        context,
        error.location,
        context.getString(R.string.options_error_damage_range, error.damageMin, error.damageMax),
    )
}

private fun invalidAt(context: Context, location: SaveLocation, problem: String): String {
    val where = locationText(context, location)
        ?: return context.getString(R.string.options_error_invalid, problem)
    return context.getString(R.string.options_error_invalid_at, where, problem)
}

/** Location of a value in German, or null for top-level values (e.g. gold). */
private fun locationText(context: Context, location: SaveLocation): String? = when (location) {
    SaveLocation.Root -> null
    SaveLocation.Character -> context.getString(R.string.options_location_character)
    is SaveLocation.EquippedItem -> context.getString(
        R.string.options_location_equipped,
        context.getString(slotLabel(location.slot)),
        itemNameText(context, location.itemName),
    )
    is SaveLocation.InventoryItem ->
        context.getString(R.string.options_location_inventory, location.position, itemNameText(context, location.itemName))
    is SaveLocation.ShopOffer ->
        context.getString(R.string.options_location_shop_offer, location.position, itemNameText(context, location.itemName))
    SaveLocation.World -> context.getString(R.string.options_location_world)
    is SaveLocation.WorldZone -> context.getString(R.string.options_location_world_zone, location.zoneId)
    SaveLocation.Shop -> context.getString(R.string.options_location_shop)
    SaveLocation.Progress -> context.getString(R.string.options_location_progress)
    is SaveLocation.ProgressGoal ->
        context.getString(R.string.options_location_progress_goal, location.position, location.goalId)
}

private fun itemNameText(context: Context, name: String): String =
    if (name.isBlank()) context.getString(R.string.options_unnamed) else context.getString(R.string.options_quoted, name)

private fun fieldLabel(context: Context, field: SaveField): String = context.getString(
    when (field) {
        SaveField.XP -> R.string.options_field_xp
        SaveField.UNSPENT_POINTS -> R.string.options_field_unspent_points
        SaveField.STRENGTH -> R.string.options_field_strength
        SaveField.DEXTERITY -> R.string.options_field_dexterity
        SaveField.VITALITY -> R.string.options_field_vitality
        SaveField.LUCK -> R.string.options_field_luck
        SaveField.GOLD -> R.string.options_field_gold
        SaveField.PRICE -> R.string.options_field_price
        SaveField.DAMAGE_MIN -> R.string.options_field_damage_min
        SaveField.DAMAGE_MAX -> R.string.options_field_damage_max
        SaveField.ITEM_LEVEL -> R.string.options_field_item_level
        SaveField.HERO_HP -> R.string.options_field_hero_hp
        SaveField.TOTAL_KILLS -> R.string.options_field_total_kills
        SaveField.ZONE_KILLS -> R.string.options_field_zone_kills
        SaveField.LAST_ACTIVE -> R.string.options_field_last_active
        SaveField.BOSS_DEFEATED_AT_KILLS -> R.string.options_field_boss_defeated_at_kills
        SaveField.RESTOCKED_AT -> R.string.options_field_restocked_at
        SaveField.STOCK_LEVEL -> R.string.options_field_stock_level
        SaveField.PAID_RESTOCKS -> R.string.options_field_paid_restocks
        SaveField.DAILY_LAST_CLAIM -> R.string.options_field_daily_last_claim
        SaveField.DAILY_STREAK -> R.string.options_field_daily_streak
        SaveField.CLOCK_HIGH_WATER -> R.string.options_field_clock_high_water
        SaveField.GOALS_COMPLETED -> R.string.options_field_goals_completed
        SaveField.GOAL_TARGET -> R.string.options_field_goal_target
        SaveField.GOAL_BASELINE -> R.string.options_field_goal_baseline
    },
)

/** German slot name for an [EquipmentSlot] enum name (falls back to the raw name). */
private fun slotName(context: Context, slotKey: String): String =
    EquipmentSlot.entries.firstOrNull { it.name == slotKey }?.let { context.getString(slotLabel(it)) } ?: slotKey

/** Slot names belong to the Ausrüstung tab (`strings_ausruestung.xml`); used read-only. */
private fun slotLabel(slot: EquipmentSlot): Int = when (slot) {
    EquipmentSlot.WEAPON -> R.string.equipment_slot_weapon
    EquipmentSlot.HELMET -> R.string.equipment_slot_helmet
    EquipmentSlot.ARMOR -> R.string.equipment_slot_armor
    EquipmentSlot.GLOVES -> R.string.equipment_slot_gloves
    EquipmentSlot.BOOTS -> R.string.equipment_slot_boots
    EquipmentSlot.RING -> R.string.equipment_slot_ring
    EquipmentSlot.AMULET -> R.string.equipment_slot_amulet
}

/** Unknown zone ids listed in a note before "und N weitere". */
private const val MAX_LISTED_ZONES = 5

/** German text of an import hint; zone names belong to the Spielwelt tab (`Zone.nameRes`). */
private fun noteMessage(context: Context, note: ImportNote): String = when (note) {
    is ImportNote.UnknownCurrentZone -> context.getString(
        R.string.options_note_unknown_zone,
        SaveTransfer.shortName(note.zoneId),
        context.getString(Zones.byId(note.fallbackZoneId).nameRes()),
    )
    is ImportNote.UnknownGoalsDropped -> context.getString(R.string.options_note_unknown_goals, note.count)
    is ImportNote.UnknownZonesIgnored -> {
        val shown = note.zoneIds.take(MAX_LISTED_ZONES)
            .joinToString(", ") { context.getString(R.string.options_quoted, SaveTransfer.shortName(it)) }
        val more = note.zoneIds.size - MAX_LISTED_ZONES
        if (more > 0) {
            context.getString(R.string.options_note_unknown_zones_more, shown, more)
        } else {
            context.getString(R.string.options_note_unknown_zones, shown)
        }
    }
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
