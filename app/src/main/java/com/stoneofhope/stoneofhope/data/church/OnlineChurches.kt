package com.stoneofhope.stoneofhope.data.church

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.OnlineChurchEntity
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * "My churches" and the one primary church (docs/CHURCH_IMPORT_PLAN.md §2.6): a church is added by
 * its first successful import, the first one added becomes primary, the user can move primary to
 * another, and when the primary goes the earliest remaining one takes over. Every change marks the
 * row for the `settings/preferences.churches` push (C4).
 */
@Singleton
class OnlineChurches @Inject constructor(private val db: AppDatabase, private val clock: Clock) {
    private val dao get() = db.onlineChurchDao()

    fun observe(): Flow<List<OnlineChurchEntity>> = dao.observeActive()

    suspend fun followed(): List<OnlineChurchEntity> = dao.active()

    /** After an import that left this church's sermons on the device. */
    suspend fun recordImport(churchKey: String, version: String?) = db.withTransaction {
        val now = clock.instant()
        val existing = dao.getByKey(churchKey)?.takeIf { it.deletedAt == null }
        when {
            existing == null -> dao.upsert(
                OnlineChurchEntity(churchKey = churchKey, importedAt = now, lastImportedVersion = version, updatedAt = now, needsSync = true),
            )
            existing.lastImportedVersion != version -> dao.upsert(existing.copy(lastImportedVersion = version, needsSync = true))
        }
        normalize(now)
    }

    /** Makes [churchKey] the only primary. False when it isn't followed. */
    suspend fun setPrimary(churchKey: String): Boolean = db.withTransaction {
        val live = dao.active()
        if (live.none { it.churchKey == churchKey }) return@withTransaction false
        val now = clock.instant()
        for (row in live) {
            val primary = row.churchKey == churchKey
            if (row.isPrimary != primary) dao.upsert(row.copy(isPrimary = primary, updatedAt = now, needsSync = true))
        }
        true
    }

    /** Unfollows (a tombstone, so other devices learn of it). No UI yet. */
    suspend fun remove(churchKey: String) = db.withTransaction {
        val row = dao.getByKey(churchKey)?.takeIf { it.deletedAt == null } ?: return@withTransaction
        val now = clock.instant()
        dao.upsert(row.copy(isPrimary = false, deletedAt = now, updatedAt = now, needsSync = true))
        normalize(now)
    }

    /**
     * Exactly one primary while any church is followed: none → the earliest import; several (two
     * devices chose differently) → the most recently changed one.
     */
    private suspend fun normalize(now: Instant) {
        val live = dao.active()
        val primaries = live.filter { it.isPrimary }
        val keep = when {
            primaries.size == 1 -> return
            primaries.isEmpty() -> live.minWithOrNull(compareBy({ it.importedAt }, { it.churchKey })) ?: return
            else -> primaries.maxWith(compareBy({ it.updatedAt }, { it.churchKey }))
        }
        for (row in live) {
            val primary = row.churchKey == keep.churchKey
            if (row.isPrimary != primary) dao.upsert(row.copy(isPrimary = primary, updatedAt = now, needsSync = true))
        }
    }
}
