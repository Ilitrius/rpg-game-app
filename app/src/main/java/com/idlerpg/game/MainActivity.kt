package com.idlerpg.game

import android.graphics.Color
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import java.util.Locale
import com.idlerpg.game.ui.options.GameSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.idlerpg.game.audio.SoundManager
import com.idlerpg.game.ui.MainScreen
import com.idlerpg.game.update.InAppUpdater
import com.idlerpg.game.ui.options.BackupReminderDialog
import com.idlerpg.game.ui.theme.IdleRPGTheme

class MainActivity : ComponentActivity() {
    // Created during construction: registers an activity-result launcher (Play in-app update).
    private val updater = InAppUpdater(this)

    // Sound plays only while the activity is visible (SoundPool is released in the background).
    private val soundLifecycle = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = SoundManager.get(this@MainActivity).onForeground()
        override fun onStop(owner: LifecycleOwner) = SoundManager.get(this@MainActivity).onBackground()
    }

    override fun attachBaseContext(newBase: Context) {
        val tag = runCatching { GameSettings.get(newBase).language.tag }.getOrNull()
        if (tag == null) {
            // "System": restore the device locale (an earlier explicit choice changed Locale.getDefault()).
            Locale.setDefault(newBase.resources.configuration.locales[0])
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(soundLifecycle)
        // Game is always dark-themed, so force light system bar icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            IdleRPGTheme {
                MainScreen(updater = updater)
                BackupReminderDialog()
            }
        }
    }
}
