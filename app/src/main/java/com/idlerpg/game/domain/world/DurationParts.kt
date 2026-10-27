package com.idlerpg.game.domain.world

/** A duration split into whole days, hours and minutes (seconds are dropped; negative = 0). */
data class DurationParts(val days: Int, val hours: Int, val minutes: Int) {
    companion object {
        fun of(millis: Long): DurationParts {
            val totalMinutes = (millis.coerceAtLeast(0L) / 60_000L)
            val days = (totalMinutes / (24 * 60)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val rest = (totalMinutes % (24 * 60)).toInt()
            return DurationParts(days, rest / 60, rest % 60)
        }
    }
}
