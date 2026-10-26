package com.stoneofhope.stoneofhope.data.account

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import com.stoneofhope.stoneofhope.data.sync.CloudAccountFlags
import com.stoneofhope.stoneofhope.data.sync.CloudSync
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Sign-in / sign-out (iOS `SocialRegistrationService` + parts of `CloudAccountService`).
 *
 * Signing in only flips the account flags; `SyncTriggers` sees the auth change and runs the full
 * sync (pull this account, then upload local rows — local-first data is merged by stable ids).
 *
 * Today: email/password, used by the debug demo sign-in (iOS `linkOrSignInDemoFromSchemeEnvironment`).
 * Google / Apple come with the production `google-services.json`.
 */
@Singleton
class AccountService @Inject constructor(
    private val bootstrap: FirebaseBootstrap,
    private val flags: CloudAccountFlags,
    private val cloudSync: CloudSync,
    private val session: CryptoSession,
) {
    data class SignedInUser(val uid: String, val email: String?)

    private val _user = MutableStateFlow<SignedInUser?>(null)
    val user: StateFlow<SignedInUser?> = _user.asStateFlow()

    private var listening = false

    /** Starts mirroring Firebase's current user into [user]. Safe to call repeatedly. */
    fun observe() {
        if (listening || !bootstrap.isConfigured) return
        listening = true
        bootstrap.auth.addAuthStateListener(FirebaseAuth.AuthStateListener { auth ->
            _user.value = auth.currentUser?.let { SignedInUser(it.uid, it.email) }
        })
    }

    /**
     * Email/password sign-in. With [createIfMissing] (Firebase emulator only) a missing account is
     * created, so a fresh emulator needs no manual setup.
     */
    suspend fun signInWithEmail(email: String, password: String, createIfMissing: Boolean) {
        val auth = bootstrap.auth
        try {
            auth.signInWithEmailAndPassword(email, password).await()
        } catch (e: Exception) {
            // Missing user surfaces as InvalidUser, or as InvalidCredentials when email
            // enumeration protection is on.
            val maybeMissing = e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException
            if (!createIfMissing || !maybeMissing) throw e
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
            } catch (collision: FirebaseAuthUserCollisionException) {
                throw e // the account exists: the original error (wrong password) is the real one
            }
        }
        completeSignIn()
    }

    /**
     * Local sign-out that keeps this device's data (and its sync bookkeeping, so signing back into the
     * same account resumes incrementally). Not iOS "登出云": that also wipes the cloud copy and needs
     * the `endCloudSession` Cloud Function.
     */
    suspend fun signOutKeepingLocalData() {
        cloudSync.cancelAndWait()
        session.lock(clearCache = true)
        flags.hasLinkedCloudAccount = false
        bootstrap.auth.signOut()
    }

    private fun completeSignIn() {
        flags.hasLinkedCloudAccount = true
        flags.isCloudSyncPaused = false
    }
}
