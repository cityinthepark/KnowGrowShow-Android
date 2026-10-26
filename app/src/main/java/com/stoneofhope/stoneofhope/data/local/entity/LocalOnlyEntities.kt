package com.stoneofhope.stoneofhope.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stoneofhope.stoneofhope.data.model.ConflictResolution
import com.stoneofhope.stoneofhope.data.model.SyncConflictType
import java.time.Instant

// Tables in this file never sync to Firestore.

/** Port of iOS `SyncConflict`: a pull that hit a locally dirty row, logged for user review. */
@Entity(tableName = "syncConflicts", indices = [Index("recordId")])
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val recordType: SyncConflictType = SyncConflictType.Note,
    /** Id of the conflicted `Note` / `NoteComment` / `IndexedText`. */
    val recordId: String,
    /** Short display-only snapshots for the resolution sheet — never re-synced from these. */
    val localPreview: String = "",
    val remotePreview: String = "",
    val localUpdatedAt: Instant,
    val remoteUpdatedAt: Instant,
    val detectedAt: Instant,
    /** Null while unresolved. */
    val resolvedAt: Instant? = null,
    val resolution: ConflictResolution? = null,
) {
    val isResolved: Boolean get() = resolvedAt != null
}

/** Port of iOS `DownloadedVerse`: one verse of a fully downloaded Bible version. */
@Entity(tableName = "downloadedVerses", indices = [Index("versionAbbr")])
data class DownloadedVerseEntity(
    @PrimaryKey val id: String,
    val versionAbbr: String,
    val bookOrd: Int,
    val chapterOrd: Int,
    val verseOrd: Int,
    val verseContent: String,
)

/** Port of iOS `DownloadedVersion`. */
@Entity(tableName = "downloadedVersions")
data class DownloadedVersionEntity(
    @PrimaryKey val versionAbbr: String,
    val downloadedOn: Instant,
    val latestHistoryVersionNumber: String? = null,
    val latestVersionDate: Instant? = null,
    val copyrightInfo: String? = null,
)

/** Port of iOS `FetchedVerseCache`: verses fetched on demand from the Bible API. */
@Entity(
    tableName = "fetchedVerseCache",
    indices = [Index("versionAbbr"), Index("bookOrd"), Index("chapterOrd"), Index("verseOrd")],
)
data class FetchedVerseCacheEntity(
    @PrimaryKey val id: String,
    val versionAbbr: String,
    val bookOrd: Int,
    val chapterOrd: Int,
    val verseOrd: Int,
    val verseContent: String,
    val fetchedAt: Instant,
    val lastAccessedAt: Instant,
)
