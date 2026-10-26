package com.stoneofhope.stoneofhope.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stoneofhope.stoneofhope.data.model.MarkStyle
import java.time.Instant

/** Port of iOS `FlashCard`. Local [updatedAt] ↔ wire `contentUpdatedAt`. */
@Entity(
    tableName = "flashCards",
    indices = [Index("bookOrd"), Index("chapterOrd"), Index("refKey"), Index("needsSync")],
)
data class FlashCardEntity(
    @PrimaryKey val id: String,
    val bookOrd: Int? = null,
    val chapterOrd: Int? = null,
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
    val versionAbbr: String? = null,
    val refKey: String? = null,
    val topic: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isArchived: Boolean = false,
    override val deletedAt: Instant? = null,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow

/** Port of iOS `ChapterLog`. Field is `chapter` (not `chapterOrd`) on iOS and on the wire. */
@Entity(tableName = "chapterLogs", indices = [Index("bookOrd"), Index("readOn"), Index("needsSync")])
data class ChapterLogEntity(
    @PrimaryKey val id: String,
    val bookOrd: Int,
    val chapter: Int,
    val readOn: Instant,
    override val deletedAt: Instant? = null,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow

/** Port of iOS `BookProgress`. [readChapters] is one 0/1 flag per chapter, e.g. `[1, 0, 1, 0]`. */
@Entity(tableName = "bookProgress", indices = [Index("bookOrd"), Index("needsSync")])
data class BookProgressEntity(
    @PrimaryKey val id: String,
    val bookOrd: Int,
    val readChapters: List<Int> = emptyList(),
    val createdOn: Instant,
    val completedOn: Instant? = null,
    val lastUpdatedOn: Instant,
    override val deletedAt: Instant? = null,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow {
    val isCompleted: Boolean get() = 0 !in readChapters
}

/**
 * Port of iOS `UserVerse` (USER_VERSE_CONTRACT). Primary key is the compound
 * `{versionAbbr}_{bookOrd}_{chapterOrd}_{verseOrd}` — the second documented exception to the
 * ObjectId rule. Always build it with [makeId]; never hand-fill.
 *
 * iOS `marks: List<IndexedText>` is a local-only relationship; here marks are queried by
 * `IndexedTextEntity.sourceId == id`.
 */
@Entity(
    tableName = "userVerses",
    indices = [
        Index("versionAbbr"), Index("bookOrd"), Index("chapterOrd"), Index("verseOrd"),
        Index("isDeleted"), Index("needsSync"),
    ],
)
data class UserVerseEntity(
    @PrimaryKey val id: String,
    val versionAbbr: String,
    val bookOrd: Int,
    val chapterOrd: Int,
    val verseOrd: Int,
    /** Frozen after first non-empty write (USER_VERSE_CONTRACT §3.1). */
    val content: String = "",
    val tagIds: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    override val deletedAt: Instant? = null,
    val isDeleted: Boolean = false,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow {
    val matchesIdInvariant: Boolean get() = id == makeId(versionAbbr, bookOrd, chapterOrd, verseOrd)

    companion object {
        /** Same format as iOS `FetchedVerseCache.makeId`. `versionAbbr` never contains `_`. */
        fun makeId(versionAbbr: String, bookOrd: Int, chapterOrd: Int, verseOrd: Int): String =
            "${versionAbbr}_${bookOrd}_${chapterOrd}_$verseOrd"
    }
}

/**
 * Port of iOS `IndexedText`: a highlight/underline span over a verse. Syncs on its own,
 * independent of its parent `UserVerse` (USER_VERSE_CONTRACT §3.3).
 * Offsets are UTF-16 code unit indices into the verse content (same as Kotlin `String` indices).
 */
@Entity(
    tableName = "indexedTexts",
    indices = [Index("sourceId"), Index("isDeleted"), Index("needsSync")],
)
data class IndexedTextEntity(
    @PrimaryKey val id: String,
    /** Only `"verse"` is implemented; `"note"` is reserved. */
    val sourceType: String = SOURCE_TYPE_VERSE,
    /** For `sourceType == "verse"`: the `UserVerse.id`. */
    val sourceId: String = "",
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val markStyle: MarkStyle = MarkStyle.Highlight,
    val colorKey: String = DEFAULT_COLOR_KEY,
    /** Selected substring, display-only. */
    val indexedText: String = "",
    val createdAt: Instant,
    val updatedAt: Instant,
    override val deletedAt: Instant? = null,
    val isDeleted: Boolean = false,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow {
    companion object {
        const val SOURCE_TYPE_VERSE = "verse"
        const val DEFAULT_COLOR_KEY = "yellow"
    }
}
