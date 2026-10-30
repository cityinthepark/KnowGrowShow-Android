package com.stoneofhope.stoneofhope.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

/** The three texts iOS `BibleVersionCopyrightInfo` keeps for a version: about, how it is provided, copyright. */
class VersionCopyrightText(@StringRes val about: Int, @StringRes val howProvided: Int, @StringRes val notice: Int)

object VersionCopyright {
    fun of(abbr: String): VersionCopyrightText? = when (abbr.uppercase()) {
        "KJV" -> VersionCopyrightText(R.string.bible_copyright_kjv_about, R.string.bible_copyright_kjv_how_provided, R.string.bible_copyright_kjv_notice)
        "CUV" -> VersionCopyrightText(R.string.bible_copyright_cuv_about, R.string.bible_copyright_cuv_how_provided, R.string.bible_copyright_cuv_notice)
        "NIV" -> VersionCopyrightText(R.string.bible_copyright_niv_about, R.string.bible_copyright_niv_how_provided, R.string.bible_copyright_niv_notice)
        "NKJV" -> VersionCopyrightText(R.string.bible_copyright_nkjv_about, R.string.bible_copyright_nkjv_how_provided, R.string.bible_copyright_nkjv_notice)
        else -> null
    }
}

/** iOS `BibleVersionCopyrightView`. Argument: the version's abbreviation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleVersionCopyrightScreen(abbr: String, onBack: () -> Unit) {
    val version = BibleVersions.find(abbr)
    val text = VersionCopyright.of(abbr)
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("versionCopyright")) {
        TopAppBar(
            title = { Text(version?.displayName ?: abbr) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0),
        )
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            version?.let { Text(it.name, style = textStyle(20, weight = FontWeight.SemiBold), color = AppTheme.colors.neutral100) }
            if (text != null) {
                Section(R.string.bible_copyright_section_about, text.about)
                Section(R.string.bible_copyright_section_how_provided, text.howProvided)
                Section(R.string.bible_copyright_section_copyright, text.notice)
            }
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, @StringRes body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = textStyle(13, weight = FontWeight.SemiBold), color = AppTheme.colors.neutral50)
        Text(stringResource(body), style = textStyle(15), color = AppTheme.colors.neutral100)
    }
}
