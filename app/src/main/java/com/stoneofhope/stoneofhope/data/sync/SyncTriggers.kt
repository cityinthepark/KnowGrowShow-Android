package com.stoneofhope.stoneofhope.data.sync

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Wires app events to [CloudSync] the way iOS `AppLaunchController` / `AppRootView` /
 * `SyncNetworkMonitor` / `CryptoGateController` do. Started once from the Application.
 */
@Singleton
class SyncTriggers @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val bootstrap: FirebaseBootstrap,
    private val identity: SyncIdentity,
    private val cloudSync: CloudSync,
    private val session: CryptoSession,
    private val network: NetworkMonitor,
    private val ops: LocalSyncOps,
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private var started = false
    private var coldStart = true
    private var lastAuthUid: String? = null

    fun start() {
        if (started) return
        started = true
        lastAuthUid = identity.currentAuthUid

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val full = coldStart
                coldStart = false
                scope.launch {
                    // Local and account-independent: a pinned note logs each day the app is opened.
                    db.withTransaction { ops.refreshTodayLedgerForPinnedNotes(clock.instant(), ZoneId.systemDefault()) }
                    val uid = identity.currentAuthUid ?: return@launch
                    if (full) cloudSync.fullSync(uid) else cloudSync.autoSync(uid)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                if (identity.currentAuthUid != null) PushWorker.enqueue(context)
            }
        })

        scope.launch {
            network.isOnline.drop(1).filter { it }.collect {
                identity.currentAuthUid?.let { uid -> cloudSync.autoSync(uid) }
            }
        }

        if (bootstrap.isConfigured) {
            bootstrap.auth.addAuthStateListener(FirebaseAuth.AuthStateListener { onAuthChanged() })
        }
    }

    /** Sign-in → full sync (pull this account's data, upload local rows). Switch / sign-out → drop the key. */
    private fun onAuthChanged() {
        val uid = identity.currentAuthUid
        val previous = lastAuthUid
        if (uid == previous) return
        lastAuthUid = uid
        if (previous != null) session.lock(clearCache = true)
        if (uid != null) scope.launch { cloudSync.fullSync(uid) }
    }
}
