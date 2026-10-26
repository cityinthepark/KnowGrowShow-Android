package com.stoneofhope.stoneofhope.data.reading

import com.stoneofhope.stoneofhope.data.local.entity.BookProgressEntity
import com.stoneofhope.stoneofhope.data.local.entity.ChapterLogEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure reading-progress rules ported from iOS `BibleReadingManager` / `BookCarouselView`.
 *
 * Dates: iOS stores `ChapterLog.readOn` and `BookProgress.createdOn` / `completedOn` as the start of
 * the local day. Writes here do the same; reads compare by local calendar day (a range), not by
 * exact equality with a start-of-day timestamp, so a timezone change doesn't hide today's reads.
 */
object ReadingModel {

    enum class ChipState { ReadToday, Read, Unread }

    data class Chip(val chapter: Int, val state: ChipState, val totalReads: Int)

    data class CarouselBook(
        val progress: BookProgressEntity,
        val chaptersRead: Int,
        val chapterCount: Int,
        val chips: List<Chip>,
    ) {
        val bookOrd: Int get() = progress.bookOrd
    }

    enum class Sort { LastRead, BookOrder }

    fun startOfDay(day: LocalDate, zone: ZoneId): Instant = day.atStartOfDay(zone).toInstant()

    fun localDay(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    /**
     * After a cross-device merge two devices may each have created a `BookProgress` for the same book.
     * One row wins for UI and writes: latest `lastUpdatedOn`, then latest `lastSyncedAt`, then id.
     */
    fun canonical(rows: List<BookProgressEntity>): BookProgressEntity? = rows
        .filter { it.deletedAt == null }
        .maxWithOrNull(
            compareBy<BookProgressEntity> { it.lastUpdatedOn }
                .thenBy { it.lastSyncedAt ?: Instant.MIN }
                .thenBy { it.id }
        )

    /** iOS `getBooksForCarousel()` + its two sort orders. */
    fun carousel(
        progressRows: List<BookProgressEntity>,
        logs: List<ChapterLogEntity>,
        today: LocalDate,
        zone: ZoneId,
        sort: Sort,
        windowStarts: MutableMap<Int, Int> = mutableMapOf(),
    ): List<CarouselBook> {
        val liveLogs = logs.filter { it.deletedAt == null }
        val logsByBook = liveLogs.groupBy { it.bookOrd }
        return progressRows.groupBy { it.bookOrd }
            .mapNotNull { (_, rows) -> canonical(rows) }
            .filter { p -> p.readChapters.count { it == 1 } > 0 }
            .map { p -> carouselBook(p, logsByBook[p.bookOrd].orEmpty(), today, zone, windowStarts) }
            .let { books ->
                when (sort) {
                    Sort.LastRead -> books.sortedWith(
                        compareByDescending<CarouselBook> { it.progress.lastUpdatedOn }.thenBy { it.bookOrd }
                    )
                    Sort.BookOrder -> books.sortedBy { it.bookOrd }
                }
            }
    }

    /**
     * [windowStarts] keeps each book's chip window where it first appeared (iOS
     * `BookCarouselShortcutCache`), so tapping a chip never slides the chips under the finger. The
     * caller owns the map and clears it when the day changes.
     */
    private fun carouselBook(
        progress: BookProgressEntity,
        bookLogs: List<ChapterLogEntity>,
        today: LocalDate,
        zone: ZoneId,
        windowStarts: MutableMap<Int, Int>,
    ): CarouselBook {
        val chapterCount = progress.readChapters.size
        val readsByChapter = bookLogs.groupingBy { it.chapter }.eachCount()
        val readToday = bookLogs.filter { localDay(it.readOn, zone) == today }.map { it.chapter }.toSet()
        val start = windowStarts.getOrPut(progress.bookOrd) {
            chipWindow(lastReadChapter(progress, bookLogs), chapterCount).firstOrNull() ?: 1
        }
        val chips = (start..minOf(chapterCount, start + 2)).map { ch ->
            Chip(
                chapter = ch,
                state = when {
                    ch in readToday -> ChipState.ReadToday
                    progress.readChapters.getOrNull(ch - 1) == 1 -> ChipState.Read
                    else -> ChipState.Unread
                },
                totalReads = readsByChapter[ch] ?: 0,
            )
        }
        return CarouselBook(progress, progress.readChapters.count { it == 1 }, chapterCount, chips)
    }

    /** iOS `getLastReadChapter`: latest log (then highest chapter), else highest chapter marked read. */
    fun lastReadChapter(progress: BookProgressEntity, bookLogs: List<ChapterLogEntity>): Int? =
        bookLogs.filter { it.deletedAt == null }
            .maxWithOrNull(compareBy<ChapterLogEntity> { it.readOn }.thenBy { it.chapter })
            ?.chapter
            ?: progress.readChapters.indexOfLast { it == 1 }.takeIf { it >= 0 }?.plus(1)

    /** Up to 3 consecutive chapters centred on [lastRead], clipped to the book; 1–3 when nothing is read. */
    fun chipWindow(lastRead: Int?, chapterCount: Int): List<Int> {
        if (chapterCount <= 0) return emptyList()
        val first = if (lastRead == null) 1 else (lastRead - 1).coerceIn(1, maxOf(1, chapterCount - 2))
        return (first..minOf(chapterCount, first + 2)).toList()
    }

    /** One book's state on the Reading Log screen (iOS `BibleBookRowView` / `ChapterGridView`). */
    data class BookLog(
        /** Current cycle (canonical row), or null if the book was never started / was cleared. */
        val progress: BookProgressEntity?,
        val readToday: Set<Int>,
        /** All-time non-deleted reads per chapter (the badge). */
        val readCounts: Map<Int, Int>,
    ) {
        fun state(chapter: Int): ChipState = when {
            chapter in readToday -> ChipState.ReadToday
            progress?.readChapters?.getOrNull(chapter - 1) == 1 -> ChipState.Read
            else -> ChipState.Unread
        }

        val isCompleted: Boolean
            get() = progress != null && progress.readChapters.isNotEmpty() && 0 !in progress.readChapters

        /** iOS hides "Restart" on the day the book was finished. */
        fun canRestart(today: LocalDate, zone: ZoneId): Boolean =
            isCompleted && progress?.completedOn?.let { localDay(it, zone) } != today

        companion object {
            val Empty = BookLog(null, emptySet(), emptyMap())
        }
    }

    fun bookLogs(
        progressRows: List<BookProgressEntity>,
        logs: List<ChapterLogEntity>,
        today: LocalDate,
        zone: ZoneId,
    ): Map<Int, BookLog> {
        val logsByBook = logs.filter { it.deletedAt == null }.groupBy { it.bookOrd }
        val progressByBook = progressRows.groupBy { it.bookOrd }.mapValues { (_, rows) -> canonical(rows) }
        return (logsByBook.keys + progressByBook.keys).associateWith { ord ->
            val bookLogs = logsByBook[ord].orEmpty()
            BookLog(
                progress = progressByBook[ord],
                readToday = bookLogs.filter { localDay(it.readOn, zone) == today }.map { it.chapter }.toSet(),
                readCounts = bookLogs.groupingBy { it.chapter }.eachCount(),
            )
        }
    }

    /** "99+" cap for the per-chapter read-count badge (shown only when > 1). */
    fun readCountBadge(totalReads: Int): String? = when {
        totalReads <= 1 -> null
        totalReads > 99 -> "99+"
        else -> totalReads.toString()
    }
}
