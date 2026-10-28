package com.stoneofhope.stoneofhope.data.bible

import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity

/** iOS `BibleLinkBucket`: the context a `.bibleVerse` anchor is de-duplicated within. */
sealed interface BibleLinkBucket {
    data object General : BibleLinkBucket
    data class Sermon(val sermonId: String) : BibleLinkBucket
    data class YouTube(val youtubeID: String) : BibleLinkBucket

    companion object {
        /** `sermonId` wins when both are present. */
        fun from(sermonId: String?, youtubeID: String?): BibleLinkBucket {
            if (sermonId != null) return Sermon(sermonId)
            val trimmed = youtubeID?.let(SwiftText::trim)
            return if (!trimmed.isNullOrEmpty()) YouTube(trimmed) else General
        }

        fun from(note: NoteEntity): BibleLinkBucket = from(note.sermonId, note.youtubeID)
    }
}

/** iOS `BookChapter`: one chapter of one book (1-based ords). */
data class BookChapter(val bookOrd: Int, val chapterOrd: Int)

/** iOS `BibleAnchor`: ord-based identity of a verse reference in one version. */
data class BibleAnchor(
    val bookOrd: Int,
    val chapterOrd: Int,
    val verseStart: Int?,
    val verseEnd: Int?,
    val versionAbbr: String,
) {
    val isChapterLevel: Boolean get() = verseStart == null

    /** Inclusive verse numbers; empty for a chapter-level anchor (no `userVerses`). */
    val verseOrds: List<Int>
        get() {
            val start = verseStart ?: return emptyList()
            val end = verseEnd ?: start
            return (minOf(start, end)..maxOf(start, end)).toList()
        }

    fun refLabel(showVersion: Boolean = false): String? =
        BibleFormatter.display(bookOrd, chapterOrd, verseStart, verseEnd, versionAbbr, showVersion)

    companion object {
        /** iOS `BibleAnchor.from(_ note:)`: needs book, chapter and a non-blank version. */
        fun from(note: NoteEntity): BibleAnchor? {
            val bookOrd = note.bookOrd ?: return null
            val chapterOrd = note.chapterOrd ?: return null
            val abbr = note.versionAbbr?.let(SwiftText::trim).orEmpty()
            if (abbr.isEmpty()) return null
            return BibleAnchor(bookOrd, chapterOrd, note.verseStart, note.verseEnd, abbr)
        }
    }
}

/** iOS `BibleAnchorKey` + `NotesManager.makeRefKey`: the legacy string keys iOS still writes. */
object BibleAnchorKey {
    fun chapterKey(bookOrd: Int?, chapterOrd: Int?): String? =
        if (bookOrd != null && chapterOrd != null) "CH:$bookOrd:$chapterOrd" else null

    fun verseRefKey(bookOrd: Int, chapterOrd: Int, verseStart: Int, verseEnd: Int?): String =
        "VS:$bookOrd:$chapterOrd:$verseStart-${verseEnd ?: verseStart}"
}
