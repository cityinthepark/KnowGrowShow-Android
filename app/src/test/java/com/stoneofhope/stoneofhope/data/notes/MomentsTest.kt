package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MomentsTabView` / `NoteListViewModel(.forMoments)` rules, with IOS_ISSUES I3, I4, I6. */
class MomentsTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 27)
    private val window = MomentsRange.Last.Days30.days(today)
    private var n = 0

    private fun at(day: LocalDate, hour: Int): Instant = day.atTime(hour, 0).atZone(zone).toInstant()
    private fun id() = "64a0000000000000000%05x".format(++n)

    private fun note(type: NoteType, created: Instant, content: String = "x", block: NoteEntity.() -> NoteEntity = { this }) =
        NoteEntity(id = id(), noteType = type, content = content, createdAt = created, updatedAt = created).block()

    private fun comment(noteId: String, created: Instant, block: NoteCommentEntity.() -> NoteCommentEntity = { this }) =
        NoteCommentEntity(id = id(), noteId = noteId, content = "c", createdAt = created).block()

    private fun build(
        notes: List<NoteEntity>, comments: List<NoteCommentEntity> = emptyList(), filter: NoteFilter = NoteFilter.All,
        tag: String? = null, tags: Map<String, String> = emptyMap(), days: ClosedRange<LocalDate> = window,
    ) = MomentsBuilder.build(NoteListData(notes, comments, tags, emptySet(), emptySet()), filter, tag, days, zone)

    private fun List<MomentsSection>.ids() = flatMap { s -> s.notes.map { it.id } }

    @Test fun windowsEndTodayAndPresetsCoverWhatTheySay() {
        assertEquals(LocalDate.of(2026, 9, 27)..today, MomentsRange.Last.Days30.days(today))
        assertEquals(LocalDate.of(2026, 10, 20)..today, MomentsRange.Last.Days7.days(today))
        assertEquals(LocalDate.of(2026, 7, 29)..today, MomentsRange.Last.Months3.days(today))
        assertEquals(LocalDate.of(2025, 10, 27)..today, MomentsRange.Last.Months12.days(today))
        // I4: the default window is computed from whatever today is.
        assertEquals(today.plusDays(1), MomentsRange.Last.Days30.days(today.plusDays(1)).endInclusive)
        val custom = MomentsRange.Custom(LocalDate.of(2026, 5, 9), LocalDate.of(2026, 5, 1))
        assertEquals(LocalDate.of(2026, 5, 1)..LocalDate.of(2026, 5, 9), custom.days(today))
    }

    @Test fun newestFirstByDayInsideTheWindowOnly() {
        val todayLate = note(NoteType.Note, at(today, 20))
        val todayEarly = note(NoteType.Prayer, at(today, 8))
        val lastWeek = note(NoteType.StudyNote, at(today.minusDays(7), 9))
        val firstDay = note(NoteType.Note, at(today.minusDays(30), 0))
        val tooOld = note(NoteType.Note, at(today.minusDays(31), 23))
        val sections = build(listOf(tooOld, lastWeek, todayEarly, firstDay, todayLate))
        assertEquals(listOf(today, today.minusDays(7), today.minusDays(30)), sections.map { it.day })
        assertEquals(listOf(todayLate.id, todayEarly.id, lastWeek.id, firstDay.id), sections.ids())
    }

    /** I6: an old note commented in the window shows on the day of its latest comment there, not in March. */
    @Test fun anOldNoteCommentedInTheWindowSitsOnItsLatestCommentDay() {
        val old = note(NoteType.Note, at(LocalDate.of(2026, 3, 3), 9))
        val recent = note(NoteType.Note, at(today.minusDays(2), 9))
        val comments = listOf(
            comment(old.id, at(today.minusDays(5), 10)),
            comment(old.id, at(today.minusDays(1), 10)),
            comment(old.id, at(today.plusDays(0).minusDays(40), 10)), // outside the window: ignored
        )
        val sections = build(listOf(old, recent), comments)
        assertEquals(listOf(today.minusDays(1), today.minusDays(2)), sections.map { it.day })
        assertEquals(listOf(old.id, recent.id), sections.ids())
        // A deleted comment brings nothing in.
        assertTrue(build(listOf(old), listOf(comment(old.id, at(today, 9)) { copy(deletedAt = at(today, 10)) })).isEmpty())
    }

    @Test fun reflectionsUnderAnAnchorAreNotRowsOfTheirOwn() {
        val anchor = note(NoteType.BibleVerse, at(today, 9), NoteVerseAnchor.PLACEHOLDER) {
            copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16, versionAbbr = "KJV")
        }
        val reflection = note(NoteType.Note, at(today, 9), "so loved") {
            copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16, versionAbbr = "KJV")
        }
        val loose = note(NoteType.Note, at(today, 9), "web note") {
            copy(bookOrd = 1, chapterOrd = 1, verseStart = 1, verseEnd = 1, versionAbbr = "KJV")
        }
        assertEquals(setOf(anchor.id, loose.id), build(listOf(anchor, reflection, loose)).ids().toSet())
    }

    @Test fun typeAndTagMenusUseTheNoteListRules() {
        val prayer = note(NoteType.Prayer, at(today, 9)) { copy(tagIds = listOf("t1")) }
        val quick = note(NoteType.Note, at(today, 10))
        val chapterNote = note(NoteType.Note, at(today, 11)) { copy(bookOrd = 1, chapterOrd = 2) }
        val starredByComment = note(NoteType.Note, at(today, 12))
        val comments = listOf(comment(starredByComment.id, at(today.minusDays(60), 9)) { copy(starLevel = 2, tagIds = listOf("t2")) })
        val notes = listOf(prayer, quick, chapterNote, starredByComment)
        val tags = mapOf("t1" to "Family", "t2" to "Hope")
        assertEquals(listOf(prayer.id), build(notes, comments, NoteFilter.Prayer, tags = tags).ids())
        assertEquals(listOf(starredByComment.id, quick.id), build(notes, comments, NoteFilter.QuickNote, tags = tags).ids())
        assertEquals(listOf(chapterNote.id), build(notes, comments, NoteFilter.Bible, tags = tags).ids())
        assertEquals(listOf(starredByComment.id), build(notes, comments, NoteFilter.Starred, tags = tags).ids())
        assertEquals(listOf(prayer.id), build(notes, comments, tag = "Family", tags = tags).ids())
        assertEquals(listOf(starredByComment.id), build(notes, comments, tag = "Hope", tags = tags).ids())
    }

    // MARK: - Row lines (iOS MomentsNoteRow.displayLines)

    @Test fun anAnchorShowsItsReferenceThenItsVerse() {
        val anchor = note(NoteType.BibleVerse, at(today, 9), NoteVerseAnchor.PLACEHOLDER) {
            copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16, versionAbbr = "KJV", refKey = "VS:43:3:16-16")
        }
        assertEquals(
            listOf(MomentLine.Reference("John 3:16", sermonMark = true), MomentLine.Body("For God so loved the world")),
            MomentsBuilder.lines(anchor, "For God so loved the world", sermonMark = true),
        )
        // Verse text not in the pool yet: just the reference.
        assertEquals(listOf(MomentLine.Reference("John 3:16", false)), MomentsBuilder.lines(anchor, NoteVerseAnchor.PLACEHOLDER, false))
    }

    @Test fun otherNotesShowTheirNonBlankLinesUpToFour() {
        val text = "first\n\n  second  \nthird\nfourth\nfifth"
        val lines = MomentsBuilder.lines(note(NoteType.Note, at(today, 9), text), text, false)
        assertEquals(listOf("first", "second", "third", "fourth"), lines.map { (it as MomentLine.Body).text })
        assertEquals(listOf(MomentLine.Body("")), MomentsBuilder.lines(note(NoteType.Note, at(today, 9), "  "), "  ", false))
    }

    @Test fun longTextStopsAt120CharactersWithAnEllipsis() {
        val long = "愛".repeat(130)
        val line = (MomentsBuilder.lines(note(NoteType.Prayer, at(today, 9), long), long, false).single() as MomentLine.Body).text
        assertEquals("愛".repeat(120) + "…", line)
        val exact = "a".repeat(120)
        assertEquals(exact, (MomentsBuilder.lines(note(NoteType.Note, at(today, 9), exact), exact, false).single() as MomentLine.Body).text)
    }
}
