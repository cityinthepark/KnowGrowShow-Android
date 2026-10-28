package com.stoneofhope.stoneofhope.data.today

import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.data.today.TodayActivity.TileKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What one calendar day shows (iOS `DayContent`): the tiles that day's activity would have, and whether
 * a sermon was preached or a video added. Read off [TodayActivityBuilder] so the bars always match the
 * summary below the calendar (IOS_ISSUES I1).
 */
data class DayMarks(val tiles: Set<TileKind>, val hasSermon: Boolean, val hasVideo: Boolean) {

    /** iOS `calendarBars(for:)`, in its order; sermons and videos tint the day instead. */
    fun bars(filter: NoteFilter): List<TileKind> = when (filter) {
        NoteFilter.All -> AllBarsOrder.filter { it in tiles }
        NoteFilter.Sermon, NoteFilter.YouTubeVideo -> emptyList()
        else -> listOfNotNull(filter.tileKind?.takeIf { it in tiles })
    }

    fun showsSermon(filter: NoteFilter) = hasSermon && (filter == NoteFilter.All || filter == NoteFilter.Sermon)

    fun showsVideo(filter: NoteFilter) = hasVideo && (filter == NoteFilter.All || filter == NoteFilter.YouTubeVideo)

    companion object {
        private val AllBarsOrder = listOf(
            TileKind.QuickNote, TileKind.StudyNote, TileKind.Starred, TileKind.Prayer,
            TileKind.AnsweredPrayer, TileKind.Pinned, TileKind.Image, TileKind.Bible,
        )
    }
}

/** The tile a type filter stands for (iOS `NoteFilterUI.summaryItemId`). */
val NoteFilter.tileKind: TileKind?
    get() = when (this) {
        NoteFilter.QuickNote -> TileKind.QuickNote
        NoteFilter.StudyNote -> TileKind.StudyNote
        NoteFilter.Prayer -> TileKind.Prayer
        NoteFilter.AnsweredPrayer -> TileKind.AnsweredPrayer
        NoteFilter.Pinned -> TileKind.Pinned
        NoteFilter.Image -> TileKind.Image
        NoteFilter.Bible -> TileKind.Bible
        NoteFilter.Starred -> TileKind.Starred
        NoteFilter.All, NoteFilter.Sermon, NoteFilter.YouTubeVideo -> null
    }

/**
 * iOS `NoteFilterUI.includesSummaryItem`: the calendar's type menu narrows the day's activity to that
 * tile; sermons always stay, chapters only under "All", videos under "All" and "Video".
 */
fun TodayActivity.filtered(filter: NoteFilter): TodayActivity {
    if (filter == NoteFilter.All) return this
    return copy(
        tiles = tiles.filter { it.kind == filter.tileKind },
        chaptersRead = emptyList(),
        videos = if (filter == NoteFilter.YouTubeVideo) videos else emptyList(),
    )
}

/** iOS `CalendarSummaryView.loadData`: one month of day marks from the month's rows. */
object CalendarMonthBuilder {

    private val DayKey: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    /**
     * @param created active notes created in the month
     * @param comments active comments created in the month
     * @param commentedNotes the active notes those comments belong to
     * @param pinned every active pinned note: it marks today only, like iOS
     * @param sermons sermons preached in the month; [videos] videos added in it
     */
    fun build(
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
        created: List<NoteEntity>,
        comments: List<NoteCommentEntity>,
        commentedNotes: List<NoteEntity>,
        pinned: List<NoteEntity>,
        sermons: List<SermonEntity>,
        videos: List<YouTubeVideoEntity>,
    ): Map<LocalDate, DayMarks> {
        fun day(n: NoteEntity) = n.createdAt.atZone(zone).toLocalDate()
        val createdByDay = created.groupBy(::day)
        val commentsByDay = comments.groupBy { it.createdAt.atZone(zone).toLocalDate() }
        val notesById = commentedNotes.associateBy { it.id }
        val sermonsByKey = sermons.groupBy { it.preachedOn }
        val videosByDay = videos.groupBy { it.createdAt.atZone(zone).toLocalDate() }

        val marks = LinkedHashMap<LocalDate, DayMarks>()
        for (dayOfMonth in 1..month.lengthOfMonth()) {
            val date = month.atDay(dayOfMonth)
            val dayComments = commentsByDay[date].orEmpty()
            val daySermons = sermonsByKey[date.format(DayKey)].orEmpty()
            val dayVideos = videosByDay[date].orEmpty()
            val activity = TodayActivityBuilder.build(
                createdThatDay = createdByDay[date].orEmpty(),
                commentsThatDay = dayComments,
                commentedNotes = dayComments.mapNotNull { c -> c.noteId?.let(notesById::get) }.distinctBy { it.id },
                pinned = if (date == today) pinned else emptyList(),
                chaptersRead = emptyList(),
                sermons = daySermons,
                sermonNotes = emptyList(),
                videos = dayVideos,
                videoNotes = emptyList(),
                tags = emptyList(),
            )
            val mark = DayMarks(activity.tiles.map { it.kind }.toSet(), daySermons.isNotEmpty(), dayVideos.isNotEmpty())
            if (mark.tiles.isNotEmpty() || mark.hasSermon || mark.hasVideo) marks[date] = mark
        }
        return marks
    }
}

/** iOS `CustomCalendarView.daysInMonthGrid`: weeks of seven, blank (null) before the 1st and after the last day. */
object CalendarGrid {
    fun weeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells: MutableList<LocalDate?> = MutableList(leading) { null }
        for (d in 1..month.lengthOfMonth()) cells += month.atDay(d)
        while (cells.size % 7 != 0) cells += null
        return cells.chunked(7)
    }
}
