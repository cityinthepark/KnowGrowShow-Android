package com.stoneofhope.stoneofhope.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.BuildConfig
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.account.AccountService
import com.stoneofhope.stoneofhope.data.account.RegisteredDevice
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/** iOS `AccountSettingsView`: signed out → register; signed in → sign-in method, email, linked devices, sign out of the cloud. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.isAnonymous.collectAsStateWithLifecycle()
    val hasPendingWipe by viewModel.hasPendingWipe.collectAsStateWithLifecycle()
    var showRegistration by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("accountScreen")) {
        TopAppBar(
            title = { Text(stringResource(R.string.account_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0),
        )
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isAnonymous) {
                SectionCard { Text(stringResource(R.string.account_local_only), Modifier.padding(16.dp)) }
                if (hasPendingWipe) {
                    SectionCard {
                        TextButton(
                            onClick = viewModel::signOutOfCloud,
                            enabled = !viewModel.isSigningOut,
                            modifier = Modifier.fillMaxWidth().testTag("retryCloudWipe"),
                        ) { ButtonLabel(stringResource(R.string.account_sign_out_cloud_retry), viewModel.isSigningOut) }
                    }
                    Footer(stringResource(R.string.account_sign_out_cloud_pending_footer))
                }
                SectionCard {
                    TextButton(onClick = { showRegistration = true }, modifier = Modifier.fillMaxWidth().testTag("registerButton")) {
                        Text(stringResource(R.string.account_register))
                    }
                }
                Footer(stringResource(R.string.account_register_footer))
            } else {
                val signedIn = user
                SectionCard {
                    LabeledRow(stringResource(R.string.account_sign_in_method), signedIn?.let { methodSummary(it) }.orEmpty(), "signInMethod")
                }
                SectionCard {
                    LabeledRow(
                        stringResource(R.string.account_email),
                        signedIn?.emails?.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: stringResource(R.string.account_email_none),
                        "accountEmail",
                    )
                }
                LinkedDevices(viewModel)
                SectionCard {
                    TextButton(
                        onClick = { confirmSignOut = true },
                        enabled = !viewModel.isSigningOut,
                        modifier = Modifier.fillMaxWidth().testTag("signOutCloud"),
                    ) {
                        ButtonLabel(stringResource(R.string.account_sign_out_cloud), viewModel.isSigningOut, destructive = true)
                    }
                }
                Footer(stringResource(R.string.account_sign_out_cloud_footer))
            }
            if (BuildConfig.DEBUG) DeveloperSection(viewModel, signedIn = !isAnonymous)
        }
    }

    if (showRegistration) RegisterSheet(viewModel, onDismiss = { showRegistration = false })

    if (confirmSignOut) {
        val others = viewModel.deviceList.collectAsStateWithLifecycle().value.any { !it.isCurrentDevice }
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.account_sign_out_cloud_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        if (others) R.string.account_sign_out_cloud_confirm_message_other else R.string.account_sign_out_cloud_confirm_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; viewModel.signOutOfCloud() }, modifier = Modifier.testTag("confirmSignOutCloud")) {
                    Text(stringResource(R.string.account_sign_out_cloud_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    viewModel.signOutProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = { viewModel.signOutProblem = null },
            title = { Text(stringResource(R.string.account_sign_out_cloud_problem_title)) },
            text = {
                Text(
                    stringResource(
                        if (problem == SignOutProblem.NeedsNetwork) R.string.account_sign_out_cloud_needs_network else R.string.account_sign_out_cloud_incomplete,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.signOutProblem = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun methodSummary(user: AccountService.SignedInUser): String {
    val parts = user.providerIds.distinct().map {
        when (it) {
            "google.com" -> stringResource(R.string.account_method_google)
            "apple.com" -> stringResource(R.string.account_method_apple)
            "password" -> stringResource(R.string.account_method_email)
            "phone" -> stringResource(R.string.account_method_phone)
            "anonymous" -> ""
            else -> it
        }
    }.filter { it.isNotEmpty() }
    return if (parts.isEmpty()) stringResource(R.string.account_method_signed_in) else parts.joinToString(" · ")
}

@Composable
private fun LinkedDevices(viewModel: AccountViewModel) {
    val devices by viewModel.deviceList.collectAsStateWithLifecycle()
    val loading by viewModel.devicesLoading.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.account_devices_section),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        SectionCard {
            Column(Modifier.testTag("linkedDevices")) {
                when {
                    loading && devices.isEmpty() -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.account_devices_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    devices.isEmpty() -> Text(stringResource(R.string.account_devices_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> devices.forEachIndexed { index, device ->
                        DeviceRow(device, viewModel)
                        if (index < devices.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
        Footer(stringResource(R.string.account_devices_footer))
    }

    viewModel.devicePendingRemoval?.let { device ->
        val label = deviceTitle(device).let { if (device.isCurrentDevice || device.isWeb) it else "$it · ${stringResource(R.string.account_device_id_suffix, device.idSuffix)}" }
        AlertDialog(
            onDismissRequest = { viewModel.devicePendingRemoval = null },
            title = { Text(stringResource(R.string.account_devices_remove_title)) },
            text = { Text(stringResource(R.string.account_devices_remove_message, label)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemoveDevice, enabled = !viewModel.isRemovingDevice, modifier = Modifier.testTag("confirmRemoveDevice")) {
                    Text(stringResource(R.string.account_devices_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.devicePendingRemoval = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    viewModel.devicesError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDevicesError,
            title = { Text(stringResource(R.string.account_devices_error_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissDevicesError) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun deviceTitle(device: RegisteredDevice): String = when {
    device.isWeb -> stringResource(R.string.account_device_web)
    device.isCurrentDevice -> stringResource(R.string.account_device_this)
    else -> device.modelIdentifier
}

@Composable
private fun DeviceRow(device: RegisteredDevice, viewModel: AccountViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("device_${device.deviceId}"), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (device.isCurrentDevice) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                    Text(
                        stringResource(R.string.account_device_this),
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Text(deviceTitle(device), style = MaterialTheme.typography.bodyLarge)
            }
            val subtitle = when {
                device.isWeb -> stringResource(R.string.account_device_web_subtitle)
                device.isCurrentDevice && device.systemVersion.isNotEmpty() -> "${device.modelIdentifier} · ${device.systemVersion}"
                device.isCurrentDevice -> device.modelIdentifier
                else -> device.systemVersion.ifEmpty { device.modelIdentifier }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!device.isWeb) {
                Text(
                    stringResource(R.string.account_device_id_suffix, device.idSuffix),
                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                device.lastUpdatedAt?.let { stringResource(R.string.account_device_last_upload, formatTimestamp(it)) }
                    ?: stringResource(R.string.account_device_last_upload_never),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (device.canRemove && device.isPossiblyInactive(viewModel.now)) {
                Text(stringResource(R.string.account_device_inactive), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        if (device.canRemove) {
            TextButton(onClick = { viewModel.devicePendingRemoval = device }, enabled = !viewModel.isRemovingDevice) {
                Text(stringResource(R.string.account_devices_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatTimestamp(at: Instant): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date.from(at))

/** iOS `AccountRegistrationSheet`. Google sign-in needs the production Firebase config (`default_web_client_id`); debug builds also get the email demo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterSheet(viewModel: AccountViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.prepareCloud() }
    val context = LocalContext.current
    val signedIn by viewModel.user.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.isAnonymous.collectAsStateWithLifecycle()
    LaunchedEffect(isAnonymous, signedIn) { if (!isAnonymous) onDismiss() }

    ModalBottomSheet(onDismissRequest = { if (!viewModel.isRegistering) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).testTag("registerSheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.account_register_sheet_title), style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = { viewModel.signInWithGoogle(context) },
                enabled = !viewModel.isRegistering,
                modifier = Modifier.fillMaxWidth().testTag("registerGoogle"),
            ) { Text(stringResource(R.string.account_register_google)) }

            if (BuildConfig.DEBUG) {
                var email by rememberSaveable { mutableStateOf(viewModel.demoEmail) }
                var password by rememberSaveable { mutableStateOf(viewModel.demoPassword) }
                Text("Developer demo (email)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.account_email)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("demoEmail"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text("Password") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("demoPassword"),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Button(
                    onClick = { viewModel.signInWithEmail(email, password) },
                    enabled = !viewModel.isRegistering && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("demoSignIn"),
                ) { Text("Developer demo (email)") }
            }
            if (viewModel.isRegistering) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.account_registering))
                }
            }
        }
    }
    val errorText = viewModel.registerErrorRes?.let { stringResource(it) } ?: viewModel.registerError
    if (errorText != null) {
        val close = { viewModel.registerError = null; viewModel.registerErrorRes = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.account_auth_error_title)) },
            text = { Text(errorText) },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** Debug builds only: which Firebase this build talks to, the note key, and a manual sync. */
@Composable
private fun DeveloperSection(viewModel: AccountViewModel, signedIn: Boolean) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val unlockedUid by viewModel.unlockedUid.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Developer (debug build)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        SectionCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(viewModel.firebaseMode, style = MaterialTheme.typography.bodyMedium)
                user?.let {
                    Text("uid: ${it.uid}", style = MaterialTheme.typography.bodySmall)
                    Text(if (unlockedUid == it.uid) "Note key: unlocked" else "Note key: locked", style = MaterialTheme.typography.bodySmall)
                }
                if (signedIn) {
                    OutlinedButton(onClick = viewModel::syncNow, enabled = !isSyncing, modifier = Modifier.testTag("syncButton")) { Text("Sync now") }
                }
                viewModel.syncMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun Footer(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun LabeledRow(label: String, value: String, tag: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Text(
            value, Modifier.weight(2f, fill = false).testTag(tag), textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ButtonLabel(text: String, busy: Boolean, destructive: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}
