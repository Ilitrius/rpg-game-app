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
import com.idlerpg.game.ui.MainScreen
import com.idlerpg.game.ui.theme.IdleRPGTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val tag = runCatching { GameSettings.get(newBase).language.tag }.getOrNull()
        if (tag == null) {
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
        super.onCreate(savedInstanceState)
        // Game is always dark-themed, so force light system bar icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            IdleRPGTheme {
                MainScreen()
            }
        }
    }
}
