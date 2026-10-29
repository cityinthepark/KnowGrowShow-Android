package com.stoneofhope.stoneofhope.data.remote.wire

import com.stoneofhope.stoneofhope.data.crypto.StoneOfHopeCrypto
import com.stoneofhope.stoneofhope.data.local.entity.BookProgressEntity
import com.stoneofhope.stoneofhope.data.local.entity.ChapterLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema.ENCRYPTED_BODY_SCHEMA_VERSION
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema.SCHEMA_VERSION
import java.time.ZoneId

/**
 * Entity → Firestore payload. A port of iOS `FirestoreUserDataPush.performPush`: same field
 * names, same optional-field omission, same `FieldValue.delete()` cleanups, same tombstones.
 *
 * Normal rows are [WriteMode.Merge] writes, so a field omitted here is *left as-is* remotely
 * (e.g. clearing `verseStart` locally does not clear it in Firestore). That is the iOS behaviour
 * and must not be "fixed" on one platform only.
 */
object WireEncoder {

    /** iOS `FirestoreTombstone.slimPayload()`: replaces the whole document. */
    fun tombstone(collection: String, documentId: String) = WireWrite(
        collection = collection,
        documentId = documentId,
        data = mapOf(
            "isDeleted" to true,
            "updatedAt" to WireValue.ServerTimestamp,
        ),
        mode = WriteMode.Replace,
    )

    fun tag(tag: TagEntity, ownerUid: String): WireWrite {
        if (tag.deletedAt != null) return tombstone(FirestoreSchema.TAGS, tag.id)
        return merge(
            FirestoreSchema.TAGS, tag.id,
            "ownerUid" to ownerUid,
            "name" to tag.name,
            "createdAt" to tag.createdAt,
            "schemaVersion" to SCHEMA_VERSION,
            "updatedAt" to WireValue.ServerTimestamp,
        )
    }

    /** [sermonIds] = ids of sermons whose `seriesId` is this series (iOS `series.sermons`). */
    fun sermonSeries(series: SermonSeriesEntity, ownerUid: String, sermonIds: List<String>): WireWrite {
        if (series.deletedAt != null) return tombstone(FirestoreSchema.SERMON_SERIES, series.id)
        return merge(
            FirestoreSchema.SERMON_SERIES, series.id,
            "ownerUid" to ownerUid,
            "title" to series.title,
            "isFinished" to series.isFinished,
            "sermonIds" to sermonIds,
            "schemaVersion" to SCHEMA_VERSION,
            "updatedAt" to WireValue.ServerTimestamp,
            "bookOrd" to series.bookOrd.orDelete(),
            "startedOn" to series.startedOn.ifEmpty { null }.orDelete(),
        )
    }

    fun sermon(sermon: SermonEntity, ownerUid: String): WireWrite {
        if (sermon.deletedAt != null) return tombstone(FirestoreSchema.SERMONS, sermon.id)
        return merge(
            FirestoreSchema.SERMONS, sermon.id,
            "ownerUid" to ownerUid,
            "isUserData" to true,
            "title" to sermon.title,
            "chapters" to sermon.chapters,
            "createdAt" to sermon.createdAt,
            "tagIds" to sermon.tagIds,
            "schemaVersion" to SCHEMA_VERSION,
            "updatedAt" to WireValue.ServerTimestamp,
            "preacher" to sermon.preacher.orDelete(),
            "youtubeID" to sermon.youtubeID.orDelete(),
            "book" to sermon.book.orDelete(),
            "bookOrd" to sermon.bookOrd.orDelete(),
            "chapterRange" to sermon.chapterRange.orDelete(),
            "audioUrl" to sermon.audioUrl.orDelete(),
            "videoUrl" to sermon.videoUrl.orDelete(),
            "pdfUrl" to sermon.pdfUrl.orDelete(),
            "preachedOn" to sermon.preachedOn.ifEmpty { null }.orDelete(),
            "intro" to sermon.intro.orDelete(),
            "seriesId" to sermon.seriesId.orDelete(),
        )
    }

    fun youtubeVideo(video: YouTubeVideoEntity, ownerUid: String): WireWrite {
        if (video.deletedAt != null) return tombstone(FirestoreSchema.YOUTUBE_VIDEOS, video.youtubeID)
        return merge(
            FirestoreSchema.YOUTUBE_VIDEOS, video.youtubeID,
            "ownerUid" to ownerUid,
            "youtubeID" to video.youtubeID,
            "title" to video.title,
            "createdAt" to video.createdAt,
            "tagIds" to video.tagIds,
            "schemaVersion" to SCHEMA_VERSION,
            "updatedAt" to WireValue.ServerTimestamp,
            "channelName" to video.channelName.orDelete(),
            "channelId" to video.channelId.orDelete(),
            "thumbnailUrl" to video.thumbnailUrl.orDelete(),
            "bookOrd" to video.bookOrd.orDelete(),
            "chapterOrd" to video.chapterOrd.orDelete(),
            "sermonId" to video.sermonId.orDelete(),
        )
    }

