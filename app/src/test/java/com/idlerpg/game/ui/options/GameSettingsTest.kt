package com.idlerpg.game.ui.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSettingsTest {
    private class FakeStore(val map: MutableMap<String, Boolean> = mutableMapOf()) : SettingsStore {
        override fun getBoolean(key: String, default: Boolean) = map[key] ?: default
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        val strings = mutableMapOf<String, String>()
        override fun getString(key: String, default: String) = strings[key] ?: default
        override fun putString(key: String, value: String) { strings[key] = value }
    }

    @Test
    fun languageDefaultsToSystemAndPersists() {
        val store = FakeStore()
        val settings = GameSettings(store)
        assertEquals(AppLanguage.SYSTEM, settings.language)
        assertEquals(null, settings.language.tag)
        settings.language = AppLanguage.ENGLISH
        assertEquals("en", store.strings[GameSettings.KEY_LANGUAGE])
        assertEquals(AppLanguage.ENGLISH, GameSettings(store).language)
        assertEquals(AppLanguage.ENGLISH, settings.languageFlow.value)
    }

    @Test
    fun unknownLanguageCodeFallsBackToSystem() {
        val store = FakeStore().apply { strings[GameSettings.KEY_LANGUAGE] = "klingon" }
        assertEquals(AppLanguage.SYSTEM, GameSettings(store).language)
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromCode("de"))
    }

    @Test
    fun volumeDefaultsPersistsAndIsClamped() {
        val store = FakeStore()
        val settings = GameSettings(store)
        assertEquals(GameSettings.DEFAULT_VOLUME_PERCENT, settings.soundVolume)
        settings.soundVolume = 35
        assertEquals(35, GameSettings(store).soundVolume)
        settings.soundVolume = 250
        assertEquals(100, settings.soundVolume)
        settings.soundVolume = -4
        assertEquals(0, settings.soundVolume)
        store.strings[GameSettings.KEY_VOLUME] = "garbage"
        assertEquals(GameSettings.DEFAULT_VOLUME_PERCENT, GameSettings(store).soundVolume)
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
}
