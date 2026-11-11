package com.stoneofhope.stoneofhope.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Sell
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.ui.settings.displayString
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.R

@HiltViewModel
class ProfileViewModel @Inject constructor(private val versions: BibleVersionPreferences) : ViewModel() {
    /** Read each time the page opens, so a change on the versions page shows when you come back. */
    val versionsLabel: String get() = versions.displayString()
}

/** iOS `ProfileView` (the Today avatar): my tags, the Bible versions, and the Account section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenTags: () -> Unit, onOpenVersions: () -> Unit, onOpenAccount: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("profile")) {
        TopAppBar(
            title = { Text(stringResource(R.string.today_profile)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.tags_my)) },
            leadingContent = { Icon(Icons.Outlined.Sell, null) },
            trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
            modifier = Modifier.testTag("profileTags").clickable(onClick = onOpenTags),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.profile_bible_version)) },
            supportingContent = { Text(viewModel.versionsLabel) },
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
            trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
            modifier = Modifier.testTag("profileVersions").clickable(onClick = onOpenVersions),
        )
        // iOS shows this section only when `AppDistribution.accountSignInEnabled`; Android always has sign-in.
        Text(
            stringResource(R.string.settings_account_sync),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_account_sync)) },
            leadingContent = { Icon(Icons.Outlined.CloudSync, null) },
            trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
            modifier = Modifier.testTag("openAccount").clickable(onClick = onOpenAccount),
        )
    }
}
