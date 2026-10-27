package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.model.MarkStyle
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `UserVerse.highlightedAttributedString` / `combinedHighlightedText` (read-only `IndexedText`). */
class VerseMarksTest {

    private val t = Instant.parse("2026-10-26T02:00:00Z")
    private fun mark(id: String, source: String, start: Int, end: Int, style: MarkStyle = MarkStyle.Highlight, color: String = "yellow", deleted: Boolean = false) =
        IndexedTextEntity(
            id = id, sourceId = source, startOffset = start, endOffset = end, markStyle = style, colorKey = color,
            createdAt = t, updatedAt = t, deletedAt = if (deleted) t else null, isDeleted = deleted,
        )

    @Test fun outOfRangeEmptyAndDeletedMarksAreSkipped() {
        val text = "In the beginning"
        val rows = VerseMarks.byVerseId(
            listOf(
                mark("ok", "v", 7, 16),
                mark("pastEnd", "v", 10, 17),
                mark("negative", "v", -1, 2),
                mark("empty", "v", 3, 3),
                mark("backwards", "v", 5, 2),
                mark("deleted", "v", 0, 2, deleted = true),
                mark("note", "v", 0, 2).copy(sourceType = "note"),
            ),
        )
        assertEquals(listOf(VerseMark(7, 16, MarkStyle.Highlight, "yellow")), VerseMarks.onText(text, rows.getValue("v")))
    }

    /** Offsets are UTF-16 units (the Web's JS indexing); a mark may not split an emoji. */
    @Test fun utf16OffsetsAndSurrogatePairs() {
        val text = "Joy 😀 comes"
        assertEquals(listOf(4, 6), VerseMarks.onText(text, listOf(mark("emoji", "v", 4, 6))).flatMap { listOf(it.start, it.end) })
        assertEquals(emptyList<VerseMark>(), VerseMarks.onText(text, listOf(mark("half", "v", 4, 5))))
        assertEquals(emptyList<VerseMark>(), VerseMarks.onText(text, listOf(mark("otherHalf", "v", 5, 8))))
    }

    @Test fun overlapsAndBothStylesAreKept() {
        val marks = VerseMarks.onText(
            "God so loved the world",
            listOf(mark("u", "v", 4, 12, MarkStyle.Underline, "blue"), mark("h", "v", 0, 6, color = "pink")),
        )
        assertEquals(listOf("pink", "blue"), marks.map { it.colorKey })
        assertEquals(listOf(MarkStyle.Highlight, MarkStyle.Underline), marks.map { it.style })
    }

    @Test fun resolvedVerseCombinesMarksAtEachVerseStart() {
        val note = NoteEntity(
            id = "a", noteType = NoteType.BibleVerse, content = NoteVerseAnchor.PLACEHOLDER, createdAt = t, updatedAt = t,
            bookOrd = 1, chapterOrd = 1, verseStart = 1, verseEnd = 2, versionAbbr = "KJV",
        )
        val v1 = UserVerseEntity(id = "KJV_1_1_1", versionAbbr = "KJV", bookOrd = 1, chapterOrd = 1, verseOrd = 1, content = "In the beginning", createdAt = t, updatedAt = t)
        val v2 = UserVerseEntity(id = "KJV_1_1_2", versionAbbr = "KJV", bookOrd = 1, chapterOrd = 1, verseOrd = 2, content = "And the earth", createdAt = t, updatedAt = t)
        val marks = VerseMarks.byVerseId(listOf(mark("m1", "KJV_1_1_1", 7, 16), mark("m2", "KJV_1_1_2", 8, 13, MarkStyle.Underline)))
        val resolved = VerseDisplay.resolve(note, mapOf(v1.id to v1, v2.id to v2), marks)!!
        assertEquals(listOf(VerseMark(8, 13, MarkStyle.Underline, "yellow")), resolved.verse(2, "And the earth").marks)
        val combined = resolved.combined
        assertEquals("In the beginning And the earth", combined.text)
        assertEquals(listOf("beginning", "earth"), combined.marks.map { combined.text.substring(it.start, it.end) })
    }

    @Test fun trimmingMovesAndCutsMarks() {
        val v = MarkedVerse("  grace and peace ", listOf(VerseMark(0, 7, MarkStyle.Highlight, "green"), VerseMark(12, 18, MarkStyle.Highlight, "green")))
        val trimmed = v.trimmed()
        assertEquals("grace and peace", trimmed.text)
        assertEquals(listOf("grace", "peace"), trimmed.marks.map { trimmed.text.substring(it.start, it.end) })
    }
}
