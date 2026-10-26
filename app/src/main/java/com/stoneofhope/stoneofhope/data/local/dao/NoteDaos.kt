package com.stoneofhope.stoneofhope.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogPendingDeleteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

// "Active" = not soft-deleted: `deletedAt IS NULL` (plus `isDeleted = 0` where the table has it),
// matching iOS `RealmCloudSync.notSoftDeleted`.

@Dao
interface NoteDao {
    @Upsert suspend fun upsert(note: NoteEntity)
    @Upsert suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE deletedAt IS NULL AND isDeleted = 0 " +
            "AND tagIds LIKE '%\"' || :tagId || '\"%' ORDER BY createdAt DESC"
    )
    fun observeActiveWithTag(tagId: String): Flow<List<NoteEntity>>

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

    @Query("SELECT * FROM noteComments WHERE id = :id")
    suspend fun getById(id: String): NoteCommentEntity?

    @Query(
        "SELECT * FROM noteComments WHERE noteId = :noteId AND deletedAt IS NULL AND isDeleted = 0 " +
            "ORDER BY createdAt"
    )
    fun observeActiveForNote(noteId: String): Flow<List<NoteCommentEntity>>

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
