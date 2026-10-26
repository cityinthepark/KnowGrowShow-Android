package com.stoneofhope.stoneofhope.data.sync

import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * iOS `SyncIdentity`: the single choke point every sync trigger reads. Null while signed out,
 * while Firebase isn't configured, or while cloud sync is paused ("登出云" in progress) even if
 * Firebase still has a cached user.
 */
interface SyncIdentity {
    val currentAuthUid: String?

    /** Local sign-out of the Firebase session (data on device is untouched). */
    fun signOut()
}

@Singleton
class FirebaseSyncIdentity @Inject constructor(
    private val bootstrap: FirebaseBootstrap,
    private val flags: CloudAccountFlags,
) : SyncIdentity {

    override val currentAuthUid: String?
        get() {
            if (!bootstrap.configured.value || flags.isCloudSyncPaused) return null
            return bootstrap.auth.currentUser?.uid?.takeIf { it.isNotEmpty() }
        }

    override fun signOut() {
        if (bootstrap.configured.value) bootstrap.auth.signOut()
    }
}
