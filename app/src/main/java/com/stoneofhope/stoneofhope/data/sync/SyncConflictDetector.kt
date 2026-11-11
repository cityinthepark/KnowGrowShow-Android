package com.stoneofhope.stoneofhope.data.sync

import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SyncConflictEntity
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.model.SyncConflictType
import com.stoneofhope.stoneofhope.data.remote.wire.NoteBodyCodec
import com.stoneofhope.stoneofhope.data.remote.wire.RemoteBody
import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireDecoder
import com.stoneofhope.stoneofhope.data.remote.wire.instant
import com.stoneofhope.stoneofhope.data.remote.wire.int
import com.stoneofhope.stoneofhope.data.remote.wire.string
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS `SyncConflictDetector` + `SyncConflictManager.logConflict`. Pull still skips the
 * dirty local row (contract §2.2); this only records the clash for later user review.
 */
@Singleton
class SyncConflictDetector @Inject constructor(private val db: AppDatabase) {

    suspend fun detectNote(existing: NoteEntity, remote: WireData, noteKey: ByteArray?, now: Instant) {
        val remoteUpdatedAt = remoteChange(existing.lastSyncedAt, remote) ?: return
        log(
            SyncConflictType.Note, existing.id,
            localPreview = existing.content,
            remotePreview = remotePreview(remote, noteKey),
            localUpdatedAt = existing.updatedAt,
            remoteUpdatedAt = remoteUpdatedAt,
            now = now,
        )
    }

    suspend fun detectNoteComment(existing: NoteCommentEntity, remote: WireData, noteKey: ByteArray?, now: Instant) {
        val remoteUpdatedAt = remoteChange(existing.lastSyncedAt, remote) ?: return
        log(
            SyncConflictType.NoteComment, existing.id,
            localPreview = existing.content,
            remotePreview = remotePreview(remote, noteKey),
            localUpdatedAt = existing.lastSyncedAt ?: existing.createdAt,
            remoteUpdatedAt = remoteUpdatedAt,
            now = now,
        )
    }

    /** Mark conflicts are style/offset/colour drift, so previews describe the mark's shape. */
    suspend fun detectIndexedText(existing: IndexedTextEntity, remote: WireData, now: Instant) {
        val remoteUpdatedAt = remoteChange(existing.lastSyncedAt, remote) ?: return
        fun describe(style: String, color: String, start: Int, end: Int) = "$style/$color [$start..<$end]"
        log(
            SyncConflictType.IndexedText, existing.id,
            localPreview = describe(existing.markStyle.raw, existing.colorKey, existing.startOffset, existing.endOffset),
            remotePreview = describe(
                remote.string("markStyle") ?: "?", remote.string("colorKey") ?: "?",
                remote.int("startOffset") ?: -1, remote.int("endOffset") ?: -1,
            ),
            localUpdatedAt = existing.lastSyncedAt ?: existing.createdAt,
            remoteUpdatedAt = remoteUpdatedAt,
            now = now,
        )
    }

    /** The remote `updatedAt` when it counts as a conflicting change, else null. */
    private fun remoteChange(localSyncedAt: Instant?, remote: WireData): Instant? {
        if (WireDecoder.isRemoteTombstone(remote)) return null
        val remoteUpdatedAt = remote.instant("updatedAt")
        return remoteUpdatedAt.takeIf { isConflict(localSyncedAt, it) }
    }

    private fun remotePreview(remote: WireData, noteKey: ByteArray?): String =
        (NoteBodyCodec.decode(remote, noteKey) as? RemoteBody.Readable)?.text ?: UNREADABLE_PREVIEW

    /** One open conflict per record: a repeat detection refreshes it instead of adding a row. */
    private suspend fun log(
        type: SyncConflictType,
        recordId: String,
        localPreview: String,
        remotePreview: String,
        localUpdatedAt: Instant,
        remoteUpdatedAt: Instant,
        now: Instant,
    ) {
        val dao = db.syncConflictDao()
        // An incremental pull re-reads the document at the cursor (`>=`). If the user already ruled on this
        // very remote version, a still-dirty row (e.g. "keep this device's version" before the next push)
        // must not raise the same conflict again. iOS has no such check, so every resolution is followed by
        // one repeat entry until the push lands.
        dao.latestResolvedRemoteUpdatedAt(recordId, type)?.let { if (remoteUpdatedAt <= it) return }
        val existing = dao.findUnresolved(recordId, type)
        dao.upsert(
            existing?.copy(
                localPreview = localPreview, remotePreview = remotePreview,
                localUpdatedAt = localUpdatedAt, remoteUpdatedAt = remoteUpdatedAt, detectedAt = now,
            ) ?: SyncConflictEntity(
                id = ObjectIds.newId(), recordType = type, recordId = recordId,
                localPreview = localPreview, remotePreview = remotePreview,
                localUpdatedAt = localUpdatedAt, remoteUpdatedAt = remoteUpdatedAt, detectedAt = now,
            )
        )
    }

    companion object {
        const val UNREADABLE_PREVIEW = "(unable to decrypt remote preview)"

        /**
         * The remote row changed after this device last merged its own copy. A never-synced local
         * row facing an existing remote row counts as a conflict too.
         */
        fun isConflict(localSyncedAt: Instant?, remoteUpdatedAt: Instant?): Boolean {
            if (remoteUpdatedAt == null) return false
            if (localSyncedAt == null) return true
            return remoteUpdatedAt > localSyncedAt
        }
    }
}
