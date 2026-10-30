package com.stoneofhope.stoneofhope.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.bible.VersionToggle
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

/**
 * iOS `AvailableVersionsView`: versions grouped by language, a check circle to choose up to two (the
 * first is primary). Android's two bundled texts are always "built in"; NIV / NKJV are listed but can't
 * be chosen until API.Bible is wired. The per-version copyright pages are not ported yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleVersionsScreen(onBack: () -> Unit, viewModel: BibleVersionsViewModel = hiltViewModel()) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    var showIntro by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    val c = AppTheme.colors
    val groups = BibleVersions.available.groupBy { it.language }.toSortedMap(compareBy { it.ordinal })

    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("bibleVersions")) {
        TopAppBar(
            title = { Text(stringResource(R.string.versions_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            actions = { IconButton(onClick = { showIntro = true }) { Icon(Icons.Outlined.Info, stringResource(R.string.versions_info_title), tint = MaterialTheme.colorScheme.primary) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { (language, versions) ->
                item(key = "h$language") { Text(languageTitle(language), style = textStyle(13, weight = FontWeight.SemiBold), color = c.neutral50, modifier = Modifier.padding(top = 8.dp)) }
                versions.forEach { version ->
                    item(key = version.abbreviation) {
                        val isSelected = selected.any { it.equals(version.abbreviation, ignoreCase = true) }
                        val usable = BibleVersions.isSelectable(version.abbreviation)
                        Surface(shape = RoundedCornerShape(14.dp), color = c.surface0, modifier = Modifier.fillMaxWidth().testTag("version_${version.abbreviation}")) {
                            Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(version.displayName, style = textStyle(17, weight = FontWeight.SemiBold), color = c.neutral100)
                                    Text(version.name, style = textStyle(14), color = c.neutral50)
                                    Text(
                                        stringResource(if (usable) R.string.version_built_in else R.string.version_not_yet),
                                        style = textStyle(12), color = if (usable) MaterialTheme.colorScheme.primary else c.neutral50,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        notice = when (viewModel.toggle(version.abbreviation)) {
                                            VersionToggle.LimitReached -> R.string.versions_limit_message
                                            VersionToggle.Unavailable -> R.string.versions_unavailable_message
                                            is VersionToggle.Changed -> null
                                        }
                                    },
                                    modifier = Modifier.testTag("versionToggle_${version.abbreviation}"),
                                ) {
                                    Icon(
                                        if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                        stringResource(R.string.versions_title),
                                        tint = if (usable) MaterialTheme.colorScheme.primary else c.neutral50,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "footer") { Text(stringResource(R.string.versions_footer), style = textStyle(13), color = c.neutral90, modifier = Modifier.padding(top = 12.dp)) }
        }
    }

    if (showIntro) {
        AlertDialog(
            onDismissRequest = { showIntro = false },
            title = { Text(stringResource(R.string.versions_info_title)) },
            text = { Text(stringResource(R.string.versions_intro)) },
            confirmButton = { TextButton(onClick = { showIntro = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
    notice?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text(stringResource(if (message == R.string.versions_unavailable_message) R.string.versions_unavailable_title else R.string.versions_limit_title)) },
            text = { Text(stringResource(message), modifier = Modifier.testTag("versionsNotice")) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun languageTitle(language: BibleLanguage): String = stringResource(
    when (language) {
        BibleLanguage.English -> R.string.language_english
        BibleLanguage.ChineseSimplified -> R.string.language_chinese_simplified
        BibleLanguage.ChineseTraditional -> R.string.language_chinese_traditional
    },
)
