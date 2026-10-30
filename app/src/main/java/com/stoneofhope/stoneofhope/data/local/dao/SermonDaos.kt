package com.stoneofhope.stoneofhope.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface SermonSeriesDao {
    @Upsert suspend fun upsert(series: SermonSeriesEntity)

    @Query("SELECT * FROM sermonSeries WHERE id = :id")
    suspend fun getById(id: String): SermonSeriesEntity?

    @Query("SELECT * FROM sermonSeries WHERE deletedAt IS NULL ORDER BY startedOn DESC")
    fun observeActive(): Flow<List<SermonSeriesEntity>>

    @Query("SELECT * FROM sermonSeries WHERE deletedAt IS NULL ORDER BY startedOn DESC")
    suspend fun active(): List<SermonSeriesEntity>

    @Query("SELECT * FROM sermonSeries WHERE id = :id")
    fun observeById(id: String): Flow<SermonSeriesEntity?>

    /** Church imports (`isUserData == 0`) never sync. */
    @Query("SELECT * FROM sermonSeries WHERE needsSync = 1 AND isUserData = 1")
    suspend fun dirty(): List<SermonSeriesEntity>

    @Query("DELETE FROM sermonSeries WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM sermonSeries WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM sermonSeries WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE sermonSeries SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface SermonDao {
    @Upsert suspend fun upsert(sermon: SermonEntity)

    /** Rows carrying [tagId], deleted or not (iOS `reassignTag` walks every object). */
    @Query("SELECT * FROM sermons WHERE tagIds LIKE '%\"' || :tagId || '\"%'")
    suspend fun withTag(tagId: String): List<SermonEntity>

    @Query("SELECT * FROM sermons WHERE id = :id")
    suspend fun getById(id: String): SermonEntity?

    @Query("SELECT * FROM sermons WHERE id = :id")
    fun observeById(id: String): Flow<SermonEntity?>

    /** iOS `sermon(withYouTubeID:)`. */
    @Query("SELECT * FROM sermons WHERE youtubeID = :youtubeID AND deletedAt IS NULL LIMIT 1")
    suspend fun activeWithYouTubeID(youtubeID: String): SermonEntity?

    /** iOS `createSermon(youtubeID:title:refText:preacher:)` second lookup. */
    @Query("SELECT * FROM sermons WHERE title = :title AND chapterRange = :chapterRange AND deletedAt IS NULL LIMIT 1")
    suspend fun activeWithTitleAndRange(title: String, chapterRange: String): SermonEntity?

    /** Titles for iOS `uniqueSermonTitle(for:)`. */
    @Query("SELECT title FROM sermons WHERE deletedAt IS NULL AND (title = :base OR title LIKE :base || ' - %')")
    suspend fun activeTitlesLike(base: String): List<String>

    @Query("SELECT * FROM sermons WHERE deletedAt IS NULL ORDER BY preachedOn DESC, createdAt DESC")
    fun observeActive(): Flow<List<SermonEntity>>

    /** iOS `Note.hasLinkedSermon` resolves the FK against live sermons. */
    @Query("SELECT id FROM sermons WHERE deletedAt IS NULL")
    fun observeActiveIds(): Flow<List<String>>

    /** iOS `getSermons(on:)`: `preachedOn` day key (`yyyyMMdd`), church imports included. */
    @Query("SELECT * FROM sermons WHERE preachedOn = :dayKey AND deletedAt IS NULL")
    fun observePreachedOn(dayKey: String): Flow<List<SermonEntity>>

    /** Day keys in [startKey, endKeyExclusive) — `yyyyMMdd` sorts like the dates (iOS `CalendarDayKey.queryRange`). */
    @Query("SELECT * FROM sermons WHERE preachedOn >= :startKey AND preachedOn < :endKeyExclusive AND deletedAt IS NULL")
    fun observePreachedBetween(startKey: String, endKeyExclusive: String): Flow<List<SermonEntity>>

    /**
     * iOS `fetchSermonsByBibleChapter` also requires `ANY chapters == c`; `chapters` is a JSON column
     * here, so callers filter by chapter.
     */
    @Query("SELECT * FROM sermons WHERE bookOrd = :bookOrd AND deletedAt IS NULL")
    fun observeActiveForBook(bookOrd: Int): Flow<List<SermonEntity>>

    /**
     * Members of a series in the order pushed as `sermonSeries.sermonIds`.
     * Includes soft-deleted rows, like iOS `series.sermons` (LinkingObjects ignore `deletedAt`).
     */
    @Query("SELECT id FROM sermons WHERE seriesId = :seriesId ORDER BY preachedOn, createdAt, id")
    suspend fun idsInSeries(seriesId: String): List<String>

    @Query("UPDATE sermons SET seriesId = :seriesId WHERE id = :sermonId")
    suspend fun setSeries(sermonId: String, seriesId: String?)

    /** Live church-import sermons of [churchKey] (iOS `hasImported`'s Realm check). */
    @Query("SELECT COUNT(*) FROM sermons WHERE churchKey = :churchKey AND isUserData = 0 AND deletedAt IS NULL")
    suspend fun countActiveForChurch(churchKey: String): Int

    @Query("SELECT * FROM sermons WHERE needsSync = 1 AND isUserData = 1")
    suspend fun dirty(): List<SermonEntity>

    @Query("DELETE FROM sermons WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM sermons WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM sermons WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE sermons SET needsSync = 1")
    suspend fun markAllDirty()
}

@Dao
interface YouTubeVideoDao {
    @Upsert suspend fun upsert(video: YouTubeVideoEntity)

    /** Rows carrying [tagId], deleted or not (iOS `reassignTag` walks every object). */
    @Query("SELECT * FROM youtubeVideos WHERE tagIds LIKE '%\"' || :tagId || '\"%'")
    suspend fun withTag(tagId: String): List<YouTubeVideoEntity>

    @Query("SELECT * FROM youtubeVideos WHERE youtubeID = :youtubeID")
    suspend fun getById(youtubeID: String): YouTubeVideoEntity?

    @Query("SELECT * FROM youtubeVideos WHERE youtubeID = :youtubeID")
    fun observeById(youtubeID: String): Flow<YouTubeVideoEntity?>

    /** Personal catalog only: sermon dedup-index rows (`sermonId` set) stay out of Video UI. */
    @Query("SELECT * FROM youtubeVideos WHERE deletedAt IS NULL AND sermonId IS NULL ORDER BY createdAt DESC")
    fun observePersonalCatalog(): Flow<List<YouTubeVideoEntity>>

    /** iOS `getYouTubeVideos(on:)`: personal rows created in [start, end), newest first. */
    @Query(
        "SELECT * FROM youtubeVideos WHERE deletedAt IS NULL AND sermonId IS NULL " +
            "AND createdAt >= :start AND createdAt < :end ORDER BY createdAt DESC"
    )
    fun observeCreatedBetween(start: Instant, end: Instant): Flow<List<YouTubeVideoEntity>>

    /** iOS `Note.hasLinkedYouTubeVideo` resolves the id against live videos. */
    @Query("SELECT youtubeID FROM youtubeVideos WHERE deletedAt IS NULL")
    fun observeActiveIds(): Flow<List<String>>

    @Query("SELECT * FROM youtubeVideos WHERE needsSync = 1")
    suspend fun dirty(): List<YouTubeVideoEntity>

    @Query("DELETE FROM youtubeVideos WHERE youtubeID = :youtubeID")
    suspend fun hardDelete(youtubeID: String)

    @Query("DELETE FROM youtubeVideos WHERE deletedAt IS NOT NULL AND needsSync = 0")
    suspend fun purgeSyncedTombstones(): Int

    @Query("DELETE FROM youtubeVideos WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE youtubeVideos SET needsSync = 1")
    suspend fun markAllDirty()
}
