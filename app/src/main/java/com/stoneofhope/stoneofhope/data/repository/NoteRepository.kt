package com.stoneofhope.stoneofhope.data.repository

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Note reads/writes for screens. Every local write marks rows `needsSync` (contract §2.1). */
@Singleton
class NoteRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val noteDao get() = db.noteDao()

    fun observeActive(): Flow<List<NoteEntity>> = noteDao.observeActive()

    /** iOS `getAllTagNames()`: active tags, oldest first. */
    fun observeTagNames(): Flow<List<String>> = db.tagDao().observeActiveByCreation().map { tags -> tags.map { it.name } }

    /**
     * A plain quick note (iOS `saveSeparatedNoteInput` with no reference).
     * Not yet: detecting a "Book C:V" reference in the body (Today plan T2).
     */
    suspend fun createQuickNote(content: String): NoteEntity? {
        val text = content.trim()
        if (text.isEmpty()) return null
        val now = clock.instant()
        val note = NoteEntity(
            id = ObjectIds.newId(), noteType = NoteType.Note, content = text,
            createdAt = now, updatedAt = now, needsSync = true,
        )
        noteDao.upsert(note)
        return note
    }

    /**
     * iOS `savePrayerNote`: trimmed text, optional tag matched case-insensitively or created.
     * Not yet: the Bible reference field (Today plan T2).
     */
    suspend fun createPrayer(content: String, tagName: String?): NoteEntity? {
        val text = content.trim()
        if (text.isEmpty()) return null
        val now = clock.instant()
        return db.withTransaction {
            val tagIds = tagName?.takeIf { it.isNotEmpty() }?.let { listOf(findOrCreateTag(it).id) }.orEmpty()
            NoteEntity(
                id = ObjectIds.newId(), noteType = NoteType.Prayer, content = text,
                createdAt = now, updatedAt = now, tagIds = tagIds, needsSync = true,
            ).also { noteDao.upsert(it) }
        }
    }

    /** Soft delete; the tombstone syncs, then the row is purged locally. */
    suspend fun delete(note: NoteEntity) {
        val now = clock.instant()
        noteDao.upsert(note.copy(deletedAt = now, isDeleted = true, updatedAt = now, needsSync = true))
    }

    private suspend fun findOrCreateTag(name: String): TagEntity =
        db.tagDao().findActiveByName(name)
            ?: TagEntity(id = ObjectIds.newId(), name = name, createdAt = clock.instant(), needsSync = true)
                .also { db.tagDao().upsert(it) }
}
