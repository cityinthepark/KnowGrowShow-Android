package com.stoneofhope.stoneofhope.data.remote.wire

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
import com.stoneofhope.stoneofhope.data.model.MarkStyle
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import java.time.Instant

/** What pull should do with one remote document. */
sealed interface Decoded<out T> {
    /** Write this merged row locally (already marked clean: `needsSync = false`). */
    data class Row<T>(val value: T) : Decoded<T>

    /** Remote tombstone (or empty PinLog): hard-delete the local row if it exists. */
    data object Tombstone : Decoded<Nothing>

    data class Skip(val reason: SkipReason) : Decoded<Nothing>
}

enum class SkipReason {
    InvalidDocumentId,
    UnsupportedSchema,
    MissingRequiredField,

    /** Church-import sermon; never merged from the cloud. */
    NotUserData,

    /** Encrypted body unreadable — pull must hold its cursor back (iOS "K2"). */
    UnreadableBody,

    /** `userVerses` doc whose id ≠ `{versionAbbr}_{bookOrd}_{chapterOrd}_{verseOrd}`. iOS traps here. */
    IdInvariantViolated,
}

/** A series row plus the ordered `sermonIds` used to rebuild `Sermon.seriesId` after sermons merge. */
data class DecodedSeries(val series: SermonSeriesEntity, val sermonIds: List<String>)

/**
 * Firestore payload → entity. A port of iOS `FirestoreUserDataPull.merge*`.
 *
 * Callers must check `existing.needsSync` *before* calling (local dirty rows are never
 * overwritten, FIRESTORE_SYNC_CONTRACT §2.2). [now] stands in for iOS `Date()` fallbacks and
 * becomes `lastSyncedAt`.
 *
 * Deliberate difference from iOS: id references (`tagIds`, `noteId`, `seriesId`) are kept even when
 * the target row isn't local yet, instead of being dropped. Realm needs the object to link it; Room
 * stores the id, so an out-of-order pull no longer loses the reference.
 */
object WireDecoder {

    fun tag(documentId: String, data: WireData, existing: TagEntity?, now: Instant): Decoded<TagEntity> {
        val id = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        val name = data.string("name") ?: return skip(SkipReason.MissingRequiredField)
        return Decoded.Row(
            (existing ?: TagEntity(id = id, name = name, createdAt = now)).copy(
                name = name,
                createdAt = data.instant("createdAt") ?: now,
                deletedAt = null,
                needsSync = false,
                lastSyncedAt = now,
            )
        )
    }

    fun sermonSeries(
        documentId: String,
        data: WireData,
        existing: SermonSeriesEntity?,
        now: Instant,
    ): Decoded<DecodedSeries> {
        val id = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        val title = data.string("title") ?: return skip(SkipReason.MissingRequiredField)
        val series = (existing ?: SermonSeriesEntity(id = id, title = title)).copy(
            title = title,
            isFinished = data.bool("isFinished") ?: false,
            bookOrd = data.int("bookOrd"),
            startedOn = data.string("startedOn") ?: "",
            deletedAt = null,
            needsSync = false,
            lastSyncedAt = now,
        )
        val sermonIds = data.stringList("sermonIds")?.mapNotNull(ObjectIds::parse).orEmpty()
        return Decoded.Row(DecodedSeries(series, sermonIds))
    }

    fun sermon(documentId: String, data: WireData, existing: SermonEntity?, now: Instant): Decoded<SermonEntity> {
        val id = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        if (data.int("schemaVersion").let { (it ?: 1) > FirestoreSchema.SUPPORTED_SCHEMA_VERSION }) {
            return skip(SkipReason.UnsupportedSchema)
        }
        if (data.bool("isUserData") == false) return skip(SkipReason.NotUserData)
        if (existing != null && !existing.isUserData) return skip(SkipReason.NotUserData)
        if (isRemoteTombstone(data)) return Decoded.Tombstone
        val title = data.string("title") ?: return skip(SkipReason.MissingRequiredField)
        val base = existing ?: SermonEntity(id = id, title = title, createdAt = now)
        return Decoded.Row(
            base.copy(
                title = title,
                preacher = data.string("preacher"),
                youtubeID = data.string("youtubeID"),
                churchKey = null,
                book = data.string("book"),
                bookOrd = data.int("bookOrd"),
                chapterRange = data.string("chapterRange"),
                // iOS only replaces chapters when the field is a valid [Int].
                chapters = data.intList("chapters") ?: base.chapters,
                audioUrl = data.string("audioUrl"),
                videoUrl = data.string("videoUrl"),
                pdfUrl = data.string("pdfUrl"),
                preachedOn = data.string("preachedOn") ?: "",
                intro = data.string("intro"),
                createdAt = data.instant("createdAt") ?: now,
                isUserData = true,
                deletedAt = null,
                needsSync = false,
                lastSyncedAt = now,
                tagIds = tagIds(data),
                seriesId = ObjectIds.parse(data.string("seriesId")),
            )
        )
    }

