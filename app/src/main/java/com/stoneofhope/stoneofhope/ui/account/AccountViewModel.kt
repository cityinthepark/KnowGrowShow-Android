package com.stoneofhope.stoneofhope.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.BuildConfig
import com.stoneofhope.stoneofhope.data.account.AccountService
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import com.stoneofhope.stoneofhope.data.repository.NoteActionRepository
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.settings.OnboardingPreferences
import com.stoneofhope.stoneofhope.data.sync.CloudSync
import com.stoneofhope.stoneofhope.data.sync.SyncIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val account: AccountService,
    private val bootstrap: FirebaseBootstrap,
    private val cloudSync: CloudSync,
    private val identity: SyncIdentity,
    private val noteRepository: NoteRepository,
    private val noteActions: NoteActionRepository,
    private val onboarding: OnboardingPreferences,
    session: CryptoSession,
) : ViewModel() {

    init {
        account.observe()
    }

    val user = account.user
    val isSyncing = cloudSync.isSyncing
    val unlockedUid = session.unlockedUid
    val notes: StateFlow<List<NoteEntity>> =
        noteRepository.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** False in "this device only" mode: Firebase was never started. */
    val cloudEnabled: StateFlow<Boolean> = bootstrap.configured

    val firebaseMode: String
        get() = when {
            !bootstrap.configured.value -> "This device only — notes stay on this phone"
            bootstrap.emulator != null -> bootstrap.emulator.let { "Firebase emulator · ${it.host} · ${it.projectId}" }
            else -> "Firebase production"
        }
    val demoEmail: String = BuildConfig.DEMO_AUTH_EMAIL
    val demoPassword: String = BuildConfig.DEMO_AUTH_PASSWORD

    var message by mutableStateOf<String?>(null)
        private set

    /** A local-only user opting into cloud sync later (iOS: register from Account settings). */
    fun turnOnCloudSync() = launchReporting(null) {
        onboarding.optIntoCloud()
        check(bootstrap.configureIfNeeded()) { "Cloud sync isn't available in this build (no google-services.json)" }
        account.observe()
    }

    fun signIn(email: String, password: String) = launchReporting("Signed in") {
        account.signInWithEmail(email.trim(), password, createIfMissing = bootstrap.emulator != null)
    }

    fun syncNow() = launchReporting("Sync finished") {
        val uid = identity.currentAuthUid ?: error("Not signed in")
        cloudSync.fullSync(uid)
    }

    fun signOut() = launchReporting("Signed out (local data kept)") { account.signOutKeepingLocalData() }

    fun addNote(text: String) = launchReporting(null) {
        if (text.isNotBlank()) noteRepository.saveNoteFromContent(text, bibleRefText = null)
    }

    fun delete(note: NoteEntity) = launchReporting(null) { noteActions.delete(note.id) }

    private fun launchReporting(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            message = try {
                block()
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
        }
    }
}
