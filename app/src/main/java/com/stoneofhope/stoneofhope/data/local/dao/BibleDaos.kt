package com.stoneofhope.stoneofhope.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stoneofhope.stoneofhope.data.local.entity.BookProgressEntity
import com.stoneofhope.stoneofhope.data.local.entity.ChapterLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.DownloadedVerseEntity
import com.stoneofhope.stoneofhope.data.local.entity.DownloadedVersionEntity
import com.stoneofhope.stoneofhope.data.local.entity.FetchedVerseCacheEntity
import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.SyncConflictEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.model.SyncConflictType
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashCardDao {
    @Upsert suspend fun upsert(card: FlashCardEntity)

    @Query("SELECT * FROM flashCards WHERE id = :id")
    suspend fun getById(id: String): FlashCardEntity?

    @Query("SELECT * FROM flashCards WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<FlashCardEntity>>

    /** iOS `flashCardMatching`: live cards for one reference key (the version is compared by the caller). */
    @Query("SELECT * FROM flashCards WHERE refKey = :refKey AND deletedAt IS NULL")
    suspend fun liveByRefKey(refKey: String): List<FlashCardEntity>

    @Query("SELECT COUNT(*) FROM flashCards WHERE deletedAt IS NULL AND isArchived = 0")
    suspend fun activeCount(): Int

    /** iOS `flashCardActiveAndTotalCounts().active`. */
    @Query("SELECT COUNT(*) FROM flashCards WHERE deletedAt IS NULL AND isArchived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM flashCards WHERE needsSync = 1")
    suspend fun dirty(): List<FlashCardEntity>

    @Query("DELETE FROM flashCards WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM flashCards WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM flashCards WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE flashCards SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface ChapterLogDao {
    @Upsert suspend fun upsert(log: ChapterLogEntity)

    @Query("SELECT * FROM chapterLogs WHERE id = :id")
    suspend fun getById(id: String): ChapterLogEntity?

    @Query("SELECT * FROM chapterLogs WHERE deletedAt IS NULL ORDER BY readOn DESC")
    fun observeActive(): Flow<List<ChapterLogEntity>>

    @Query("SELECT * FROM chapterLogs WHERE bookOrd = :bookOrd AND chapter = :chapter AND deletedAt IS NULL")
    suspend fun activeFor(bookOrd: Int, chapter: Int): List<ChapterLogEntity>

    @Query("SELECT * FROM chapterLogs WHERE needsSync = 1")
    suspend fun dirty(): List<ChapterLogEntity>

    @Query("DELETE FROM chapterLogs WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM chapterLogs WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM chapterLogs WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE chapterLogs SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface BookProgressDao {
    @Upsert suspend fun upsert(progress: BookProgressEntity)

    @Query("SELECT * FROM bookProgress WHERE id = :id")
    suspend fun getById(id: String): BookProgressEntity?

    @Query("SELECT * FROM bookProgress WHERE deletedAt IS NULL ORDER BY lastUpdatedOn DESC")
    fun observeActive(): Flow<List<BookProgressEntity>>

    @Query("SELECT * FROM bookProgress WHERE bookOrd = :bookOrd AND deletedAt IS NULL")
    suspend fun activeFor(bookOrd: Int): List<BookProgressEntity>

    @Query("SELECT * FROM bookProgress WHERE needsSync = 1")
    suspend fun dirty(): List<BookProgressEntity>

    @Query("DELETE FROM bookProgress WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM bookProgress WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM bookProgress WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE bookProgress SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface UserVerseDao {
    @Upsert suspend fun upsert(verse: UserVerseEntity)

    /** Rows carrying [tagId], deleted or not (iOS `reassignTag` walks every object). */
    @Query("SELECT * FROM userVerses WHERE tagIds LIKE '%\"' || :tagId || '\"%'")
    suspend fun withTag(tagId: String): List<UserVerseEntity>

    @Query("SELECT * FROM userVerses WHERE id = :id")
    suspend fun getById(id: String): UserVerseEntity?

    @Query(
        "SELECT * FROM userVerses WHERE versionAbbr = :versionAbbr AND bookOrd = :bookOrd " +
            "AND chapterOrd = :chapterOrd AND deletedAt IS NULL AND isDeleted = 0 ORDER BY verseOrd"
    )
    fun observeChapter(versionAbbr: String, bookOrd: Int, chapterOrd: Int): Flow<List<UserVerseEntity>>

    /** Like iOS `realm.object(forPrimaryKey:)`, display reads rows whatever their deletion state. */
    @Query("SELECT * FROM userVerses WHERE id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<UserVerseEntity>>

    /** One chapter in every version, whatever the deletion state (display reads, like [observeByIds]). */
    @Query("SELECT * FROM userVerses WHERE bookOrd = :bookOrd AND chapterOrd = :chapterOrd")
    fun observeInChapter(bookOrd: Int, chapterOrd: Int): Flow<List<UserVerseEntity>>

    @Query("SELECT * FROM userVerses")
    fun observeAll(): Flow<List<UserVerseEntity>>

    @Query("SELECT * FROM userVerses WHERE needsSync = 1")
    suspend fun dirty(): List<UserVerseEntity>

    @Query("DELETE FROM userVerses WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM userVerses WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM userVerses WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE userVerses SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface IndexedTextDao {
    @Upsert suspend fun upsert(mark: IndexedTextEntity)

    @Query("SELECT * FROM indexedTexts WHERE id = :id")
    suspend fun getById(id: String): IndexedTextEntity?

    /** Replaces iOS `UserVerse.marks`; orphans (parent not yet pulled) attach automatically. */
    @Query(
        "SELECT * FROM indexedTexts WHERE sourceType = 'verse' AND sourceId = :userVerseId " +
            "AND deletedAt IS NULL AND isDeleted = 0 ORDER BY startOffset"
    )
    fun observeMarksForVerse(userVerseId: String): Flow<List<IndexedTextEntity>>

    /** Every active verse mark (read-only overlays on verse text, like iOS). */
    @Query("SELECT * FROM indexedTexts WHERE sourceType = 'verse' AND deletedAt IS NULL AND isDeleted = 0")
    fun observeActiveVerseMarks(): Flow<List<IndexedTextEntity>>

    @Query(
        "SELECT * FROM indexedTexts WHERE sourceType = 'verse' AND sourceId IN (:userVerseIds) " +
            "AND deletedAt IS NULL AND isDeleted = 0"
    )
    suspend fun activeForVerses(userVerseIds: List<String>): List<IndexedTextEntity>

    @Query("SELECT * FROM indexedTexts WHERE needsSync = 1")
    suspend fun dirty(): List<IndexedTextEntity>

    @Query("DELETE FROM indexedTexts WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM indexedTexts WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM indexedTexts WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE indexedTexts SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface SyncConflictDao {
    @Upsert suspend fun upsert(conflict: SyncConflictEntity)

    @Query("SELECT * FROM syncConflicts WHERE resolvedAt IS NULL ORDER BY detectedAt DESC")
    fun observeUnresolved(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM syncConflicts WHERE recordId = :recordId AND recordType = :recordType AND resolvedAt IS NULL LIMIT 1")
    suspend fun findUnresolved(recordId: String, recordType: SyncConflictType): SyncConflictEntity?
}

@Dao
interface BibleCacheDao {
    @Upsert suspend fun upsertVerses(verses: List<DownloadedVerseEntity>)
    @Upsert suspend fun upsertVersion(version: DownloadedVersionEntity)
    @Upsert suspend fun upsertFetched(verses: List<FetchedVerseCacheEntity>)

    @Query("SELECT * FROM downloadedVersions")
    fun observeDownloadedVersions(): Flow<List<DownloadedVersionEntity>>

    @Query(
        "SELECT * FROM downloadedVerses WHERE versionAbbr = :versionAbbr AND bookOrd = :bookOrd " +
            "AND chapterOrd = :chapterOrd ORDER BY verseOrd"
    )
    suspend fun downloadedChapter(versionAbbr: String, bookOrd: Int, chapterOrd: Int): List<DownloadedVerseEntity>

    @Query(
        "SELECT * FROM fetchedVerseCache WHERE versionAbbr = :versionAbbr AND bookOrd = :bookOrd " +
            "AND chapterOrd = :chapterOrd ORDER BY verseOrd"
    )
    suspend fun fetchedChapter(versionAbbr: String, bookOrd: Int, chapterOrd: Int): List<FetchedVerseCacheEntity>

    @Query("DELETE FROM downloadedVerses WHERE versionAbbr = :versionAbbr")
    suspend fun deleteDownloadedVersion(versionAbbr: String)
}