    /** Document id is the YouTube video id, not an ObjectId (FIRESTORE_SYNC_CONTRACT §1). */
    fun youtubeVideo(
        documentId: String,
        data: WireData,
        existing: YouTubeVideoEntity?,
        now: Instant,
    ): Decoded<YouTubeVideoEntity> {
        if (documentId.isEmpty()) return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        val title = data.string("title") ?: return skip(SkipReason.MissingRequiredField)
        val base = existing ?: YouTubeVideoEntity(youtubeID = documentId, title = title, createdAt = now, updatedAt = now)
        return Decoded.Row(
            base.copy(
                title = title,
                channelName = data.string("channelName"),
                channelId = data.string("channelId"),
                thumbnailUrl = data.string("thumbnailUrl"),
                bookOrd = data.int("bookOrd"),
                chapterOrd = data.int("chapterOrd"),
                sermonId = ObjectIds.parse(data.string("sermonId")),
                createdAt = data.instant("createdAt") ?: now,
                deletedAt = null,
                needsSync = false,
                lastSyncedAt = now,
                tagIds = tagIds(data),
            )
        )
    }

    /** [noteKey] null = crypto session locked; v6 bodies are then [SkipReason.UnreadableBody]. */
    fun note(
        documentId: String,
        data: WireData,
        existing: NoteEntity?,
        noteKey: ByteArray?,
        now: Instant,
    ): Decoded<NoteEntity> {
        val id = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        val body = NoteBodyCodec.decode(data, noteKey)
        if (body is RemoteBody.Unreadable) return skip(SkipReason.UnreadableBody)

        val createdAt = data.instant("createdAt") ?: now
        val base = existing ?: NoteEntity(id = id, createdAt = now, updatedAt = now)
        val isPinned = data.bool("isPinned") ?: if (data.bool("isOngoing") == true) true else null
        return Decoded.Row(
            base.copy(
                // Unknown raw values (a newer client's type) keep the current value, like iOS.
                noteType = NoteType.fromRaw(data.string("noteType")) ?: base.noteType,
                content = (body as? RemoteBody.Readable)?.text ?: base.content,
                createdAt = createdAt,
                updatedAt = data.instant("contentUpdatedAt") ?: data.instant("createdAt") ?: now,
                starLevel = data.int("starLevel") ?: 0,
                bookOrd = data.int("bookOrd"),
                chapterOrd = data.int("chapterOrd"),
                verseStart = data.int("verseStart"),
                verseEnd = data.int("verseEnd"),
                versionAbbr = data.string("versionAbbr"),
                refKey = data.string("refKey"),
                chapterKey = data.string("chapterKey"),
                imagePath = data.string("imagePath"),
                thumbnailPath = data.string("thumbnailPath"),
                thumbnailUrl = data.string("thumbnailUrl"),
                youtubeID = data.string("youtubeID"),
                isPinned = isPinned,
                isAnswered = data.bool("isAnswered"),
                answeredAt = data.instant("answeredAt"),
                deletedAt = null,
                isDeleted = false,
                needsSync = false,
                lastSyncedAt = now,
                sermonId = ObjectIds.parse(data.string("sermonId")),
                tagIds = tagIds(data),
            )
        )
    }

    /** Document id = note id. Empty `pinnedDays` → [Decoded.Tombstone] (physical delete). */
    fun pinLog(documentId: String, data: WireData, existing: PinLogEntity?, now: Instant): Decoded<PinLogEntity> {
        val noteId = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        if (!schemaOk(data)) return skip(SkipReason.UnsupportedSchema)
        val days = data.stringList("pinnedDays").orEmpty()
        if (days.isEmpty()) return Decoded.Tombstone
        return Decoded.Row(
            (existing ?: PinLogEntity(noteId = noteId)).copy(
                pinnedDays = days.sorted(),
                needsSync = false,
                lastSyncedAt = now,
            )
        )
    }

