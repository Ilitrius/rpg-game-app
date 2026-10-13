package com.idlerpg.game.ui.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSettingsTest {
    private class FakeStore(val map: MutableMap<String, Boolean> = mutableMapOf()) : SettingsStore {
        override fun getBoolean(key: String, default: Boolean) = map[key] ?: default
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    }

    @Test
    fun soundDefaultsToOn() {
        assertTrue(GameSettings(FakeStore()).soundEnabled)
    }

    @Test
    fun soundPersistsAndFlowUpdates() {
        val store = FakeStore()
        val settings = GameSettings(store)
        settings.soundEnabled = false
        assertFalse(settings.soundEnabledFlow.value)
        assertFalse(GameSettings(store).soundEnabled)
        settings.soundEnabled = true
        assertTrue(GameSettings(store).soundEnabled)
    }

    @Test
    fun donateUrlIsHttps() {
        assertTrue(DonateLink.isValid())
        assertFalse(DonateLink.isValid("http://x.de"))
        assertFalse(DonateLink.isValid("https://"))
        assertTrue(DonateLink.URL.startsWith("https://buy.stripe.com/"))
    }
}
