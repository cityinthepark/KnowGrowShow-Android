package com.stoneofhope.stoneofhope.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.ui.today.TodayScreen

/** The five tabs of iOS `MainTabView`, same order. Icons are the closest Material equivalents. */
enum class MainTab(@param:StringRes val title: Int, val icon: ImageVector) {
    Today(R.string.tab_today, Icons.Outlined.Today),
    Series(R.string.tab_series, Icons.AutoMirrored.Outlined.MenuBook),
    Moments(R.string.tab_moments, Icons.Outlined.AutoAwesome),
    Calendar(R.string.tab_calendar, Icons.Outlined.CalendarMonth),
    Settings(R.string.tab_settings, Icons.Outlined.Settings),
}

/** Main app shell after onboarding (iOS `MainTabView`). */
@Composable
fun MainScreen(onOpenAccount: () -> Unit, onOpenPlaceholder: (title: String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(MainTab.Today) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.title)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.testTag("tab_${tab.name}"),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                MainTab.Today -> TodayScreen(onOpen = onOpenPlaceholder)
                MainTab.Series -> PlaceholderTab(MainTab.Series)
                MainTab.Moments -> PlaceholderTab(MainTab.Moments)
                MainTab.Calendar -> PlaceholderTab(MainTab.Calendar)
                MainTab.Settings -> SettingsTab(onOpenAccount)
            }
        }
    }
}
