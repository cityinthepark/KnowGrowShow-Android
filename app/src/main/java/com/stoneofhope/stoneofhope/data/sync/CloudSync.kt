package com.stoneofhope.stoneofhope.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Port of iOS `CloudSync` + `SyncRunner` (FIRESTORE_SYNC_CONTRACT §5.3). The only entry point for
 * cloud sync; screens and triggers never call pull / push directly.
 *
 * | Trigger                                  | Call         |
 * | ---------------------------------------- | ------------ |
 * | back to foreground, network regained     | [autoSync]   |
 * | cold start, sign-in, pull-to-refresh     | [fullSync]   |
 * | leaving the app                          | [push]       |
 *
 * Runs never overlap. A request that hasn't started yet absorbs later requests for the same
 * account, upgraded to the strongest kind asked for.
 */
@Singleton
class CloudSync @Inject constructor(
    private val identity: SyncIdentity,
    private val network: NetworkMonitor,
    private val flags: CloudAccountFlags,
    private val state: SyncStateStore,
    private val session: CryptoSession,
    private val puller: SyncPuller,
    private val pusher: SyncPusher,
    private val peers: SyncPeerRegistry,
    private val epoch: AccountEpoch,
    private val ops: LocalSyncOps,
    private val db: AppDatabase,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    /** Ordered by strength: a queued request is upgraded, never downgraded. */
    enum class Kind { Push, Auto, Full }

    private class Queued(val ownerUid: String, var kind: Kind) {
        lateinit var job: Job
    }

    private val lock = Mutex()
    private var queued: Queued? = null
    private var tail: Job? = null
    private val scheduled = mutableSetOf<Job>()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Pull only if another endpoint changed, then push. */
    suspend fun autoSync(ownerUid: String) = request(Kind.Auto, ownerUid)

    /** Always pull (restore / cross-device safety net), then push. */
    suspend fun fullSync(ownerUid: String) = request(Kind.Full, ownerUid)

    /** Upload pending local changes only. */
    suspend fun push(ownerUid: String) = request(Kind.Push, ownerUid)

    /** Runs (or joins) a sync and returns when the run covering this request ends. */
    private suspend fun request(kind: Kind, ownerUid: String) {
        val job = lock.withLock {
            queued?.takeIf { it.ownerUid == ownerUid }?.let { pending ->
                if (kind > pending.kind) pending.kind = kind
                return@withLock pending.job
            }
            val entry = Queued(ownerUid, kind)
            val previous = tail
            entry.job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    previous?.join()
                    val run = lock.withLock { entry.takeIf { queued === it }?.also { queued = null } } ?: return@launch
                    execute(run.kind, run.ownerUid)
                } finally {
                    withContext(NonCancellable) { lock.withLock { scheduled.remove(entry.job) } }
                }
            }
            queued = entry
            tail = entry.job
            scheduled += entry.job
            entry.job.start()
            entry.job
        }
        job.join()
    }

    /**
     * Cancels queued and running syncs and waits (up to [timeoutMs]) for them to unwind. Call before
     * wiping cloud data or signing out; [SyncGuard] stops late wakers from writing afterwards.
     */
    suspend fun cancelAndWait(timeoutMs: Long = 8_000) {
        val jobs = lock.withLock {
            queued = null
            scheduled.toList()
        }
        jobs.forEach { it.cancel() }
        withTimeoutOrNull(timeoutMs) { jobs.joinAll() }
    }

    private suspend fun execute(kind: Kind, ownerUid: String) {
        val guard = SyncGuard(ownerUid, identity)
        if (!guard.isValid || !network.isOnline.value) return
        _isSyncing.value = true
        try {
            when (kind) {
                Kind.Push -> runPush(ownerUid, guard)
                Kind.Auto -> runAutoSync(ownerUid, guard)
                Kind.Full -> runFullSync(ownerUid, guard)
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "$kind sync stopped: ${e.message}")
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun runAutoSync(ownerUid: String, guard: SyncGuard) {
        if (reconcileAccountEpoch(ownerUid, guard)) return
        prepare(ownerUid)
        peers.peerUpdateRequiringPull(ownerUid)?.let { peerWatermark ->
            // Mark peers synced only after a complete pull, so a failed / held-back pull retries.
            if (pull(ownerUid, guard)) peers.markPeersSynced(ownerUid, peerWatermark)
        }
        runPush(ownerUid, guard)
    }

    private suspend fun runFullSync(ownerUid: String, guard: SyncGuard) {
        if (reconcileAccountEpoch(ownerUid, guard)) return
        prepare(ownerUid)
        if (pull(ownerUid, guard)) peers.refreshPeerWatermark(ownerUid)
        runPush(ownerUid, guard)
    }

    private suspend fun runPush(ownerUid: String, guard: SyncGuard) {
        val result = bestEffort("push") { pusher.push(ownerUid, guard) } ?: return
        // Stamp this device only when something was really uploaded, so peers pull for our edits
        // but a no-op push never makes anyone pull.
        if (result.documentsUploaded > 0) peers.recordLocalUpdate(ownerUid)
    }

    /** True when the pull completed (cursor advanced, nothing held back). */
    private suspend fun pull(ownerUid: String, guard: SyncGuard): Boolean =
        bestEffort("pull") { puller.pull(ownerUid, guard) }?.complete == true

    /** Signed-in users need `note_key` before note bodies can move; device metadata stays fresh. */
    private suspend fun prepare(ownerUid: String) {
        session.ensureUnlocked(ownerUid)
        peers.ensureCurrentDeviceRegistered(ownerUid)
    }

    /**
     * iOS L5/K3: if the cloud was wiped (another device signed out of the cloud) since this device last
     * synced, this device signs out too and KEEPS its local data, marked for re-upload. Returns true
     * when that happened — the run must stop.
     */
    private suspend fun reconcileAccountEpoch(ownerUid: String, guard: SyncGuard): Boolean {
        if (epoch.reconcile(ownerUid, guard) != AccountEpoch.Outcome.ResetDetected) return false
        Log.i(TAG, "Cloud reset detected — signing out locally, keeping local data")
        db.withTransaction {
            ops.purgeAllTombstonesForCloudWipe()
            ops.markAllUserScopedDataForResync()
        }
        session.lock(clearCache = true)
        state.clearAccountEpoch(ownerUid)
        state.resetPullCursor(ownerUid)
        state.resetPeerWatermark(ownerUid)
        flags.hasLinkedCloudAccount = false
        identity.signOut()
        flags.isCloudSyncPaused = false
        flags.showCloudResetNotice = true
        return true
    }

    /** Pull / push failures are logged and the run continues, like iOS (`do/catch` + `NSLog`). */
    private suspend fun <T> bestEffort(label: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$label failed", e)
        null
    }

    private companion object {
        const val TAG = "CloudSync"
    }
}
