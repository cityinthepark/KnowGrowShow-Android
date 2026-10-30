package com.stoneofhope.stoneofhope.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A church this user follows ("My churches", docs/CHURCH_IMPORT_PLAN.md §2.5–2.6). No iOS
 * counterpart yet. Pushed as one element of `users/{uid}/settings/preferences.churches` (C4);
 * the device's own import progress stays in `app_prefs` and never syncs.
 */
@Entity(tableName = "onlineChurches")
data class OnlineChurchEntity(
    /** = `onlineResourceProfiles` document id. */
    @PrimaryKey val churchKey: String,
    /** At most one live row is primary (OnlineChurches keeps that true). */
    val isPrimary: Boolean = false,
    /** First import on any device; the earlier one wins when merging. */
    val importedAt: Instant,
    /** Latest version imported — for Web to show; never used to skip an import on this device. */
    val lastImportedVersion: String? = null,
    /**
     * Last change to `isPrimary` or `deletedAt` (not to the version): the newer side's `isPrimary`
     * wins when merging, so a later import elsewhere can't undo a primary choice.
     */
    val updatedAt: Instant,
    override val deletedAt: Instant? = null,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow<OnlineChurchEntity> {
    override fun withSyncState(needsSync: Boolean, lastSyncedAt: Instant?) =
        copy(needsSync = needsSync, lastSyncedAt = lastSyncedAt)
}
