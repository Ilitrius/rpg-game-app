package com.idlerpg.game.domain.character

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Stats
import kotlin.math.pow
import kotlin.math.roundToLong

/** The four attributes a player can raise with stat points. */
enum class StatType { STRENGTH, DEXTERITY, VITALITY, LUCK }

/** Reads the value of [type] from these stats. */
operator fun Stats.get(type: StatType): Int = when (type) {
    StatType.STRENGTH -> strength
    StatType.DEXTERITY -> dexterity
    StatType.VITALITY -> vitality
    StatType.LUCK -> luck
}

/** Returns a copy with [type] increased by [amount]. */
fun Stats.plus(type: StatType, amount: Int): Stats = when (type) {
    StatType.STRENGTH -> copy(strength = strength + amount)
    StatType.DEXTERITY -> copy(dexterity = dexterity + amount)
    StatType.VITALITY -> copy(vitality = vitality + amount)
    StatType.LUCK -> copy(luck = luck + amount)
}

/** Result of [Progression.applyXp]. */
data class XpResult(val character: CharacterState, val levelsGained: Int)

/**
 * Level/XP rules. [CharacterState.xp] is the XP collected **within the current level**
 * (progress towards [xpForNextLevel]); it resets on every level-up (overflow carries over).
 */
object Progression {

    const val STAT_POINTS_PER_LEVEL = 3
    const val MAX_LEVEL = 999

    /** XP needed to go from [level] to level + 1: round(50 × level^1.6). L1 = 50, L2 = 152, L10 = 1991. */
    fun xpForNextLevel(level: Int): Long = (50.0 * level.coerceAtLeast(1).toDouble().pow(1.6)).roundToLong()

    /** Progress within the current level, 0f..1f (for progress bars). */
    fun levelProgress(character: CharacterState): Float {
        if (character.level >= MAX_LEVEL) return 1f
        return (character.xp.toDouble() / xpForNextLevel(character.level)).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Adds [gained] XP, performing as many level-ups as it pays for. Every level-up grants
     * [STAT_POINTS_PER_LEVEL] unspent points. At [MAX_LEVEL] XP stops accumulating.
     */
    fun applyXp(character: CharacterState, gained: Long): XpResult {
        if (gained <= 0) return XpResult(character, 0)
        var level = character.level.coerceAtLeast(1)
        var xp = character.xp.coerceAtLeast(0) + gained
        if (xp < 0) xp = Long.MAX_VALUE // overflow guard
        var levels = 0
        while (level < MAX_LEVEL) {
            val need = xpForNextLevel(level)
            if (xp < need) break
            xp -= need
            level++
            levels++
        }
        if (level >= MAX_LEVEL) xp = 0
        return XpResult(
            character.copy(
                level = level,
                xp = xp,
                unspentPoints = character.unspentPoints + levels * STAT_POINTS_PER_LEVEL,
            ),
            levels,
        )
    }

    /** [applyXp] on the whole game state. */
    fun applyXp(state: GameState, gained: Long): GameState =
        state.copy(character = applyXp(state.character, gained).character)

    /** Spends [amount] unspent points on [type]. Returns [state] unchanged if not enough points. */
    fun spendPoint(state: GameState, type: StatType, amount: Int = 1): GameState {
        val c = state.character
        if (amount <= 0 || c.unspentPoints < amount) return state
        return state.copy(
            character = c.copy(
                unspentPoints = c.unspentPoints - amount,
                stats = c.stats.plus(type, amount),
            ),
        )
    }
}
