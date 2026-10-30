package com.stoneofhope.stoneofhope.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.stoneofhope.stoneofhope.data.local.dao.BibleCacheDao
import com.stoneofhope.stoneofhope.data.local.dao.BookProgressDao
import com.stoneofhope.stoneofhope.data.local.dao.ChapterLogDao
import com.stoneofhope.stoneofhope.data.local.dao.FlashCardDao
import com.stoneofhope.stoneofhope.data.local.dao.IndexedTextDao
import com.stoneofhope.stoneofhope.data.local.dao.NoteCommentDao
import com.stoneofhope.stoneofhope.data.local.dao.NoteDao
import com.stoneofhope.stoneofhope.data.local.dao.OnlineChurchDao
import com.stoneofhope.stoneofhope.data.local.dao.PinLogDao
import com.stoneofhope.stoneofhope.data.local.dao.SermonDao
import com.stoneofhope.stoneofhope.data.local.dao.SermonSeriesDao
import com.stoneofhope.stoneofhope.data.local.dao.SyncConflictDao
import com.stoneofhope.stoneofhope.data.local.dao.TagDao
import com.stoneofhope.stoneofhope.data.local.dao.UserVerseDao
import com.stoneofhope.stoneofhope.data.local.dao.YouTubeVideoDao
import com.stoneofhope.stoneofhope.data.local.entity.BookProgressEntity
import com.stoneofhope.stoneofhope.data.local.entity.ChapterLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.DownloadedVerseEntity
import com.stoneofhope.stoneofhope.data.local.entity.DownloadedVersionEntity
import com.stoneofhope.stoneofhope.data.local.entity.FetchedVerseCacheEntity
import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.OnlineChurchEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogPendingDeleteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.local.entity.SyncConflictEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity

/**
 * Single source of truth on device, signed in or not. Firestore is only a transport
 * (iOS keeps the same split with Realm).
 *
 * The only place that opens the database is [DatabaseModule]; local encryption (e.g. SQLCipher)
 * would plug in there without touching DAOs.
 */
@Database(
    entities = [
        NoteEntity::class,
        NoteCommentEntity::class,
        TagEntity::class,
        PinLogEntity::class,
        PinLogPendingDeleteEntity::class,
        SermonSeriesEntity::class,
        SermonEntity::class,
        YouTubeVideoEntity::class,
        FlashCardEntity::class,
        ChapterLogEntity::class,
        BookProgressEntity::class,
        UserVerseEntity::class,
        IndexedTextEntity::class,
        SyncConflictEntity::class,
        DownloadedVerseEntity::class,
        DownloadedVersionEntity::class,
        FetchedVerseCacheEntity::class,
        OnlineChurchEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteCommentDao(): NoteCommentDao
    abstract fun tagDao(): TagDao
    abstract fun pinLogDao(): PinLogDao
    abstract fun sermonSeriesDao(): SermonSeriesDao
    abstract fun sermonDao(): SermonDao
    abstract fun youTubeVideoDao(): YouTubeVideoDao
    abstract fun flashCardDao(): FlashCardDao
    abstract fun chapterLogDao(): ChapterLogDao
    abstract fun bookProgressDao(): BookProgressDao
    abstract fun userVerseDao(): UserVerseDao
    abstract fun indexedTextDao(): IndexedTextDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun bibleCacheDao(): BibleCacheDao
    abstract fun onlineChurchDao(): OnlineChurchDao

    companion object {
        const val NAME = "stoneofhope.db"
    }
}
