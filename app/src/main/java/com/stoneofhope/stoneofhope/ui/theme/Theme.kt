package com.stoneofhope.stoneofhope.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5A45),
    secondary = Color(0xFF6B7F72)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB8A3),
    secondary = Color(0xFFB8C9BC)
)

val KnowGrowShowThemeSurface
    @Composable get() = MaterialTheme.colorScheme.background

@Composable
fun KnowGrowShowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
