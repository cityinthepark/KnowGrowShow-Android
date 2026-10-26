package com.stoneofhope.stoneofhope.data.repository

import com.stoneofhope.stoneofhope.data.bible.BibleAnchor
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.dao.UserVerseDao
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * iOS `UserVerseManager`: the user's verse pool. `content` is frozen on the first non-empty write
 * and never updated afterwards (USER_VERSE_CONTRACT §3.1). Writes are meant to run inside the
 * caller's Room transaction.
 */
@Singleton
class UserVerseManager @Inject constructor(private val dao: UserVerseDao) {

    suspend fun userVerse(versionAbbr: String, bookOrd: Int, chapterOrd: Int, verseOrd: Int): UserVerseEntity? =
        dao.getById(UserVerseEntity.makeId(versionAbbr, bookOrd, chapterOrd, verseOrd))

    /**
     * iOS `getOrCreateUserVerse`: idempotent by ords. An existing row keeps its content; it is only
     * touched to undelete it or to fill content that is still blank.
     */
    suspend fun getOrCreate(
        versionAbbr: String, bookOrd: Int, chapterOrd: Int, verseOrd: Int, content: String, now: Instant,
    ): UserVerseEntity {
        val id = UserVerseEntity.makeId(versionAbbr, bookOrd, chapterOrd, verseOrd)
        val existing = dao.getById(id)
        if (existing != null) {
            val shouldFillEmpty = SwiftText.trim(existing.content).isEmpty() && SwiftText.trim(content).isNotEmpty()
            val isDeleted = existing.deletedAt != null || existing.isDeleted
            if (!isDeleted && !shouldFillEmpty) return existing
            return existing.copy(
                content = if (shouldFillEmpty) content else existing.content,
                deletedAt = null,
                isDeleted = false,
                needsSync = true,
                updatedAt = now,
            ).also { dao.upsert(it) }
        }
        val row = UserVerseEntity(
            id = id, versionAbbr = versionAbbr, bookOrd = bookOrd, chapterOrd = chapterOrd, verseOrd = verseOrd,
            content = content, createdAt = now, updatedAt = now, needsSync = true,
        )
        check(row.matchesIdInvariant) { "UserVerse.id must equal makeId(versionAbbr, bookOrd, chapterOrd, verseOrd)" }
        dao.upsert(row)
        return row
    }

    /** iOS `frozenContents`: non-blank pool content for a range; blank rows are left out so they refetch. */
    suspend fun frozenContents(versionAbbr: String, bookOrd: Int, chapterOrd: Int, verseRange: IntRange): Map<Int, String> =
        verseRange.mapNotNull { v ->
            userVerse(versionAbbr, bookOrd, chapterOrd, v)?.content?.takeIf { SwiftText.trim(it).isNotEmpty() }?.let { v to it }
        }.toMap()

    /** iOS `getOrCreateUserVerses`: one row per verse of the anchor; a missing text becomes "". */
    suspend fun getOrCreateAll(anchor: BibleAnchor, contentsByVerseOrd: Map<Int, String>, now: Instant): List<UserVerseEntity> {
        if (anchor.isChapterLevel) return emptyList()
        return anchor.verseOrds.map { v ->
            getOrCreate(anchor.versionAbbr, anchor.bookOrd, anchor.chapterOrd, v, contentsByVerseOrd[v].orEmpty(), now)
        }
    }
}
