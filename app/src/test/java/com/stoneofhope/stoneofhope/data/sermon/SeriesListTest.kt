package com.stoneofhope.stoneofhope.data.sermon

import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import java.time.Instant
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** iOS `SeriesListLogic` for the Series tab, with IOS_ISSUES K17, K18 and the ungrouped rule. */
class SeriesListTest {

    private val defaultLocale = Locale.getDefault()

    @Before fun english() = Locale.setDefault(Locale.US)

    @After fun restore() = Locale.setDefault(defaultLocale)

    private fun series(id: String, bookOrd: Int? = null, startedOn: String = "", user: Boolean = true, title: String = id) =
        SermonSeriesEntity(id = id, title = title, bookOrd = bookOrd, startedOn = startedOn, isUserData = user)

    private fun sermon(id: String, seriesId: String? = null, preachedOn: String = "", bookOrd: Int? = null, title: String = id, chapterRange: String? = null) =
        SermonEntity(id = id, title = title, seriesId = seriesId, preachedOn = preachedOn, bookOrd = bookOrd, chapterRange = chapterRange, createdAt = Instant.EPOCH)

    @Test fun seriesWithABookAreOnlyUnderBooks() {
        // IOS_ISSUES K17: iOS lists the user's own "Romans" under both sections.
        val rows = SeriesList.withSermons(listOf(series("romans", bookOrd = 45), series("prayer"), series("church", bookOrd = 1, user = false)), emptyList())
        assertEquals(listOf("prayer"), SeriesList.topical(rows).map { it.series.id })
        assertEquals(listOf("romans", "church"), SeriesList.bookTracked(rows).map { it.series.id })
    }

    @Test fun bookGroupsAndLastPreached() {
        val rows = SeriesList.withSermons(
            listOf(series("rom1", 45, "20250101"), series("gen", 1, "20240101"), series("rom2", 45, "20260101"), series("rom3", 45, "")),
            listOf(sermon("s", seriesId = "rom3", preachedOn = "20260601")),
        )
        val groups = SeriesList.groupByBook(SeriesList.bookTracked(rows))
        assertEquals(listOf(1, 45), groups.map { it.bookOrd })
        // A series without a start date sorts by its latest sermon.
        assertEquals(listOf("rom3", "rom2", "rom1"), groups[1].series.map { it.series.id })
        assertEquals(listOf("rom3", "rom2", "rom1", "gen"), SeriesList.sortByStartedOnDescending(rows).map { it.series.id })
    }

    @Test fun sortingIsStable() {
        // IOS_ISSUES K18: iOS sorts by the "first" sermon of an unordered list.
        val rows = SeriesList.withSermons(
            listOf(series("b", title = "Beta"), series("a", title = "alpha"), series("c", title = "Gamma")),
            listOf(sermon("s1", "b", "20260301", bookOrd = 45), sermon("s2", "b", "20260101", bookOrd = 1), sermon("s3", "c", "20260101", bookOrd = 2)),
        )
        assertEquals(listOf("a", "b", "c"), SeriesList.sort(rows, SeriesSort.BookOrder).map { it.series.id })
        assertEquals(listOf("a", "b", "c"), SeriesList.sort(rows, SeriesSort.Name).map { it.series.id })
        assertEquals(listOf("a", "c", "b"), SeriesList.sort(rows, SeriesSort.BookName).map { it.series.id })
    }

    @Test fun searchMatchesTitlesBooksAndSermons() {
        val rows = SeriesList.withSermons(
            listOf(series("r", 45, title = "Letters"), series("p", title = "Prayer")),
            listOf(sermon("s", "p", title = "Grace abounds", chapterRange = "John 4")),
        )
        assertEquals(listOf("r"), SeriesList.filter(rows, "roman").map { it.series.id })
        assertEquals(listOf("p"), SeriesList.filter(rows, "grace").map { it.series.id })
        assertEquals(listOf("p"), SeriesList.filter(rows, "john").map { it.series.id })
        assertEquals(2, SeriesList.filter(rows, " ").size)
    }

    @Test fun ungroupedSermonsOldestFirstIncludingLostSeries() {
        val sermons = listOf(
            sermon("undated"), sermon("late", preachedOn = "20261001"), sermon("early", preachedOn = "20260101"),
            sermon("inSeries", seriesId = "live"), sermon("orphan", seriesId = "gone", preachedOn = "20260501"),
            sermon("deleted").copy(deletedAt = Instant.EPOCH),
        )
        assertEquals(listOf("early", "orphan", "late", "undated"), SeriesList.ungrouped(sermons, setOf("live"), "").map { it.id })
        assertEquals(listOf("late"), SeriesList.ungrouped(sermons, setOf("live"), "lat").map { it.id })
    }
}
