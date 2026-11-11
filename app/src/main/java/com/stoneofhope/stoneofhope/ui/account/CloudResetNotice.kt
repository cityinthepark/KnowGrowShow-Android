package com.stoneofhope.stoneofhope.ui.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.sync.CloudAccountFlags
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * iOS `AppRootView` alert bound to `showCloudResetSignedOutNotice`: shown whenever the flag is set
 * (anywhere in the app, including right after a sync run set it) and cleared when the user taps OK.
 */
@HiltViewModel
class CloudResetNoticeViewModel @Inject constructor(private val flags: CloudAccountFlags) : ViewModel() {

    val visible: StateFlow<Boolean> = flags.showCloudResetNoticeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, flags.showCloudResetNotice)

    fun dismiss() {
        flags.showCloudResetNotice = false
    }
}

@Composable
fun CloudResetNoticeDialog(viewModel: CloudResetNoticeViewModel = hiltViewModel()) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    if (!visible) return
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text(stringResource(R.string.cloud_reset_title)) },
        text = { Text(stringResource(R.string.cloud_reset_message)) },
        confirmButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}
