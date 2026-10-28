package com.stoneofhope.stoneofhope.data.chapter

import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** One year of the Chapter Sermons tab; [year] null holds sermons without a preached-on date. */
data class SermonYear(val year: Int?, val sermons: List<SermonEntity>)

/** iOS `ChapterSermonView` / `SermonRow` rules. */
object ChapterSermons {

    private val DayKey = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** iOS `fetchSermonsByBibleChapter`: same book (the query) and the chapter among `chapters`. */
    fun forChapter(sermonsInBook: List<SermonEntity>, chapterOrd: Int): List<SermonEntity> =
        sermonsInBook.filter { it.deletedAt == null && chapterOrd in it.chapters }

    /**
     * iOS `groupedSermons`: by the year of `preachedOn`, newest year first. iOS leaves each year in
     * Realm order and sorts "Unknown" first; here a year runs newest first and undated sermons come
     * last (IOS_ISSUES H6).
     */
    fun byYear(sermons: List<SermonEntity>): List<SermonYear> =
        sermons.groupBy { year(it) }
            .map { (year, list) -> SermonYear(year, list.sortedWith(compareByDescending<SermonEntity> { it.preachedOn }.thenByDescending { it.createdAt })) }
            .sortedWith(compareBy(nullsLast(reverseOrder())) { it.year })

    private fun year(sermon: SermonEntity): Int? =
        sermon.preachedOn.takeIf { it.length >= 4 }?.take(4)?.let(SwiftText::asciiInt)

    /** `preachedOn` (`yyyyMMdd`) as a date, for display. */
    fun preachedOn(sermon: SermonEntity): LocalDate? =
        try {
            sermon.preachedOn.takeIf { it.length == 8 }?.let { LocalDate.parse(it, DayKey) }
        } catch (_: DateTimeParseException) {
            null
        }

    /** iOS `Sermon.youtubeURL`: the watch page of `youtubeID`, else `videoUrl`. */
    fun watchUrl(sermon: SermonEntity): String? =
        sermon.youtubeID?.let(SwiftText::trim)?.takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/watch?v=$it" }
            ?: sermon.videoUrl?.let(SwiftText::trim)?.takeIf { it.isNotEmpty() }

    fun audioUrl(sermon: SermonEntity): String? = sermon.audioUrl?.let(SwiftText::trim)?.takeIf { it.isNotEmpty() }

    fun pdfUrl(sermon: SermonEntity): String? = sermon.pdfUrl?.let(SwiftText::trim)?.takeIf { it.isNotEmpty() }
}
