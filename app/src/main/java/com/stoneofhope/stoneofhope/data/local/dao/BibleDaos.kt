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
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashCardDao {
    @Upsert suspend fun upsert(card: FlashCardEntity)

    @Query("SELECT * FROM flashCards WHERE id = :id")
    suspend fun getById(id: String): FlashCardEntity?

    @Query("SELECT * FROM flashCards WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<FlashCardEntity>>

    @Query("SELECT * FROM flashCards WHERE needsSync = 1")
    suspend fun dirty(): List<FlashCardEntity>

    @Query("DELETE FROM flashCards WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM flashCards WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int
}

@Dao
interface ChapterLogDao {
    @Upsert suspend fun upsert(log: ChapterLogEntity)

    @Query("SELECT * FROM chapterLogs WHERE id = :id")
    suspend fun getById(id: String): ChapterLogEntity?

    @Query("SELECT * FROM chapterLogs WHERE deletedAt IS NULL ORDER BY readOn DESC")
    fun observeActive(): Flow<List<ChapterLogEntity>>

    @Query("SELECT * FROM chapterLogs WHERE needsSync = 1")
    suspend fun dirty(): List<ChapterLogEntity>

    @Query("DELETE FROM chapterLogs WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM chapterLogs WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int
}

@Dao
interface BookProgressDao {
    @Upsert suspend fun upsert(progress: BookProgressEntity)

    @Query("SELECT * FROM bookProgress WHERE id = :id")
    suspend fun getById(id: String): BookProgressEntity?

    @Query("SELECT * FROM bookProgress WHERE deletedAt IS NULL ORDER BY lastUpdatedOn DESC")
    fun observeActive(): Flow<List<BookProgressEntity>>

    @Query("SELECT * FROM bookProgress WHERE needsSync = 1")
    suspend fun dirty(): List<BookProgressEntity>

    @Query("DELETE FROM bookProgress WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM bookProgress WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int
}

@Dao
interface UserVerseDao {
    @Upsert suspend fun upsert(verse: UserVerseEntity)

    @Query("SELECT * FROM userVerses WHERE id = :id")
    suspend fun getById(id: String): UserVerseEntity?

    @Query(
        "SELECT * FROM userVerses WHERE versionAbbr = :versionAbbr AND bookOrd = :bookOrd " +
            "AND chapterOrd = :chapterOrd AND deletedAt IS NULL AND isDeleted = 0 ORDER BY verseOrd"
    )
    fun observeChapter(versionAbbr: String, bookOrd: Int, chapterOrd: Int): Flow<List<UserVerseEntity>>

    @Query("SELECT * FROM userVerses WHERE needsSync = 1")
    suspend fun dirty(): List<UserVerseEntity>

    @Query("DELETE FROM userVerses WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM userVerses WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int
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

    @Query("SELECT * FROM indexedTexts WHERE needsSync = 1")
    suspend fun dirty(): List<IndexedTextEntity>

    @Query("DELETE FROM indexedTexts WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM indexedTexts WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int
}

@Dao
interface SyncConflictDao {
    @Upsert suspend fun upsert(conflict: SyncConflictEntity)

    @Query("SELECT * FROM syncConflicts WHERE resolvedAt IS NULL ORDER BY detectedAt DESC")
    fun observeUnresolved(): Flow<List<SyncConflictEntity>>
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
