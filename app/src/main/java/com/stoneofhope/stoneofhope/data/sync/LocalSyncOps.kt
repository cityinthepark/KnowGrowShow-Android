package com.stoneofhope.stoneofhope.data.sync

import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.PinLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogPendingDeleteEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only bookkeeping shared by pull, push and sign-out: ports of iOS `LocalTombstonePurge`,
 * `PinLog` helpers, `CloudAccountService.markAllUserScopedDataForResync` and
 * `FlashCardUserVerseBackfill.apply`. Callers wrap these in a Room transaction.
 */
@Singleton
class LocalSyncOps @Inject constructor(private val db: AppDatabase) {

    /** iOS `hardDeleteNote`: also removes the note's PinLog (queuing its Firestore delete) and comments. */
    suspend fun hardDeleteNote(noteId: String) {
        removePinLog(noteId)
        db.noteCommentDao().hardDeleteForNote(noteId)
        db.noteDao().hardDelete(noteId)
    }

    /** iOS `PinLog.removeAll(for:)`. */
    suspend fun removePinLog(noteId: String) {
        val dao = db.pinLogDao()
        dao.getByNoteId(noteId) ?: return
        dao.enqueueDelete(PinLogPendingDeleteEntity(noteId))
        dao.hardDelete(noteId)
    }

    /** Hard-deletes tombstones that are already in Firestore (`needsSync == false`). */
    suspend fun purgeSyncedTombstones() {
        for (id in db.noteDao().syncedTombstoneIds()) hardDeleteNote(id)
        db.noteCommentDao().purgeSyncedTombstones()
        db.tagDao().purgeSyncedTombstones()
        db.flashCardDao().purgeSyncedTombstones()
        db.chapterLogDao().purgeSyncedTombstones()
        db.bookProgressDao().purgeSyncedTombstones()
        db.sermonDao().purgeSyncedTombstones()
        db.sermonSeriesDao().purgeSyncedTombstones()
        db.youTubeVideoDao().purgeSyncedTombstones()
        db.userVerseDao().purgeSyncedTombstones()
        db.indexedTextDao().purgeSyncedTombstones()
    }

    /** Every tombstone, pushed or not. Only safe right after the whole cloud copy was wiped. */
    suspend fun purgeAllTombstonesForCloudWipe() {
        for (id in db.noteDao().allTombstoneIds()) hardDeleteNote(id)
        db.noteCommentDao().purgeAllTombstones()
        db.tagDao().purgeAllTombstones()
        db.flashCardDao().purgeAllTombstones()
        db.chapterLogDao().purgeAllTombstones()
        db.bookProgressDao().purgeAllTombstones()
        db.sermonDao().purgeAllTombstones()
        db.sermonSeriesDao().purgeAllTombstones()
        db.youTubeVideoDao().purgeAllTombstones()
        db.userVerseDao().purgeAllTombstones()
        db.indexedTextDao().purgeAllTombstones()
        db.onlineChurchDao().purgeAllTombstones()
    }

    /** After the cloud copy is gone, everything local must be uploaded again on the next sign-in. */
    suspend fun markAllUserScopedDataForResync() {
        db.tagDao().markAllDirty()
        db.sermonSeriesDao().markAllDirty()
        db.sermonDao().markAllDirty()
        db.youTubeVideoDao().markAllDirty()
        db.userVerseDao().markAllDirty()
        db.indexedTextDao().markAllDirty()
        db.noteDao().markAllDirty()
        db.noteCommentDao().markAllDirty()
        db.flashCardDao().markAllDirty()
        db.chapterLogDao().markAllDirty()
        db.bookProgressDao().markAllDirty()
        db.onlineChurchDao().markAllDirty()
        db.pinLogDao().markAllDirty()
        db.pinLogDao().clearAllPendingDeletes()
    }

