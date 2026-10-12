package com.idlerpg.game.ui.adventure

import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.world.MonsterScaling
import com.idlerpg.game.domain.world.OfflineProgress
import com.idlerpg.game.domain.world.OfflineReward
import com.idlerpg.game.domain.world.WorldProgress
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossHudStatusTest {

    private val ready = AdventureUiState(
        zone = Zones.WIESE,
        world = WorldState(zoneKills = mapOf(Zones.WIESE_ID to WorldProgress.BOSS_KILLS_REQUIRED)),
    )
    private val boss = MonsterUi(1, MonsterScaling.boss(Zones.WIESE), 100)
    private val slime = MonsterUi(2, MonsterScaling.spawn(Zones.WIESE, 1), 10)

    @Test
    fun progressUntilEnoughKills() {
        val s = AdventureUiState(world = WorldState(zoneKills = mapOf(Zones.WIESE_ID to 3L)))
        assertEquals(BossHudStatus.PROGRESS, s.bossHudStatus)
        assertFalse(s.canChallengeBoss)
    }

    @Test
    fun readyWithEnoughKillsAndRegularMonster() {
        val s = ready.copy(monster = slime, phase = MonsterPhase.FIGHTING)
        assertEquals(BossHudStatus.READY, s.bossHudStatus)
        assertTrue(s.canChallengeBoss)
    }

    @Test
    fun neverReadyWhileTheBossFightRuns() {
        // Kill counter still says "ready" during the whole fight (it only resets on victory).
        listOf(MonsterPhase.APPROACHING, MonsterPhase.FIGHTING, MonsterPhase.DYING).forEach { phase ->
            val s = ready.copy(bossFight = true, monster = boss, phase = phase)
            assertEquals(phase.name, BossHudStatus.FIGHTING, s.bossHudStatus)
            assertFalse(phase.name, s.canChallengeBoss)
        }
    }

    @Test
    fun dyingBossBeforeVictoryIsSavedIsNoNewChallenge() {
        // onBossKill ends the fight flag before the repository confirms the victory.
        val s = ready.copy(bossFight = false, monster = boss, phase = MonsterPhase.DYING)
        assertEquals(BossHudStatus.FIGHTING, s.bossHudStatus)
        assertFalse(s.canChallengeBoss)
    }

    @Test
    fun readyAgainAfterAFailedFight() {
        val s = ready.copy(bossFight = false, monster = null, phase = MonsterPhase.GONE)
        assertEquals(BossHudStatus.READY, s.bossHudStatus)
        assertTrue(s.canChallengeBoss)
    }
}

class WelcomeBackPolicyTest {

    private fun reward(awayMinutes: Long, kills: Long, tooWeak: Boolean = false): OfflineReward {
        val away = awayMinutes * 60_000
        return OfflineReward(away, away.coerceAtMost(OfflineProgress.MAX_OFFLINE_MILLIS), kills, kills * 5, kills * 2, 0, heroTooWeak = tooWeak)
    }

    @Test
    fun shortAbsenceIsCreditedSilently() {
        assertFalse(shouldShowWelcomeBack(null))
        assertFalse(shouldShowWelcomeBack(reward(10, kills = 2)))
        assertFalse(shouldShowWelcomeBack(reward(29, kills = 6)))
    }

    @Test
    fun longAbsenceWithKillsShowsSummary() {
        assertTrue(shouldShowWelcomeBack(reward(30, kills = 6)))
        assertTrue(shouldShowWelcomeBack(reward(9 * 60, kills = 100)))
    }

    @Test
    fun tooWeakHeroGetsAnExplanationInsteadOfSilence() {
        assertTrue(shouldShowWelcomeBack(reward(8 * 60, kills = 0, tooWeak = true)))
        assertFalse(shouldShowWelcomeBack(reward(8 * 60, kills = 0)))
    }

    @Test
    fun capIsVisibleFromTheReward() {
        assertTrue(reward(9 * 60, kills = 100).capped)
        assertFalse(reward(8 * 60, kills = 100).capped)
    }
}
