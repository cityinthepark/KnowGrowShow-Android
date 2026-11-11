package com.stoneofhope.stoneofhope.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.annotation.StringRes
import com.stoneofhope.stoneofhope.BuildConfig
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.account.AccountService
import com.stoneofhope.stoneofhope.data.account.DeviceRegistry
import com.stoneofhope.stoneofhope.data.account.GoogleCredentialSource
import com.stoneofhope.stoneofhope.data.account.SocialSignInException
import com.stoneofhope.stoneofhope.data.account.RegisteredDevice
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import com.stoneofhope.stoneofhope.data.settings.OnboardingPreferences
import com.stoneofhope.stoneofhope.data.sync.CloudSync
import com.stoneofhope.stoneofhope.data.sync.SyncIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What went wrong with "Sign Out of Cloud", for the alert (iOS `signOutProblem`). */
enum class SignOutProblem { NeedsNetwork, Incomplete }

/** iOS `AccountSettingsView` + `AccountRegistrationSheet`: who is signed in, the linked devices, signing out of the cloud. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val account: AccountService,
    private val google: GoogleCredentialSource,
    private val bootstrap: FirebaseBootstrap,
    private val cloudSync: CloudSync,
    private val identity: SyncIdentity,
    private val devices: DeviceRegistry,
    private val onboarding: OnboardingPreferences,
    private val clock: Clock,
    session: CryptoSession,
) : ViewModel() {

    init {
        if (bootstrap.configured.value) account.observe()
    }

    val user = account.user

    private val _paused = MutableStateFlow(account.isSyncPaused)
    private val _pendingWipe = MutableStateFlow(account.hasPendingWipe)

    /** No cloud session, or sync is paused by an unfinished "登出云": either way this is a local-only device. */
    val isAnonymous: StateFlow<Boolean> = combine(user, _paused) { u, paused -> u == null || paused }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val hasPendingWipe: StateFlow<Boolean> = _pendingWipe.asStateFlow()

    private val _devices = MutableStateFlow<List<RegisteredDevice>>(emptyList())
    val deviceList: StateFlow<List<RegisteredDevice>> = _devices.asStateFlow()
    private val _devicesLoading = MutableStateFlow(false)
    val devicesLoading: StateFlow<Boolean> = _devicesLoading.asStateFlow()

    /** Shown in the "Devices" alert. */
    var devicesError by mutableStateOf<String?>(null)
        private set
    var devicePendingRemoval by mutableStateOf<RegisteredDevice?>(null)
    var isRemovingDevice by mutableStateOf(false)
        private set

    var isSigningOut by mutableStateOf(false)
        private set
    var signOutProblem by mutableStateOf<SignOutProblem?>(null)

    /** Busy while a registration / sign-in runs, with the message for the "Could not register" alert. */
    var isRegistering by mutableStateOf(false)
        private set
    var registerError by mutableStateOf<String?>(null)

    /** Same alert, for failures that have a localized message (Google sign-in). */
    @get:StringRes var registerErrorRes by mutableStateOf<Int?>(null)

    val now: Instant get() = clock.instant()

    // Developer section (debug builds only).
    val unlockedUid = session.unlockedUid
    val isSyncing = cloudSync.isSyncing
    var syncMessage by mutableStateOf<String?>(null)
        private set
    val firebaseMode: String
        get() = when {
            !bootstrap.configured.value -> "Firebase not started"
            bootstrap.emulator != null -> "Firebase emulator · ${bootstrap.emulator.host} · ${bootstrap.emulator.projectId}"
            else -> "Firebase production"
        }
    val demoEmail: String = BuildConfig.DEMO_AUTH_EMAIL
    val demoPassword: String = BuildConfig.DEMO_AUTH_PASSWORD

    init {
        // Reload the linked devices whenever the signed-in account (or the paused state) changes.
        viewModelScope.launch {
            combine(user.map { it?.uid }, _paused) { uid, paused -> uid.takeIf { !paused } }
                .distinctUntilChanged()
                .collect { uid -> if (uid == null) _devices.value = emptyList() else loadDevices(uid) }
        }
    }

    /** Opening the register sheet starts Firebase (iOS `configureIfNeeded` on appear). */
    fun prepareCloud() {
        onboarding.optIntoCloud()
        if (bootstrap.configureIfNeeded()) account.observe()
    }

    /** Debug "Developer demo (email)": a missing emulator account is created on the spot. */
    fun signInWithEmail(email: String, password: String) = runRegistration {
        check(bootstrap.configured.value) { "Cloud sync isn't available in this build" }
        account.signInWithEmail(email.trim(), password, createIfMissing = bootstrap.emulator != null)
        refreshState()
    }

    /** "Continue with Google" (iOS `signInGoogle`). [activity] hosts Credential Manager's account picker. */
    fun signInWithGoogle(activity: Context) = runRegistration {
        check(bootstrap.configured.value) { "Cloud sync isn't available in this build" }
        account.linkOrSignIn(google.credential(activity))
        refreshState()
    }

    fun reloadDevices() {
        val uid = user.value?.uid ?: return
        if (!_paused.value) viewModelScope.launch { loadDevices(uid) }
    }

    private suspend fun loadDevices(uid: String) {
        _devicesLoading.value = true
        try {
            _devices.value = devices.list(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            devicesError = e.message ?: e.javaClass.simpleName
        } finally {
            _devicesLoading.value = false
        }
    }

    fun confirmRemoveDevice() {
        val device = devicePendingRemoval ?: return
        val uid = user.value?.uid ?: return
        viewModelScope.launch {
            isRemovingDevice = true
            try {
                devices.remove(uid, device)
                _devices.value = devices.list(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                devicesError = e.message ?: e.javaClass.simpleName
            } finally {
                isRemovingDevice = false
                devicePendingRemoval = null
            }
        }
    }

    fun dismissDevicesError() { devicesError = null }

    /** "Sign Out of Cloud": clears this account's cloud copy, keeps this device's data. */
    fun signOutOfCloud() {
        if (isSigningOut) return
        viewModelScope.launch {
            isSigningOut = true
            try {
                when (account.signOutOfCloud()) {
                    AccountService.SignOutResult.Success -> Unit
                    AccountService.SignOutResult.NeedsNetwork -> signOutProblem = SignOutProblem.NeedsNetwork
                    AccountService.SignOutResult.Incomplete -> signOutProblem = SignOutProblem.Incomplete
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                registerError = e.message ?: e.javaClass.simpleName
            } finally {
                isSigningOut = false
                refreshState()
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncMessage = try {
                cloudSync.fullSync(identity.currentAuthUid ?: error("Not signed in"))
                "Sync finished"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
        }
    }

    private fun refreshState() {
        _paused.value = account.isSyncPaused
        _pendingWipe.value = account.hasPendingWipe
        _devices.value = if (user.value == null || _paused.value) emptyList() else _devices.value
    }

    private fun runRegistration(block: suspend () -> Unit) {
        if (isRegistering) return
        viewModelScope.launch {
            isRegistering = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocialSignInException) {
                when (e.kind) {
                    SocialSignInException.Kind.Cancelled -> Unit // the user closed the picker
                    SocialSignInException.Kind.MissingConfig -> registerErrorRes = R.string.account_auth_config_missing
                    SocialSignInException.Kind.MissingToken -> registerErrorRes = R.string.account_auth_google_token_missing
                    SocialSignInException.Kind.NoUser -> registerErrorRes = R.string.account_auth_no_user
                }
            } catch (e: Exception) {
                registerError = e.message ?: e.javaClass.simpleName
            } finally {
                isRegistering = false
            }
        }
    }
}
