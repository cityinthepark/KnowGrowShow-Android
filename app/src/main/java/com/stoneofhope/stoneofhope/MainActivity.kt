package com.stoneofhope.stoneofhope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stoneofhope.stoneofhope.ui.theme.KnowGrowShowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KnowGrowShowTheme {
                KnowGrowShowApp()
            }
        }
    }
}
