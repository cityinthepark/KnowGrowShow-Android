package com.stoneofhope.stoneofhope.data.repository

import com.stoneofhope.stoneofhope.data.local.dao.NoteDao
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Note reads/writes for screens. Every local write marks the row `needsSync` (contract §2.1). */
@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val clock: Clock,
) {
    fun observeActive(): Flow<List<NoteEntity>> = noteDao.observeActive()

    /** iOS `Note(content:noteType:)` for a quick note. */
    suspend fun createQuickNote(content: String): NoteEntity {
        val now = clock.instant()
        val note = NoteEntity(
            id = ObjectIds.newId(),
            noteType = NoteType.Note,
            content = content,
            createdAt = now,
            updatedAt = now,
            needsSync = true,
        )
        noteDao.upsert(note)
        return note
    }

    /** Soft delete; the tombstone syncs, then the row is purged locally. */
    suspend fun delete(note: NoteEntity) {
        val now = clock.instant()
        noteDao.upsert(note.copy(deletedAt = now, isDeleted = true, updatedAt = now, needsSync = true))
    }
}
