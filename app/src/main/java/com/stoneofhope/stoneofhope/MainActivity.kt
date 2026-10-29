package com.stoneofhope.stoneofhope

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stoneofhope.stoneofhope.ui.sermon.IncomingShare
import com.stoneofhope.stoneofhope.ui.theme.KnowGrowShowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        IncomingShare.offer(intent)
        setContent {
            KnowGrowShowTheme {
                KnowGrowShowApp()
            }
        }
    }

    /** A link shared from YouTube while the app is open (single-task activity). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        IncomingShare.offer(intent)
    }
}