    /** Never includes marks: `IndexedText` syncs as its own collection (USER_VERSE_CONTRACT §3.3). */
    fun userVerse(verse: UserVerseEntity, ownerUid: String): WireWrite {
        if (verse.deletedAt != null || verse.isDeleted) return tombstone(FirestoreSchema.USER_VERSES, verse.id)
        return merge(
            FirestoreSchema.USER_VERSES, verse.id,
            "ownerUid" to ownerUid,
            "versionAbbr" to verse.versionAbbr,
            "bookOrd" to verse.bookOrd,
            "chapterOrd" to verse.chapterOrd,
            "verseOrd" to verse.verseOrd,
            "content" to verse.content,
            "createdAt" to verse.createdAt,
            "tagIds" to verse.tagIds,
            "schemaVersion" to SCHEMA_VERSION,
            "updatedAt" to WireValue.ServerTimestamp,
            "isDeleted" to false,
        )
    }

    fun indexedText(mark: IndexedTextEntity, ownerUid: String): WireWrite {
        if (mark.deletedAt != null || mark.isDeleted) return tombstone(FirestoreSchema.INDEXED_TEXTS, mark.id)
        return merge(
            FirestoreSchema.INDEXED_TEXTS, mark.id,
            "ownerUid" to ownerUid,
            "sourceType" to mark.sourceType,
            "sourceId" to mark.sourceId,
            "startOffset" to mark.startOffset,
            "endOffset" to mark.endOffset,
            "markStyle" to mark.markStyle.raw,
            "colorKey" to mark.colorKey,
            "indexedText" to mark.indexedText,
            "createdAt" to mark.createdAt,
            "schemaVersion" to SCHEMA_VERSION,
            "updatedAt" to WireValue.ServerTimestamp,
            "isDeleted" to false,
        )
    }

    /**
     * [body] is the encrypted `content`; null only for tombstones (callers must not push a live
     * note while the crypto session is locked — iOS skips it and leaves `needsSync` set).
     * [zone] decides `calendarDay` (local start of `createdAt`, iOS `Date.startOfDay`).
     */
    fun note(note: NoteEntity, ownerUid: String, body: EncryptedBody?, zone: ZoneId): WireWrite {
        if (note.isDeleted || note.deletedAt != null) return tombstone(FirestoreSchema.NOTES, note.id)
        requireNotNull(body) { "Live note ${note.id} needs an encrypted body" }
        val calendarDay = note.createdAt.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        return merge(
            FirestoreSchema.NOTES, note.id,
            "ownerUid" to ownerUid,
            "noteType" to note.noteType.raw,
            "iv" to body.iv,
            "ciphertext" to body.ciphertext,
            "encVersion" to StoneOfHopeCrypto.ENC_VERSION,
            "content" to WireValue.DeleteField,
            "createdAt" to note.createdAt,
            "calendarDay" to calendarDay,
            // `updatedAt` is the server-stamped sync watermark (FIRESTORE_SYNC_CONTRACT §3).
            "updatedAt" to WireValue.ServerTimestamp,
            // `contentUpdatedAt` carries the client-side domain edit time for UI ordering.
            "contentUpdatedAt" to note.updatedAt,
            "starLevel" to note.starLevel,
            "tagIds" to note.tagIds,
            "schemaVersion" to ENCRYPTED_BODY_SCHEMA_VERSION,
            "bookOrd" to note.bookOrd,
            "chapterOrd" to note.chapterOrd,
            "verseStart" to note.verseStart,
            "verseEnd" to note.verseEnd,
            "versionAbbr" to note.versionAbbr,
            "refKey" to note.refKey,
            "chapterKey" to note.chapterKey,
            "imagePath" to note.imagePath,
            "thumbnailPath" to note.thumbnailPath,
            "thumbnailUrl" to note.thumbnailUrl,
            "youtubeID" to note.youtubeID,
            "isPinned" to note.isPinned,
            "isOngoing" to WireValue.DeleteField,
            "isAnswered" to note.isAnswered,
            "answeredAt" to note.answeredAt,
            // Pushed even if the local Sermon row is orphaned (NOTE_CONTAINER_ID_REFACTOR §7.1).
            "sermonId" to note.sermonId,
            "isDeleted" to false,
            "prayerOngoingIntervals" to WireValue.DeleteField,
        )
    }

