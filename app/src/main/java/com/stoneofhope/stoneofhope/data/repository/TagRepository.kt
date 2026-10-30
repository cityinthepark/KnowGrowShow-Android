package com.stoneofhope.stoneofhope.data.repository

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.tag.TagCounts
import com.stoneofhope.stoneofhope.data.tag.TagRenameResult
import com.stoneofhope.stoneofhope.data.tag.TagStats
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The Android side of iOS `NotesManager`'s tag management (`createTag`, `renameTag`, `deleteTag`,
 * `mergeTags`). Tags travel as `tagIds` on notes, comments, verses, sermons and videos, so deleting
 * or merging rewrites those rows and marks them `needsSync`. Unlike iOS it leaves every note's
 * `updatedAt` alone, and a comment does not touch its note (IOS_ISSUES L11).
 */
@Singleton
class TagRepository @Inject constructor(private val db: AppDatabase, private val clock: Clock) {
    private val tags get() = db.tagDao()

    /** Live tags, oldest first (iOS `MyTagsViewModel`). */
    fun observeTags(): Flow<List<TagEntity>> = tags.observeActiveByCreation()

    fun observeCounts(): Flow<Map<String, TagCounts>> = combine(
        db.noteDao().observeActive(), db.noteCommentDao().observeActive(),
        db.sermonDao().observeActive(), db.youTubeVideoDao().observePersonalCatalog(),
        TagStats::counts,
    )

    /** iOS `createTag(named:)`: no-op for a blank name or a case-insensitive duplicate. Returns the tag that now carries the name. */
    suspend fun create(name: String): TagEntity? {
        val trimmed = SwiftText.trim(name)
        if (trimmed.isEmpty()) return null
        tags.findActiveByName(trimmed)?.let { return it }
        return TagEntity(id = ObjectIds.newId(), name = trimmed, createdAt = clock.instant(), needsSync = true).also { tags.upsert(it) }
    }

    /** iOS `renameTag`, plus a duplicate check (IOS_ISSUES L7). */
    suspend fun rename(tagId: String, newName: String): TagRenameResult {
        val trimmed = SwiftText.trim(newName)
        if (trimmed.isEmpty()) return TagRenameResult.Empty
        val tag = tags.getById(tagId)?.takeIf { it.deletedAt == null } ?: return TagRenameResult.Renamed
        tags.findActiveByName(trimmed)?.takeIf { it.id != tagId }?.let { return TagRenameResult.Duplicate }
        tags.upsert(tag.copy(name = trimmed, needsSync = true))
        return TagRenameResult.Renamed
    }

    /** iOS `deleteTag`: taken off everything, then tombstoned. */
    suspend fun delete(tagId: String) {
        val tag = tags.getById(tagId) ?: return
        db.withTransaction {
            reassign(tagId, null)
            tags.upsert(tag.copy(deletedAt = clock.instant(), needsSync = true))
        }
    }

    /** iOS `mergeTags`: everything labelled [sourceId] gets [targetId] instead; the source is tombstoned. */
    suspend fun merge(sourceId: String, targetId: String): Boolean {
        if (sourceId == targetId) return false
        val source = tags.getById(sourceId)?.takeIf { it.deletedAt == null } ?: return false
        tags.getById(targetId)?.takeIf { it.deletedAt == null } ?: return false
        db.withTransaction {
            reassign(sourceId, targetId)
            tags.upsert(source.copy(deletedAt = clock.instant(), needsSync = true))
        }
        return true
    }

    private fun List<String>.swap(source: String, target: String?): List<String> {
        val without = filterNot { it == source }
        return if (target != null && target !in without) without + target else without
    }

    private suspend fun reassign(source: String, target: String?) {
        db.noteDao().withTag(source).forEach { db.noteDao().upsert(it.copy(tagIds = it.tagIds.swap(source, target), needsSync = true)) }
        db.noteCommentDao().withTag(source).forEach { db.noteCommentDao().upsert(it.copy(tagIds = it.tagIds.swap(source, target), needsSync = true)) }
        db.userVerseDao().withTag(source).forEach { db.userVerseDao().upsert(it.copy(tagIds = it.tagIds.swap(source, target), needsSync = true)) }
        db.sermonDao().withTag(source).forEach { db.sermonDao().upsert(it.copy(tagIds = it.tagIds.swap(source, target), needsSync = true)) }
        db.youTubeVideoDao().withTag(source).forEach { db.youTubeVideoDao().upsert(it.copy(tagIds = it.tagIds.swap(source, target), needsSync = true)) }
    }
}
