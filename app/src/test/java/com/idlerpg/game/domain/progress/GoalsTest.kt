package com.idlerpg.game.domain.progress

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.GoalState
import com.idlerpg.game.data.ProgressState
import com.idlerpg.game.data.WorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsTest {
    private fun withKills(s: GameState, kills: Long) = s.copy(world = s.world.copy(totalKills = kills))

    @Test
    fun catalogIsLargeAndStrictlyAscendingPerType() {
        assertTrue(Goals.catalog.size in 30..45)
        assertEquals(Goals.catalog.size, Goals.catalog.map { it.id }.toSet().size)
        for (type in GoalTypes.all) {
            val targets = Goals.catalog.filter { it.type == type }.map { it.target }
            assertTrue(type, targets.isNotEmpty())
            assertEquals(type, targets.sorted().distinct(), targets)
            assertTrue(Goals.catalog.filter { it.type == type }.all { it.id == "${type}_${it.target}" })
        }
        assertEquals(listOf(25L, 50L, 100L), Goals.catalog.filter { it.type == GoalTypes.KILLS }.take(3).map { it.target })
    }

    @Test
    fun threeActiveGoalsOfDifferentTypes() {
        val s = Goals.ensureActive(GameState())
        val active = Goals.active(s)
        assertEquals(3, active.size)
        assertEquals(3, active.map { it.type }.toSet().size)
        assertSame(s, Goals.ensureActive(s))
    }

    @Test
    fun progressIsDerivedFromCountersMinusBaseline() {
        var s = Goals.ensureActive(withKills(GameState(), 1000))
        val kills = Goals.active(s).first { it.type == GoalTypes.KILLS }
        assertEquals(1000, kills.baseline)
        assertEquals(0L, Goals.progress(s, kills))
        s = withKills(s, 1010)
        assertEquals(10L, Goals.progress(s, kills))
        assertTrue(!Goals.isComplete(s, kills))
        s = withKills(s, 5000)
        assertEquals(25L, Goals.progress(s, kills))
        assertTrue(Goals.isComplete(s, kills))
        // Counter below baseline (imported save) never gives negative progress.
        assertEquals(0L, Goals.progress(withKills(s, 3), kills))
    }

    @Test
    fun levelBossAndZoneCountersAreUsed() {
        var s = Goals.ensureActive(GameState())
        s = s.copy(
            character = CharacterState(level = 2),
            world = WorldState(unlockedZoneIds = listOf("wiese", "wald"), defeatedBossIds = listOf("wiese")),
        )
        for (g in Goals.active(s)) assertTrue(g.type, Goals.isComplete(s, g) || g.type == GoalTypes.KILLS)
        assertEquals(1L, Goals.progress(s, Goals.active(s).first { it.type == GoalTypes.BOSSES }))
    }

    @Test
    fun claimPaysOnceAndNextGoalMovesUp() {
        var s = Goals.ensureActive(GameState())
        val before = Goals.active(s).map { it.id }
        s = withKills(s, 100)
        assertEquals("kills_25", before[0])
        val gold0 = s.gold
        val reward = Goals.reward(s, Goals.active(s)[0])
        assertTrue(reward > 0)
        val claimed = Goals.claim(s, "kills_25")
        assertEquals(gold0 + reward, claimed.gold)
        assertEquals(1L, claimed.progress.goalsCompleted)
        val after = Goals.active(claimed)
        assertEquals(3, after.size)
        assertTrue("kills_25" !in after.map { it.id })
        // The next unclaimed catalog entry moves up, starting at its current counter.
        assertEquals("zones_1", after.last().id)
        assertEquals(1L, after.last().baseline)
        // No double claim, no claim of unfinished goals.
        assertSame(claimed, Goals.claim(claimed, "kills_25"))
        assertSame(claimed, Goals.claim(claimed, "zones_1"))
    }

    @Test
    fun rewardScalesWithZoneAndLevel() {
        val s0 = Goals.ensureActive(GameState())
        val goal = Goals.active(s0)[0]
        val strong = s0.copy(character = CharacterState(level = 35), world = s0.world.copy(currentZoneId = "hoehle"))
        assertTrue(Goals.reward(strong, goal) > Goals.reward(s0, goal) * 5)
    }

    @Test
    fun unknownGoalsAreSkippedWithoutCrash() {
        val bad = GoalState("kills_25", "magie", 25)
        val bad2 = GoalState("quatsch", "kills", 5)
        val s = GameState(progress = ProgressState(goals = listOf(bad, bad2)))
        assertNull(Goals.progress(s, bad))
        assertNull(Goals.progress(s, bad2))
        assertEquals(0L, Goals.reward(s, bad))
        assertTrue(Goals.active(s).isEmpty())
        assertSame(s, Goals.claim(s, "kills_25"))
        // They don't block refilling.
        assertEquals(3, Goals.active(Goals.ensureActive(s)).size)
    }

    @Test
    fun catalogCanBeExhaustedWithoutCrash() {
        var s = GameState()
        repeat(Goals.catalog.size + 5) {
            s = Goals.ensureActive(s)
            s = s.copy(
                character = s.character.copy(level = s.character.level + 1000),
                world = s.world.copy(totalKills = s.world.totalKills + 1_000_000, defeatedBossIds = List(500) { "b$it" + s.world.totalKills }),
            )
            Goals.active(s).forEach { s = Goals.claim(s, it.id) }
        }
        assertTrue(Goals.active(s).size <= 3)
    }

    @Test
    fun oldSaveWithoutProgressLoadsWithDefaults() {
        val old = """{"schemaVersion":1,"character":{"name":"Held","level":4},"gold":12}"""
        val s = GameJson.decode(old)
        assertEquals(ProgressState(), s.progress)
        assertEquals(12L, s.gold)
        // And a progress block with fewer fields decodes too.
        val partial = GameJson.decode("""{"progress":{"dailyStreak":2,"goals":[{"id":"x","type":"foo","target":3}]}}""")
        assertEquals(2, partial.progress.dailyStreak)
        assertEquals(0L, partial.progress.dailyLastClaimEpochMillis)
        assertEquals(false, partial.progress.goals.single().claimed)
    }
}
