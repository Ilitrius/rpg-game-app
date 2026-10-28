package com.idlerpg.game.ui.adventure

import com.idlerpg.game.domain.character.Hit
import com.idlerpg.game.domain.world.MonsterScaling
import com.idlerpg.game.domain.world.MonsterType
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AdventureTapTest {
    private val stats = MonsterScaling.stats(MonsterType.SLIME, Zones.WIESE, 1)

    private fun state(hp: Long = 20, phase: MonsterPhase = MonsterPhase.FIGHTING) =
        AdventureUiState(monster = MonsterUi(1, stats, hp), phase = phase)

    @Test
    fun tapDealsHitDamageIncludingCrit() {
        assertEquals(15L, applyTapHit(state(20), Hit(5, false))!!.monster!!.hp)
        assertEquals(10L, applyTapHit(state(20), Hit(10, true))!!.monster!!.hp)
    }

    @Test
    fun overkillClampsToZero() {
        assertEquals(0L, applyTapHit(state(3), Hit(50, false))!!.monster!!.hp)
    }

    @Test
    fun tapIgnoredUnlessMonsterIsFighting() {
        for (p in listOf(MonsterPhase.APPROACHING, MonsterPhase.DYING, MonsterPhase.GONE)) {
            assertNull(p.name, applyTapHit(state(phase = p), Hit(5, false)))
        }
        assertNull(applyTapHit(AdventureUiState(), Hit(5, false)))
        assertNull(applyTapHit(state().copy(defeated = true), Hit(5, false)))
        assertNull(applyTapHit(state().copy(travelingTo = Zones.WALD), Hit(5, false)))
    }

    @Test
    fun noDoubleProcessingAfterKill() {
        val killed = applyTapHit(state(5), Hit(9, false))!!
        assertEquals(0L, killed.monster!!.hp)
        assertNull("dead monster is not hit again", applyTapHit(killed, Hit(9, false)))
        assertNull(applyTapHit(killed.copy(phase = MonsterPhase.DYING), Hit(9, false)))
        assertNotNull(applyTapHit(state(5), Hit(1, false)))
    }
}
