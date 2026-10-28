package com.stoneofhope.stoneofhope.data.chapter

import com.stoneofhope.stoneofhope.data.bible.BibleFormatter
import com.stoneofhope.stoneofhope.data.bible.BookChapter
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.isChapterNote
import com.stoneofhope.stoneofhope.data.model.isVerseReflection
import com.stoneofhope.stoneofhope.data.notes.isAnsweredMarker

/**
 * iOS `ChapterNoteAnchor` (verse case): one verse range of a chapter. The version is left out on
 * purpose — the chapter page groups by verse, not by translation.
 */
data class ChapterVerse(val bookOrd: Int, val chapterOrd: Int, val verseStart: Int, val verseEnd: Int) {
    val chapter: BookChapter get() = BookChapter(bookOrd, chapterOrd)

    /** "Genesis 2:1" / "創世記 2:1-3": book names follow [versionAbbr]'s language, like iOS `refLabel(versionAbbr:)`. */
    fun refLabel(versionAbbr: String?): String =
        BibleFormatter.display(bookOrd, chapterOrd, verseStart, verseEnd, versionAbbr, showVersion = false).orEmpty()

    companion object {
        /** iOS `ChapterNoteAnchor.from(_ note:)`; an unset `verseEnd` reads as `verseStart` (IOS_ISSUES F3). */
        fun of(note: NoteEntity): ChapterVerse? {
            val start = note.verseStart ?: return null
            return ChapterVerse(note.bookOrd ?: return null, note.chapterOrd ?: return null, start, note.verseEnd ?: start)
        }
    }
}

/** Everything one verse range of the chapter holds (a group of the Chapter Notes tab, and the verse page). */
data class ChapterVerseGroup(
    val verse: ChapterVerse,
    /** Active `.bibleVerse` anchors here, newest first: one per day, version and sermon / video. */
    val anchors: List<NoteEntity> = emptyList(),
    /** Quick notes with real text on these verses, newest first, whichever anchor (if any) they belong to. */
    val reflections: List<NoteEntity> = emptyList(),
    /** Comments left on the anchors themselves (older data), without "Answered" markers. */
    val anchorComments: List<NoteCommentEntity> = emptyList(),
    /** Prayers linked to these verses, newest first. */
    val prayers: List<NoteEntity> = emptyList(),
) {
    /** What the verse page lists under "Reflections", so the chapter list counts the same (IOS_ISSUES H1, H3). */
    val reflectionCount: Int get() = reflections.size + anchorComments.size

    /** Notes that may carry this verse's text, best first: anchors, then reflections, then prayers. */
    val textSources: List<NoteEntity> get() = anchors + reflections + prayers
}

/** The Chapter Notes tab: the chapter's own notes first, then each verse range in verse order. */
data class ChapterNotes(
    /** Quick notes anchored to the whole chapter, oldest first (iOS order). */
    val chapterNotes: List<NoteEntity>,
    val verses: List<ChapterVerseGroup>,
) {
    val isEmpty: Boolean get() = chapterNotes.isEmpty() && verses.isEmpty()
}

/**
 * iOS `ChapterNoteViewModel.groupedEntries` and `ChapterNoteDetailsView`, with these differences:
 * - every anchor of a verse counts, not one picked at random (H1);
 * - counts are exactly what the verse page shows, so comments on reflections and loose comments
 *   carrying a verse no longer inflate them (H3);
 * - a verse that only has an anchor (no reflection yet) still shows (H4);
 * - comments on chapter notes stay with their note (H2): its card shows them, not a verse page.
 */
object ChapterNotesBuilder {

    private val NoteEntity.isActive get() = deletedAt == null && !isDeleted

    fun build(notes: List<NoteEntity>, comments: List<NoteCommentEntity>, chapter: BookChapter): ChapterNotes {
        val inChapter = notes.filter { it.isActive && it.bookOrd == chapter.bookOrd && it.chapterOrd == chapter.chapterOrd }
        val chapterNotes = inChapter.filter { it.isChapterNote }.sortedBy { it.createdAt }
        val verses = inChapter.mapNotNull { note -> ChapterVerse.of(note)?.takeIf { note.belongsOnVersePage } }
            .distinct()
            .sortedWith(compareBy({ it.verseStart }, { it.verseEnd }))
            .map { group(inChapter, comments, it) }
        return ChapterNotes(chapterNotes, verses)
    }

    /** The verse page (iOS `ChapterNoteDetailsView`). Lists are empty when nothing is there (yet). */
    fun verse(notes: List<NoteEntity>, comments: List<NoteCommentEntity>, verse: ChapterVerse): ChapterVerseGroup =
        group(notes.filter { it.isActive }, comments, verse)

    private val NoteEntity.belongsOnVersePage: Boolean
        get() = noteType == NoteType.BibleVerse || noteType == NoteType.Prayer || isVerseReflection

    private fun group(notes: List<NoteEntity>, comments: List<NoteCommentEntity>, verse: ChapterVerse): ChapterVerseGroup {
        val here = notes.filter { ChapterVerse.of(it) == verse }.sortedByDescending { it.createdAt }
        val anchors = here.filter { it.noteType == NoteType.BibleVerse }
        val anchorIds = anchors.mapTo(HashSet()) { it.id }
        return ChapterVerseGroup(
            verse = verse,
            anchors = anchors,
            reflections = here.filter { it.isVerseReflection },
            anchorComments = comments.filter { it.noteId in anchorIds && it.deletedAt == null && !it.isDeleted && !it.isAnsweredMarker },
            prayers = here.filter { it.noteType == NoteType.Prayer },
        )
    }
}
