package com.stoneofhope.stoneofhope.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Port of iOS `SermonSeries`. iOS derives membership from the inverse of `Sermon.series`;
 * here membership lives only in [SermonEntity.seriesId], and the wire `sermonIds` array is
 * computed from it at push time.
 */
@Entity(tableName = "sermonSeries", indices = [Index("startedOn"), Index("churchKey"), Index("needsSync")])
data class SermonSeriesEntity(
    @PrimaryKey val id: String,
    val title: String,
    val bookOrd: Int? = null,
    /** Calendar day when the series started (`yyyyMMdd`), or empty. */
    val startedOn: String = "",
    val isFinished: Boolean = false,
    /** Set on church JSON imports (e.g. `riverside`). User-created series stay null. */
    val churchKey: String? = null,
    /** `true` = user-created (may sync). `false` = church import (local only). */
    val isUserData: Boolean = true,
    override val deletedAt: Instant? = null,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow<SermonSeriesEntity> {
    override fun withSyncState(needsSync: Boolean, lastSyncedAt: Instant?) =
        copy(needsSync = needsSync, lastSyncedAt = lastSyncedAt)
}

/** Port of iOS `Sermon`. `series: SermonSeries?` becomes the [seriesId] FK (wire field `seriesId`). */
@Entity(
    tableName = "sermons",
    indices = [Index("churchKey"), Index("preachedOn"), Index("seriesId"), Index("needsSync")],
)
data class SermonEntity(
    @PrimaryKey val id: String,
    val title: String,
    val preacher: String? = null,
    val youtubeID: String? = null,
    /** Set only on church JSON imports (`isUserData == false`). */
    val churchKey: String? = null,
    val book: String? = null,
    val bookOrd: Int? = null,
    val chapterRange: String? = null,
    val chapters: List<Int> = emptyList(),
    val audioUrl: String? = null,
    val videoUrl: String? = null,
    val pdfUrl: String? = null,
    /** Calendar day the sermon was preached (`yyyyMMdd`), or empty. */
    val preachedOn: String = "",
    val createdAt: Instant,
    val seriesId: String? = null,
    val intro: String? = null,
    val tagIds: List<String> = emptyList(),
    override val deletedAt: Instant? = null,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
    /** `true` = user-created (may sync). `false` = church import (local only). */
    val isUserData: Boolean = true,
) : SyncedRow<SermonEntity> {
    override fun withSyncState(needsSync: Boolean, lastSyncedAt: Instant?) =
        copy(needsSync = needsSync, lastSyncedAt = lastSyncedAt)
}

/**
 * Port of iOS `YouTubeVideo`. Primary key is the YouTube video id itself — the documented
 * exception to the ObjectId rule (FIRESTORE_SYNC_CONTRACT §1).
 */
@Entity(
    tableName = "youtubeVideos",
    indices = [Index("channelId"), Index("bookOrd"), Index("chapterOrd"), Index("sermonId"), Index("needsSync")],
)
data class YouTubeVideoEntity(
    @PrimaryKey val youtubeID: String,
    val title: String,
    val channelName: String? = null,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val bookOrd: Int? = null,
    val chapterOrd: Int? = null,
    /** When set, this row is a hidden dedup index for a Sermon (not shown in Video UI). */
    val sermonId: String? = null,
    val tagIds: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    override val deletedAt: Instant? = null,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow<YouTubeVideoEntity> {
    override fun withSyncState(needsSync: Boolean, lastSyncedAt: Instant?) =
        copy(needsSync = needsSync, lastSyncedAt = lastSyncedAt)

    /** Personal catalog entries only (`sermonId == null`). Sermon index rows stay out of Video UI. */
    val isPersonalCatalogEntry: Boolean get() = sermonId == null
}
