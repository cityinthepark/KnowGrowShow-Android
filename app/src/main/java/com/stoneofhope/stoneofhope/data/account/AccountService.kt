package com.stoneofhope.stoneofhope.data.account

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.remote.NoteImageCloud
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import com.stoneofhope.stoneofhope.data.sync.CloudAccountFlags
import com.stoneofhope.stoneofhope.data.sync.CloudSync
import com.stoneofhope.stoneofhope.data.sync.NetworkMonitor
import com.stoneofhope.stoneofhope.data.sync.SyncIdentity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val remote: RemoteStore,
    private val images: NoteImageCloud,
    private val network: NetworkMonitor,
    private val identity: SyncIdentity,
    private val db: AppDatabase,
) {
    /** [providerIds] are Firebase's, e.g. `password`, `google.com`; [emails] every distinct address it knows. */
    data class SignedInUser(
        val uid: String,
        val email: String?,
        val providerIds: List<String> = emptyList(),
        val emails: List<String> = listOfNotNull(email),
    )

    enum class SignOutResult {
        Success,

        /** Nothing was changed: clearing the cloud backup needs a connection. */
        NeedsNetwork,

        /** Some cloud data is still there. Sync stays paused; try again later. */
        Incomplete,
    }

    private val _user = MutableStateFlow<SignedInUser?>(null)
    val user: StateFlow<SignedInUser?> = _user.asStateFlow()

    private var listening = false

    /** Starts mirroring Firebase's current user into [user]. Safe to call repeatedly. */
    fun observe() {
        if (listening || !bootstrap.configured.value) return
        listening = true
        bootstrap.auth.addAuthStateListener(FirebaseAuth.AuthStateListener { auth ->
            _user.value = auth.currentUser?.let(::signedInUser)
        })
    }

    private fun signedInUser(user: FirebaseUser): SignedInUser {
        val own = user.email?.trim().orEmpty()
        val all = (listOf(own) + user.providerData.map { it.email?.trim().orEmpty() }).filter { it.isNotEmpty() }
        return SignedInUser(
            uid = user.uid,
            email = user.email,
            providerIds = user.providerData.map { it.providerId }.filter { it != FIREBASE_PROVIDER },
            emails = all.distinctBy { it.lowercase() },
        )
    }

    /** True while cloud sync is paused ("登出云" unfinished): the Account page then looks signed out. */
    val isSyncPaused: Boolean get() = flags.isCloudSyncPaused

    /** A cloud sign-out that started but did not finish (failed, offline, or the app was killed). */
    val hasPendingWipe: Boolean get() = flags.pendingWipeUid != null

    /**
     * Email/password sign-in. With [createIfMissing] (Firebase emulator only) a missing account is
     * created, so a fresh emulator needs no manual setup.
     */
    suspend fun signInWithEmail(email: String, password: String, createIfMissing: Boolean) {
        settlePendingWipeBeforeSignIn()
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

    /**
     * iOS "登出云": the user stops using cloud sync. This device keeps all its data; the account's cloud
     * copy (every collection and every thumbnail) is deleted right away, then the session ends.
     *
     * Order matters (iOS L1/L2/L3): refuse while offline before changing anything, persist a "wipe in
     * progress" marker and pause sync, stop running syncs, clear the cloud and confirm it is empty by
     * server reads, and only then sign out. A failure leaves the marker, so it can be finished later.
     */
    suspend fun signOutOfCloud(): SignOutResult {
        flags.pendingWipeUid?.let { return continueSignOut(it) } // finish the earlier attempt
        val uid = identity.sessionUid ?: return SignOutResult.Success
        if (!network.isOnline.value) return SignOutResult.NeedsNetwork
        flags.pendingWipeUid = uid
        flags.isCloudSyncPaused = true
        cloudSync.cancelAndWait()
        return continueSignOut(uid)
    }

    private suspend fun continueSignOut(uid: String): SignOutResult {
        if (identity.sessionUid != uid) {
            // The session is gone and the security rules only let the signed-in uid delete its own data,
            // so the wipe cannot continue. Finish locally; what is left in the cloud merges back by id.
            cloudSync.finishLocalSignOut(uid)
            return SignOutResult.Success
        }
        if (!network.isOnline.value) return SignOutResult.NeedsNetwork
        if (!wipeCloud(uid)) return SignOutResult.Incomplete
        cloudSync.finishLocalSignOut(uid)
        return SignOutResult.Success
    }

    /** Every collection (checked empty by a server read), then every thumbnail. The note key stays: only a Cloud Function can delete it. */
    private suspend fun wipeCloud(uid: String): Boolean = coroutineScope {
        val collections = WIPED_COLLECTIONS.map { async { remote.deleteAllDocuments(uid, it) } }
        val thumbnails = async { images.deleteAllThumbnails(uid, db.noteDao().imageNoteIds()) }
        collections.awaitAll().all { it } && thumbnails.await()
    }

    /**
     * iOS L4: before any sign-in, finish or settle an unfinished sign-out so the new credential is a
     * clean sign-in and can never be linked into the previous account.
     */
    private suspend fun settlePendingWipeBeforeSignIn() {
        val pending = flags.pendingWipeUid
        if (pending == null && !flags.isCloudSyncPaused) return
        if (pending != null && network.isOnline.value) continueSignOut(pending)
        if (flags.pendingWipeUid == null && !flags.isCloudSyncPaused) return
        val uid = flags.pendingWipeUid ?: identity.sessionUid
        if (uid != null) cloudSync.finishLocalSignOut(uid) else flags.isCloudSyncPaused = false
    }

    private fun completeSignIn() {
        flags.hasLinkedCloudAccount = true
        flags.isCloudSyncPaused = false
    }

    private companion object {
        const val FIREBASE_PROVIDER = "firebase"

        /** Every `users/{uid}/…` collection the app writes, plus the device registry and the account epoch (iOS `userScopedCollections`). */
        val WIPED_COLLECTIONS = FirestoreSchema.pullOrder + listOf("devices", "meta")
    }
}
