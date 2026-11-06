package com.idlerpg.game.domain.prestige

import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.PrestigeState

/**
 * Talent effects as multipliers on final values - the single place where talent levels become numbers.
 * Neutral (no talents) = all multipliers 1.0, additive parts 0. Unknown talent ids and levels above the
 * maximum are ignored/clamped, so tampered saves cannot exceed the catalog.
 *
 * [of] (GameState) also folds in the hero rank bonuses.
 * Wiring status: [damage]/[speed]/[hp]/[critBonus] in `CombatStats.of`, [click] via `TapStats.clickBonus`.
 * [xp]/[gold]/[drop]/[extraSlots]/[restFactor]/[comboWindowMillis] are API for spielwelt/ausruestung.
 */
data class PrestigeBonuses(
    val damage: Double = 1.0,
    val speed: Double = 1.0,
    val hp: Double = 1.0,
    /** Added to the crit multiplier (e.g. 0.5 = crits deal +0.5x). */
    val critBonus: Double = 0.0,
    val xp: Double = 1.0,
    val gold: Double = 1.0,
    /** Multiplier for `Loot.rollDrop(dropMultiplier = ...)`. */
    val drop: Double = 1.0,
    /** Extra inventory slots on top of 40 + bought slots. */
    val extraSlots: Int = 0,
    /** Multiplier for tap damage (combine with `Buffs.CLICK` via `TapStats.clickBonus`). */
    val click: Double = 1.0,
    /** Multiplier for the combo window ([com.idlerpg.game.domain.character.TapStats.COMBO_WINDOW_MILLIS]). */
    val comboWindow: Double = 1.0,
    /** Multiplier for the rest time after a defeat (<= 1.0, min 0.5). */
    val restFactor: Double = 1.0,
) {
    val comboWindowMillis: Long
        get() = Math.round(com.idlerpg.game.domain.character.TapStats.COMBO_WINDOW_MILLIS * comboWindow)

    val isNeutral: Boolean get() = this == NEUTRAL

    companion object {
        val NEUTRAL = PrestigeBonuses()

        /** Talent bonuses combined with the permanent hero-rank bonuses (domain/rank): one place for all permanent boni. */
        fun of(state: GameState): PrestigeBonuses {
            val talents = of(state.prestige)
            val r = com.idlerpg.game.domain.rank.RankBonuses.of(state)
            if (r == com.idlerpg.game.domain.rank.RankBonuses.NEUTRAL) return talents
            return talents.copy(
                damage = talents.damage * r.damage,
                hp = talents.hp * r.hp,
                speed = talents.speed * r.speed,
                gold = talents.gold * r.gold,
                extraSlots = talents.extraSlots + r.extraSlots,
            )
        }

        /** Extra levels the next run starts with (+2 per talent level). */
        fun startLevelBonus(p: PrestigeState): Int =
            (Prestige.level(p, Talents.START_LEVEL) * Talents.byId(Talents.START_LEVEL)!!.perLevel).toInt()

        fun of(p: PrestigeState): PrestigeBonuses {
            if (p.talents.isEmpty()) return NEUTRAL
            fun lv(id: String) = Prestige.level(p, id)
            fun per(id: String) = Talents.byId(id)!!.perLevel
            return PrestigeBonuses(
                damage = 1.0 + lv(Talents.DAMAGE) * per(Talents.DAMAGE),
                speed = 1.0 + lv(Talents.SPEED) * per(Talents.SPEED),
                hp = 1.0 + lv(Talents.HP) * per(Talents.HP),
                critBonus = lv(Talents.CRIT) * per(Talents.CRIT),
                xp = 1.0 + lv(Talents.XP) * per(Talents.XP),
                gold = 1.0 + lv(Talents.GOLD) * per(Talents.GOLD),
                drop = 1.0 + lv(Talents.DROP) * per(Talents.DROP),
                extraSlots = (lv(Talents.SLOTS) * per(Talents.SLOTS)).toInt(),
                click = 1.0 + lv(Talents.CLICK) * per(Talents.CLICK),
                comboWindow = 1.0 + lv(Talents.COMBO) * per(Talents.COMBO),
                restFactor = (1.0 - lv(Talents.REST) * per(Talents.REST)).coerceAtLeast(0.5),
            )
        }
    }
}