    fun noteComment(comment: NoteCommentEntity, ownerUid: String, body: EncryptedBody?): WireWrite {
        if (comment.isDeleted || comment.deletedAt != null) return tombstone(FirestoreSchema.NOTE_COMMENTS, comment.id)
        requireNotNull(body) { "Live comment ${comment.id} needs an encrypted body" }
        return merge(
            FirestoreSchema.NOTE_COMMENTS, comment.id,
            "ownerUid" to ownerUid,
            "iv" to body.iv,
            "ciphertext" to body.ciphertext,
            "encVersion" to StoneOfHopeCrypto.ENC_VERSION,
            "content" to WireValue.DeleteField,
            "createdAt" to comment.createdAt,
            "starLevel" to comment.starLevel,
            "tagIds" to comment.tagIds,
            "schemaVersion" to ENCRYPTED_BODY_SCHEMA_VERSION,
            "updatedAt" to WireValue.ServerTimestamp,
            "noteId" to comment.noteId,
            "sermonId" to comment.sermonId,
            "bookOrd" to comment.bookOrd,
            "chapterOrd" to comment.chapterOrd,
            "verseStart" to comment.verseStart,
            "verseEnd" to comment.verseEnd,
            "versionAbbr" to comment.versionAbbr,
            "refKey" to comment.refKey,
            "chapterKey" to comment.chapterKey,
            "youtubeID" to comment.youtubeID,
            "isDeleted" to false,
        )
    }

    fun flashCard(card: FlashCardEntity, ownerUid: String): WireWrite {
        if (card.deletedAt != null) return tombstone(FirestoreSchema.FLASH_CARDS, card.id)
        return merge(
            FirestoreSchema.FLASH_CARDS, card.id,
            "ownerUid" to ownerUid,
            "createdAt" to card.createdAt,
            "updatedAt" to WireValue.ServerTimestamp,
            "contentUpdatedAt" to card.updatedAt,
            "isArchived" to card.isArchived,
            "schemaVersion" to SCHEMA_VERSION,
            "bookOrd" to card.bookOrd,
            "chapterOrd" to card.chapterOrd,
            "verseStart" to card.verseStart,
            "verseEnd" to card.verseEnd,
            "versionAbbr" to card.versionAbbr,
            "refKey" to card.refKey,
            "topic" to card.topic,
            // Legacy verse text moved to `userVerses`.
            "content" to WireValue.DeleteField,
        )
    }

    fun chapterLog(log: ChapterLogEntity, ownerUid: String): WireWrite {
        if (log.deletedAt != null) return tombstone(FirestoreSchema.CHAPTER_LOGS, log.id)
        return merge(
            FirestoreSchema.CHAPTER_LOGS, log.id,
            "ownerUid" to ownerUid,
            "bookOrd" to log.bookOrd,
            "chapter" to log.chapter,
            "readOn" to log.readOn,
            "schemaVersion" to SCHEMA_VERSION,
            "updatedAt" to WireValue.ServerTimestamp,
        )
    }

    fun bookProgress(progress: BookProgressEntity, ownerUid: String): WireWrite {
        if (progress.deletedAt != null) return tombstone(FirestoreSchema.BOOK_PROGRESS, progress.id)
        return merge(
            FirestoreSchema.BOOK_PROGRESS, progress.id,
            "ownerUid" to ownerUid,
            "bookOrd" to progress.bookOrd,
            "readChapters" to progress.readChapters,
            "createdOn" to progress.createdOn,
            "lastUpdatedOn" to progress.lastUpdatedOn,
            "schemaVersion" to SCHEMA_VERSION,
            "updatedAt" to WireValue.ServerTimestamp,
            "completedOn" to progress.completedOn,
        )
    }

    /** Document id is the note id. iOS writes PinLog with a full `setData` + merge, never a tombstone. */
    fun pinLog(log: PinLogEntity, ownerUid: String): WireWrite = merge(
        FirestoreSchema.PIN_LOG, log.noteId,
        "ownerUid" to ownerUid,
        "noteId" to log.noteId,
        "pinnedDays" to log.pinnedDays,
        "schemaVersion" to SCHEMA_VERSION,
        "updatedAt" to WireValue.ServerTimestamp,
    )

    /** Physical delete for a PinLog whose `pinnedDays` became empty (contract §2.5 exception). */
    fun pinLogDelete(documentId: String) =
        WireWrite(FirestoreSchema.PIN_LOG, documentId, emptyMap(), WriteMode.Delete)

    /** Merge write; `null` values are omitted, mirroring iOS `if let v = ... { d[key] = v }`. */
    /**
     * An optional field that is now empty is deleted remotely. iOS leaves it out of the merge write,
     * so a value cleared on the device stays in Firestore and comes back on other devices
     * (IOS_ISSUES K1). The document looks exactly like one iOS writes from scratch.
     */
    private fun Any?.orDelete(): Any = this ?: WireValue.DeleteField

    private fun merge(collection: String, documentId: String, vararg fields: Pair<String, Any?>) = WireWrite(
        collection = collection,
        documentId = documentId,
        data = fields.filter { it.second != null }.toMap(),
        mode = WriteMode.Merge,
    )
}
