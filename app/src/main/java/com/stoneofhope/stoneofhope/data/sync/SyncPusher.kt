package com.stoneofhope.stoneofhope.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.SyncedRow
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.wire.EncryptedBody
import com.stoneofhope.stoneofhope.data.remote.wire.NoteBodyCodec
import com.stoneofhope.stoneofhope.data.remote.wire.WireEncoder
import com.stoneofhope.stoneofhope.data.remote.wire.WireWrite
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * Port of iOS `FirestoreUserDataPush`: uploads every `needsSync` row, then clears `needsSync` only
 * for rows that did not change while the upload was in flight (so an edit made mid-push is never
 * dropped and then overwritten by the next pull).
 *
 * Difference from iOS: "unchanged" compares the whole row, not a handful of fields. iOS checks only
 * `updatedAt` for notes, so e.g. toggling `isPinned` mid-push could be marked clean without being
 * uploaded. A whole-row check can only cause an extra (idempotent) re-push.
 */
@Singleton
class SyncPusher @Inject constructor(
    private val db: AppDatabase,
    private val remote: RemoteStore,
    private val session: CryptoSession,
    private val ops: LocalSyncOps,
    private val images: NoteImageSync,
    private val clock: Clock,
) {
    data class Result(
        val documentsUploaded: Int,
        /** Live notes/comments left dirty because the crypto session was locked or encrypt failed. */
        val bodiesSkipped: Int,
    )

    /** Runs inside the final transaction: marks one pushed row clean if it is still unchanged. */
    private fun interface Settle {
        suspend fun apply(syncedAt: Instant)
    }

    suspend fun push(ownerUid: String, guard: SyncGuard): Result {
        val key = session.keyFor(ownerUid)
        val zone = ZoneId.systemDefault()
        val writes = mutableListOf<WireWrite>()
        val settles = mutableListOf<Settle>()
        var bodiesSkipped = 0
        val thumbUploads = mutableListOf<NoteImageSync.Upload>()
        val thumbDeletes = mutableListOf<String>()

        with(db.tagDao()) {
            for (row in dirty()) {
                writes += WireEncoder.tag(row, ownerUid)
                settles += settleIfUnchanged(row, { getById(row.id) }, ::upsert)
            }
        }
        val sermonDao = db.sermonDao()
        with(db.sermonSeriesDao()) {
            for (row in dirty()) {
                val sermonIds = sermonDao.idsInSeries(row.id)
                writes += WireEncoder.sermonSeries(row, ownerUid, sermonIds)
                // Membership lives on sermons, so it is part of this row's snapshot too.
                settles += settleIfUnchanged(row, { getById(row.id)?.takeIf { sermonDao.idsInSeries(row.id) == sermonIds } }, ::upsert)
            }
        }
        with(sermonDao) {
            for (row in dirty()) {
                writes += WireEncoder.sermon(row, ownerUid)
                settles += settleIfUnchanged(row, { getById(row.id) }, ::upsert)
            }
        }
        with(db.youTubeVideoDao()) {
            for (row in dirty()) {
                writes += WireEncoder.youtubeVideo(row, ownerUid)
                settles += settleIfUnchanged(row, { getById(row.youtubeID) }, ::upsert)
            }
        }
        with(db.userVerseDao()) {
            for (row in dirty()) {
                writes += WireEncoder.userVerse(row, ownerUid)
                settles += settleIfUnchanged(row, { getById(row.id) }, ::upsert)
            }
        }
        with(db.indexedTextDao()) {
            for (row in dirty()) {
                writes += WireEncoder.indexedText(row, ownerUid)
                settles += settleIfUnchanged(row, { getById(row.id) }, ::upsert)
            }
        }
        with(db.noteDao()) {
            for (row in dirty()) {
                val body = if (row.isDeleted || row.deletedAt != null) null else {
                    encryptOrNull(row.content, key) ?: run { bodiesSkipped++; continue }
                }
                writes += WireEncoder.note(row, ownerUid, body, zone)
                if (body == null && row.noteType == NoteType.Image) thumbDeletes += row.id
                images.uploadFor(row)?.let { thumbUploads += it }
                settles += settleIfUnchanged(row, { getById(row.id) }, ::upsert)
            }
        }
        with(db.noteCommentDao()) {
            for (row in dirty()) {
                val body = if (row.isDeleted || row.deletedAt != null) null else {
                    encryptOrNull(row.content, key) ?: run { bodiesSkipped++; continue }
                }
                writes += WireEncoder.noteComment(row, ownerUid, body)
                settles += settleIfUnchanged(row, { getById(row.id) }, ::upsert)
            }
        }
        if (bodiesSkipped > 0) Log.i(TAG, "Skipped $bodiesSkipped encrypted bodies (session locked or encrypt error)")
        with(db.flashCardDao()) {
            for (row in dirty()) {
                writes += WireEncoder.flashCard(row, ownerUid)
                settles += settleIfUnchanged(row, { getById(row.id) }, ::upsert)
            }
        }
        with(db.chapterLogDao()) {
            for (row in dirty()) {
                writes += WireEncoder.chapterLog(row, ownerUid)
                settles += settleIfUnchanged(row, { getById(row.id) }, ::upsert)
            }
        }
        with(db.bookProgressDao()) {
            for (row in dirty()) {
                writes += WireEncoder.bookProgress(row, ownerUid)
                settles += settleIfUnchanged(row, { getById(row.id) }, ::upsert)
            }
        }

        commitBatches(ownerUid, writes, guard, delayBetweenBatches = false)
        val pinWrites = pushPinLogs(ownerUid, guard, settles)

        // Thumbnails go up before the dirty flags are cleared: if the app dies mid-upload the notes are
        // still dirty and the next push sends them again. The documents are already committed.
        val failedThumbnails = images.upload(thumbUploads, ownerUid)
        images.delete(thumbDeletes, ownerUid)

        // If the account changed / sync was paused meanwhile, leave dirty flags alone.
        guard.check()
        val syncedAt = clock.instant()
        db.withTransaction {
            for (settle in settles) settle.apply(syncedAt)
            // A thumbnail that did not upload keeps its note dirty so the next push retries it.
            for (id in failedThumbnails) db.noteDao().getById(id)?.let { db.noteDao().upsert(it.copy(needsSync = true)) }
            ops.purgeSyncedTombstones()
        }
        return Result(documentsUploaded = writes.size + pinWrites, bodiesSkipped = bodiesSkipped)
    }

    /**
     * PinLog goes as its own pass, like iOS: queued physical deletes, then dirty rows. A failure is
     * logged and retried next time without failing the rest of the push. Returns writes committed.
     */
    private suspend fun pushPinLogs(ownerUid: String, guard: SyncGuard, settles: MutableList<Settle>): Int {
        val dao = db.pinLogDao()
        val pendingDeletes = dao.pendingDeletes()
        val dirty = dao.dirty()
        val pinWrites = pendingDeletes.map(WireEncoder::pinLogDelete) + dirty.map { WireEncoder.pinLog(it, ownerUid) }
        if (pinWrites.isEmpty()) return 0
        try {
            commitBatches(ownerUid, pinWrites, guard, delayBetweenBatches = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "PinLog push failed", e)
            return 0
        }
        if (pendingDeletes.isNotEmpty()) dao.clearPendingDeletes(pendingDeletes)
        for (row in dirty) {
            settles += Settle { at ->
                val current = dao.getByNoteId(row.noteId) ?: return@Settle
                if (current.pinnedDays.sorted() == row.pinnedDays.sorted()) {
                    dao.upsert(current.copy(needsSync = false, lastSyncedAt = at))
                }
            }
        }
        return pinWrites.size
    }

    private fun <T : SyncedRow<T>> settleIfUnchanged(
        snapshot: T,
        reload: suspend () -> T?,
        save: suspend (T) -> Unit,
    ) = Settle { at ->
        val current = reload() ?: return@Settle
        if (current.withSyncState(false, null) == snapshot.withSyncState(false, null)) {
            save(current.withSyncState(false, at))
        }
    }

    private fun encryptOrNull(plaintext: String, key: ByteArray?): EncryptedBody? {
        key ?: return null
        return try {
            NoteBodyCodec.encrypt(plaintext, key)
        } catch (e: Exception) {
            Log.w(TAG, "encrypt failed", e)
            null
        }
    }

    private suspend fun commitBatches(ownerUid: String, writes: List<WireWrite>, guard: SyncGuard, delayBetweenBatches: Boolean) {
        val chunks = writes.chunked(BATCH_SIZE)
        for ((index, chunk) in chunks.withIndex()) {
            // Stop between batches once cancelled / signed out / paused.
            guard.check()
            remote.commit(ownerUid, chunk)
            if (delayBetweenBatches && index < chunks.lastIndex) delay(INTER_BATCH_DELAY_MS)
        }
    }

    private companion object {
        const val TAG = "SyncPusher"
        const val BATCH_SIZE = 400
        const val INTER_BATCH_DELAY_MS = 300L
    }
}