    fun noteComment(
        documentId: String,
        data: WireData,
        existing: NoteCommentEntity?,
        noteKey: ByteArray?,
        now: Instant,
    ): Decoded<NoteCommentEntity> {
        val id = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        val body = NoteBodyCodec.decode(data, noteKey)
        if (body is RemoteBody.Unreadable) return skip(SkipReason.UnreadableBody)

        val base = existing ?: NoteCommentEntity(id = id, createdAt = now)
        return Decoded.Row(
            base.copy(
                content = (body as? RemoteBody.Readable)?.text ?: base.content,
                createdAt = data.instant("createdAt") ?: now,
                starLevel = data.int("starLevel") ?: 0,
                bookOrd = data.int("bookOrd"),
                chapterOrd = data.int("chapterOrd"),
                verseStart = data.int("verseStart"),
                verseEnd = data.int("verseEnd"),
                versionAbbr = data.string("versionAbbr"),
                refKey = data.string("refKey"),
                chapterKey = data.string("chapterKey"),
                youtubeID = data.string("youtubeID"),
                deletedAt = null,
                isDeleted = false,
                needsSync = false,
                lastSyncedAt = now,
                noteId = ObjectIds.parse(data.string("noteId")),
                sermonId = ObjectIds.parse(data.string("sermonId")),
                tagIds = tagIds(data),
            )
        )
    }

    /**
     * Legacy docs may still carry verse text in `content`; iOS backfills it into `userVerses`
     * (`FlashCardUserVerseBackfill`). [legacyContent] exposes it for the sync engine to do the same.
     */
    data class DecodedFlashCard(val card: FlashCardEntity, val legacyContent: String)

    fun flashCard(
        documentId: String,
        data: WireData,
        existing: FlashCardEntity?,
        now: Instant,
    ): Decoded<DecodedFlashCard> {
        val id = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        val base = existing ?: FlashCardEntity(id = id, createdAt = now, updatedAt = now)
        val card = base.copy(
            createdAt = data.instant("createdAt") ?: now,
            updatedAt = data.instant("contentUpdatedAt") ?: data.instant("createdAt") ?: now,
            isArchived = data.bool("isArchived") ?: false,
            topic = data.string("topic"),
            bookOrd = data.int("bookOrd"),
            chapterOrd = data.int("chapterOrd"),
            verseStart = data.int("verseStart"),
            verseEnd = data.int("verseEnd"),
            versionAbbr = data.string("versionAbbr"),
            refKey = data.string("refKey"),
            deletedAt = null,
            needsSync = false,
            lastSyncedAt = now,
        )
        return Decoded.Row(DecodedFlashCard(card, data.string("content") ?: ""))
    }

    fun chapterLog(documentId: String, data: WireData, existing: ChapterLogEntity?, now: Instant): Decoded<ChapterLogEntity> {
        val id = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        return Decoded.Row(
            (existing ?: ChapterLogEntity(id = id, bookOrd = 0, chapter = 0, readOn = now)).copy(
                bookOrd = data.int("bookOrd") ?: 0,
                chapter = data.int("chapter") ?: 0,
                readOn = data.instant("readOn") ?: now,
                deletedAt = null,
                needsSync = false,
                lastSyncedAt = now,
            )
        )
    }

    fun bookProgress(
        documentId: String,
        data: WireData,
        existing: BookProgressEntity?,
        now: Instant,
    ): Decoded<BookProgressEntity> {
        val id = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        val base = existing ?: BookProgressEntity(id = id, bookOrd = 0, createdOn = now, lastUpdatedOn = now)
        return Decoded.Row(
            base.copy(
                // iOS only overwrites these two when present.
                bookOrd = data.int("bookOrd") ?: base.bookOrd,
                readChapters = data.intList("readChapters") ?: base.readChapters,
                createdOn = data.instant("createdOn") ?: now,
                completedOn = data.instant("completedOn"),
                lastUpdatedOn = data.instant("lastUpdatedOn") ?: now,
                deletedAt = null,
                needsSync = false,
                lastSyncedAt = now,
            )
        )
    }

