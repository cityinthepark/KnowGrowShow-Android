package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.model.MarkStyle

/**
 * A highlight or underline over part of a verse (iOS `IndexedText`). Read-only on Android, as on iOS:
 * marks are made on the Web (USER_VERSE_CONTRACT §3.2.1). Offsets are UTF-16 indices, as Kotlin's.
 */
data class VerseMark(val start: Int, val end: Int, val style: MarkStyle, val colorKey: String)

/** Text with the marks that fall on it (offsets into [text]). */
data class MarkedVerse(val text: String, val marks: List<VerseMark> = emptyList()) {

    /** [text] trimmed, marks moved with it and cut to what is left. */
    fun trimmed(): MarkedVerse {
        val lead = text.length - text.trimStart().length
        val t = text.trim()
        return MarkedVerse(t, marks.mapNotNull { m ->
            val s = (m.start - lead).coerceIn(0, t.length)
            val e = (m.end - lead).coerceIn(0, t.length)
            if (s < e) m.copy(start = s, end = e) else null
        })
    }
}

object VerseMarks {

    /** Active verse marks grouped by their `UserVerse` id. */
    fun byVerseId(rows: List<IndexedTextEntity>): Map<String, List<IndexedTextEntity>> =
        rows.filter { it.sourceType == IndexedTextEntity.SOURCE_TYPE_VERSE && it.deletedAt == null && !it.isDeleted }
            .groupBy { it.sourceId }

    /**
     * iOS `highlightedAttributedString`: marks outside the text, empty ones and ones splitting a
     * character (a surrogate pair) are skipped; the rest are kept, overlaps included.
     */
    fun onText(text: String, rows: List<IndexedTextEntity>): List<VerseMark> = rows
        .filter { m ->
            m.startOffset >= 0 && m.endOffset <= text.length && m.startOffset < m.endOffset &&
                !splitsPair(text, m.startOffset) && !splitsPair(text, m.endOffset)
        }
        .sortedBy { it.startOffset }
        .map { VerseMark(it.startOffset, it.endOffset, it.markStyle, it.colorKey) }

    private fun splitsPair(text: String, offset: Int) =
        offset in 1 until text.length && text[offset].isLowSurrogate() && text[offset - 1].isHighSurrogate()
}
