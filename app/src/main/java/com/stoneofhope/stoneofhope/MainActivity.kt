package com.stoneofhope.stoneofhope

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stoneofhope.stoneofhope.ui.sermon.IncomingShare
import com.stoneofhope.stoneofhope.ui.theme.KnowGrowShowTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.stoneofhope.stoneofhope.ui.effects.LocalTransformEffects
import com.stoneofhope.stoneofhope.ui.effects.TransformEffectController
import com.stoneofhope.stoneofhope.ui.effects.TransformEffectOverlay
import com.stoneofhope.stoneofhope.ui.effects.animationsOff
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.data.settings.AppearancePreferences
import com.stoneofhope.stoneofhope.data.settings.ThemePreference
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appearance: AppearancePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        IncomingShare.offer(intent)
        setContent {
            val theme by appearance.theme.collectAsStateWithLifecycle()
            val fontSize by appearance.fontSize.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemePreference.System -> isSystemInDarkTheme()
                ThemePreference.Light -> false
                ThemePreference.Dark -> true
            }
            // iOS sets a Dynamic Type size on the root; here the app's text scale multiplies the system one.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * fontSize.scale)) {
                KnowGrowShowTheme(darkTheme = dark) {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    val haptics = LocalHapticFeedback.current
                    val effects = remember {
                        TransformEffectController(
                            scope, reduceMotion = { animationsOff(context) },
                            onThump = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onSettled = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        )
                    }
                    CompositionLocalProvider(LocalTransformEffects provides effects) {
                        Box(Modifier.fillMaxSize()) {
                            KnowGrowShowApp()
                            TransformEffectOverlay(effects)
                        }
                    }
                }
            }
        }
    }

    /** A link shared from YouTube while the app is open (single-task activity). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        IncomingShare.offer(intent)
    }
}
