package com.stoneofhope.stoneofhope.data.remote

import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireWrite
import java.time.Instant

/** One document under `users/{uid}/{collection}`. */
data class RemoteDocument(val id: String, val data: WireData)

/**
 * Everything the sync engine needs from the backend, in wire terms. Production is
 * `FirestoreRemoteStore`; tests use an in-memory fake, so pull/push/epoch logic runs on the JVM.
 *
 * All paths are under `users/{ownerUid}/…`.
 */
interface RemoteStore {

    /**
     * All documents in [collection], or only those with `updatedAt >= since` when [since] is set.
     * `>=` (not `>`) so documents sharing the watermark timestamp are not skipped.
     * Must read from the server, never a local cache (a partial cache would advance the cursor
     * past documents this device never saw).
     */
    suspend fun fetch(ownerUid: String, collection: String, since: Instant?): List<RemoteDocument>

    /** Commits [writes] atomically (one Firestore batch; callers keep it at or under 400). */
    suspend fun commit(ownerUid: String, writes: List<WireWrite>)

    /**
     * `users/{uid}/meta/account.epoch` from the server, creating it with [fresh] in a transaction
     * when missing. Returns whichever epoch is stored (another device may have won the race).
     */
    suspend fun fetchOrCreateAccountEpoch(ownerUid: String, fresh: String): String

    /**
     * Deletes every document in [collection] under `users/{uid}`, then confirms by a server read that
     * it is empty (iOS `CloudDataWipeService.deleteAndVerifyEmpty`). False when anything is left.
     */
    suspend fun deleteAllDocuments(ownerUid: String, collection: String): Boolean

    /** Removes one `users/{uid}/devices/{deviceId}` row (a peer registry entry, not user data). */
    suspend fun deleteDevice(ownerUid: String, deviceId: String)

    /** Every document in `users/{uid}/devices`. */
    suspend fun listDevices(ownerUid: String): List<RemoteDocument>

    /** Merge-writes `users/{uid}/devices/{deviceId}`. */
    suspend fun mergeDevice(ownerUid: String, deviceId: String, data: WireData)

    /**
     * One transaction on `users/{uid}/settings/preferences` (Web's document): [patch] gets the
     * stored fields (null when the document doesn't exist) and returns fields to merge-write, or
     * null to write nothing. It may run more than once, so it must have no side effects. Returns
     * what the committed attempt read.
     */
    suspend fun updatePreferences(ownerUid: String, patch: (WireData?) -> WireData?): WireData?
}
