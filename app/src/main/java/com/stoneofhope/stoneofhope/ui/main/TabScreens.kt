package com.stoneofhope.stoneofhope.ui.main

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.data.sermon.ScriptCaptureDuration
import com.stoneofhope.stoneofhope.data.settings.FontSizePreference
import com.stoneofhope.stoneofhope.data.settings.ThemePreference
import com.stoneofhope.stoneofhope.ui.settings.OnlineResourcesViewModel
import com.stoneofhope.stoneofhope.ui.settings.SettingsViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.BuildConfig
import com.stoneofhope.stoneofhope.R

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
fun PlaceholderTab(tab: MainTab) {
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(tab.title))
        ComingSoon()
    }
}

/** iOS `SettingsView`: Online Resources, App Settings, Legal, then the account row (Sync conflicts come later). */
@Composable
fun SettingsTab(
    onOpenAccount: () -> Unit,
    onOpenChurch: (churchKey: String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    online: OnlineResourcesViewModel = hiltViewModel(),
) {
    val churches by online.churches.collectAsStateWithLifecycle()
    val primaryKey by online.primaryKey.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val showOnLaunch by viewModel.showOnLaunch.collectAsStateWithLifecycle()
    val seconds by viewModel.scriptSeconds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TabHeader(stringResource(R.string.tab_settings))
        // iOS shows one hard-coded Riverside row; here every enabled church in the catalog.
        if (churches.isNotEmpty()) {
            SectionTitle(stringResource(R.string.online_resources))
            churches.forEach { church ->
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(church.name, modifier = Modifier.weight(1f, fill = false))
                            if (church.churchKey == primaryKey) PrimaryBadge()
                        }
                    },
                    supportingContent = church.subtitle?.let { { Text(it) } },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.testTag("church_${church.churchKey}").clickable { onOpenChurch(church.churchKey) },
                )
            }
        }
        SectionTitle(stringResource(R.string.settings_app))
        ChoiceRow(
            stringResource(R.string.settings_theme), "settingsTheme",
            options = ThemePreference.entries.map { it to stringResource(it.title()) }, selected = theme, onSelect = viewModel::setTheme,
        )
        ChoiceRow(
            stringResource(R.string.settings_font_size), "settingsFontSize",
            options = FontSizePreference.entries.map { it to stringResource(it.title()) }, selected = fontSize, onSelect = viewModel::setFontSize,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_show_verse)) },
            trailingContent = { Switch(checked = showOnLaunch, onCheckedChange = viewModel::setShowOnLaunch, modifier = Modifier.testTag("settingsShowVerse")) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_script_duration)) },
            supportingContent = { Text(stringResource(R.string.settings_script_footer)) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.setScriptSeconds(seconds - 1) }, enabled = seconds > ScriptCaptureDuration.MIN, modifier = Modifier.testTag("scriptMinus")) { Icon(Icons.Filled.Remove, null) }
                    Text(pluralStringResource(R.plurals.settings_script_value, seconds, seconds), modifier = Modifier.testTag("scriptValue"))
                    IconButton(onClick = { viewModel.setScriptSeconds(seconds + 1) }, enabled = seconds < ScriptCaptureDuration.MAX, modifier = Modifier.testTag("scriptPlus")) { Icon(Icons.Filled.Add, null) }
                }
            },
        )
        SectionTitle(stringResource(R.string.settings_legal))
        LinkRow(stringResource(R.string.settings_privacy), "settingsPrivacy") { open(LegalUrls.PRIVACY) }
        LinkRow(stringResource(R.string.settings_terms), "settingsTerms") { open(LegalUrls.TERMS) }
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

private fun ThemePreference.title() = when (this) {
    ThemePreference.System -> R.string.theme_system
    ThemePreference.Light -> R.string.theme_light
    ThemePreference.Dark -> R.string.theme_dark
}

private fun FontSizePreference.title() = when (this) {
    FontSizePreference.Small -> R.string.font_small
    FontSizePreference.Medium -> R.string.font_medium
    FontSizePreference.Large -> R.string.font_large
    FontSizePreference.ExtraLarge -> R.string.font_extra_large
}

@Composable
private fun PrimaryBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(start = 8.dp).testTag("primaryBadge"),
    ) {
        Text(
            stringResource(R.string.online_primary),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun LinkRow(title: String, tag: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, modifier = Modifier.testTag(tag).clickable(onClick = onClick))
}

/** A row that shows the current choice and opens a menu of the others (iOS `Picker`). */
@Composable
private fun <T> ChoiceRow(title: String, tag: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(title) },
            trailingContent = { Text(options.firstOrNull { it.first == selected }?.second.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.testTag(tag).clickable { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onSelect(value) }, modifier = Modifier.testTag("${tag}_$label"))
            }
        }
    }
}

/** iOS `LegalURLs` (the non-China build). */
private object LegalUrls {
    const val PRIVACY = "https://stoneofhope.app/privacy.html"
    const val TERMS = "https://stoneofhope.app/terms.html"
}