    /** iOS `PinLog.appendDay`: de-duplicated; marks the row dirty only when it changed. */
    suspend fun appendPinnedDay(noteId: String, dayKey: String) {
        if (dayKey.isEmpty()) return
        val dao = db.pinLogDao()
        val row = dao.getByNoteId(noteId) ?: PinLogEntity(noteId = noteId)
        if (dayKey in row.pinnedDays) return
        dao.upsert(row.copy(pinnedDays = row.pinnedDays + dayKey, needsSync = true))
    }

    /**
     * iOS `PinLog.applyUnpin`: applies [daysToRemoveOnUnpin] to the note's log; the row is removed
     * (queuing its Firestore delete) once no days are left, and marked dirty only when it changed.
     */
    suspend fun applyUnpin(noteId: String, today: LocalDate) {
        val dao = db.pinLogDao()
        val row = dao.getByNoteId(noteId) ?: return
        val removed = daysToRemoveOnUnpin(row.pinnedDays, today).toSet()
        val kept = row.pinnedDays.filter { it !in removed }
        if (kept.isEmpty()) removePinLog(noteId)
        else if (removed.isNotEmpty()) dao.upsert(row.copy(pinnedDays = kept, needsSync = true))
    }

    /** iOS `refreshTodayLedgerForOpenPinnedNotes`: while a note is pinned, each local day is logged. */
    suspend fun refreshTodayLedgerForPinnedNotes(now: Instant, zone: ZoneId) {
        val today = dayKey(now, zone)
        for (id in db.noteDao().pinnedIds()) appendPinnedDay(id, today)
    }

    /** iOS `FlashCardUserVerseBackfill.apply`: existing non-empty pool rows win. */
    suspend fun applyFlashCardBackfill(rows: List<FlashCardUserVerseBackfill.VerseRow>, now: Instant) {
        val dao = db.userVerseDao()
        for (row in rows) {
            val existing = dao.getById(row.id)
            if (existing == null) {
                dao.upsert(
                    UserVerseEntity(
                        id = row.id, versionAbbr = row.versionAbbr, bookOrd = row.bookOrd,
                        chapterOrd = row.chapterOrd, verseOrd = row.verseOrd, content = row.content,
                        createdAt = now, updatedAt = now, needsSync = true,
                    )
                )
                continue
            }
            val shouldFill = existing.content.isBlank() && row.content.isNotEmpty()
            val shouldRestore = existing.deletedAt != null || existing.isDeleted
            if (!shouldFill && !shouldRestore) continue
            dao.upsert(
                existing.copy(
                    content = if (shouldFill) row.content else existing.content,
                    deletedAt = if (shouldRestore) null else existing.deletedAt,
                    isDeleted = if (shouldRestore) false else existing.isDeleted,
                    needsSync = true,
                    updatedAt = now,
                )
            )
        }
    }

    companion object {
        private val DAY_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /** iOS `PinLog.dayKey`: local calendar day, `yyyy-MM-dd`. */
        fun dayKey(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(DAY_KEY)

        /**
         * iOS `PinLog.daysToRemoveOnUnpin` (same rule as the web's `pinDaysToRemoveOnUnpin`): the
         * stored entries to drop when a note is unpinned on [today]. Earlier days always stay as
         * history. Today stays when yesterday is logged (already pinned before today), otherwise the
         * pin started today and unpinning undoes it. Later days (clock skew) always go. Entries that
         * aren't valid days are left alone.
         */
        fun daysToRemoveOnUnpin(days: List<String>, today: LocalDate): List<String> {
            val keyed = days.map { it to comparableDay(it) }
            val pinnedBeforeToday = keyed.any { it.second == today.minusDays(1) }
            return keyed.filter { (_, day) ->
                day != null && (day > today || (day == today && !pinnedBeforeToday))
            }.map { it.first }
        }

        /** `yyyy-MM-dd` or `yyyyMMdd`, like iOS's `CalendarDayKey.normalize`; null if not a real day. */
        private fun comparableDay(entry: String): LocalDate? =
            runCatching { LocalDate.parse(entry.replace("-", ""), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
    }
}
