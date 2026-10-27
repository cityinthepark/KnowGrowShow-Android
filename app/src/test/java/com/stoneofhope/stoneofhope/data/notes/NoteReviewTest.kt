package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `notesForDailyReview`. */
class NoteReviewTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 26)
    private fun at(day: LocalDate, hour: Int) = day.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant()
    private fun note(id: String, type: NoteType, day: LocalDate = today, hour: Int = 9, block: NoteEntity.() -> NoteEntity = { this }) =
        NoteEntity(id = id, noteType = type, content = "text $id", createdAt = at(day, hour), updatedAt = at(day, hour)).block()

    @Test fun dayNotesOfTheReviewTypesOldestFirst() {
        val notes = listOf(
            note("prayer", NoteType.Prayer, hour = 20),
            note("quick", NoteType.Note, hour = 8),
            note("study", NoteType.StudyNote, hour = 12),
            note("image", NoteType.Image, hour = 13),
            note("sermon", NoteType.SermonNote, hour = 9),
            note("growth", NoteType.PersonalGrowth, hour = 9),
            note("yesterday", NoteType.Note, day = today.minusDays(1)),
        )
        assertEquals(listOf("quick", "study", "image", "prayer"), NoteReview.notes(today, today, notes, emptyList(), zone).map { it.id })
    }

    @Test fun aCommentThatDayBringsAnOlderNoteIn() {
        val old = note("old", NoteType.Prayer, day = today.minusDays(5))
        val comment = NoteCommentEntity(id = "c", noteId = "old", content = "amen", createdAt = at(today, 7))
        assertEquals(listOf("old"), NoteReview.notes(today, today, listOf(old), listOf(comment), zone).map { it.id })
        assertEquals(emptyList<String>(), NoteReview.notes(today.minusDays(1), today, listOf(old), listOf(comment), zone).map { it.id })
    }

    @Test fun pinnedNotesJoinOnlyOnToday() {
        val pinned = note("pinned", NoteType.Note, day = today.minusDays(30)) { copy(isPinned = true) }
        val todays = note("todays", NoteType.Note, hour = 10) { copy(isPinned = true) }
        assertEquals(listOf("pinned", "todays"), NoteReview.notes(today, today, listOf(todays, pinned), emptyList(), zone).map { it.id })
        val yesterday = today.minusDays(1)
        assertEquals(emptyList<String>(), NoteReview.notes(yesterday, today, listOf(todays, pinned), emptyList(), zone).map { it.id })
    }

    @Test fun reflectionsStayUnderTheirAnchor() {
        val anchor = note("anchor", NoteType.BibleVerse, hour = 8) {
            copy(content = NoteVerseAnchor.PLACEHOLDER, bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16, versionAbbr = "KJV")
        }
        val reflection = note("reflection", NoteType.Note, hour = 9) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16, versionAbbr = "KJV") }
        assertEquals(listOf("anchor"), NoteReview.notes(today, today, listOf(anchor, reflection), emptyList(), zone).map { it.id })
    }
}
