package com.stoneofhope.stoneofhope.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.BuildConfig
import com.stoneofhope.stoneofhope.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Tab shells. Each tab's real content is ported from iOS in later steps; until then these show
// the tab title so the navigation can be tried end to end.

/** Large title like the iOS navigation title, with an optional trailing line (e.g. today's date). */
@Composable
private fun TabHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        trailing?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ComingSoon() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.tab_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TodayTab() {
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(R.string.tab_today), trailing = date)
        ComingSoon()
    }
}

@Composable
fun PlaceholderTab(tab: MainTab) {
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(tab.title))
        ComingSoon()
    }
}

@Composable
fun SettingsTab(onOpenAccount: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(R.string.tab_settings))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_account_sync)) },
            leadingContent = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
            trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.testTag("openAccount").clickable(onClick = onOpenAccount),
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            trailingContent = {
                Text(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}
