package com.idlerpg.game.ui.options

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal key-value storage so [GameSettings] stays JVM-testable (SharedPreferences adapter below). */
interface SettingsStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
}

/** Selectable UI language. [code] is persisted; [tag] null = follow the system locale. */
enum class AppLanguage(val code: String, val tag: String?) {
    SYSTEM("system", null),
    GERMAN("de", "de"),
    ENGLISH("en", "en");

    companion object {
        const val DEFAULT_CODE = "system"

        /** Unknown/corrupt codes fall back to [SYSTEM]. */
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * Device-level app settings (not part of the save game: importing a save must not change them).
 *
 * Audio API for later use: read [soundEnabled] (or collect [soundEnabledFlow]) before playing any
 * sound. Currently the game has no audio; only the preference is stored.
 */
class GameSettings(private val store: SettingsStore) {
    private val _soundEnabled = MutableStateFlow(store.getBoolean(KEY_SOUND, DEFAULT_SOUND_ENABLED))

    val soundEnabledFlow: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    /** Sound on/off, default on. Persisted immediately. */
    var soundEnabled: Boolean
        get() = _soundEnabled.value
        set(value) {
            store.putBoolean(KEY_SOUND, value)
            _soundEnabled.value = value
        }

    private val _language = MutableStateFlow(AppLanguage.fromCode(store.getString(KEY_LANGUAGE, AppLanguage.DEFAULT_CODE)))

    val languageFlow: StateFlow<AppLanguage> = _language.asStateFlow()

    /** Language choice, default [AppLanguage.SYSTEM]. Applied by `MainActivity.attachBaseContext`. */
    var language: AppLanguage
        get() = _language.value
        set(value) {
            store.putString(KEY_LANGUAGE, value.code)
            _language.value = value
        }

    companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_SOUND = "sound_enabled"
        const val DEFAULT_SOUND_ENABLED = true

        @Volatile
        private var instance: GameSettings? = null

        /** App-wide instance backed by SharedPreferences `game_settings`. */
        fun get(context: Context): GameSettings = instance ?: synchronized(this) {
            instance ?: GameSettings(PrefsStore(context.applicationContext)).also { instance = it }
        }
    }
}

private class PrefsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("game_settings", Context.MODE_PRIVATE)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getString(key: String, default: String) = prefs.getString(key, default) ?: default
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}
