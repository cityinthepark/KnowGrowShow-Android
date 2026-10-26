package com.stoneofhope.stoneofhope.data.sync

import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor

/**
 * Port of iOS `FlashCardUserVerseBackfill`: legacy flash-card docs carried the verse text in
 * `content` ("3:16 For God so loved… 3:17 For God did not send…"). On pull it is split per verse
 * into the `userVerses` pool, where verse text now lives.
 */
object FlashCardUserVerseBackfill {

    data class VerseRow(
        val id: String,
        val versionAbbr: String,
        val bookOrd: Int,
        val chapterOrd: Int,
        val verseOrd: Int,
        val content: String,
    )

    /** Empty when content is the placeholder, ords/version are missing, or the text doesn't split. */
    fun verses(
        content: String,
        bookOrd: Int?,
        chapterOrd: Int?,
        verseStart: Int?,
        verseEnd: Int?,
        versionAbbr: String?,
    ): List<VerseRow> {
        if (NoteVerseAnchor.isPlaceholderContent(content)) return emptyList()
        if (bookOrd == null || chapterOrd == null || verseStart == null) return emptyList()
        val end = verseEnd ?: verseStart
        val abbr = versionAbbr?.trim().orEmpty()
        if (abbr.isEmpty()) return emptyList()
        val perVerse = splitLegacyVerseContent(content, chapterOrd, minOf(verseStart, end)..maxOf(verseStart, end))
            ?: return emptyList()
        return perVerse.toSortedMap().mapNotNull { (verseOrd, text) ->
            if (text.isBlank()) return@mapNotNull null
            VerseRow(
                id = UserVerseEntity.makeId(abbr, bookOrd, chapterOrd, verseOrd),
                versionAbbr = abbr,
                bookOrd = bookOrd,
                chapterOrd = chapterOrd,
                verseOrd = verseOrd,
                content = text,
            )
        }
    }

    /**
     * iOS `LegacyBibleVerseNoteMigration.splitLegacyVerseContent`: finds `"{chapter}:{verse} "`
     * markers in order; null if any marker is missing or any segment is empty.
     */
    fun splitLegacyVerseContent(content: String, chapterOrd: Int, verseRange: IntRange): Map<Int, String>? {
        val markers = mutableListOf<Triple<Int, Int, Int>>() // verseOrd, markerStart, markerEnd
        var searchStart = 0
        for (verseOrd in verseRange) {
            val marker = "$chapterOrd:$verseOrd "
            val found = content.indexOf(marker, searchStart)
            if (found < 0) return null
            markers += Triple(verseOrd, found, found + marker.length)
            searchStart = found + marker.length
        }
        val result = mutableMapOf<Int, String>()
        for ((index, entry) in markers.withIndex()) {
            val segmentEnd = if (index + 1 < markers.size) markers[index + 1].second else content.length
            val text = content.substring(entry.third, segmentEnd).trim()
            if (text.isEmpty()) return null
            result[entry.first] = text
        }
        return result
    }
}
