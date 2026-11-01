package com.idlerpg.game.ui.options

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
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
 * Small, polite donation request shown once per app start (after the system splash). It contains
 * the donation link ("Spenden" opens the external payment page in the browser) and says where the
 * button stays (Optionen -> Unterstuetzen). "Nicht mehr anzeigen" switches the hint off for good.
 * Dismissed state survives recreation (e.g. language change).
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
        text = {
            Column {
                Text(stringResource(R.string.options_donation_hint_text))
                TextButton(onClick = {
                    settings.donationHintHidden = true
                    show = false
                }) { Text(stringResource(R.string.options_donation_hint_never)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(DonateLink.URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    show = false
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.options_donate_no_browser, Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Toast.makeText(context, R.string.options_donate_no_browser, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.options_donate_button)) }
        },
        dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.options_donation_hint_ok)) } },
    )
}
