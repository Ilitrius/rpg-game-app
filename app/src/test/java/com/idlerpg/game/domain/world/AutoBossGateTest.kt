package com.idlerpg.game.domain.world

import com.idlerpg.game.data.WorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBossGateTest {
    private val zone = Zones.WALD
    private fun world(kills: Long, defeated: List<String> = emptyList()) =
        WorldState(zoneKills = mapOf(zone.id to kills), defeatedBossIds = defeated, unlockedZoneIds = listOf(zone.id))

    @Test
    fun startsOnlyWhenEnabledReadyAndHealthy() {
        val g = AutoBossGate()
        assertTrue(g.shouldStart(true, world(25), zone, 70, 100))
        assertFalse("disabled", g.shouldStart(false, world(25), zone, 100, 100))
        assertFalse("kills missing", g.shouldStart(true, world(24), zone, 100, 100))
        assertFalse("hp below 70 %", g.shouldStart(true, world(25), zone, 69, 100))
    }

    @Test
    fun notForDefeatedBossOrLastZone() {
        val g = AutoBossGate()
        assertFalse(g.shouldStart(true, world(60, listOf(zone.id)), zone, 100, 100))
        val last = Zones.last
        val w = WorldState(zoneKills = mapOf(last.id to 99L), defeatedBossIds = Zones.all.dropLast(1).map { it.id })
        assertFalse("zone 100 has no successor", g.shouldStart(true, w, last, 100, 100))
    }

    @Test
    fun failureBlocksForMoreAndMoreKillsCappedAtFifty() {
        val g = AutoBossGate()
        g.onFailure(30)
        assertEquals(40L, g.blockedUntil)
        assertFalse(g.shouldStart(true, world(39), zone, 100, 100))
        assertTrue(g.shouldStart(true, world(40), zone, 100, 100))
        g.onFailure(40)
        assertEquals(60L, g.blockedUntil)
        g.onFailure(60); g.onFailure(100); g.onFailure(200)
        assertEquals(250L, g.blockedUntil) // 200 + capped 50
        assertEquals(5, g.failCount)
    }

    @Test
    fun victoryAndLevelUpClearPenalty() {
        val g = AutoBossGate()
        g.observeLevel(10)
        g.onFailure(30)
        g.observeLevel(10)
        assertEquals(40L, g.blockedUntil)
        g.observeLevel(11)
        assertEquals(0L, g.blockedUntil)
        g.onFailure(30); g.onVictory()
        assertEquals(0, g.failCount)
        assertTrue(g.shouldStart(true, world(25), zone, 100, 100))
    }

    @Test
    fun evaluateNamesTheBlockingReason() {
        val g = AutoBossGate()
        assertEquals(AutoBossReason.OFF, g.evaluate(false, world(25), zone, 100, 100).reason)
        val kills = g.evaluate(true, world(20), zone, 100, 100)
        assertEquals(AutoBossReason.KILLS, kills.reason)
        assertEquals(5L, kills.remaining)
        assertEquals(AutoBossReason.LOW_HP, g.evaluate(true, world(25), zone, 50, 100).reason)
        assertEquals(AutoBossReason.BOSS_DEFEATED, g.evaluate(true, world(60, listOf(zone.id)), zone, 100, 100).reason)
        g.onFailure(30)
        val cd = g.evaluate(true, world(35), zone, 100, 100)
        assertEquals(AutoBossReason.COOLDOWN, cd.reason)
        assertEquals(5L, cd.remaining)
        assertEquals(AutoBossReason.START, g.evaluate(true, world(40), zone, 100, 100).reason)
    }

    /** Regression: a hero below 70 % HP is only blocked by HP; resting (regen) must lead to START. */
    @Test
    fun lowHpOnlyBlocksUntilRegenerated() {
        val g = AutoBossGate()
        val w = world(25)
        var hp = 40.0
        var ticks = 0
        while (g.evaluate(true, w, zone, Math.ceil(hp).toInt(), 100).reason == AutoBossReason.LOW_HP) {
            hp += com.idlerpg.game.domain.combat.CombatRules.regen(100, 250); ticks++
            assertTrue(ticks < 400)
        }
        assertEquals(AutoBossReason.START, g.evaluate(true, w, zone, Math.ceil(hp).toInt(), 100).reason)
    }
}
