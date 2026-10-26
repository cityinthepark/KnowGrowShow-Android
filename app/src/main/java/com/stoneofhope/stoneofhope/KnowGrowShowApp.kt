package com.stoneofhope.stoneofhope

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stoneofhope.stoneofhope.ui.ComingSoonScreen
import com.stoneofhope.stoneofhope.ui.RootViewModel
import com.stoneofhope.stoneofhope.ui.account.AccountScreen
import com.stoneofhope.stoneofhope.ui.main.MainScreen
import com.stoneofhope.stoneofhope.ui.reading.ReadingLogScreen
import com.stoneofhope.stoneofhope.ui.reading.ReadingLogViewModel
import com.stoneofhope.stoneofhope.ui.theme.KnowGrowShowThemeSurface
import com.stoneofhope.stoneofhope.ui.welcome.WelcomeScreen

private object Routes {
    const val Welcome = "welcome"
    const val Main = "main"
    const val Account = "account"
    const val ComingSoon = "comingSoon/{title}"
    const val ReadingLog = "readingLog/{${ReadingLogViewModel.ARG_BOOK_ORD}}"

    fun readingLog(bookOrd: Int) = "readingLog/$bookOrd"

    fun comingSoon(title: String) = "comingSoon/" + Uri.encode(title)
}

@Composable
fun KnowGrowShowApp(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    // Decided once per launch: the welcome flow only until the user has made a choice.
    val startDestination = remember { if (rootViewModel.showWelcome) Routes.Welcome else Routes.Main }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = KnowGrowShowThemeSurface
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(Routes.Welcome) {
                // Like iOS, both choices land on the tabs; cloud sign-in lives under Settings.
                WelcomeScreen(
                    onContinueLocal = {
                        rootViewModel.chooseLocalOnly()
                        navController.navigate(Routes.Main) { popUpTo(Routes.Welcome) { inclusive = true } }
                    },
                    onContinueRegister = {
                        rootViewModel.chooseCloud()
                        navController.navigate(Routes.Main) { popUpTo(Routes.Welcome) { inclusive = true } }
                    }
                )
            }
            composable(Routes.Main) {
                MainScreen(
                    onOpenAccount = { navController.navigate(Routes.Account) },
                    onOpenPlaceholder = { title -> navController.navigate(Routes.comingSoon(title)) },
                    onOpenReadingLog = { bookOrd -> navController.navigate(Routes.readingLog(bookOrd)) },
                )
            }
            composable(Routes.Account) {
                AccountScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.ReadingLog,
                arguments = listOf(navArgument(ReadingLogViewModel.ARG_BOOK_ORD) { type = NavType.IntType }),
            ) {
                ReadingLogScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChapter = { title -> navController.navigate(Routes.comingSoon(title)) },
                )
            }
            composable(Routes.ComingSoon) { entry ->
                ComingSoonScreen(title = entry.arguments?.getString("title").orEmpty(), onBack = { navController.popBackStack() })
            }
        }
    }
}
