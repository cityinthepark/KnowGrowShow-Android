package com.stoneofhope.stoneofhope

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stoneofhope.stoneofhope.ui.theme.KnowGrowShowThemeSurface
import com.stoneofhope.stoneofhope.ui.welcome.WelcomeScreen

private object Routes {
    const val Welcome = "welcome"
    const val Home = "home"
}

@Composable
fun KnowGrowShowApp() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = KnowGrowShowThemeSurface
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Welcome
        ) {
            composable(Routes.Welcome) {
                WelcomeScreen(
                    onContinueLocal = { navController.navigate(Routes.Home) {
                        popUpTo(Routes.Welcome) { inclusive = true }
                    } },
                    onContinueRegister = { navController.navigate(Routes.Home) {
                        popUpTo(Routes.Welcome) { inclusive = true }
                    } }
                )
            }
            composable(Routes.Home) {
                HomePlaceholderScreen()
            }
        }
    }
}
