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

    /** Defaults keep older [SettingsStore] implementations working. */
    fun getInt(key: String, default: Int): Int = getString(key, default.toString()).toIntOrNull() ?: default
    fun putInt(key: String, value: Int) = putString(key, value.toString())
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
 * Audio API: `audio/SoundManager` reads [soundEnabled] and [soundVolume] before playing any effect.
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

    private val _volume = MutableStateFlow(store.getInt(KEY_VOLUME, DEFAULT_VOLUME_PERCENT).coerceIn(0, 100))

    val soundVolumeFlow: StateFlow<Int> = _volume.asStateFlow()

    /** Effect volume in percent (0..100), default [DEFAULT_VOLUME_PERCENT]. Persisted immediately. */
    var soundVolume: Int
        get() = _volume.value
        set(value) {
            val v = value.coerceIn(0, 100)
            store.putInt(KEY_VOLUME, v)
            _volume.value = v
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

    private val _autoBoss = MutableStateFlow(store.getBoolean(KEY_AUTO_BOSS, DEFAULT_AUTO_BOSS))

    val autoBossFlow: StateFlow<Boolean> = _autoBoss.asStateFlow()

    /**
     * Auto-boss mode of the adventure view (starts the zone boss by itself when it is safe, see
     * `AutoBossGate`), default off. Device setting, not part of the save game.
     */
    var autoBoss: Boolean
        get() = _autoBoss.value
        set(value) {
            store.putBoolean(KEY_AUTO_BOSS, value)
            _autoBoss.value = value
        }

    private val _lastExport = MutableStateFlow(store.getString(KEY_LAST_EXPORT, "").toLongOrNull() ?: 0L)

    val lastExportFlow: StateFlow<Long> = _lastExport.asStateFlow()

    /** Epoch millis of the last successful save export (0 = never). Device setting, not in the save game. */
    var lastExportEpochMillis: Long
        get() = _lastExport.value
        set(value) {
            store.putString(KEY_LAST_EXPORT, value.toString())
            _lastExport.value = value
        }

    /** Epoch millis the start-up backup reminder was last shown (0 = never). */
    var lastReminderPromptEpochMillis: Long
        get() = store.getString(KEY_LAST_PROMPT, "").toLongOrNull() ?: 0L
        set(value) = store.putString(KEY_LAST_PROMPT, value.toString())

    private val _reminders = MutableStateFlow(store.getBoolean(KEY_REMINDERS, DEFAULT_REMINDERS))

    val remindersEnabledFlow: StateFlow<Boolean> = _reminders.asStateFlow()

    /** Backup reminders (hint card + start-up dialog), default on. */
    var remindersEnabled: Boolean
        get() = _reminders.value
        set(value) {
            store.putBoolean(KEY_REMINDERS, value)
            _reminders.value = value
        }

    companion object {
        const val KEY_LAST_EXPORT = "last_export_epoch_millis"
        const val KEY_LAST_PROMPT = "last_backup_prompt_epoch_millis"
        const val KEY_REMINDERS = "backup_reminders"
        const val DEFAULT_REMINDERS = true
        const val KEY_AUTO_BOSS = "auto_boss"
        const val DEFAULT_AUTO_BOSS = false
        const val KEY_LANGUAGE = "language"
        const val KEY_SOUND = "sound_enabled"
        const val DEFAULT_SOUND_ENABLED = true
        const val KEY_VOLUME = "sound_volume"
        const val DEFAULT_VOLUME_PERCENT = 70

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

/** Donation link. */
object DonateLink {
    // Stripe Payment Link (public URL, no secret key involved).
    const val URL = "https://buy.stripe.com/dRmaEY3Me3TO5Z54R1eUU00"

    fun isValid(url: String = URL): Boolean =
        url.startsWith("https://") && url.length > "https://".length && url.none { it.isWhitespace() }
}
