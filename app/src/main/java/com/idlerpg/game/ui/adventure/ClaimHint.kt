package com.idlerpg.game.ui.adventure

import com.idlerpg.game.data.GameState
import com.idlerpg.game.domain.progress.DailyReward
import com.idlerpg.game.domain.progress.Goals

/** True if the daily reward can be claimed or any active goal is complete (HUD hint dot). */
fun hasClaimable(state: GameState, nowMillis: Long): Boolean =
    DailyReward.status(state, nowMillis).canClaim || Goals.active(state).any { Goals.isComplete(state, it) }
