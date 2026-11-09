package com.stoneofhope.stoneofhope.data.sermon

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.bible.BibleAnchorKey
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.bible.BookChapter
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.reading.ReadingModel
import com.stoneofhope.stoneofhope.data.reading.ReadingRepository
import com.stoneofhope.stoneofhope.data.repository.NoteInputSaveResult
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.LocalSyncOps
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Why a link could not be saved. */
enum class LinkFailure { InvalidLink, Shorts, Unavailable }

sealed interface SermonCreateResult {
    data class Created(val sermonId: String) : SermonCreateResult

    /** The link was already a sermon; it was opened instead (iOS meant to say so: IOS_ISSUES D3). */
    data class Existing(val sermonId: String) : SermonCreateResult

    /** The link is already a saved video (IOS_ISSUES K6). */
    data class ExistingVideo(val youtubeID: String) : SermonCreateResult

    data class Failed(val reason: LinkFailure) : SermonCreateResult
}

/** The edit page's fields (iOS `SermonEditView` → `updateSermon`), plus the preached-on day (§4 question 4). */
data class SermonEdit(
    val title: String,
    val preachedOn: LocalDate,
    val refText: String,
    val preacher: String,
    val videoUrl: String,
    val audioUrl: String,
    val pdfUrl: String,
    val intro: String,
)

/**
 * Sermon and series writes (iOS `NotesManager` + `NotesManager-Sermons`). Fields, types and date
 * meanings are iOS's; every change marks the row `needsSync`. Church imports (`isUserData == false`)
 * are never changed.
 */
