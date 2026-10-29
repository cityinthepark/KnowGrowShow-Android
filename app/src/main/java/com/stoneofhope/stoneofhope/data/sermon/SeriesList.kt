package com.stoneofhope.stoneofhope.data.sermon

import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import java.text.Collator

/** iOS `SeriesSortOption`, in menu order. */
enum class SeriesSort { BookOrder, BookName, StartedOn, Name }

/** A series with its active sermons (iOS `series.sermons.where { $0.deletedAt == nil }`). */
data class SeriesWithSermons(val series: SermonSeriesEntity, val sermons: List<SermonEntity>) {
    /**
     * The book the series is about: its own, else its earliest sermon's. iOS reads the "first" sermon of
     * an unordered Realm list, so the order changes between runs (IOS_ISSUES K18).
     */
    val bookOrd: Int? get() = series.bookOrd ?: sermons.sortedBy { it.preachedOn.ifEmpty { "99999999" } }.firstNotNullOfOrNull { it.bookOrd }
}

/** iOS `SeriesListLogic` (the parts the series picker uses; the Series tab adds its sections in S2). */
object SeriesList {

    fun withSermons(series: List<SermonSeriesEntity>, sermons: List<SermonEntity>): List<SeriesWithSermons> {
        val bySeries = sermons.filter { it.deletedAt == null && it.seriesId != null }.groupBy { it.seriesId }
        return series.filter { it.deletedAt == null }.map { SeriesWithSermons(it, bySeries[it.id].orEmpty()) }
    }

    /** iOS `isVisibleInTopicalSeriesSection`: the user's own series and church series without a book. */
    fun topical(rows: List<SeriesWithSermons>) = rows.filter { it.series.isUserData || it.series.bookOrd == null }

    /** iOS `isVisibleInBookSeriesSection`. */
    fun bookTracked(rows: List<SeriesWithSermons>) = rows.filter { it.series.bookOrd != null }

    private val collator: Collator get() = Collator.getInstance().apply { strength = Collator.SECONDARY }

    fun sort(rows: List<SeriesWithSermons>, by: SeriesSort): List<SeriesWithSermons> {
        val byTitle = compareBy(collator) { it: SeriesWithSermons -> it.series.title }
        return when (by) {
            SeriesSort.BookOrder -> rows.sortedWith(compareBy<SeriesWithSermons> { it.bookOrd ?: 0 }.then(byTitle))
            SeriesSort.BookName -> rows.sortedWith(compareBy(collator) { it: SeriesWithSermons -> it.bookOrd?.let(BibleBooks::name).orEmpty() }.then(byTitle))
            SeriesSort.StartedOn -> rows.sortedWith(compareByDescending<SeriesWithSermons> { it.series.startedOn }.then(byTitle))
            SeriesSort.Name -> rows.sortedWith(byTitle)
        }
    }

    /** iOS `sortByBookOrder`: by book, newest start first within a book. */
    fun sortByBookOrder(rows: List<SeriesWithSermons>): List<SeriesWithSermons> =
        rows.sortedWith(compareBy<SeriesWithSermons> { it.series.bookOrd ?: Int.MAX_VALUE }.thenByDescending { startedOnKey(it) }.thenBy(collator) { it.series.title })

    /** iOS `startedOnSortKey`: the start day, else the latest preached-on day. */
    fun startedOnKey(row: SeriesWithSermons): String =
        row.series.startedOn.ifEmpty { row.sermons.maxOfOrNull { it.preachedOn }.orEmpty() }

    /** iOS `SeriesListLogic.filter`: title, the book's name, or any sermon's title / book / reference. */
    fun filter(rows: List<SeriesWithSermons>, query: String): List<SeriesWithSermons> {
        val q = query.trim()
        if (q.isEmpty()) return rows
        return rows.filter { row ->
            row.series.title.contains(q, ignoreCase = true) ||
                row.series.bookOrd?.let { BibleBooks.name(it).contains(q, ignoreCase = true) } == true ||
                row.sermons.any { sermonMatches(it, q) }
        }
    }

    fun sermonMatches(sermon: SermonEntity, q: String): Boolean =
        sermon.title.contains(q, ignoreCase = true) ||
            sermon.book?.contains(q, ignoreCase = true) == true ||
            sermon.chapterRange?.contains(q, ignoreCase = true) == true

    /** iOS `SeriesViewModel.displaySeries`: the picker's flat list, topical first, without repeats. */
    fun pickerRows(rows: List<SeriesWithSermons>, sortBy: SeriesSort, query: String): List<SeriesWithSermons> {
        val topical = filter(sort(topical(rows), sortBy), query)
        val books = sortByBookOrder(filter(bookTracked(rows), query))
        return (topical + books).distinctBy { it.series.id }
    }
}
