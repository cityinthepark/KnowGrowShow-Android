package com.stoneofhope.stoneofhope.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stoneofhope.stoneofhope.data.model.NoteType
import java.time.Instant

/**
 * Field-for-field port of iOS `Note` (Models/Note.swift). Relationships are stored the way they
 * travel over the wire: `tags: List<Tag>` → [tagIds], `sermonId` stays a FK.
 *
 * Local [updatedAt] is the domain edit time; on the wire it is `contentUpdatedAt` (the wire
 * `updatedAt` is the server-stamped sync watermark and never stored here).
 */
@Entity(
    tableName = "notes",
    indices = [
        Index("bookOrd"), Index("chapterOrd"), Index("refKey"), Index("chapterKey"),
        Index("isPinned"), Index("sermonId"), Index("youtubeID"), Index("isDeleted"),
        Index("needsSync"), Index("createdAt"),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val noteType: NoteType = NoteType.Note,
    /** Plaintext body. Encrypted only on the wire (CRYPTO_SYNC_CONTRACT §1, v1 has no local encryption). */
    val content: String = "",

    val bookOrd: Int? = null,
    val chapterOrd: Int? = null,
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
    val versionAbbr: String? = null,
    val refKey: String? = null,
    val chapterKey: String? = null,

    val createdAt: Instant,
    val updatedAt: Instant,
    val starLevel: Int = 0,
    val imagePath: String? = null,
    val thumbnailPath: String? = null,
    /** Optional remote thumbnail (e.g. a YouTube CDN URL). */
    val thumbnailUrl: String? = null,

    val isPinned: Boolean? = null,
    val isAnswered: Boolean? = null,
    val answeredAt: Instant? = null,

    /** Ordered `Tag.id`s, exactly as pushed in `tagIds`. */
    val tagIds: List<String> = emptyList(),
    /** Unidirectional FK to `Sermon.id`. Mutually exclusive with [youtubeID]. */
    val sermonId: String? = null,
    val youtubeID: String? = null,

    override val deletedAt: Instant? = null,
    val isDeleted: Boolean = false,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow<NoteEntity> {
    override fun withSyncState(needsSync: Boolean, lastSyncedAt: Instant?) =
        copy(needsSync = needsSync, lastSyncedAt = lastSyncedAt)
}

/** Port of iOS `NoteComment`. `note: Note?` becomes the [noteId] FK (wire field `noteId`). */
@Entity(
    tableName = "noteComments",
    indices = [
        Index("noteId"), Index("sermonId"), Index("youtubeID"), Index("bookOrd"),
        Index("chapterOrd"), Index("refKey"), Index("chapterKey"), Index("isDeleted"),
        Index("needsSync"),
    ],
)
data class NoteCommentEntity(
    @PrimaryKey val id: String,
    val noteId: String? = null,
    val sermonId: String? = null,
    val youtubeID: String? = null,

    val bookOrd: Int? = null,
    val chapterOrd: Int? = null,
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
    val versionAbbr: String? = null,
    val refKey: String? = null,
    val chapterKey: String? = null,

    val content: String = "",
    val createdAt: Instant,
    val starLevel: Int = 0,
    val tagIds: List<String> = emptyList(),

    override val deletedAt: Instant? = null,
    val isDeleted: Boolean = false,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow<NoteCommentEntity> {
    override fun withSyncState(needsSync: Boolean, lastSyncedAt: Instant?) =
        copy(needsSync = needsSync, lastSyncedAt = lastSyncedAt)
}

/** Port of iOS `Tag`. */
@Entity(tableName = "tags", indices = [Index("needsSync")])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Instant,
    override val deletedAt: Instant? = null,
    override val needsSync: Boolean = false,
    override val lastSyncedAt: Instant? = null,
) : SyncedRow<TagEntity> {
    override fun withSyncState(needsSync: Boolean, lastSyncedAt: Instant?) =
        copy(needsSync = needsSync, lastSyncedAt = lastSyncedAt)
}

/**
 * Port of iOS `PinLog` (FIRESTORE_SYNC_CONTRACT §2.5). Document id = [noteId].
 * Not soft-deleted: an empty [pinnedDays] means the Firestore document is physically deleted.
 */
@Entity(tableName = "PinLog", indices = [Index("needsSync")])
data class PinLogEntity(
    @PrimaryKey val noteId: String,
    /** `yyyy-MM-dd` keys, de-duplicated. */
    val pinnedDays: List<String> = emptyList(),
    val needsSync: Boolean = false,
    val lastSyncedAt: Instant? = null,
)

/** Port of iOS `PinLogFirestoreOutbox` (UserDefaults there): PinLog docs waiting for a physical delete. */
@Entity(tableName = "pinLogPendingDeletes")
data class PinLogPendingDeleteEntity(
    @PrimaryKey val firestoreDocumentId: String,
)
