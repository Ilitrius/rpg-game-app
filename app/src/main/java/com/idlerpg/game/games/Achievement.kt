package com.idlerpg.game.games

import com.idlerpg.game.R
import com.idlerpg.game.data.GameState

/**
 * Play Games achievements. [idRes] points to the achievement ID from the Play Console
 * (res/values/games_ids.xml); an empty ID means "not configured yet" and is skipped.
 */
enum class Achievement(val idRes: Int, val reached: (GameState) -> Boolean) {
    KILLS_100(R.string.achievement_kills_100, { it.world.totalKills >= 100 }),
    KILLS_1000(R.string.achievement_kills_1000, { it.world.totalKills >= 1_000 }),
    KILLS_10000(R.string.achievement_kills_10000, { it.world.totalKills >= 10_000 }),
    LEVEL_10(R.string.achievement_level_10, { it.character.level >= 10 }),
    LEVEL_50(R.string.achievement_level_50, { it.character.level >= 50 }),
    LEVEL_100(R.string.achievement_level_100, { it.character.level >= 100 }),
    BOSS_FIRST(R.string.achievement_boss_first, { it.world.defeatedBossIds.isNotEmpty() }),
    BOSS_10(R.string.achievement_boss_10, { it.world.defeatedBossIds.size >= 10 }),
    ZONES_10(R.string.achievement_zones_10, { it.world.unlockedZoneIds.size >= 10 }),
    GOLD_1M(R.string.achievement_gold_1m, { it.stats.goldEarned >= 1_000_000 });

    companion object {
        /** All achievements the state has earned (pure; unlocking is idempotent on the Play side). */
        fun reached(state: GameState): Set<Achievement> = entries.filterTo(mutableSetOf()) { it.reached(state) }
    }
}
