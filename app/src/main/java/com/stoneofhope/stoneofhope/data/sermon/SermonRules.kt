package com.stoneofhope.stoneofhope.data.sermon

import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** The pure rules behind sermon writes and lists, kept apart from Room so they can be unit tested. */
object SermonRules {

    /**
     * iOS `CalendarDayKey.from`: the local day as `yyyyMMdd`. iOS formats with the device's calendar
     * and digits, so a Buddhist or Japanese calendar writes another year (IOS_ISSUES K2); this is
     * always the Gregorian date in ASCII digits.
     */
    fun dayKey(day: LocalDate): String = day.format(DateTimeFormatter.BASIC_ISO_DATE)

    fun day(key: String): LocalDate? = try {
        key.takeIf { it.length == 8 && it.all { c -> c in '0'..'9' } }?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * iOS `uniqueSermonTitle(for:)`: [base] itself, else "base - 2", "base - 3", … one past the highest
     * suffix already used by an active sermon.
     */
    fun uniqueTitle(base: String, existingTitles: List<String>): String {
        val trimmedBase = SwiftText.trim(base)
        if (trimmedBase.isEmpty()) return base
        val prefix = "$trimmedBase - "
        var maxSuffix = 0
        for (title in existingTitles.map(SwiftText::trim)) {
            if (title == trimmedBase) {
                maxSuffix = maxOf(maxSuffix, 1)
                continue
            }
            if (!title.startsWith(prefix)) continue
            val suffix = SwiftText.asciiInt(title.removePrefix(prefix)) ?: continue
            if (suffix >= 2) maxSuffix = maxOf(maxSuffix, suffix)
        }
        return when (maxSuffix) {
            0 -> trimmedBase
            1 -> "$trimmedBase - 2"
            else -> "$trimmedBase - ${maxSuffix + 1}"
        }
    }

    /**
     * The series a new sermon joins when the user picked none (§4 question 3, IOS_ISSUES K3): the newest
     * series of the user's own that covers the same book, started within six months and isn't finished.
     * iOS falls back to the newest topical series whatever the book, and also picks finished and church
     * series; Android leaves the sermon without a series instead.
     */
    fun detectSeries(sermonBookOrd: Int?, series: List<SermonSeriesEntity>, today: LocalDate): SermonSeriesEntity? {
        val bookOrd = sermonBookOrd ?: return null
        val cutoff = dayKey(today.minusMonths(6))
        return series
            .filter { it.deletedAt == null && it.isUserData && !it.isFinished && it.startedOn.isNotEmpty() }
            .filter { it.bookOrd == bookOrd && it.startedOn >= cutoff }
            .maxWithOrNull(compareBy<SermonSeriesEntity> { it.startedOn }.thenBy { it.id })
    }

    /** iOS `Sermon.resolvedYouTubeVideoID`: `youtubeID`, else the id in `videoUrl`. */
    fun youTubeID(sermon: SermonEntity): String? =
        sermon.youtubeID?.let(SwiftText::trim)?.ifEmpty { null } ?: sermon.videoUrl?.let(YouTubeLinkParser::videoID)

    /** iOS `NotesManager.summaryDisplayTitle(for:)`: "Romans 8 - Title", or the title alone. */
    fun displayTitle(sermon: SermonEntity): String {
        val ref = sermon.chapterRange?.let(SwiftText::trim).orEmpty()
        return if (ref.isEmpty()) sermon.title else "$ref - ${sermon.title}"
    }

    /**
     * What a merge target keeps from the sermon merged into it (IOS_ISSUES K9). iOS moves only the
     * notes, so a target without a video or audio loses the merged sermon's. Fields the target already
     * has are never overwritten; tags are combined.
     */
    fun filledFrom(target: SermonEntity, source: SermonEntity): SermonEntity {
        fun String?.blank() = this.isNullOrBlank()
        var t = target
        if (t.preacher.blank() && !source.preacher.blank()) t = t.copy(preacher = source.preacher)
        if (t.youtubeID.blank() && t.videoUrl.blank() && !(source.youtubeID.blank() && source.videoUrl.blank())) {
            t = t.copy(youtubeID = source.youtubeID, videoUrl = source.videoUrl)
        }
        if (t.audioUrl.blank() && !source.audioUrl.blank()) t = t.copy(audioUrl = source.audioUrl)
        if (t.pdfUrl.blank() && !source.pdfUrl.blank()) t = t.copy(pdfUrl = source.pdfUrl)
        if (t.intro.blank() && !source.intro.blank()) t = t.copy(intro = source.intro)
        val targetHasRef = t.bookOrd != null || t.chapters.isNotEmpty() || !t.chapterRange.blank()
        val sourceHasRef = source.bookOrd != null || source.chapters.isNotEmpty() || !source.chapterRange.blank()
        if (!targetHasRef && sourceHasRef) {
            t = t.copy(book = source.book, bookOrd = source.bookOrd, chapterRange = source.chapterRange, chapters = source.chapters)
        }
        if (t.seriesId == null && source.seriesId != null) t = t.copy(seriesId = source.seriesId)
        t = t.copy(tagIds = (t.tagIds + source.tagIds).distinct())
        return t
    }

    /** A row of the merge dialog (iOS `SermonMergeTargetRow`): only sermons without notes can receive. */
    data class MergeTarget(val sermon: SermonEntity, val title: String, val noteCount: Int) {
        val canMerge: Boolean get() = noteCount == 0
    }

    /** iOS `sermonListItems` "Merge": the day's other sermons, mergeable ones first, then by title. */
    fun mergeTargets(source: SermonEntity, sameDay: List<SermonEntity>, noteCounts: Map<String, Int>): List<MergeTarget> =
        sameDay.filter { it.id != source.id && it.deletedAt == null }
            .map { MergeTarget(it, displayTitle(it), noteCounts[it.id] ?: 0) }
            .sortedWith(compareBy<MergeTarget> { !it.canMerge }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    /** iOS: "Merge" shows on a row with notes when the day has two sermons or more. */
    fun canOfferMerge(noteCount: Int, sermonsThatDay: Int): Boolean = noteCount > 0 && sermonsThatDay >= 2
}
