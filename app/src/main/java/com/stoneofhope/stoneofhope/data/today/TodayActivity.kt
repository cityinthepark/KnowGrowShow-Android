package com.stoneofhope.stoneofhope.data.today

import com.stoneofhope.stoneofhope.data.local.entity.ChapterLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.isChapterNote
import com.stoneofhope.stoneofhope.data.model.isVerseAnchoredQuickNote

/** One day's activity on the Today tab (iOS `TodayActivityBuilder.build`). */
data class TodayActivity(
    val tiles: List<Tile>,
    val chaptersRead: List<ChapterRead>,
    val sermons: List<SermonRow>,
    val videos: List<VideoRow>,
    /** iOS shows "Review" when there are notes/prayers that day or any pinned note. */
    val showReview: Boolean,
) {
    /** The whole section is hidden when there is nothing to show (no empty-state message, as on iOS). */
    val isEmpty: Boolean get() = tiles.isEmpty() && chaptersRead.isEmpty() && sermons.isEmpty() && videos.isEmpty()

    enum class TileKind { Pinned, Starred, QuickNote, StudyNote, Bible, Prayer, AnsweredPrayer, Image }

    /** A chapter that counts as read that day: marked read ([isLogged]), or one the user wrote a note on. */
    data class ChapterRead(val bookOrd: Int, val chapter: Int, val isLogged: Boolean)

    data class Tile(val kind: TileKind, val count: Int)

    data class SermonRow(val sermon: SermonEntity, val noteCount: Int, val tagNames: List<String>)

    data class VideoRow(val video: YouTubeVideoEntity, val noteCount: Int, val tagNames: List<String>)

    companion object {
        val Empty = TodayActivity(emptyList(), emptyList(), emptyList(), emptyList(), showReview = false)
    }
}

object TodayActivityBuilder {

    /** iOS `chaptersWithNotes(on:)`: marked read, plus chapters of any Bible-anchored note written that day. */
    internal fun chaptersReadOn(logs: List<ChapterLogEntity>, notesThatDay: List<NoteEntity>): List<TodayActivity.ChapterRead> {
        val logged = logs.map { TodayActivity.ChapterRead(it.bookOrd, it.chapter, isLogged = true) }
        val seen = logged.map { it.bookOrd to it.chapter }.toSet()
        val noted = notesThatDay
            .mapNotNull { n ->
                val book = n.bookOrd ?: return@mapNotNull null
                val chapter = n.chapterOrd?.takeIf { it > 0 } ?: return@mapNotNull null
                book to chapter
            }
            .distinct()
            .filter { it !in seen }
            .map { TodayActivity.ChapterRead(it.first, it.second, isLogged = false) }
        return (logged + noted).sortedWith(compareBy({ it.bookOrd }, { it.chapter }))
    }

    /**
     * @param createdThatDay active notes whose `createdAt` falls on the day
     * @param commentsThatDay active comments created on the day
     * @param commentedNotes the active notes those comments belong to (any creation date)
     * @param pinned every active pinned note; pass empty when the day isn't today
     * @param sermonNotes / videoNotes active notes linked to the day's sermons / videos
     */
    fun build(
        createdThatDay: List<NoteEntity>,
        commentsThatDay: List<NoteCommentEntity>,
        commentedNotes: List<NoteEntity>,
        pinned: List<NoteEntity>,
        chaptersRead: List<ChapterLogEntity>,
        sermons: List<SermonEntity>,
        sermonNotes: List<NoteEntity>,
        videos: List<YouTubeVideoEntity>,
        videoNotes: List<NoteEntity>,
        tags: List<TagEntity>,
    ): TodayActivity {
        // A note belongs to the day if it was created that day or got a comment that day.
        val createdIds = createdThatDay.map { it.id }.toSet()
        val dayNotes = (createdThatDay + commentedNotes.filter { it.id !in createdIds }).distinctBy { it.id }
        fun ofType(type: NoteType) = dayNotes.filter { it.noteType == type }

        val noteTypeNotes = ofType(NoteType.Note)
        val studyNoteCount = ofType(NoteType.StudyNote).size
        val chapterNoteCount = noteTypeNotes.count { it.isChapterNote }
        val quickNoteCount = noteTypeNotes.count { !it.isChapterNote && !it.isVerseAnchoredQuickNote }
        // "Bible" shows verse notes and chapter notes together, so its count includes both.
        val bibleCount = ofType(NoteType.BibleVerse).size + chapterNoteCount
        val imageCount = ofType(NoteType.Image).size
        val prayers = ofType(NoteType.Prayer)
        // Same as iOS: prayers of the day that are answered (by creation day, not `answeredAt`).
        val answeredCount = prayers.count { it.isAnswered == true }
        val notesAndPrayerCount = quickNoteCount + studyNoteCount + bibleCount + imageCount + prayers.size

        // Starred: created that day with stars, plus notes whose starred comment was made that day.
        val liveIds = dayNotes.map { it.id }.toSet()
        val starredIds = createdThatDay.filter { it.starLevel > 0 }.map { it.id }.toSet() +
            commentsThatDay.filter { it.starLevel > 0 }.mapNotNull { it.noteId }.filter { it in liveIds }

        val tiles = listOf(
            TodayActivity.TileKind.Pinned to pinned.size,
            TodayActivity.TileKind.Starred to starredIds.size,
            TodayActivity.TileKind.QuickNote to quickNoteCount,
            TodayActivity.TileKind.StudyNote to studyNoteCount,
            TodayActivity.TileKind.Bible to bibleCount,
            TodayActivity.TileKind.Prayer to prayers.size,
            TodayActivity.TileKind.AnsweredPrayer to answeredCount,
            TodayActivity.TileKind.Image to imageCount,
        ).filter { it.second > 0 }.map { TodayActivity.Tile(it.first, it.second) }

        val tagNames = tags.filter { it.deletedAt == null }.associate { it.id to it.name }
        fun names(ids: List<String>) = ids.mapNotNull(tagNames::get)

        return TodayActivity(
            tiles = tiles,
            chaptersRead = chaptersReadOn(chaptersRead, createdThatDay),
            sermons = sermons
                .sortedWith(compareBy({ it.createdAt }, { it.title }))
                .map { s -> TodayActivity.SermonRow(s, sermonNotes.count { it.sermonId == s.id }, names(s.tagIds)) },
            videos = videos
                .sortedByDescending { it.createdAt }
                .map { v -> TodayActivity.VideoRow(v, videoNotes.count { it.youtubeID == v.youtubeID }, names(v.tagIds)) },
            showReview = notesAndPrayerCount > 0 || pinned.isNotEmpty(),
        )
    }
}
