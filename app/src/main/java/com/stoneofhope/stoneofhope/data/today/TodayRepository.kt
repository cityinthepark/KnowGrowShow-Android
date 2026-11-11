package com.stoneofhope.stoneofhope.data.today

import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.reading.ReadingModel
import com.stoneofhope.stoneofhope.data.reading.ReadingRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Live data for the Today tab's activity section; re-emits whenever any underlying table changes. */
@Singleton
class TodayRepository @Inject constructor(
    private val db: AppDatabase,
    private val reading: ReadingRepository,
) {
    private class NoteInputs(
        val created: List<NoteEntity>,
        val comments: List<NoteCommentEntity>,
        val commented: List<NoteEntity>,
        val pinned: List<NoteEntity>,
    )

    private class MediaInputs(
        val sermons: List<SermonEntity>,
        val sermonNotes: List<NoteEntity>,
        val videos: List<YouTubeVideoEntity>,
        val videoNotes: List<NoteEntity>,
        val tags: List<TagEntity>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActivity(day: LocalDate, zone: ZoneId, isToday: Boolean): Flow<TodayActivity> {
        val start = ReadingModel.startOfDay(day, zone)
        val end = ReadingModel.startOfDay(day.plusDays(1), zone)
        val noteDao = db.noteDao()

        val comments = db.noteCommentDao().observeCreatedBetween(start, end)
        val commented = comments.flatMapLatest { list ->
            val ids = list.mapNotNull { it.noteId }.distinct()
            if (ids.isEmpty()) flowOf(emptyList()) else noteDao.observeByIds(ids)
        }
        val notes = combine(
            noteDao.observeCreatedBetween(start, end),
            comments,
            commented,
            if (isToday) noteDao.observePinned() else flowOf(emptyList()),
        ) { created, c, commentedNotes, pinned -> NoteInputs(created, c, commentedNotes, pinned) }

        // iOS `getSermons(on:)` / `getYouTubeVideos(on:)`: the day's own, plus older ones the user wrote a
        // note or comment on that day (otherwise today's writing on an earlier sermon can't be found from today).
        val writtenOn = notes.map { n -> (n.created + n.commented).distinctBy { it.id } }
        // iOS `CalendarDayKey`: the local day as yyyyMMdd.
        val sermons = combine(
            db.sermonDao().observePreachedOn(day.format(DateTimeFormatter.BASIC_ISO_DATE)),
            writtenOn.map { list -> list.mapNotNull { it.sermonId }.distinct() }.distinctUntilChanged().flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList()) else db.sermonDao().observeByIds(ids)
            },
        ) { own, extra -> (own + extra).distinctBy { it.id } }
        val sermonNotes = sermons.flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList()) else noteDao.observeForSermons(list.map { it.id })
        }
        val videos = combine(
            db.youTubeVideoDao().observeCreatedBetween(start, end),
            writtenOn.map { list -> list.mapNotNull { it.youtubeID }.distinct() }.distinctUntilChanged().flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList()) else db.youTubeVideoDao().observeByIds(ids)
            },
        ) { own, extra -> (own + extra).distinctBy { it.youtubeID } }
        val videoNotes = videos.flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList()) else noteDao.observeForVideos(list.map { it.youtubeID })
        }

        val media = combine(sermons, sermonNotes, videos, videoNotes, db.tagDao().observeActive(), ::MediaInputs)

        return combine(notes, reading.observeLogsOn(day, zone), media) { n, logs, m ->
            TodayActivityBuilder.build(
                createdThatDay = n.created,
                commentsThatDay = n.comments,
                commentedNotes = n.commented,
                pinned = n.pinned,
                chaptersRead = logs,
                sermons = m.sermons,
                sermonNotes = m.sermonNotes,
                videos = m.videos,
                videoNotes = m.videoNotes,
                tags = m.tags,
            )
        }
    }

    /** The Calendar tab's marks for [month] (iOS `CalendarSummaryView.loadData`), live. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMonth(month: YearMonth, today: LocalDate, zone: ZoneId): Flow<Map<LocalDate, DayMarks>> {
        val start = ReadingModel.startOfDay(month.atDay(1), zone)
        val end = ReadingModel.startOfDay(month.plusMonths(1).atDay(1), zone)
        val noteDao = db.noteDao()
        val comments = db.noteCommentDao().observeCreatedBetween(start, end)
        val commented = comments.flatMapLatest { list ->
            val ids = list.mapNotNull { it.noteId }.distinct()
            if (ids.isEmpty()) flowOf(emptyList()) else noteDao.observeByIds(ids)
        }
        val pinned = if (YearMonth.from(today) == month) noteDao.observePinned() else flowOf(emptyList())
        val notes = combine(noteDao.observeCreatedBetween(start, end), comments, commented, pinned) { n, c, cn, p ->
            NoteInputs(n, c, cn, p)
        }
        val sermons = db.sermonDao().observePreachedBetween(
            month.atDay(1).format(DateTimeFormatter.BASIC_ISO_DATE),
            month.plusMonths(1).atDay(1).format(DateTimeFormatter.BASIC_ISO_DATE),
        )
        val videos = db.youTubeVideoDao().observeCreatedBetween(start, end)
        return combine(notes, sermons, videos) { n, s, v ->
            CalendarMonthBuilder.build(month, today, zone, n.created, n.comments, n.commented, n.pinned, s, v)
        }
    }
}
