package com.idlerpg.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9A227),
    onPrimary = Color(0xFF2B1D05),
    secondary = Color(0xFF8D6E63),
    background = Color(0xFF1B1F1A),
    surface = Color(0xFF262B25),
)

@Composable
fun IdleRPGTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
