package com.idlerpg.game

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk
import com.idlerpg.game.data.GameRepository
import com.idlerpg.game.domain.character.StarterItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IdleRpgApp : Application() {
    val repository: GameRepository by lazy { GameRepository(this) }

    /** Process-wide scope for one-off background work (e.g. save migrations). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Play Games Services initialisieren (fail-safe: ohne Play Services/ID läuft das Spiel normal).
        runCatching { PlayGamesSdk.initialize(this) }
        // New games and old saves without a weapon receive the starter dagger once.
        appScope.launch { runCatching { repository.update(StarterItems::ensureStarterWeapon) } }
    }
}
