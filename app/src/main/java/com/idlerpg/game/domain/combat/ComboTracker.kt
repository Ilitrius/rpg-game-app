package com.idlerpg.game.domain.combat

import com.idlerpg.game.domain.character.TapStats

/**
 * Counts taps in a row: a tap within [TapStats.COMBO_WINDOW_MILLIS] of the previous one continues
 * the combo, a longer pause restarts it. [register] returns the combo count to pass to
 * [TapStats.tapHit] (number of taps before this one, so the first tap has 0).
 */
class ComboTracker(private val windowMillis: Long = TapStats.COMBO_WINDOW_MILLIS) {
    private var count = 0
    private var lastTap = Long.MIN_VALUE

    fun register(nowMillis: Long): Int {
        count = if (lastTap != Long.MIN_VALUE && nowMillis - lastTap <= windowMillis) count + 1 else 0
        lastTap = nowMillis
        return count
    }

    /** Current combo as shown in the HUD: 0 if the window has already passed. */
    fun current(nowMillis: Long): Int =
        if (lastTap != Long.MIN_VALUE && nowMillis - lastTap <= windowMillis) count else 0

    fun reset() {
        count = 0
        lastTap = Long.MIN_VALUE
    }
}
