package com.idlerpg.game.ui.tutorial

import androidx.annotation.StringRes
import com.idlerpg.game.R
import com.idlerpg.game.ui.MainTab

/** Screen area a tutorial step highlights. */
sealed interface TutorialTarget {
    /** No highlight, card in the middle. */
    data object None : TutorialTarget

    /** Monster in the adventure view. */
    data object Enemy : TutorialTarget

    /** Zone label (top left of the adventure view). */
    data object Zone : TutorialTarget

    /** One or more entries of the bottom menu bar. */
    data class Tabs(val tabs: List<MainTab>) : TutorialTarget
}

data class TutorialStep(@StringRes val title: Int, @StringRes val text: Int, val target: TutorialTarget)

/** Steps of the introduction, in order. Pure data so the order is unit-testable. */
object TutorialSteps {
    val all: List<TutorialStep> = listOf(
        TutorialStep(R.string.tutorial_welcome_title, R.string.tutorial_welcome_text, TutorialTarget.None),
        TutorialStep(R.string.tutorial_tap_title, R.string.tutorial_tap_text, TutorialTarget.Enemy),
        TutorialStep(R.string.tutorial_zone_title, R.string.tutorial_zone_text, TutorialTarget.Zone),
        TutorialStep(R.string.tutorial_character_title, R.string.tutorial_character_text, TutorialTarget.Tabs(listOf(MainTab.CHARACTER))),
        TutorialStep(
            R.string.tutorial_gear_title, R.string.tutorial_gear_text,
            TutorialTarget.Tabs(listOf(MainTab.EQUIPMENT, MainTab.SHOP)),
        ),
        TutorialStep(R.string.tutorial_offline_title, R.string.tutorial_offline_text, TutorialTarget.Tabs(listOf(MainTab.OPTIONS))),
    )
}
