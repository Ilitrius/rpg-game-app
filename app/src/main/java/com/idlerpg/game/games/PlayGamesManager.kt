package com.idlerpg.game.games

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.compositionLocalOf
import com.google.android.gms.games.PlayGames
import com.idlerpg.game.data.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Provided by MainActivity; null in previews/tests. */
val LocalPlayGames = compositionLocalOf<PlayGamesManager?> { null }

/**
 * Play Games sign-in and achievements. Fail-safe: every call is wrapped, so a device without
 * Play Services or a missing configuration never affects the game itself.
 * Create during Activity construction (registers an activity-result launcher).
 */
class PlayGamesManager(private val activity: ComponentActivity) {
    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    // Already sent in this process; avoids resubmitting on every state change.
    private val submitted = mutableSetOf<Achievement>()

    private val overlay = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    /** Asks Play Games whether the player is signed in (automatic sign-in happens at app start). */
    fun refresh() {
        runCatching {
            PlayGames.getGamesSignInClient(activity).isAuthenticated.addOnCompleteListener { task ->
                _signedIn.value = task.isSuccessful && task.result.isAuthenticated
            }
        }
    }

    /** Manual sign-in (e.g. after the player declined the automatic one). */
    fun signIn() {
        runCatching {
            PlayGames.getGamesSignInClient(activity).signIn().addOnCompleteListener { task ->
                _signedIn.value = task.isSuccessful && task.result.isAuthenticated
                if (_signedIn.value) submitted.clear()
            }
        }
    }

    /** Unlocks every achievement the [state] has earned (no-op while signed out). */
    fun sync(state: GameState) {
        if (!_signedIn.value) return
        val client = runCatching { PlayGames.getAchievementsClient(activity) }.getOrNull() ?: return
        for (a in Achievement.reached(state)) {
            if (a in submitted) continue
            val id = activity.getString(a.idRes)
            if (id.isBlank()) continue
            runCatching { client.unlock(id) }.onSuccess { submitted += a }
        }
    }

    /** Opens the Play Games achievements screen. */
    fun showAchievements() {
        runCatching {
            PlayGames.getAchievementsClient(activity).achievementsIntent
                .addOnSuccessListener { overlay.launch(it) }
        }
    }
}
