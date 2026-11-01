package com.idlerpg.game.ui.options

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.idlerpg.game.R

/**
 * Small, polite donation request shown once per app start (after the system splash). It says where
 * the donate button is (Optionen → Unterstützen); the button itself stays in the options. "Nicht mehr
 * anzeigen" switches it off for good. Dismissed state survives recreation (e.g. language change).
 */
@Composable
fun DonationHintDialog() {
    val context = LocalContext.current
    val settings = GameSettings.get(context)
    var show by rememberSaveable { mutableStateOf(!settings.donationHintHidden) }
    if (!show) return
    AlertDialog(
        onDismissRequest = { show = false },
        title = { Text(stringResource(R.string.options_donation_hint_title)) },
        text = { Text(stringResource(R.string.options_donation_hint_text)) },
        confirmButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.options_donation_hint_ok)) } },
        dismissButton = {
            TextButton(onClick = {
                settings.donationHintHidden = true
                show = false
            }) { Text(stringResource(R.string.options_donation_hint_never)) }
        },
    )
}
