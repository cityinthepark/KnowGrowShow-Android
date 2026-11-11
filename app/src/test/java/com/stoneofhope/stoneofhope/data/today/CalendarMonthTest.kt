package com.stoneofhope.stoneofhope.data.today

import com.stoneofhope.stoneofhope.data.local.entity.ChapterLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.data.today.TodayActivity.TileKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `CalendarSummaryView` day marks and grid, with IOS_ISSUES I1 (bars match the day's summary). */
class CalendarMonthTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val month = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 27)
    private var n = 0

    private fun at(day: Int, hour: Int = 9): Instant = month.atDay(day).atTime(hour, 0).atZone(zone).toInstant()
    private fun id() = "64a0000000000000000%05x".format(++n)

    private fun note(type: NoteType, created: Instant, block: NoteEntity.() -> NoteEntity = { this }) =
        NoteEntity(id = id(), noteType = type, content = "x", createdAt = created, updatedAt = created).block()

    private fun build(
        created: List<NoteEntity> = emptyList(),
        comments: List<NoteCommentEntity> = emptyList(),
        commented: List<NoteEntity> = emptyList(),
        pinned: List<NoteEntity> = emptyList(),
        sermons: List<SermonEntity> = emptyList(),
        videos: List<YouTubeVideoEntity> = emptyList(),
    ) = CalendarMonthBuilder.build(month, today, zone, created, comments, commented, pinned, sermons, videos)

    @Test fun eachDayGetsTheTilesItsSummaryWouldShow() {
        val quick = note(NoteType.Note, at(3))
        val study = note(NoteType.StudyNote, at(3))
        val chapterNote = note(NoteType.Note, at(5)) { copy(bookOrd = 1, chapterOrd = 2) }
        val anchor = note(NoteType.BibleVerse, at(6)) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16) }
        val answered = note(NoteType.Prayer, at(8)) { copy(isAnswered = true) }
        val image = note(NoteType.Image, at(9))
        val marks = build(created = listOf(quick, study, chapterNote, anchor, answered, image))
        assertEquals(setOf(TileKind.QuickNote, TileKind.StudyNote), marks.getValue(month.atDay(3)).tiles)
        assertEquals(setOf(TileKind.Bible), marks.getValue(month.atDay(5)).tiles)
        assertEquals(setOf(TileKind.Bible), marks.getValue(month.atDay(6)).tiles)
        assertEquals(setOf(TileKind.Prayer, TileKind.AnsweredPrayer), marks.getValue(month.atDay(8)).tiles)
        assertEquals(setOf(TileKind.Image), marks.getValue(month.atDay(9)).tiles)
        assertNull("days with nothing are left out", marks[month.atDay(4)])
    }

    /** I1: iOS marks a reflection (`.note` with a verse) as a quick note, but the summary has no such tile. */
    @Test fun aReflectionDoesNotMakeAQuickNoteBar() {
        val reflection = note(NoteType.Note, at(12)) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16) }
        assertNull(build(created = listOf(reflection))[month.atDay(12)])
    }

    @Test fun aCommentMarksItsDayAndAStarredCommentStars() {
        val old = note(NoteType.Note, Instant.parse("2026-01-01T01:00:00Z"))
        val comment = NoteCommentEntity(id = id(), noteId = old.id, content = "c", createdAt = at(14), starLevel = 1)
        assertEquals(setOf(TileKind.QuickNote, TileKind.Starred), build(comments = listOf(comment), commented = listOf(old)).getValue(month.atDay(14)).tiles)
    }

    @Test fun pinnedNotesMarkTodayOnly() {
        val pinned = note(NoteType.Note, at(2)) { copy(isPinned = true) }
        val marks = build(pinned = listOf(pinned))
        assertEquals(setOf(TileKind.Pinned), marks.getValue(today).tiles)
        assertEquals(listOf(today), marks.keys.toList())
    }

    @Test fun sermonsAndVideosTintTheirDays() {
        val sermon = SermonEntity(id = id(), title = "s", preachedOn = "20261018", createdAt = at(1))
        val video = YouTubeVideoEntity(youtubeID = "abc", title = "v", createdAt = at(18, 20), updatedAt = at(18, 20))
        val day = build(sermons = listOf(sermon), videos = listOf(video)).getValue(month.atDay(18))
        assertTrue(day.hasSermon && day.hasVideo && day.tiles.isEmpty())
        assertTrue(day.showsSermon(NoteFilter.All) && day.showsVideo(NoteFilter.All))
        assertFalse(day.showsSermon(NoteFilter.YouTubeVideo))
        assertTrue(day.showsVideo(NoteFilter.YouTubeVideo))
        assertFalse(day.showsVideo(NoteFilter.Prayer))
    }

    @Test fun barsFollowIosOrderAndTheTypeMenu() {
        val day = DayMarks(TileKind.entries.toSet(), hasSermon = true, hasVideo = true)
        assertEquals(
            listOf(
                TileKind.QuickNote, TileKind.StudyNote, TileKind.Starred, TileKind.Prayer,
                TileKind.AnsweredPrayer, TileKind.Pinned, TileKind.Image, TileKind.Bible,
            ),
            day.bars(NoteFilter.All),
        )
        assertEquals(listOf(TileKind.Prayer), day.bars(NoteFilter.Prayer))
        assertEquals(listOf(TileKind.Bible), day.bars(NoteFilter.Bible))
        assertTrue(day.bars(NoteFilter.YouTubeVideo).isEmpty())
        assertTrue(DayMarks(setOf(TileKind.Image), false, false).bars(NoteFilter.Prayer).isEmpty())
    }

    @Test fun theTypeMenuNarrowsTheDaySummary() {
        val activity = TodayActivity(
            tiles = listOf(TodayActivity.Tile(TileKind.Prayer, 2), TodayActivity.Tile(TileKind.QuickNote, 1)),
            chaptersRead = listOf(TodayActivity.ChapterRead(bookOrd = 1, chapter = 1, isLogged = true)),
            sermons = listOf(TodayActivity.SermonRow(SermonEntity(id = id(), title = "s", createdAt = at(1)), 0, emptyList())),
            videos = listOf(TodayActivity.VideoRow(YouTubeVideoEntity(youtubeID = "v", title = "v", createdAt = at(1), updatedAt = at(1)), 0, emptyList())),
            showReview = true,
        )
        assertEquals(activity, activity.filtered(NoteFilter.All))
        with(activity.filtered(NoteFilter.Prayer)) {
            assertEquals(listOf(TileKind.Prayer), tiles.map { it.kind })
            assertTrue(chaptersRead.isEmpty() && videos.isEmpty())
            assertEquals(1, sermons.size)
        }
        with(activity.filtered(NoteFilter.YouTubeVideo)) {
            assertTrue(tiles.isEmpty() && chaptersRead.isEmpty())
            assertEquals(1, videos.size)
        }
    }

    // MARK: - Grid (iOS CustomCalendarView.daysInMonthGrid)

    @Test fun gridStartsOnTheLocalesFirstWeekday() {
        // 1 October 2026 is a Thursday.
        val sundayFirst = CalendarGrid.weeks(month, DayOfWeek.SUNDAY)
        assertEquals(listOf(null, null, null, null, month.atDay(1), month.atDay(2), month.atDay(3)), sundayFirst.first())
        assertEquals(5, sundayFirst.size)
        val mondayFirst = CalendarGrid.weeks(month, DayOfWeek.MONDAY)
        assertEquals(month.atDay(1), mondayFirst.first()[3])
        assertTrue(mondayFirst.all { it.size == 7 })
        assertEquals(month.atDay(31), mondayFirst.last().filterNotNull().last())
        // February 2026 starts on a Sunday and has 28 days: exactly four full weeks.
        val feb = CalendarGrid.weeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(4, feb.size)
        assertTrue(feb.flatten().none { it == null })
    }
}
