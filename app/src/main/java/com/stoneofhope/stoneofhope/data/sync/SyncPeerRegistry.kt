package com.stoneofhope.stoneofhope.data.sync

import android.util.Log
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.wire.WireValue
import com.stoneofhope.stoneofhope.data.remote.wire.instant
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Port of iOS `SyncPeerRegistry` + `DeviceRegistryService` (FIRESTORE_SYNC_CONTRACT §5): each
 * endpoint has `users/{uid}/devices/{deviceId}`, whose `lastUpdatedAt` moves **only** when that
 * endpoint actually uploaded changes. Auto sync pulls only when some *other* endpoint moved.
 */
@Singleton
class SyncPeerRegistry @Inject constructor(
    private val remote: RemoteStore,
    private val state: SyncStateStore,
) {
    /** Largest `lastUpdatedAt` across other endpoints; null if none or the read failed. */
    private suspend fun maxPeerUpdate(ownerUid: String): Instant? = try {
        val me = state.deviceId
        remote.listDevices(ownerUid).filter { it.id != me }.mapNotNull { it.data.instant("lastUpdatedAt") }.maxOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "maxPeerUpdate failed", e)
        null
    }

    /**
     * The peer watermark to advance to after pulling, or null to skip the pull. Doesn't change
     * state, so a failed pull can't suppress the next attempt.
     */
    suspend fun peerUpdateRequiringPull(ownerUid: String): Instant? {
        val maxPeer = maxPeerUpdate(ownerUid) ?: return null
        val known = state.lastKnownPeerUpdate(ownerUid)
        return maxPeer.takeIf { known == null || it > known }
    }

    fun markPeersSynced(ownerUid: String, upTo: Instant) = state.markPeersSynced(ownerUid, upTo)

    /** After an unconditional pull, catch the peer watermark up so the next auto sync doesn't re-pull. */
    suspend fun refreshPeerWatermark(ownerUid: String) {
        maxPeerUpdate(ownerUid)?.let { state.markPeersSynced(ownerUid, it) }
    }

    /** Keeps this device's display metadata current without bumping `lastUpdatedAt`. */
    suspend fun ensureCurrentDeviceRegistered(ownerUid: String) = bestEffort("ensureCurrentDeviceRegistered") {
        remote.mergeDevice(ownerUid, state.deviceId, deviceMetadata())
    }

    /** Call **only** when a push really uploaded something; otherwise peers would pull for nothing. */
    suspend fun recordLocalUpdate(ownerUid: String) = bestEffort("recordLocalUpdate") {
        remote.mergeDevice(ownerUid, state.deviceId, deviceMetadata() + ("lastUpdatedAt" to WireValue.ServerTimestamp))
    }

    private fun deviceMetadata() = mapOf(
        "deviceId" to state.deviceId,
        "modelIdentifier" to SyncStateStore.modelIdentifier,
        "systemVersion" to SyncStateStore.systemVersion,
    )

    private suspend fun bestEffort(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$label failed", e)
        }
    }

    private companion object {
        const val TAG = "SyncPeerRegistry"
    }
}

/** Port of iOS `AccountEpoch` (FIRESTORE_SYNC_CONTRACT §4.1): detects that the cloud copy was wiped. */
@Singleton
class AccountEpoch @Inject constructor(
    private val remote: RemoteStore,
    private val state: SyncStateStore,
) {
    enum class Outcome {
        /** Same epoch as last time. */
        Unchanged,

        /** First time this device sees the account — just remembered. */
        Adopted,

        /** The cloud was wiped / rebuilt since this device last synced. */
        ResetDetected,
    }

    /** Null when it couldn't be determined (offline / error); callers just carry on. */
    suspend fun reconcile(ownerUid: String, guard: SyncGuard): Outcome? {
        val local = state.accountEpoch(ownerUid)
        val remoteEpoch = try {
            remote.fetchOrCreateAccountEpoch(ownerUid, UUID.randomUUID().toString().uppercase())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AccountEpoch", "reconcile failed", e)
            return null
        }
        if (!guard.isValid) return null
        state.setAccountEpoch(ownerUid, remoteEpoch)
        return when (local) {
            null -> Outcome.Adopted
            remoteEpoch -> Outcome.Unchanged
            else -> Outcome.ResetDetected
        }
    }
}
