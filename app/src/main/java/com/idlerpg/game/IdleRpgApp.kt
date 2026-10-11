package com.idlerpg.game

import android.app.Application
import com.idlerpg.game.data.GameRepository

class IdleRpgApp : Application() {
    val repository: GameRepository by lazy { GameRepository(this) }
}
