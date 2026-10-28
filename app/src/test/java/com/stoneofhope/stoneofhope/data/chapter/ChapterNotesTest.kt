package com.stoneofhope.stoneofhope.data.chapter

import com.stoneofhope.stoneofhope.data.bible.BookChapter
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `ChapterNoteViewModel` / `ChapterNoteDetailsView` grouping, with the fixes of IOS_ISSUES H1–H4. */
class ChapterNotesTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 27)
    private val john3 = BookChapter(43, 3)
    private var n = 0

    private fun at(day: LocalDate, hour: Int): Instant = day.atTime(hour, 0).atZone(zone).toInstant()
    private fun id() = "64a0000000000000000%05x".format(++n)

    private fun note(type: NoteType, created: Instant, block: NoteEntity.() -> NoteEntity = { this }) =
        NoteEntity(id = id(), noteType = type, content = "x$n", createdAt = created, updatedAt = created).block()

    private fun NoteEntity.on(verse: Int?, verseEnd: Int? = verse, book: Int = 43, chapter: Int = 3) =
        copy(bookOrd = book, chapterOrd = chapter, verseStart = verse, verseEnd = verseEnd, versionAbbr = "KJV")

    private fun anchor(created: Instant, verse: Int, verseEnd: Int? = verse) =
        note(NoteType.BibleVerse, created) { copy(content = NoteVerseAnchor.PLACEHOLDER).on(verse, verseEnd) }

    private fun reflection(created: Instant, verse: Int, text: String = "thought") = note(NoteType.Note, created) { copy(content = text).on(verse) }

    private fun comment(noteId: String, created: Instant, block: NoteCommentEntity.() -> NoteCommentEntity = { this }) =
        NoteCommentEntity(id = id(), noteId = noteId, content = "c$n", createdAt = created).block()

    private fun build(notes: List<NoteEntity>, comments: List<NoteCommentEntity> = emptyList()) = ChapterNotesBuilder.build(notes, comments, john3)

    @Test fun chapterNotesComeFirstOldestFirstThenVersesInVerseOrder() {
        val later = note(NoteType.Note, at(today, 9)) { on(null) }
        val earlier = note(NoteType.StudyNote, at(today, 8)) { on(null) }
        val v16 = anchor(at(today, 7), 16)
        val v3to5 = reflection(at(today, 6), 3).copy(verseEnd = 5)
        val v3 = reflection(at(today, 6), 3)
        val result = build(listOf(later, v16, earlier, v3to5, v3))
        assertEquals(listOf(earlier.id, later.id), result.chapterNotes.map { it.id })
        assertEquals(listOf(3 to 3, 3 to 5, 16 to 16), result.verses.map { it.verse.verseStart to it.verse.verseEnd })
    }

    @Test fun unsetVerseEndGroupsWithTheSingleVerse() {
        val webAnchor = anchor(at(today, 9), 16, verseEnd = null)
        val reflection = reflection(at(today, 10), 16)
        val group = build(listOf(webAnchor, reflection)).verses.single()
        assertEquals(ChapterVerse(43, 3, 16, 16), group.verse)
        assertEquals(listOf(webAnchor.id), group.anchors.map { it.id })
        assertEquals(1, group.reflectionCount)
    }

    /** H1: anchors are per day; the verse page shows every one of them, newest first. */
    @Test fun everyDaysAnchorAndReflectionsBelongToTheVerse() {
        val oldAnchor = anchor(at(today.minusDays(3), 9), 16)
        val oldThought = reflection(at(today.minusDays(3), 9), 16, "then")
        val newAnchor = anchor(at(today, 9), 16)
        val newThought = reflection(at(today, 10), 16, "now")
        val cuvAnchor = anchor(at(today, 11), 16).copy(versionAbbr = "CUV")
        val group = build(listOf(oldAnchor, oldThought, newAnchor, newThought, cuvAnchor)).verses.single()
        assertEquals(listOf(cuvAnchor.id, newAnchor.id, oldAnchor.id), group.anchors.map { it.id })
        assertEquals(listOf("now", "then"), group.reflections.map { it.content })
        assertEquals(2, group.reflectionCount)
    }

    /** H3: the summary counts exactly what the verse page lists. */
    @Test fun countsOnlyWhatTheVersePageShows() {
        val a = anchor(at(today, 9), 16)
        val thought = reflection(at(today, 9), 16)
        val prayer = note(NoteType.Prayer, at(today, 9)) { on(16) }
        val legacyOnAnchor = comment(a.id, at(today, 10)) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16) }
        val answeredMarker = comment(a.id, at(today, 10)) { copy(content = "64a0000000000000000000ff") }
        val deletedOnAnchor = comment(a.id, at(today, 10)) { copy(deletedAt = at(today, 11)) }
        val onReflection = comment(thought.id, at(today, 10)) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16) }
        val group = build(listOf(a, thought, prayer), listOf(legacyOnAnchor, answeredMarker, deletedOnAnchor, onReflection)).verses.single()
        assertEquals(listOf(legacyOnAnchor.id), group.anchorComments.map { it.id })
        assertEquals(2, group.reflectionCount)
        assertEquals(listOf(prayer.id), group.prayers.map { it.id })
    }

    /** H4: a verse saved with no reflection still has its place on the page. */
    @Test fun aBareAnchorStillShows() {
        val group = build(listOf(anchor(at(today, 9), 16))).verses.single()
        assertEquals(0, group.reflectionCount)
        assertTrue(group.prayers.isEmpty())
    }

    /** H2: a chapter note's comments stay with that note; they never make a verse group. */
    @Test fun chapterNoteCommentsDoNotMakeAVerseGroup() {
        val chapterNote = note(NoteType.Note, at(today, 9)) { on(null) }
        val c = comment(chapterNote.id, at(today, 10)) { copy(bookOrd = 43, chapterOrd = 3) }
        val result = build(listOf(chapterNote), listOf(c))
        assertEquals(listOf(chapterNote.id), result.chapterNotes.map { it.id })
        assertTrue(result.verses.isEmpty())
    }

    @Test fun otherChaptersDeletedNotesPlaceholdersAndOtherTypesStayOut() {
        val otherChapter = reflection(at(today, 9), 16).copy(chapterOrd = 4)
        val otherBook = reflection(at(today, 9), 16).copy(bookOrd = 1)
        val deleted = reflection(at(today, 9), 16).copy(deletedAt = at(today, 10))
        val tombstone = reflection(at(today, 9), 16).copy(isDeleted = true)
        val placeholderNote = reflection(at(today, 9), 17, NoteVerseAnchor.PLACEHOLDER)
        val sermonNote = note(NoteType.SermonNote, at(today, 9)) { on(18) }
        val plain = note(NoteType.Note, at(today, 9))
        val result = build(listOf(otherChapter, otherBook, deleted, tombstone, placeholderNote, sermonNote, plain))
        assertTrue(result.isEmpty)
    }

    @Test fun prayersGroupByTheirVerses() {
        val p1 = note(NoteType.Prayer, at(today, 8)) { on(16, 17) }
        val p2 = note(NoteType.Prayer, at(today, 9)) { on(16, 17) }
        val group = build(listOf(p1, p2)).verses.single()
        assertEquals(ChapterVerse(43, 3, 16, 17), group.verse)
        assertEquals(listOf(p2.id, p1.id), group.prayers.map { it.id })
        assertTrue(group.anchors.isEmpty())
        assertEquals(listOf(p2.id, p1.id), group.textSources.map { it.id })
    }

    @Test fun versePageForAVerseWithNothingIsEmpty() {
        val group = ChapterNotesBuilder.verse(listOf(reflection(at(today, 9), 16)), emptyList(), ChapterVerse(43, 3, 17, 17))
        assertTrue(group.anchors.isEmpty() && group.reflections.isEmpty() && group.prayers.isEmpty())
        assertEquals(0, group.reflectionCount)
    }

    @Test fun refLabelFollowsTheVersionLanguage() {
        val v = ChapterVerse(43, 3, 16, 18)
        assertEquals("John 3:16-18", v.refLabel("KJV"))
        assertEquals("約翰福音 3:16-18", v.refLabel("CUV"))
        assertEquals("John 3:16", ChapterVerse(43, 3, 16, 16).refLabel(null))
    }

    // MARK: - Chapter sermons (iOS ChapterSermonView, H6)

    private fun sermon(preachedOn: String, created: Instant, chapters: List<Int> = listOf(3), block: SermonEntity.() -> SermonEntity = { this }) =
        SermonEntity(id = id(), title = "s$n", bookOrd = 43, chapters = chapters, preachedOn = preachedOn, createdAt = created).block()

    @Test fun sermonsForAChapterNeedTheChapterInTheirList() {
        val both = sermon("20251019", at(today, 9), listOf(2, 3))
        val other = sermon("20251019", at(today, 9), listOf(4))
        val deleted = sermon("20251019", at(today, 9)) { copy(deletedAt = at(today, 10)) }
        assertEquals(listOf(both.id), ChapterSermons.forChapter(listOf(both, other, deleted), 3).map { it.id })
    }

    @Test fun sermonsGroupByYearNewestFirstUndatedLast() {
        val undated = sermon("", at(today, 9))
        val y2024 = sermon("20240301", at(today, 9))
        val y2025a = sermon("20250105", at(today, 9))
        val y2025b = sermon("20251019", at(today, 8))
        val y2025sameDayLater = sermon("20251019", at(today, 10))
        val years = ChapterSermons.byYear(listOf(undated, y2024, y2025a, y2025b, y2025sameDayLater))
        assertEquals(listOf(2025, 2024, null), years.map { it.year })
        assertEquals(listOf(y2025sameDayLater.id, y2025b.id, y2025a.id), years[0].sermons.map { it.id })
        assertEquals(listOf(undated.id), years[2].sermons.map { it.id })
    }

    @Test fun sermonLinksAndDates() {
        val s = sermon("20251019", at(today, 9)) { copy(youtubeID = " abc123 ", videoUrl = "https://example.com/v", audioUrl = " ", pdfUrl = "https://example.com/p.pdf") }
        assertEquals("https://www.youtube.com/watch?v=abc123", ChapterSermons.watchUrl(s))
        assertEquals("https://example.com/v", ChapterSermons.watchUrl(s.copy(youtubeID = null)))
        assertNull(ChapterSermons.watchUrl(s.copy(youtubeID = "", videoUrl = null)))
        assertNull(ChapterSermons.audioUrl(s))
        assertEquals("https://example.com/p.pdf", ChapterSermons.pdfUrl(s))
        assertEquals(LocalDate.of(2025, 10, 19), ChapterSermons.preachedOn(s))
        assertNull(ChapterSermons.preachedOn(s.copy(preachedOn = "2025")))
        assertNull(ChapterSermons.preachedOn(s.copy(preachedOn = "2025xx19")))
    }
}
