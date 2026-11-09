package com.stoneofhope.stoneofhope.data.repository

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.bible.BibleAnchorKey
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.notes.isAnsweredMarker
import com.stoneofhope.stoneofhope.data.notes.linkedAnsweredCommentId
import com.stoneofhope.stoneofhope.data.sync.LocalSyncOps
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The note actions of the list and detail screens (iOS `NoteActionButtonsRow` → `NotesManager`):
 * pin, answered, stars, tags, edit, delete, comments. Field writes match iOS; every change marks the
 * note `needsSync` and bumps its `updatedAt` (wire `contentUpdatedAt`) the way iOS does.
 */
@Singleton
class NoteActionRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val notes: NoteRepository,
    private val ops: LocalSyncOps,
) {
    private val noteDao get() = db.noteDao()
    private val commentDao get() = db.noteCommentDao()

    private suspend fun update(noteId: String, block: suspend (NoteEntity) -> NoteEntity?) {
        db.withTransaction {
            val note = noteDao.getById(noteId) ?: return@withTransaction
            block(note)?.let { noteDao.upsert(it) }
        }
    }

    /**
     * iOS `setPinnedStatus`: pinning logs today in `PinLog`; unpinning applies
     * [LocalSyncOps.daysToRemoveOnUnpin], which only depends on the real today and the logged days.
     */
    suspend fun setPinned(noteId: String, pinned: Boolean, zone: ZoneId = ZoneId.systemDefault()) {
        val now = clock.instant()
        update(noteId) { note ->
            if (pinned) {
                ops.appendPinnedDay(noteId, LocalSyncOps.dayKey(now, zone))
            } else {
                ops.applyUnpin(noteId, now.atZone(zone).toLocalDate())
            }
            note.copy(isPinned = pinned, needsSync = true, updatedAt = now)
        }
    }

    /**
     * iOS `setPrayerAnsweredStatus`. Turning it off also removes the "Answered" markers logged on
     * [unmarkReferenceDay] (default today) with their paired comments; older markers are history.
     */
    suspend fun setAnswered(noteId: String, answered: Boolean, unmarkReferenceDay: LocalDate? = null, zone: ZoneId = ZoneId.systemDefault()) {
        val now = clock.instant()
        update(noteId) { note ->
            if (!answered) {
                val refDay = unmarkReferenceDay ?: now.atZone(zone).toLocalDate()
                val comments = commentDao.activeForNote(noteId)
                val markers = comments.filter { it.isAnsweredMarker && it.createdAt.atZone(zone).toLocalDate() == refDay }
                for (marker in markers) {
                    val linked = marker.linkedAnsweredCommentId
                    if (linked != null && linked != marker.id) {
                        comments.firstOrNull { it.id == linked }?.let { commentDao.upsert(it.softDeleted(now)) }
                    }
                    commentDao.upsert(marker.softDeleted(now))
                }
            }
            note.copy(isAnswered = answered, answeredAt = if (answered) now else null, needsSync = true, updatedAt = now)
        }
    }

    /** iOS `setStarLevelForNote` (0 clears; the picker offers 1–3). */
    suspend fun setStarLevel(noteId: String, level: Int) {
        val now = clock.instant()
        update(noteId) { it.copy(starLevel = level, needsSync = true, updatedAt = now) }
    }

    /** iOS `addTags(toNoteID:…)`: an active tag matched case-insensitively, else a new one. */
    suspend fun addTag(noteId: String, tagName: String) {
        val name = SwiftText.trim(tagName)
        if (name.isEmpty()) return
        val now = clock.instant()
        update(noteId) { note ->
            val tag = db.tagDao().findActiveByName(name)
                ?: TagEntity(id = ObjectIds.newId(), name = name, createdAt = now, needsSync = true).also { db.tagDao().upsert(it) }
            note.copy(tagIds = if (tag.id in note.tagIds) note.tagIds else note.tagIds + tag.id, needsSync = true, updatedAt = now)
        }
    }

    /** iOS `removeTag(fromNote:tagName:)`. */
    suspend fun removeTag(noteId: String, tagId: String) {
        val now = clock.instant()
        update(noteId) { it.copy(tagIds = it.tagIds - tagId, needsSync = true, updatedAt = now) }
    }

    /**
     * Edit the text. iOS `updateNoteContent` re-runs the old combined-input detector, so text that now
     * starts with "John 3:16 …" turns the note (even a prayer) into a verse anchor and replaces the
     * user's words with "·". Android changes the text only (IOS_ISSUES G1). Blank text is ignored.
     */
    suspend fun updateContent(noteId: String, newContent: String) {
        if (SwiftText.trim(newContent).isEmpty()) return
        val now = clock.instant()
        update(noteId) { note ->
            if (note.noteType == NoteType.BibleVerse || note.noteType == NoteType.Image) null
            else note.copy(content = newContent, needsSync = true, updatedAt = now)
        }
    }

    /** iOS `deleteNoteByID`: soft-deletes the note and its comments and drops its `PinLog`. */
    suspend fun delete(noteId: String) {
        val now = clock.instant()
        update(noteId) { note ->
            for (c in commentDao.activeForNote(noteId)) commentDao.upsert(c.softDeleted(now))
            ops.removePinLog(noteId)
            note.copy(deletedAt = now, isDeleted = true, needsSync = true, updatedAt = now)
        }
    }

    /**
     * iOS `addNoteComment`. On a `.bibleVerse` anchor this writes a reflection `.note` instead (through
     * `saveVerseReference`, so like iOS it lands on today's anchor for that verse). Other notes get a
     * `NoteComment` that copies the note's anchor (not for prayers, whose anchor is the prayer's own
     * metadata) and its sermon / video link.
     */
    suspend fun addComment(noteId: String, content: String, zone: ZoneId = ZoneId.systemDefault()) {
        val text = SwiftText.trim(content)
        if (text.isEmpty()) return
        val note = noteDao.getById(noteId) ?: return
        if (note.noteType == NoteType.BibleVerse) {
            val start = note.verseStart ?: return
            val sermonId = note.sermonId?.takeIf { id -> db.sermonDao().getById(id)?.let { it.deletedAt == null } == true }
            notes.saveVerseReference(
                note.bookOrd ?: return, note.chapterOrd ?: return, start, note.verseEnd ?: start, note.versionAbbr,
                userVerseContents = emptyMap(), reflectionText = text, sermonId = sermonId, youtubeID = note.youtubeID, zone = zone,
            )
            return
        }
        val now = clock.instant()
        db.withTransaction {
            val live = noteDao.getById(noteId) ?: return@withTransaction
            val copyAnchor = live.noteType != NoteType.Prayer
            val comment = NoteCommentEntity(
                id = ObjectIds.newId(), noteId = noteId, content = text, createdAt = now,
                bookOrd = live.bookOrd.takeIf { copyAnchor }, chapterOrd = live.chapterOrd.takeIf { copyAnchor },
                verseStart = live.verseStart.takeIf { copyAnchor }, verseEnd = live.verseEnd.takeIf { copyAnchor },
                versionAbbr = live.versionAbbr.takeIf { copyAnchor }, refKey = live.refKey.takeIf { copyAnchor },
                sermonId = live.sermonId, youtubeID = live.youtubeID?.takeIf { it.isNotEmpty() }, needsSync = true,
            )
            commentDao.upsert(comment.copy(chapterKey = BibleAnchorKey.chapterKey(comment.bookOrd, comment.chapterOrd)))
            noteDao.upsert(live.copy(needsSync = true, updatedAt = now))
        }
    }

    /**
     * iOS `addAnsweredComment`: marks the prayer answered and logs an "Answered" marker comment (its
     * content is the paired comment's id, or its own) 1 ms after the optional text comment.
     */
    suspend fun addAnsweredComment(noteId: String, content: String?) {
        val text = content?.let(SwiftText::trim).orEmpty()
        val now = clock.instant()
        db.withTransaction {
            val note = noteDao.getById(noteId) ?: return@withTransaction
            val contentComment = text.takeIf { it.isNotEmpty() }?.let {
                NoteCommentEntity(id = ObjectIds.newId(), noteId = noteId, content = it, createdAt = now, needsSync = true)
                    .also { c -> commentDao.upsert(c) }
            }
            val markerId = ObjectIds.newId()
            commentDao.upsert(
                NoteCommentEntity(
                    id = markerId, noteId = noteId, content = contentComment?.id ?: markerId,
                    createdAt = now.plusMillis(1), needsSync = true,
                ),
            )
            noteDao.upsert(note.copy(isAnswered = true, answeredAt = now, needsSync = true, updatedAt = now))
        }
    }

    // Comment actions (iOS `TimelineCommentLine`). iOS marks the comment `needsSync` and bumps its
    // note's `needsSync` / `updatedAt`; a comment has no `updatedAt` of its own.

    private suspend fun updateComment(commentId: String, block: suspend (NoteCommentEntity) -> NoteCommentEntity?) {
        val now = clock.instant()
        db.withTransaction {
            val comment = commentDao.getById(commentId)?.takeIf { it.deletedAt == null && !it.isDeleted } ?: return@withTransaction
            val changed = block(comment) ?: return@withTransaction
            commentDao.upsert(changed.copy(needsSync = true))
            comment.noteId?.let { noteDao.getById(it) }?.let { noteDao.upsert(it.copy(needsSync = true, updatedAt = now)) }
        }
    }

    /** iOS `updateNoteCommentContentByID`. Blank text is ignored; an "Answered" marker has no text to edit. */
    suspend fun updateCommentContent(commentId: String, newContent: String) {
        if (SwiftText.trim(newContent).isEmpty()) return
        updateComment(commentId) { if (it.isAnsweredMarker) null else it.copy(content = newContent) }
    }

    /** iOS `setStarLevelForComment` (0 clears). */
    suspend fun setCommentStarLevel(commentId: String, level: Int) {
        updateComment(commentId) { it.copy(starLevel = level) }
    }

    /** iOS `addTags(toNoteID:commentID:…)`: same tag lookup as [addTag]. */
    suspend fun addCommentTag(commentId: String, tagName: String) {
        val name = SwiftText.trim(tagName)
        if (name.isEmpty()) return
        val now = clock.instant()
        updateComment(commentId) { comment ->
            val tag = db.tagDao().findActiveByName(name)
                ?: TagEntity(id = ObjectIds.newId(), name = name, createdAt = now, needsSync = true).also { db.tagDao().upsert(it) }
            comment.copy(tagIds = if (tag.id in comment.tagIds) comment.tagIds else comment.tagIds + tag.id)
        }
    }

    /** iOS `removeTag(fromComment:tagName:)`. */
    suspend fun removeCommentTag(commentId: String, tagId: String) {
        updateComment(commentId) { it.copy(tagIds = it.tagIds - tagId) }
    }

    /**
     * iOS `deleteNoteComment`: soft-deletes this comment only. Like iOS, deleting an "Answered" marker
     * leaves the prayer answered, and deleting its paired comment leaves the marker (IOS_ISSUES G9).
     */
    suspend fun deleteComment(commentId: String) {
        val now = clock.instant()
        updateComment(commentId) { it.softDeleted(now) }
    }

    /** iOS `refreshTodayPinLogIfNeeded` (Note Details on appear). */
    suspend fun refreshTodayPinLog(zone: ZoneId = ZoneId.systemDefault()) {
        db.withTransaction { ops.refreshTodayLedgerForPinnedNotes(clock.instant(), zone) }
    }

    private fun NoteCommentEntity.softDeleted(now: Instant) = copy(deletedAt = now, isDeleted = true, needsSync = true)
}
