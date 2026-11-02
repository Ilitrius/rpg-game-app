package com.idlerpg.game.ui.options

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.idlerpg.game.R

/** Play-Store-Links fuer "App bewerten" und "App teilen" (keine Berechtigung, keine Datenuebertragung durch die App). */
object AppLinks {
    const val PACKAGE = "com.rp.IDLEGAMERP"
    const val STORE_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"
    const val MARKET_URL = "market://details?id=$PACKAGE"

    /** Oeffnet die Store-Seite (Play-Store-App, sonst Browser). Liefert false, wenn nichts gefunden wurde. */
    fun openRating(context: Context): Boolean {
        for (url in listOf(MARKET_URL, STORE_URL)) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    /** Teilen-Dialog mit Text und Store-Link. Liefert false, wenn keine App zum Teilen existiert. */
    fun share(context: Context): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.options_share_text, STORE_URL))
        }
        return try {
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.options_share_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
