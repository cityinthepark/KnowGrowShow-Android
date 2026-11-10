package com.stoneofhope.stoneofhope.data.sermon

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.bible.BookChapter
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.image.NoteImageStore
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.repository.NoteInputSaveResult
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.LocalSyncOps
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VideoCreateResult {
    data class Created(val youtubeID: String) : VideoCreateResult

    /** Already saved; opened instead (IOS_ISSUES D3). */
    data class Existing(val youtubeID: String) : VideoCreateResult

    /** The link belongs to a sermon (iOS `sermonForYouTubeVideoDedup`). */
    data class ExistingSermon(val sermonId: String) : VideoCreateResult

    data class Failed(val reason: LinkFailure) : VideoCreateResult
}

/**
 * Personal YouTube videos (iOS `NotesManager-YouTubeVideos`). A row whose `sermonId` is set is a
 * sermon's hidden index and never shows as a video.
 */
@Singleton
class VideoRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val notes: NoteRepository,
    private val sermons: SermonRepository,
    private val web: YouTubeWeb,
    private val ops: LocalSyncOps,
    private val bibleVersions: BibleVersionPreferences,
    private val images: NoteImageStore,
) {
    private val videoDao get() = db.youTubeVideoDao()

    private fun languages() = BibleVersions.bookMatchingLanguages(bibleVersions.selectedVersions())

    /**
     * iOS `createYouTubeVideo(from:applyTitleBibleAnchor:)` and the sheet's follow-up: title, channel and
     * thumbnail from oEmbed (§4 question 2; iOS uses the Data API, so `channelId` stays empty here);
     * the book and chapter from [refText] when it parses, else from the title. A link that is already a
     * sermon opens the sermon; an existing video opens the video. A deleted video comes back as new,
     * dated now (iOS keeps its first date, so it never shows in today's activity: IOS_ISSUES K8).
     */
    suspend fun create(url: String, refText: String?, zone: ZoneId = ZoneId.systemDefault()): VideoCreateResult {
        val videoID = YouTubeLinkParser.videoIDFromSharePayload(SwiftText.trim(url))
            ?: return VideoCreateResult.Failed(LinkFailure.InvalidLink)
        val ref = refText?.let(SwiftText::trim)?.ifEmpty { null }

        val row = videoDao.getById(videoID)?.let { resolveStaleSermonLink(it) }
        if (row != null && row.deletedAt == null) {
            row.sermonId?.let { sermonId ->
                ref?.let { sermons.applyBibleReference(sermonId, it) }
                return VideoCreateResult.ExistingSermon(sermonId)
            }
            ref?.let { applyBibleReference(videoID, it) }
            return VideoCreateResult.Existing(videoID)
        }

        val info = web.oEmbed(videoID)
        val now = clock.instant()
        val video = if (row != null) {
            // Revived: dated now, title refreshed when YouTube answers.
            row.copy(
                title = info?.title?.ifEmpty { null } ?: row.title, channelName = info?.authorName ?: row.channelName,
                thumbnailUrl = info?.thumbnailUrl ?: row.thumbnailUrl, sermonId = null,
                createdAt = now, updatedAt = now, deletedAt = null, needsSync = true,
            )
        } else {
            info ?: return VideoCreateResult.Failed(LinkFailure.Unavailable)
            var v = YouTubeVideoEntity(
                youtubeID = videoID, title = info.title, channelName = info.authorName, thumbnailUrl = info.thumbnailUrl,
                createdAt = now, updatedAt = now, needsSync = true,
            )
            if (ref == null) {
                val anchor = YouTubeVideoTitleParser.anchor(info.title, languages())
                v = v.copy(bookOrd = anchor.bookOrd, chapterOrd = anchor.chapterOrd)
            }
            v
        }
        videoDao.upsert(video)
        ref?.let { applyBibleReference(videoID, it) }
        return VideoCreateResult.Created(videoID)
    }

    /** iOS `resolveStaleSermonLink`: an index row whose sermon is gone becomes a plain row again. */
    private suspend fun resolveStaleSermonLink(video: YouTubeVideoEntity): YouTubeVideoEntity {
        val sermonId = video.sermonId ?: return video
        val sermon = db.sermonDao().getById(sermonId)
        if (sermon != null && sermon.deletedAt == null) return video
        val cleared = video.copy(sermonId = null, updatedAt = clock.instant(), needsSync = true)
        videoDao.upsert(cleared)
        return cleared
    }

    /** iOS `applyBibleReference(_:to: video)`: only a reference that parses replaces the title's. */
    suspend fun applyBibleReference(youtubeID: String, refText: String) {
        val trimmed = SwiftText.trim(refText)
        if (trimmed.isEmpty()) return
        val parsed = SermonReferenceParser.parse(trimmed, languages())
        val bookOrd = parsed.bookOrd
        if (!parsed.isValid || bookOrd == null) return
        val live = videoDao.getById(youtubeID) ?: return
        videoDao.upsert(live.copy(bookOrd = bookOrd, chapterOrd = parsed.chapters.firstOrNull(), updatedAt = clock.instant(), needsSync = true))
    }

    /**
     * iOS `deleteYouTubeVideo`: the video, its notes and their comments become tombstones; Android also
     * drops the notes' pin logs (IOS_ISSUES K7).
     */
    suspend fun delete(youtubeID: String) {
        val now = clock.instant()
        db.withTransaction {
            val video = videoDao.getById(youtubeID)?.takeIf { it.deletedAt == null } ?: return@withTransaction
            for (note in db.noteDao().activeForVideo(youtubeID)) {
                for (c in db.noteCommentDao().activeForNote(note.id)) {
                    db.noteCommentDao().upsert(c.copy(deletedAt = now, isDeleted = true, needsSync = true))
                }
                ops.removePinLog(note.id)
                images.deleteFilesOf(note)
                db.noteDao().upsert(note.copy(deletedAt = now, isDeleted = true, needsSync = true, updatedAt = now))
            }
            videoDao.upsert(video.copy(deletedAt = now, needsSync = true))
        }
    }

    suspend fun addTag(youtubeID: String, tagName: String) {
        val name = SwiftText.trim(tagName)
        if (name.isEmpty()) return
        db.withTransaction {
            val live = videoDao.getById(youtubeID) ?: return@withTransaction
            val tag = db.tagDao().findActiveByName(name)
                ?: TagEntity(id = ObjectIds.newId(), name = name, createdAt = clock.instant(), needsSync = true).also { db.tagDao().upsert(it) }
            if (tag.id !in live.tagIds) videoDao.upsert(live.copy(tagIds = live.tagIds + tag.id, needsSync = true))
        }
    }

    suspend fun removeTag(youtubeID: String, tagId: String) {
        val live = videoDao.getById(youtubeID) ?: return
        videoDao.upsert(live.copy(tagIds = live.tagIds - tagId, needsSync = true))
    }

    /** iOS `addNote(_:to: video, chapterNote:)`; same rules as `SermonRepository.saveNote`. */
    suspend fun saveNote(
        youtubeID: String,
        content: String,
        anchorChapter: BookChapter?,
        chapterNote: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): NoteInputSaveResult? =
        notes.saveNoteFromContent(content, null, anchorChapter = anchorChapter, zone = zone, youtubeID = youtubeID, anchorPlainNote = chapterNote)

    /** iOS `addOCRNoteIfNeeded(_:youtubeVideo:)`. */
    suspend fun saveScriptText(youtubeID: String, text: String, seed: BookChapter?, zone: ZoneId = ZoneId.systemDefault()) {
        if (SwiftText.trim(text).isEmpty()) return
        notes.saveNoteFromContent(text, null, anchorChapter = seed, zone = zone, youtubeID = youtubeID, anchorPlainNote = false)
    }
}
