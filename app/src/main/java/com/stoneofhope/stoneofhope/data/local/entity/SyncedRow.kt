package com.stoneofhope.stoneofhope.data.local.entity

import java.time.Instant

/** Port of iOS `CloudSyncableRealmObject`: the bookkeeping every synced row carries. */
interface SyncedRow<T : SyncedRow<T>> {
    /** Soft-delete time. Tombstoned rows are hidden in UI and hard-deleted once synced. */
    val deletedAt: Instant?

    /** Local-only dirty flag (FIRESTORE_SYNC_CONTRACT §2.1). Never written to Firestore. */
    val needsSync: Boolean

    /** Last time a sync considered this row merged (local bookkeeping only). */
    val lastSyncedAt: Instant?

    /** Copy with only the sync bookkeeping changed. */
    fun withSyncState(needsSync: Boolean, lastSyncedAt: Instant?): T
}
