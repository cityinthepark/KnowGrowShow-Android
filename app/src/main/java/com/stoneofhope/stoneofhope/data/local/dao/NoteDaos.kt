package com.stoneofhope.stoneofhope.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogPendingDeleteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

// "Active" = not soft-deleted: `deletedAt IS NULL` (plus `isDeleted = 0` where the table has it),
// matching iOS `RealmCloudSync.notSoftDeleted`.

@Dao
interface NoteDao {
    @Upsert suspend fun upsert(note: NoteEntity)
    @Upsert suspend fun upsertAll(notes: List<NoteEntity>)

    /** Rows carrying [tagId], deleted or not (iOS `reassignTag` walks every object). */
    @Query("SELECT * FROM notes WHERE tagIds LIKE '%\"' || :tagId || '\"%'")
    suspend fun withTag(tagId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    /** Every Image note id, deleted or not: the thumbnails a cloud wipe has to remove. */
    @Query("SELECT id FROM notes WHERE noteType = 'image'")
    suspend fun imageNoteIds(): List<String>

    /** Live Image notes: the ones whose pictures may still need fetching from the cloud. */
    @Query("SELECT * FROM notes WHERE noteType = 'image' AND deletedAt IS NULL AND isDeleted = 0")
    suspend fun activeImageNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    /** Active notes created in [start, end) — iOS day matching uses `createdAt`, never `updatedAt`. */
    @Query(
        "SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 " +
            "AND createdAt >= :start AND createdAt < :end ORDER BY createdAt"
    )
    fun observeCreatedBetween(start: Instant, end: Instant): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 AND id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<NoteEntity>>

    /** iOS `getPinnedNotes()`: every pinned, non-deleted note, any date. */
    @Query("SELECT * FROM notes WHERE isPinned = 1 AND deletedAt IS NULL AND isDeleted = 0")
    fun observePinned(): Flow<List<NoteEntity>>

    /** Active notes anchored anywhere in one chapter (the chapter page), oldest first. */
    @Query(
        "SELECT * FROM notes WHERE bookOrd = :bookOrd AND chapterOrd = :chapterOrd " +
            "AND deletedAt IS NULL AND isDeleted = 0 ORDER BY createdAt"
    )
    fun observeActiveInChapter(bookOrd: Int, chapterOrd: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 AND sermonId IN (:sermonIds)")
    fun observeForSermons(sermonIds: List<String>): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 AND youtubeID IN (:videoIds)")
    fun observeForVideos(videoIds: List<String>): Flow<List<NoteEntity>>

    /** iOS `notes(forSermonId:)`. */
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 AND sermonId = :sermonId")
    suspend fun activeForSermon(sermonId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 AND youtubeID = :youtubeID")
    suspend fun activeForVideo(youtubeID: String): List<NoteEntity>

    @Query(
        "SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 " +
            "AND tagIds LIKE '%\"' || :tagId || '\"%' ORDER BY createdAt DESC"
    )
    fun observeActiveWithTag(tagId: String): Flow<List<NoteEntity>>

    /** iOS `findVerseAnchor` candidates, deleted or not; the caller matches end verse, version, bucket and day. */
    @Query(
        "SELECT * FROM notes WHERE noteType = :noteType AND bookOrd = :bookOrd AND chapterOrd = :chapterOrd " +
            "AND verseStart = :verseStart ORDER BY createdAt, id"
    )
    suspend fun verseAnchorCandidates(noteType: NoteType, bookOrd: Int, chapterOrd: Int, verseStart: Int): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE needsSync = 1")
    suspend fun dirty(): List<NoteEntity>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDelete(id: String)

    /** Notes need a cascade on hard delete (comments + PinLog), so purge goes through ids. */
    @Query("SELECT id FROM notes WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun syncedTombstoneIds(): List<String>

    @Query("SELECT id FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun allTombstoneIds(): List<String>

    @Query("SELECT id FROM notes WHERE isPinned = 1 AND deletedAt IS NULL")
    suspend fun pinnedIds(): List<String>

    @Query("UPDATE notes SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface NoteCommentDao {
    @Upsert suspend fun upsert(comment: NoteCommentEntity)
    @Upsert suspend fun upsertAll(comments: List<NoteCommentEntity>)

    /** Rows carrying [tagId], deleted or not (iOS `reassignTag` walks every object). */
    @Query("SELECT * FROM noteComments WHERE tagIds LIKE '%\"' || :tagId || '\"%'")
    suspend fun withTag(tagId: String): List<NoteCommentEntity>

    @Query("SELECT * FROM noteComments WHERE id = :id")
    suspend fun getById(id: String): NoteCommentEntity?

    @Query(
        "SELECT * FROM noteComments WHERE noteId = :noteId AND deletedAt IS NULL AND isDeleted = 0 " +
            "ORDER BY createdAt"
    )
    fun observeActiveForNote(noteId: String): Flow<List<NoteCommentEntity>>

    @Query(
        "SELECT * FROM noteComments WHERE noteId = :noteId AND deletedAt IS NULL AND isDeleted = 0 " +
            "ORDER BY createdAt"
    )
    suspend fun activeForNote(noteId: String): List<NoteCommentEntity>

    @Query(
        "SELECT * FROM noteComments WHERE noteId IN (:noteIds) AND deletedAt IS NULL AND isDeleted = 0 " +
            "ORDER BY createdAt"
    )
    fun observeActiveForNotes(noteIds: List<String>): Flow<List<NoteCommentEntity>>

    /** Every active comment (note lists match tags, stars and days through them). */
    @Query("SELECT * FROM noteComments WHERE deletedAt IS NULL AND isDeleted = 0 ORDER BY createdAt")
    fun observeActive(): Flow<List<NoteCommentEntity>>

    /** Comments carry their note's `sermonId` (iOS `addNoteComment`); a merge moves them along. */
    @Query("SELECT * FROM noteComments WHERE deletedAt IS NULL AND isDeleted = 0 AND sermonId = :sermonId")
    suspend fun activeWithSermonId(sermonId: String): List<NoteCommentEntity>

    @Query(
        "SELECT * FROM noteComments WHERE deletedAt IS NULL AND isDeleted = 0 " +
            "AND createdAt >= :start AND createdAt < :end"
    )
    fun observeCreatedBetween(start: Instant, end: Instant): Flow<List<NoteCommentEntity>>

    @Query("SELECT * FROM noteComments WHERE needsSync = 1")
    suspend fun dirty(): List<NoteCommentEntity>

    @Query("DELETE FROM noteComments WHERE id = :id")
    suspend fun hardDelete(id: String)

    /** Cascade from a hard-deleted note. */
    @Query("DELETE FROM noteComments WHERE noteId = :noteId")
    suspend fun hardDeleteForNote(noteId: String)

    @Query("DELETE FROM noteComments WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM noteComments WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE noteComments SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface TagDao {
    @Upsert suspend fun upsert(tag: TagEntity)
    @Upsert suspend fun upsertAll(tags: List<TagEntity>)

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name")
    fun observeActive(): Flow<List<TagEntity>>

    /** iOS `getAllTagNames()` order. */
    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observeActiveByCreation(): Flow<List<TagEntity>>

    /** Case-insensitive match, like iOS `name ==[c] %@`. */
    @Query("SELECT * FROM tags WHERE deletedAt IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findActiveByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE needsSync = 1")
    suspend fun dirty(): List<TagEntity>

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM tags WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM tags WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE tags SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface PinLogDao {
    @Upsert suspend fun upsert(log: PinLogEntity)

    @Query("SELECT * FROM PinLog WHERE noteId = :noteId")
    suspend fun getByNoteId(noteId: String): PinLogEntity?

    @Query("SELECT * FROM PinLog WHERE noteId = :noteId")
    fun observeByNoteId(noteId: String): Flow<PinLogEntity?>

    @Query("SELECT * FROM PinLog WHERE needsSync = 1")
    suspend fun dirty(): List<PinLogEntity>

    @Query("DELETE FROM PinLog WHERE noteId = :noteId")
    suspend fun hardDelete(noteId: String)

    @Upsert suspend fun enqueueDelete(pending: PinLogPendingDeleteEntity)

    @Query("SELECT firestoreDocumentId FROM pinLogPendingDeletes")
    suspend fun pendingDeletes(): List<String>

    @Query("DELETE FROM pinLogPendingDeletes WHERE firestoreDocumentId IN (:ids)")
    suspend fun clearPendingDeletes(ids: List<String>)

    @Query("DELETE FROM pinLogPendingDeletes")
    suspend fun clearAllPendingDeletes()

    @Query("UPDATE PinLog SET needsSync = 1")
    suspend fun markAllDirty()
}
