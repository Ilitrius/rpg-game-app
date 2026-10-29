package com.idlerpg.game.ui.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {
    private val day = BackupReminder.DAY_MILLIS
    private val now = 100 * day

    @Test fun lowLevelNeverNeedsBackup() = assertFalse(BackupReminder.needsBackup(9, 0, now))
    @Test fun neverExportedAtLevel10Needs() = assertTrue(BackupReminder.needsBackup(10, 0, now))
    @Test fun freshExportOk() = assertFalse(BackupReminder.needsBackup(50, now - 14 * day, now))
    @Test fun staleExportNeeds() = assertTrue(BackupReminder.needsBackup(50, now - 14 * day - 1, now))
    @Test fun futureExportCountsFresh() = assertFalse(BackupReminder.needsBackup(50, now + day, now))

    @Test fun promptRespectsSwitchAndInterval() {
        assertFalse(BackupReminder.shouldPrompt(false, 20, 0, 0, now))
        assertTrue(BackupReminder.shouldPrompt(true, 20, 0, 0, now))
        assertFalse(BackupReminder.shouldPrompt(true, 20, 0, now - 6 * day, now))
        assertTrue(BackupReminder.shouldPrompt(true, 20, 0, now - 7 * day, now))
        assertFalse(BackupReminder.shouldPrompt(true, 5, 0, 0, now))
        assertFalse(BackupReminder.shouldPrompt(true, 20, now - day, 0, now))
        assertTrue(BackupReminder.shouldPrompt(true, 20, 0, now + day, now))
    }

    @Test fun settingsPersistDefaults() {
        val store = object : SettingsStore {
            val b = mutableMapOf<String, Boolean>(); val s = mutableMapOf<String, String>()
            override fun getBoolean(key: String, default: Boolean) = b[key] ?: default
            override fun putBoolean(key: String, value: Boolean) { b[key] = value }
            override fun getString(key: String, default: String) = s[key] ?: default
            override fun putString(key: String, value: String) { s[key] = value }
        }
        val g = GameSettings(store)
        assertEquals(0L, g.lastExportEpochMillis)
        assertTrue(g.remindersEnabled)
        g.lastExportEpochMillis = 123L; g.lastReminderPromptEpochMillis = 456L; g.remindersEnabled = false
        val g2 = GameSettings(store)
        assertEquals(123L, g2.lastExportEpochMillis); assertEquals(456L, g2.lastReminderPromptEpochMillis); assertFalse(g2.remindersEnabled)
    }
}
