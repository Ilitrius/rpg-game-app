package com.idlerpg.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.annotation.RawRes
import com.idlerpg.game.R
import com.idlerpg.game.ui.options.GameSettings

/** Sound effects of the game (synthesized by `tools/generate_sounds.py`, see `tools/SOUNDS.md`). */
enum class Sfx(@RawRes val resId: Int, val gain: Float = 1f) {
    HIT(R.raw.sfx_hit, 0.8f),
    CRIT(R.raw.sfx_crit),
    HURT(R.raw.sfx_hurt, 0.9f),
    MONSTER_DIE(R.raw.sfx_monster_die),
    LOOT(R.raw.sfx_loot),
    LEVEL_UP(R.raw.sfx_levelup),
    BOSS_START(R.raw.sfx_boss_start),
    BOSS_VICTORY(R.raw.sfx_boss_victory),
    COIN(R.raw.sfx_coin),
    CLICK(R.raw.sfx_click, 0.7f),
}

/**
 * SoundPool wrapper. Plays only while the app is visible ([onForeground]/[onBackground] from
 * `MainActivity`) and only if sound is enabled in [GameSettings]; the pool is created lazily on the
 * first foreground and released when the app goes to the background, so nothing runs while hidden.
 * Offline progress never calls [play]. All calls are fail-safe (no exception reaches the game).
 */
class SoundManager private constructor(private val context: Context) {
    private val settings = GameSettings.get(context)
    private var pool: SoundPool? = null
    private val soundIds = HashMap<Sfx, Int>()
    private val loaded = HashSet<Int>()
    private val lastPlayed = HashMap<Sfx, Long>()
    private var foreground = false

    @Synchronized
    fun onForeground() {
        foreground = true
        if (pool != null || !settings.soundEnabled) return
        createPool()
    }

    @Synchronized
    fun onBackground() {
        foreground = false
        release()
    }

    /** Plays [sfx] if sound is on, the app is visible and the volume is above zero. */
    @Synchronized
    fun play(sfx: Sfx) {
        if (!foreground || !settings.soundEnabled) return
        val volume = settings.soundVolume / 100f
        if (volume <= 0f) return
        runCatching {
            if (pool == null) createPool() // sound was switched on while the app is open
            val p = pool ?: return
            val id = soundIds[sfx] ?: return
            if (id !in loaded) return
            val now = SystemClock.elapsedRealtime()
            // Debounce: the same effect at most every 60 ms (no machine-gun stacking).
            if (now - (lastPlayed[sfx] ?: 0L) < MIN_GAP_MILLIS) return
            lastPlayed[sfx] = now
            val v = (volume * volume * sfx.gain).coerceIn(0f, 1f) // squared: perceptually even slider
            p.play(id, v, v, 1, 0, 1f)
        }
    }

    private fun createPool() {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val p = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
            p.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) synchronized(this) { loaded += sampleId }
            }
            Sfx.entries.forEach { soundIds[it] = p.load(context, it.resId, 1) }
            pool = p
        }
    }

    private fun release() {
        runCatching { pool?.release() }
        pool = null
        soundIds.clear()
        loaded.clear()
    }

    companion object {
        private const val MIN_GAP_MILLIS = 60L

        @Volatile
        private var instance: SoundManager? = null

        fun get(context: Context): SoundManager = instance ?: synchronized(this) {
            instance ?: SoundManager(context.applicationContext).also { instance = it }
        }
    }
}
