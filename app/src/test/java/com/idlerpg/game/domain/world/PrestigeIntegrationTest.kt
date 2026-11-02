package com.idlerpg.game.domain.world

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.PrestigeState
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.buffs.Buffs
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.combat.ComboTracker
import com.idlerpg.game.domain.prestige.Prestige
import com.idlerpg.game.domain.prestige.Talents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prestige bonuses in rewards, boss, offline, loot, rest, combo and the rebirth race guard. */
class PrestigeIntegrationTest {
    private val T0 = 1_000_000_000L

    private fun prestige(vararg t: Pair<String, Int>, rebirths: Int = 1) =
        PrestigeState(rebirths = rebirths, talents = mapOf(*t))

    private fun base(p: PrestigeState = PrestigeState()) =
        StarterItems.ensureStarterWeapon(GameState(prestige = p, world = WorldState(lastActiveEpochMillis = T0)))

    @Test
    fun killRewardScalesWithPrestige() {
        val s = base(prestige(Talents.XP to 10, Talents.GOLD to 20)) // xp x1.5, gold x2.0
        val r = CombatRules.applyKillReward(s, 10, 100, "wiese", T0)
        assertEquals(200L, r.gold - s.gold)
        assertEquals(Progression.applyXp(s, 15).character, r.character)
        assertEquals(200L, r.stats.goldEarned - s.stats.goldEarned)
    }

    @Test
    fun neutralPrestigeChangesNothing() {
        val s = base()
        val r = CombatRules.applyKillReward(s, 10, 100, "wiese", T0)
        assertEquals(100L, r.gold)
        assertEquals(Progression.applyXp(s, 10).character, r.character)
    }

    @Test
    fun buffAndPrestigeMultiplyNeverAddTwice() {
        val p = prestige(Talents.XP to 10) // x1.5
        val buffed = Buffs.add(base(p), Buffs.XP, 0.5, 60_000, "test") // x1.5
        val online = CombatRules.applyKillReward(buffed, 100, 0, "wiese", T0, applyBuffs = true)
        assertEquals(Progression.applyXp(buffed, 225).character, online.character) // 100 x 1.5 x 1.5
        val noBuffs = CombatRules.applyKillReward(buffed, 100, 0, "wiese", T0, applyBuffs = false)
        assertEquals(Progression.applyXp(buffed, 150).character, noBuffs.character) // prestige always, buffs only online
        assertEquals(225L, CombatRules.rewardXp(buffed, 100, applyBuffs = true))
        assertEquals(150L, CombatRules.rewardXp(buffed, 100))
    }

    @Test
    fun applyPrestigeFalseDoesNotScaleAgain() {
        val s = base(prestige(Talents.GOLD to 20))
        assertEquals(50L, CombatRules.applyKillReward(s, 0, 50, "wiese", T0, applyPrestige = false).gold)
        assertEquals(100L, CombatRules.applyKillReward(s, 0, 50, "wiese", T0).gold)
    }

    @Test
    fun bossVictoryUsesPrestigeAndTracksBestZone() {
        val s = base(prestige(Talents.GOLD to 20, Talents.XP to 20))
        val zone = Zones.WIESE
        val won = WorldProgress.applyBossVictory(s, zone, 40, 60, T0)
        assertEquals(120L, won.gold - s.gold)
        assertEquals(Progression.applyXp(s, 80).character, won.character)
        assertEquals(2, won.prestige.bestZoneOrder) // next zone unlocked and tracked
    }

    @Test
    fun levelUpTracksBestLevel() {
        val s = base()
        val need = Progression.xpForNextLevel(1)
        val r = CombatRules.applyKillReward(s, need, 0, "wiese", T0)
        assertEquals(2, r.character.level)
        assertEquals(2, r.prestige.bestLevel)
        assertEquals(1, r.prestige.bestZoneOrder)
    }

    @Test
    fun offlineRewardIncludesPrestigeExactlyOnce() {
        val plain = base().copy(character = CharacterState(level = 20))
        val boosted = plain.copy(prestige = prestige(Talents.XP to 20, Talents.GOLD to 20)) // x2 / x2
        val now = T0 + 6 * 3_600_000L
        val a = OfflineProgress.calculate(plain, now, kotlin.random.Random(1))!!
        val b = OfflineProgress.calculate(boosted, now, kotlin.random.Random(1))!!
        assertTrue(a.kills > 0 && b.kills > 0)
        assertEquals(a.xp.toDouble() * 2, b.xp.toDouble(), a.xp * 0.02 + 2)
        assertEquals(a.gold.toDouble() * 2, b.gold.toDouble(), a.gold * 0.02 + 2)
        // Applying the reward credits exactly reward.gold (no second scaling).
        val applied = OfflineProgress.apply(boosted, b, now)
        assertEquals(boosted.gold + b.gold + b.autoSoldGold, applied.gold)
    }

    @Test
    fun dropMultiplierCombinesPrestigeAndBuff() {
        val s = Buffs.add(base(prestige(Talents.DROP to 10)), Buffs.DROP, 0.5, 60_000, "test") // 1.5 x 1.5
        assertEquals(2.25, Loot.dropMultiplier(s), 1e-9)
        assertEquals(1.0, Loot.dropMultiplier(base()), 1e-9)
    }

    @Test
    fun restTalentShortensFreeRestWithFloor() {
        assertEquals(Rest.FREE_REST_MILLIS, Rest.freeRestMillis(1.0))
        assertEquals(Rest.FREE_REST_MILLIS / 2, Rest.freeRestMillis(0.5))
        assertEquals(Rest.FREE_REST_MILLIS / 2, Rest.freeRestMillis(0.1)) // clamped
    }

    @Test
    fun comboWindowIsAdjustable() {
        val c = ComboTracker()
        c.register(0)
        assertEquals(0, c.register(900)) // 600 ms window: restart
        c.windowMillis = 1000
        assertEquals(1, c.register(1800))
    }

    @Test
    fun staleSaveAfterRebirthIsRejectedButStatsSurvive() {
        val old = base().copy(
            character = CharacterState(level = 90),
            world = WorldState(currentZoneId = Zones.all[19].id, unlockedZoneIds = Zones.all.take(20).map { it.id }, lastActiveEpochMillis = T0),
        )
        val reborn = Prestige.rebirth(old, T0 + 10)
        assertEquals(1, reborn.prestige.rebirths)
        // A kill reward that was in flight when the rebirth happened (captured rebirths = 0).
        val stale = RebirthGuard.guarded(0, always = { it.copy(stats = it.stats.copy(tapCount = it.stats.tapCount + 5)) }) { s ->
            CombatRules.applyKillReward(s, 1_000_000, 1_000_000, "old", T0 + 20).copy(world = s.world.copy(heroHp = 9999))
        }
        val after = stale(reborn)
        assertEquals(reborn.gold, after.gold)
        assertEquals(reborn.character, after.character)
        assertEquals(reborn.world, after.world)
        assertEquals(reborn.stats.tapCount + 5, after.stats.tapCount)
        // Same rebirth count: normal save.
        val fresh = RebirthGuard.guarded(1) { it.copy(gold = it.gold + 7) }
        assertEquals(reborn.gold + 7, fresh(reborn).gold)
    }
}