    /** Document id is the compound verse key (second documented exception, contract §1). */
    fun userVerse(documentId: String, data: WireData, existing: UserVerseEntity?, now: Instant): Decoded<UserVerseEntity> {
        if (documentId.isEmpty()) return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        val versionAbbr = data.string("versionAbbr")
        val bookOrd = data.int("bookOrd")
        val chapterOrd = data.int("chapterOrd")
        val verseOrd = data.int("verseOrd")
        val content = data.string("content")
        if (versionAbbr == null || bookOrd == null || chapterOrd == null || verseOrd == null || content == null) {
            return skip(SkipReason.MissingRequiredField)
        }
        if (documentId != UserVerseEntity.makeId(versionAbbr, bookOrd, chapterOrd, verseOrd)) {
            return skip(SkipReason.IdInvariantViolated)
        }
        val base = existing ?: UserVerseEntity(
            id = documentId, versionAbbr = versionAbbr, bookOrd = bookOrd, chapterOrd = chapterOrd,
            verseOrd = verseOrd, createdAt = now, updatedAt = now,
        )
        return Decoded.Row(
            base.copy(
                versionAbbr = versionAbbr,
                bookOrd = bookOrd,
                chapterOrd = chapterOrd,
                verseOrd = verseOrd,
                content = content,
                createdAt = data.instant("createdAt") ?: now,
                deletedAt = null,
                isDeleted = false,
                needsSync = false,
                lastSyncedAt = now,
                tagIds = tagIds(data),
            )
        )
    }

    fun indexedText(
        documentId: String,
        data: WireData,
        existing: IndexedTextEntity?,
        now: Instant,
    ): Decoded<IndexedTextEntity> {
        val id = ObjectIds.parse(documentId) ?: return skip(SkipReason.InvalidDocumentId)
        preflight(data)?.let { return it }
        val sourceId = data.string("sourceId")
        val startOffset = data.int("startOffset")
        val endOffset = data.int("endOffset")
        val markStyle = MarkStyle.fromRaw(data.string("markStyle"))
        val indexedText = data.string("indexedText")
        if (sourceId == null || startOffset == null || endOffset == null || markStyle == null || indexedText == null) {
            return skip(SkipReason.MissingRequiredField)
        }
        val base = existing ?: IndexedTextEntity(id = id, createdAt = now, updatedAt = now)
        return Decoded.Row(
            base.copy(
                sourceType = data.string("sourceType") ?: IndexedTextEntity.SOURCE_TYPE_VERSE,
                sourceId = sourceId,
                startOffset = startOffset,
                endOffset = endOffset,
                markStyle = markStyle,
                colorKey = data.string("colorKey") ?: IndexedTextEntity.DEFAULT_COLOR_KEY,
                indexedText = indexedText,
                createdAt = data.instant("createdAt") ?: now,
                deletedAt = null,
                isDeleted = false,
                needsSync = false,
                lastSyncedAt = now,
            )
        )
    }

    /** iOS `FirestoreTombstone.isRemoteTombstone`: new `isDeleted` or legacy `deletedAt`. */
    fun isRemoteTombstone(data: WireData): Boolean = data.bool("isDeleted") == true || data["deletedAt"] is Instant

    /** iOS `schemaOK`: a missing `schemaVersion` counts as 1. */
    fun schemaOk(data: WireData): Boolean = (data.int("schemaVersion") ?: 1) <= FirestoreSchema.SUPPORTED_SCHEMA_VERSION

    /** Max server `updatedAt` across [docs]; null when none carry one (pull cursor stays put). */
    fun watermark(docs: Iterable<WireData>): Instant? = docs.mapNotNull { it.instant("updatedAt") }.maxOrNull()

    /** Schema check, then tombstone check — the order every iOS `merge*` uses. */
    private fun preflight(data: WireData): Decoded<Nothing>? = when {
        !schemaOk(data) -> skip(SkipReason.UnsupportedSchema)
        isRemoteTombstone(data) -> Decoded.Tombstone
        else -> null
    }

    /** Valid ObjectIds only; malformed entries are dropped like iOS `try? ObjectId(string:)`. */
    internal fun tagIds(data: WireData): List<String> =
        data.stringList("tagIds")?.mapNotNull(ObjectIds::parse).orEmpty()

    private fun skip(reason: SkipReason) = Decoded.Skip(reason)
}
