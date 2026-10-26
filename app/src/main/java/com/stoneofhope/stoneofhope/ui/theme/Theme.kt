package com.stoneofhope.stoneofhope.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Material roles mapped onto the iOS tokens. iOS uses the default system accent (blue) for
// buttons, links and the selected tab, so `primary` is iOS system blue.
private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = AppColors.Light.blue20,
    onPrimaryContainer = AppColors.Light.blue100,
    secondary = AppColors.Light.midGreen100,
    secondaryContainer = AppColors.Light.lightGreen40,
    onSecondaryContainer = AppColors.Light.midGreen100,
    background = AppColors.Light.pageBackground,
    onBackground = AppColors.Light.neutral100,
    surface = AppColors.Light.pageBackground,
    onSurface = AppColors.Light.neutral100,
    surfaceContainerLow = AppColors.Light.surface0,
    surfaceContainer = AppColors.Light.surface0,
    onSurfaceVariant = AppColors.Light.neutral90,
    outlineVariant = AppColors.Light.neutral20,
    error = AppColors.Light.red80,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = AppColors.Dark.blue20,
    onPrimaryContainer = AppColors.Dark.blue100,
    secondary = AppColors.Dark.midGreen100,
    secondaryContainer = AppColors.Dark.lightGreen40,
    onSecondaryContainer = AppColors.Dark.midGreen100,
    background = AppColors.Dark.pageBackground,
    onBackground = AppColors.Dark.neutral100,
    surface = AppColors.Dark.pageBackground,
    onSurface = AppColors.Dark.neutral100,
    surfaceContainerLow = AppColors.Dark.surface0,
    surfaceContainer = AppColors.Dark.surface0,
    onSurfaceVariant = AppColors.Dark.neutral90,
    outlineVariant = AppColors.Dark.neutral20,
    error = AppColors.Dark.red80,
)

val KnowGrowShowThemeSurface
    @Composable get() = MaterialTheme.colorScheme.background

/** App-specific tokens beyond Material's colour roles. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

@Composable
fun KnowGrowShowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppColors provides if (darkTheme) AppColors.Dark else AppColors.Light) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