@Singleton
class SermonRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val reading: ReadingRepository,
    private val notes: NoteRepository,
    private val web: YouTubeWeb,
    private val ops: LocalSyncOps,
    private val bibleVersions: BibleVersionPreferences,
) {
    private val sermonDao get() = db.sermonDao()
    private val seriesDao get() = db.sermonSeriesDao()
    private val videoDao get() = db.youTubeVideoDao()

    private fun languages(): List<BibleLanguage> = BibleVersions.bookMatchingLanguages(bibleVersions.selectedVersions())

    private fun today(zone: ZoneId): LocalDate = clock.instant().atZone(zone).toLocalDate()

    // MARK: - Create

    /**
     * iOS `createSermon(title:refText:preacher:series:)`: preached today, `createdAt` at today's local
     * midnight (iOS `Date().onlyDate`), a blank title replaced by today's date. Its chapters are marked
     * read (only marked: IOS_ISSUES D1). The series the user picked stays; iOS lets auto-detection
     * replace it (D2), and auto-detection only runs when nothing was picked (K3).
     */
    suspend fun createManual(
        title: String, refText: String, preacher: String, seriesId: String?, zone: ZoneId = ZoneId.systemDefault(),
    ): SermonEntity {
        val day = today(zone)
        val trimmed = SwiftText.trim(title)
        val finalTitle = trimmed.ifEmpty { defaultTitle(day) }
        var sermon = SermonEntity(
            id = ObjectIds.newId(), title = finalTitle, preacher = preacher, preachedOn = SermonRules.dayKey(day),
            createdAt = ReadingModel.startOfDay(day, zone), isUserData = true, needsSync = true,
        )
        // iOS always passes the field's text, so an empty field still writes `chapterRange = ""`.
        sermon = SermonReferenceParser.applyTo(sermon, refText, languages())
        db.withTransaction {
            val series = seriesId?.let { seriesDao.getById(it) }?.takeIf { it.deletedAt == null }
                ?: SermonRules.detectSeries(sermon.bookOrd, seriesDao.active(), day)
            sermon = sermon.copy(seriesId = series?.id)
            sermonDao.upsert(sermon)
            series?.let { touchSeries(it) }
        }
        markRead(sermon, zone)
        return sermon
    }

    /** iOS `defaultSermonTitle()`: today's long date, made unique among active sermons. */
    private suspend fun defaultTitle(day: LocalDate): String {
        val base = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()))
        return SermonRules.uniqueTitle(base, sermonDao.activeTitlesLike(base))
    }

    /**
     * iOS `createSermon(from:)`, then (from the video sheet) `applyBibleReference(_:toSermonWithID:)`
     * with [refOverride]. Title, reference and preacher come from the oEmbed title. An existing sermon
     * for the video is returned as [SermonCreateResult.Existing]; a link already saved as a video is
     * refused (K6). Read marks use the final reference (K5).
     */
    suspend fun createFromYouTube(url: String, refOverride: String? = null, zone: ZoneId = ZoneId.systemDefault()): SermonCreateResult {
        val trimmedUrl = SwiftText.trim(url)
        if (YouTubeLinkParser.isShortsURL(trimmedUrl)) return SermonCreateResult.Failed(LinkFailure.Shorts)
        val videoID = YouTubeLinkParser.videoIDFromSharePayload(trimmedUrl) ?: return SermonCreateResult.Failed(LinkFailure.InvalidLink)
        val override = refOverride?.let(SwiftText::trim)?.ifEmpty { null }

        sermonDao.activeWithYouTubeID(videoID)?.let { existing ->
            override?.let { applyBibleReference(existing.id, it) }
            return SermonCreateResult.Existing(existing.id)
        }
        videoDao.getById(videoID)?.let { row ->
            if (row.deletedAt == null && row.sermonId == null) return SermonCreateResult.ExistingVideo(videoID)
        }
        val info = web.oEmbed(videoID) ?: return SermonCreateResult.Failed(LinkFailure.Unavailable)
        val parsed = YouTubeSermonTitleParser.parse(info.title, info.authorName, videoID, languages())
        return createFromParsed(parsed, override, zone)
    }

    /** iOS `createSermon(youtubeID:title:refText:preacher:)`. */
    internal suspend fun createFromParsed(parsed: YouTubeSermonInfo, refOverride: String?, zone: ZoneId): SermonCreateResult {
        val day = today(zone)
        val existing = parsed.bibleRef?.let { sermonDao.activeWithTitleAndRange(parsed.title, it) }
        if (existing != null) {
            if (!existing.isUserData) return SermonCreateResult.Existing(existing.id)
            var updated: SermonEntity = existing
            if (existing.youtubeID == null) {
                updated = updated.copy(youtubeID = parsed.youtubeID, videoUrl = YouTubeLinkParser.watchUrl(parsed.youtubeID))
            }
            db.withTransaction {
                if (updated.seriesId == null) {
                    SermonRules.detectSeries(updated.bookOrd, seriesDao.active(), day)?.let { s ->
                        updated = updated.copy(seriesId = s.id)
                        touchSeries(s)
                    }
                }
                if (updated != existing) sermonDao.upsert(updated.copy(needsSync = true))
            }
            refOverride?.let { applyBibleReference(existing.id, it) }
            val final = sermonDao.getById(existing.id) ?: updated
            markRead(final, zone)
            upsertVideoIndex(final)
            return SermonCreateResult.Existing(existing.id)
        }

        var sermon = SermonEntity(
            id = ObjectIds.newId(), title = parsed.title, preacher = parsed.preacher.orEmpty(), youtubeID = parsed.youtubeID,
            videoUrl = YouTubeLinkParser.watchUrl(parsed.youtubeID), preachedOn = SermonRules.dayKey(day),
            createdAt = ReadingModel.startOfDay(day, zone), isUserData = true, needsSync = true,
        )
        parsed.bibleRef?.let { sermon = SermonReferenceParser.applyTo(sermon, it, languages()) }
        refOverride?.let { sermon = SermonReferenceParser.applyTo(sermon, it, languages()) }
        db.withTransaction {
            SermonRules.detectSeries(sermon.bookOrd, seriesDao.active(), day)?.let { s ->
                sermon = sermon.copy(seriesId = s.id)
                touchSeries(s)
            }
            sermonDao.upsert(sermon)
        }
        markRead(sermon, zone)
        upsertVideoIndex(sermon)
        return SermonCreateResult.Created(sermon.id)
    }

    /**
     * iOS `applyBibleReference(_:toSermonWithID:)`: a typed reference replaces the one taken from the
     * title. Blank text keeps the old one; church imports are left alone.
     */
    suspend fun applyBibleReference(sermonId: String, refText: String) {
        val trimmed = SwiftText.trim(refText)
        if (trimmed.isEmpty()) return
        val live = sermonDao.getById(sermonId)?.takeIf { it.isUserData } ?: return
        sermonDao.upsert(SermonReferenceParser.applyTo(live, trimmed, languages()).copy(needsSync = true))
    }

    private suspend fun markRead(sermon: SermonEntity, zone: ZoneId) {
        val bookOrd = sermon.bookOrd ?: return
        for (chapter in sermon.chapters) reading.markChapterRead(bookOrd, chapter, zone)
    }

    // MARK: - Edit

    /**
     * iOS `updateSermon`. Differences: the video link uses the same parser as everywhere else, and a
     * cleared link clears `youtubeID` (iOS keeps playing the old video: IOS_ISSUES K11); the old video's
     * index row goes when the video changes (K7); the preached-on day can change (§4 question 4).
     */
    suspend fun update(sermonId: String, edit: SermonEdit) {
        val live = sermonDao.getById(sermonId)?.takeIf { it.isUserData && it.deletedAt == null } ?: return
        var s = live.copy(
            title = edit.title, preachedOn = SermonRules.dayKey(edit.preachedOn), preacher = edit.preacher,
            audioUrl = edit.audioUrl, videoUrl = edit.videoUrl, pdfUrl = edit.pdfUrl, intro = edit.intro, needsSync = true,
        )
        s = if (SwiftText.trim(edit.refText).isNotEmpty()) {
            SermonReferenceParser.applyTo(s, edit.refText, languages())
        } else {
            s.copy(bookOrd = null, book = null, chapters = emptyList(), chapterRange = null)
        }
        s = s.copy(youtubeID = YouTubeLinkParser.videoID(edit.videoUrl))
        val now = clock.instant()
        db.withTransaction {
            sermonDao.upsert(s)
            val oldID = live.youtubeID?.let(SwiftText::trim)?.ifEmpty { null }
            if (oldID != null && oldID != s.youtubeID) dropVideoIndex(oldID, sermonId, now)
        }
        upsertVideoIndex(s)
    }

    /** Joins, changes or (with null) leaves a series; both series' `sermonIds` are pushed again. */
    suspend fun setSeries(sermonId: String, seriesId: String?) {
        db.withTransaction {
            val live = sermonDao.getById(sermonId)?.takeIf { it.isUserData } ?: return@withTransaction
            if (live.seriesId == seriesId) return@withTransaction
            live.seriesId?.let { seriesDao.getById(it) }?.let { touchSeries(it) }
            seriesId?.let { seriesDao.getById(it) }?.let { touchSeries(it) }
            sermonDao.upsert(live.copy(seriesId = seriesId, needsSync = true))
        }
    }

    /** The edit page's "Detect Series", with the same rule as a new sermon. False when nothing fits. */
    suspend fun detectSeries(sermonId: String, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val live = sermonDao.getById(sermonId) ?: return false
        val series = SermonRules.detectSeries(live.bookOrd, seriesDao.active(), today(zone)) ?: return false
        setSeries(sermonId, series.id)
        return true
    }

    /** iOS `createSeries(title:bookOrd:)`: started today, in progress. */
    suspend fun createSeries(title: String, bookOrd: Int?, zone: ZoneId = ZoneId.systemDefault()): SermonSeriesEntity {
        val series = SermonSeriesEntity(
            id = ObjectIds.newId(), title = SwiftText.trim(title), bookOrd = bookOrd,
            startedOn = SermonRules.dayKey(today(zone)), isUserData = true, needsSync = true,
        )
        seriesDao.upsert(series)
        return series
    }

    /** iOS `updateSeriesTitle`: blank titles and church series are ignored. */
    suspend fun renameSeries(seriesId: String, title: String) {
        val trimmed = SwiftText.trim(title)
        if (trimmed.isEmpty()) return
        val live = seriesDao.getById(seriesId)?.takeIf { it.isUserData && it.deletedAt == null } ?: return
        if (live.title != trimmed) seriesDao.upsert(live.copy(title = trimmed, needsSync = true))
    }

    /** iOS `updateSeriesStatus`. */
    suspend fun setSeriesFinished(seriesId: String, finished: Boolean) {
        val live = seriesDao.getById(seriesId)?.takeIf { it.isUserData && it.deletedAt == null } ?: return
        if (live.isFinished != finished) seriesDao.upsert(live.copy(isFinished = finished, needsSync = true))
    }

    /**
     * iOS `SeriesDetailView` deletes a series only when it has no sermons left (the series becomes a
     * tombstone). False when it still has sermons or is a church series.
     */
    suspend fun deleteEmptySeries(seriesId: String): Boolean = db.withTransaction {
        val live = seriesDao.getById(seriesId)?.takeIf { it.isUserData && it.deletedAt == null } ?: return@withTransaction false
        if (sermonDao.observeActive().first().any { it.seriesId == seriesId }) return@withTransaction false
        seriesDao.upsert(live.copy(deletedAt = clock.instant(), needsSync = true))
        true
    }

    private suspend fun touchSeries(series: SermonSeriesEntity) {
        if (series.isUserData) seriesDao.upsert(series.copy(needsSync = true))
    }

    // MARK: - Tags (iOS generic `addTags(_:toObjectOfType:)`)

    suspend fun addTag(sermonId: String, tagName: String) {
        val name = SwiftText.trim(tagName)
        if (name.isEmpty()) return
        db.withTransaction {
            val live = sermonDao.getById(sermonId) ?: return@withTransaction
            val tag = findOrCreateTag(name)
            if (tag.id !in live.tagIds) sermonDao.upsert(live.copy(tagIds = live.tagIds + tag.id, needsSync = true))
        }
    }

    suspend fun removeTag(sermonId: String, tagId: String) {
        val live = sermonDao.getById(sermonId) ?: return
        sermonDao.upsert(live.copy(tagIds = live.tagIds - tagId, needsSync = true))
    }

    private suspend fun findOrCreateTag(name: String): TagEntity =
        db.tagDao().findActiveByName(name)
            ?: TagEntity(id = ObjectIds.newId(), name = name, createdAt = clock.instant(), needsSync = true).also { db.tagDao().upsert(it) }

    // MARK: - Delete and merge

    /**
     * iOS `deleteSermon`: the sermon, its notes and their comments become tombstones. Android also
     * drops the notes' pin logs and the sermon's hidden video index row (IOS_ISSUES K7).
     */
    suspend fun delete(sermonId: String) {
        val now = clock.instant()
        db.withTransaction {
            val sermon = sermonDao.getById(sermonId)?.takeIf { it.deletedAt == null } ?: return@withTransaction
            for (note in db.noteDao().activeForSermon(sermonId)) {
                for (c in db.noteCommentDao().activeForNote(note.id)) {
                    db.noteCommentDao().upsert(c.copy(deletedAt = now, isDeleted = true, needsSync = true))
                }
                ops.removePinLog(note.id)
                db.noteDao().upsert(note.copy(deletedAt = now, isDeleted = true, needsSync = true, updatedAt = now))
            }
            sermonDao.upsert(sermon.copy(deletedAt = now, needsSync = true))
            sermon.youtubeID?.let(SwiftText::trim)?.ifEmpty { null }?.let { dropVideoIndex(it, sermonId, now) }
        }
    }

    /**
     * iOS `mergeSermon(sourceId:into:)`: [sourceId]'s notes and their comments move to [targetId], then
     * [sourceId] becomes a tombstone. Android also gives the target what it lacks (K9) and moves the
     * hidden video index row with the video (K7).
     */
    suspend fun merge(sourceId: String, targetId: String): Boolean {
        if (sourceId == targetId) return false
        val now = clock.instant()
        var mergedTarget: SermonEntity? = null
        val ok = db.withTransaction {
            val source = sermonDao.getById(sourceId)?.takeIf { it.deletedAt == null } ?: return@withTransaction false
            val target = sermonDao.getById(targetId)?.takeIf { it.deletedAt == null } ?: return@withTransaction false
            for (note in db.noteDao().activeForSermon(sourceId)) {
                db.noteDao().upsert(
                    note.copy(
                        sermonId = targetId, youtubeID = null, needsSync = true, updatedAt = now,
                        chapterKey = BibleAnchorKey.chapterKey(note.bookOrd, note.chapterOrd), isDeleted = note.deletedAt != null,
                    ),
                )
            }
            for (c in db.noteCommentDao().activeWithSermonId(sourceId)) {
                db.noteCommentDao().upsert(c.copy(sermonId = targetId, needsSync = true))
            }
            source.seriesId?.let { seriesDao.getById(it) }?.let { touchSeries(it) }
            sermonDao.upsert(source.copy(seriesId = null, deletedAt = now, needsSync = true))

            if (target.isUserData) {
                val filled = SermonRules.filledFrom(target, source)
                if (filled != target) {
                    sermonDao.upsert(filled.copy(needsSync = true))
                    if (filled.seriesId != target.seriesId) filled.seriesId?.let { seriesDao.getById(it) }?.let { touchSeries(it) }
                }
                mergedTarget = filled
            }
            val sourceVideo = source.youtubeID?.let(SwiftText::trim)?.ifEmpty { null }
            if (sourceVideo != null && sourceVideo != mergedTarget?.youtubeID) dropVideoIndex(sourceVideo, sourceId, now)
            true
        }
        mergedTarget?.let { upsertVideoIndex(it) }
        return ok
    }

    // MARK: - Hidden video index (iOS `upsertSermonYouTubeVideoIndex`)

    /**
     * The `youtubeVideos` row that lets a second device find the sermon for a link. A personal video
     * with the same id is left alone: iOS turns it into the index and the video disappears (K6).
     */
    internal suspend fun upsertVideoIndex(sermon: SermonEntity) {
        val videoID = sermon.youtubeID?.let(SwiftText::trim)?.ifEmpty { null } ?: return
        val now = clock.instant()
        val existing = videoDao.getById(videoID)
        if (existing != null && existing.deletedAt == null && existing.sermonId == null) return
        val base = existing ?: YouTubeVideoEntity(youtubeID = videoID, title = "", createdAt = now, updatedAt = now)
        videoDao.upsert(
            base.copy(
                sermonId = sermon.id, title = sermon.title, channelName = sermon.preacher, bookOrd = sermon.bookOrd,
                chapterOrd = sermon.chapters.firstOrNull(), thumbnailUrl = YouTubeLinkParser.thumbnailUrl(videoID),
                deletedAt = null, updatedAt = now, needsSync = true,
            ),
        )
    }

    private suspend fun dropVideoIndex(videoID: String, sermonId: String, now: Instant) {
        val row = videoDao.getById(videoID) ?: return
        if (row.sermonId == sermonId && row.deletedAt == null) videoDao.upsert(row.copy(deletedAt = now, updatedAt = now, needsSync = true))
    }

    // MARK: - Notes on the sermon page

    /**
     * iOS `addNote(_:to: sermon, chapterNote:)`: the note sheet's save, linked to the sermon. "v3 …" uses
     * [anchorChapter] (the chapter the page was opened from, else the sermon's first); text with no verse
     * is put on that chapter only when [chapterNote] (the sheet's "Chapter Note" switch) is on.
     */
    suspend fun saveNote(
        sermonId: String,
        content: String,
        anchorChapter: BookChapter?,
        chapterNote: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): NoteInputSaveResult? =
        notes.saveNoteFromContent(content, null, anchorChapter = anchorChapter, zone = zone, sermonId = sermonId, anchorPlainNote = chapterNote)

    /** iOS `addOCRNoteIfNeeded(_:to: sermon)`: Script text, "vN" resolved against the sermon's chapter. */
    suspend fun saveScriptText(sermonId: String, text: String, seed: BookChapter?, zone: ZoneId = ZoneId.systemDefault()) {
        if (SwiftText.trim(text).isEmpty()) return
        notes.saveNoteFromContent(text, null, anchorChapter = seed, zone = zone, sermonId = sermonId, anchorPlainNote = false)
    }
}
