package com.idlerpg.game.ui.adventure

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterHitBoxTest {
    private val w = 1080f
    private val h = 2400f
    private val horizon = 0.45f * h
    private val ground = 0.68f * h
    private fun hit(x: Float, y: Float, approach: Float, size: Float = 1f) =
        MonsterHitBox.contains(x, y, w, h, horizon, ground, size, approach, 144f)

    @Test
    fun fullSizeMonsterHitAtCenterBody() {
        assertTrue(hit(540f, ground - 300f, 1f))
        assertFalse(hit(540f, ground + 400f, 1f))
        assertFalse(hit(50f, ground - 300f, 1f))
    }

    @Test
    fun farMonsterUsesDepthPosition() {
        val depth = MonsterHitBox.depth(0f)
        val g = horizon + (ground - horizon) * depth
        assertTrue(hit(540f, g - 20f, 0f))
        // Old end position (full size ground) is far away from the distant monster.
        assertFalse(hit(540f, ground - 300f, 0f))
    }

    @Test
    fun minimumRadiusMakesTinyMonsterHittable() {
        val g = horizon + (ground - horizon) * MonsterHitBox.depth(0f)
        assertTrue(hit(540f + 140f, g - 10f, 0f))
        assertFalse(hit(540f + 200f, g - 10f, 0f))
    }

    @Test
    fun depthRange() {
        assertTrue(MonsterHitBox.depth(0f) == 0.14f)
        assertTrue(MonsterHitBox.depth(1f) == 1f)
    }
}
