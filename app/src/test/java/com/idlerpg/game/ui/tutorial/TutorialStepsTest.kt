package com.idlerpg.game.ui.tutorial

import androidx.compose.ui.geometry.Rect
import com.idlerpg.game.ui.MainTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialStepsTest {
    @Test
    fun startsWithoutHighlightAndCoversEveryTab() {
        val steps = TutorialSteps.all
        assertEquals(TutorialTarget.None, steps.first().target)
        val tabs = steps.mapNotNull { it.target as? TutorialTarget.Tabs }.flatMap { it.tabs }.toSet()
        assertEquals(MainTab.entries.toSet(), tabs)
        assertTrue(steps.any { it.target == TutorialTarget.Enemy })
        assertTrue(steps.any { it.target == TutorialTarget.Zone })
        assertEquals(steps.size, steps.map { it.title }.toSet().size)
    }

    @Test
    fun tabHighlightSpansAllNamedTabsAndIgnoresUnmeasured() {
        val anchors = TutorialAnchors(
            adventure = Rect(0f, 100f, 1080f, 2200f),
            tabs = listOf(Rect(0f, 2200f, 270f, 2350f), Rect(270f, 2200f, 540f, 2350f), Rect(540f, 2200f, 810f, 2350f), Rect.Zero),
        )
        assertEquals(Rect(270f, 2200f, 810f, 2350f), TutorialTarget.Tabs(listOf(MainTab.EQUIPMENT, MainTab.SHOP)).highlight(anchors, 96f))
        assertNull(TutorialTarget.Tabs(listOf(MainTab.OPTIONS)).highlight(anchors, 96f))
        assertNull(TutorialTarget.None.highlight(anchors, 96f))
    }

    @Test
    fun sceneHighlightsStayInsideAdventureViewAndNeedMeasurement() {
        val adventure = Rect(0f, 100f, 1080f, 2200f)
        val anchors = TutorialAnchors(adventure)
        listOf(TutorialTarget.Enemy, TutorialTarget.Zone).forEach { target ->
            val r = target.highlight(anchors, 96f)!!
            assertTrue(r.left >= adventure.left && r.right <= adventure.right && r.top >= adventure.top && r.bottom <= adventure.bottom)
        }
        assertEquals(Rect(0f, 100f, 1080f * 0.58f, 196f), TutorialTarget.Zone.highlight(anchors, 96f))
        assertNull(TutorialTarget.Enemy.highlight(TutorialAnchors(), 96f))
    }
}
