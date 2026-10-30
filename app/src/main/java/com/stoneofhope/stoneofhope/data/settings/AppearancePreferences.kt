package com.stoneofhope.stoneofhope.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** iOS `AppThemePreference`: stored by its raw name. */
enum class ThemePreference(val raw: String) {
    System("system"), Light("light"), Dark("dark");

    companion object {
        /** An unknown value reads as the default, like iOS `?? .system`. */
        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: System
    }
}

/**
 * iOS `AppFontSizePreference`. iOS sets a Dynamic Type size on the root; here [scale] multiplies the
 * system font scale.
 */
enum class FontSizePreference(val raw: String, val scale: Float) {
    Small("small", 0.9f), Medium("medium", 1f), Large("large", 1.15f), ExtraLarge("extraLarge", 1.3f);

    companion object {
        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: Medium
    }
}

/** iOS `@AppStorage("appThemePreference")` and `("appFontSizePreference")`; device-local. */
@Singleton
class AppearancePreferences(private val prefs: SharedPreferences) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))

    private val _theme = MutableStateFlow(ThemePreference.of(prefs.getString(THEME_KEY, null)))
    val theme: StateFlow<ThemePreference> = _theme

    private val _fontSize = MutableStateFlow(FontSizePreference.of(prefs.getString(FONT_KEY, null)))
    val fontSize: StateFlow<FontSizePreference> = _fontSize

    fun setTheme(value: ThemePreference) {
        prefs.edit { putString(THEME_KEY, value.raw) }
        _theme.value = value
    }

    fun setFontSize(value: FontSizePreference) {
        prefs.edit { putString(FONT_KEY, value.raw) }
        _fontSize.value = value
    }

    companion object {
        const val THEME_KEY = "appThemePreference"
        const val FONT_KEY = "appFontSizePreference"
    }
}
