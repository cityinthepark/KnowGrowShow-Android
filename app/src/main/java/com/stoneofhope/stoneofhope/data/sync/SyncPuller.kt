package com.stoneofhope.stoneofhope.data.sync

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.remote.RemoteDocument
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.wire.Decoded
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import com.stoneofhope.stoneofhope.data.remote.wire.SkipReason
import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireDecoder
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS `FirestoreUserDataPull`: downloads `users/{uid}/…` and merges into Room.
 *
 * - Rows with `needsSync == true` are never overwritten (local unpushed edits win, contract §2.2);
 *   clean rows are replaced wholesale, no `updatedAt` comparison, no field-level merge. The one
 *   exception: a never-synced verse takes the cloud's tags too ([FirstMergeVerseTags]).
 * - Incremental (`updatedAt >= cursor`) once a cursor exists; the cursor advances to the largest
 *   server `updatedAt` seen, and is held back if any encrypted body was unreadable (iOS "K2").
 */
@Singleton
class SyncPuller @Inject constructor(
    private val db: AppDatabase,
    private val remote: RemoteStore,
    private val state: SyncStateStore,
    private val session: CryptoSession,
    private val ops: LocalSyncOps,
    private val conflicts: SyncConflictDetector,
    private val clock: Clock,
) {
    data class Result(
        val documentsSeen: Int,
        val bodiesSkipped: Int,
        /** False when the cursor was held back (unreadable bodies); peers should not be marked synced. */
        val complete: Boolean,
    )

    private class Run(val ownerUid: String, val noteKey: ByteArray?, val now: Instant) {
        var bodiesSkipped = 0
        var documentsSeen = 0
        var watermark: Instant? = null

        /** `sermonSeries` id → ordered `sermonIds`, applied after sermons are merged. */
        val seriesOrder = linkedMapOf<String, List<String>>()
    }

    suspend fun pull(ownerUid: String, guard: SyncGuard): Result {
        val since = state.lastPullAt(ownerUid)
        val run = Run(ownerUid, session.keyFor(ownerUid), clock.instant())

        for (collection in FirestoreSchema.pullOrder) {
            val docs = remote.fetch(ownerUid, collection, since)
            guard.check()
            run.documentsSeen += docs.size
            WireDecoder.watermark(docs.map { it.data })?.let { w ->
                run.watermark = run.watermark?.let { maxOf(it, w) } ?: w
            }
            db.withTransaction {
                merge(collection, docs, run)
                if (collection == FirestoreSchema.SERMONS) rebuildSeriesMembership(run)
            }
        }

        guard.check()
        if (run.bodiesSkipped > 0) {
            // Don't advance past documents we couldn't read; the next pull (with the key) re-reads them.
            return Result(run.documentsSeen, run.bodiesSkipped, complete = false)
        }
        state.markPullCompleted(ownerUid, run.watermark)
        return Result(run.documentsSeen, 0, complete = true)
    }

    private suspend fun merge(collection: String, docs: List<RemoteDocument>, run: Run) {
        val now = run.now
        when (collection) {
            FirestoreSchema.TAGS -> {
                val dao = db.tagDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.tag(doc.id, doc.data, existing, now) },
                    hardDelete = dao::hardDelete,
                    write = { dao.upsert(it) },
                )
            }
            FirestoreSchema.SERMON_SERIES -> {
                val dao = db.sermonSeriesDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.sermonSeries(doc.id, doc.data, existing, now) },
                    hardDelete = dao::hardDelete,
                    write = {
                        dao.upsert(it.series)
                        run.seriesOrder[it.series.id] = it.sermonIds
                    },
                )
            }
            FirestoreSchema.SERMONS -> {
                val dao = db.sermonDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.sermon(doc.id, doc.data, existing, now) },
                    hardDelete = dao::hardDelete,
                    write = { dao.upsert(it) },
                )
            }
            FirestoreSchema.YOUTUBE_VIDEOS -> {
                val dao = db.youTubeVideoDao()
                mergeEach(
                    docs, run, { id -> id.takeIf { it.isNotEmpty() } }, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.youtubeVideo(doc.id, doc.data, existing, now) },
                    hardDelete = dao::hardDelete,
                    write = { dao.upsert(it) },
                )
            }
            FirestoreSchema.NOTES -> {
                val dao = db.noteDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.note(doc.id, doc.data, existing, run.noteKey, now) },
                    onDirty = { existing, data -> conflicts.detectNote(existing, data, run.noteKey, now) },
                    hardDelete = ops::hardDeleteNote,
                    write = { dao.upsert(it) },
                )
                // Note images are not downloaded yet: Firebase Storage needs the Blaze plan.
            }
            FirestoreSchema.PIN_LOG -> {
                val dao = db.pinLogDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getByNoteId, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.pinLog(doc.id, doc.data, existing, now) },
                    // Empty remote pinnedDays: drop the local row only (no outbox — the doc is already gone).
                    hardDelete = dao::hardDelete,
                    write = { dao.upsert(it) },
                )
            }
            FirestoreSchema.NOTE_COMMENTS -> {
                val dao = db.noteCommentDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.noteComment(doc.id, doc.data, existing, run.noteKey, now) },
                    onDirty = { existing, data -> conflicts.detectNoteComment(existing, data, run.noteKey, now) },
                    hardDelete = dao::hardDelete,
                    write = { dao.upsert(it) },
                )
            }
            FirestoreSchema.FLASH_CARDS -> {
                val dao = db.flashCardDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.flashCard(doc.id, doc.data, existing, now) },
                    hardDelete = dao::hardDelete,
                    write = { decoded ->
                        val card = decoded.card
                        dao.upsert(card)
                        ops.applyFlashCardBackfill(
                            FlashCardUserVerseBackfill.verses(
                                decoded.legacyContent, card.bookOrd, card.chapterOrd,
                                card.verseStart, card.verseEnd, card.versionAbbr,
                            ),
                            now,
                        )
                    },
                )
            }
            FirestoreSchema.CHAPTER_LOGS -> {
                val dao = db.chapterLogDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.chapterLog(doc.id, doc.data, existing, now) },
                    hardDelete = dao::hardDelete,
                    write = { dao.upsert(it) },
                )
            }
            FirestoreSchema.BOOK_PROGRESS -> {
                val dao = db.bookProgressDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.bookProgress(doc.id, doc.data, existing, now) },
                    hardDelete = dao::hardDelete,
                    write = { dao.upsert(it) },
                )
            }
            FirestoreSchema.USER_VERSES -> {
                val dao = db.userVerseDao()
                mergeEach(
                    docs, run, { id -> id.takeIf { it.isNotEmpty() } }, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.userVerse(doc.id, doc.data, existing, now) },
                    onDirty = { existing, data -> FirstMergeVerseTags.union(existing, data)?.let { dao.upsert(it) } },
                    hardDelete = dao::hardDelete,
                    write = { dao.upsert(it) },
                )
            }
            FirestoreSchema.INDEXED_TEXTS -> {
                val dao = db.indexedTextDao()
                mergeEach(
                    docs, run, ObjectIds::parse, dao::getById, { it.needsSync },
                    decode = { doc, existing -> WireDecoder.indexedText(doc.id, doc.data, existing, now) },
                    onDirty = { existing, data -> conflicts.detectIndexedText(existing, data, now) },
                    hardDelete = dao::hardDelete,
                    write = { dao.upsert(it) },
                )
                // No reattach step: marks are found by `sourceId`, so out-of-order pulls need no fix-up.
            }
            else -> error("Unknown collection $collection")
        }
    }

    /**
     * The per-document rule every iOS `merge*` follows: skip bad ids; never touch a dirty local row
     * (but record a conflict); hard-delete on a remote tombstone; otherwise replace the row.
     */
    private suspend fun <E : Any, D> mergeEach(
        docs: List<RemoteDocument>,
        run: Run,
        localId: (String) -> String?,
        load: suspend (String) -> E?,
        isDirty: (E) -> Boolean,
        decode: (RemoteDocument, E?) -> Decoded<D>,
        onDirty: suspend (E, WireData) -> Unit = { _, _ -> },
        hardDelete: suspend (String) -> Unit,
        write: suspend (D) -> Unit,
    ) {
        for (doc in docs) {
            val id = localId(doc.id) ?: continue
            val existing = load(id)
            if (existing != null && isDirty(existing)) {
                if (WireDecoder.schemaOk(doc.data)) onDirty(existing, doc.data)
                continue
            }
            when (val decoded = decode(doc, existing)) {
                is Decoded.Row -> write(decoded.value)
                Decoded.Tombstone -> if (existing != null) hardDelete(id)
                is Decoded.Skip -> if (decoded.reason == SkipReason.UnreadableBody) run.bodiesSkipped++
            }
        }
    }

    /** iOS `rebuildSermonSeriesSermonLists`: `sermonIds` on a clean series is authoritative for membership. */
    private suspend fun rebuildSeriesMembership(run: Run) {
        val seriesDao = db.sermonSeriesDao()
        val sermonDao = db.sermonDao()
        for ((seriesId, ordered) in run.seriesOrder) {
            val series = seriesDao.getById(seriesId) ?: continue
            if (series.needsSync) continue
            val keep = ordered.toSet()
            for (sermonId in sermonDao.idsInSeries(seriesId)) {
                if (sermonId !in keep) sermonDao.setSeries(sermonId, null)
            }
            for (sermonId in ordered) {
                if (sermonDao.getById(sermonId) != null) sermonDao.setSeries(sermonId, seriesId)
            }
        }
        run.seriesOrder.clear()
    }
}
