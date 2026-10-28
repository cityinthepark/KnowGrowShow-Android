package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Moments tab's date window (iOS `MomentsDateRangeSelection`). */
sealed interface MomentsRange {
    fun days(today: LocalDate): ClosedRange<LocalDate>

    /**
     * The last N days up to today: "Recent 30 Days" (the default) and the sheet's presets. iOS calls
     * the 7- and 365-day windows "This Week" and "This Year" (IOS_ISSUES I3); they are named for what
     * they cover here. Computed from today each time, so the window moves at midnight (I4).
     */
    enum class Last(val daysBack: Long) : MomentsRange {
        Days30(30), Days7(7), Months3(90), Months12(365);

        override fun days(today: LocalDate): ClosedRange<LocalDate> = today.minusDays(daysBack)..today
    }

    data class Custom(val start: LocalDate, val end: LocalDate) : MomentsRange {
        override fun days(today: LocalDate): ClosedRange<LocalDate> = minOf(start, end)..maxOf(start, end)
    }
}

/** One day of the Moments feed, newest first. */
data class MomentsSection(val day: LocalDate, val notes: List<NoteEntity>)

/** A line of a Moments row (iOS `MomentLine`). */
sealed interface MomentLine {
    data class Reference(val text: String, val sermonMark: Boolean) : MomentLine
    data class Body(val text: String) : MomentLine
}

/** iOS `MomentsTabView` + `NoteListViewModel(filter: .forMoments)`. */
object MomentsBuilder {

    private const val PREVIEW_LENGTH = 120
    private const val MAX_LINES = 4

    /**
     * Notes created in [days] or commented in them, minus reflections that sit under an anchor, by the
     * type / tag menus. A note brought in only by a comment is listed on the day of its latest comment in
     * the window; iOS lists it under its creation day, outside the window (IOS_ISSUES I6).
     */
    fun build(data: NoteListData, filter: NoteFilter, tagName: String?, days: ClosedRange<LocalDate>, zone: ZoneId): List<MomentsSection> {
        fun day(instant: Instant) = instant.atZone(zone).toLocalDate()
        val rules = NoteFilterRules(data)
        val latestComment: Map<String, Instant> = data.comments
            .filter { it.deletedAt == null && !it.isDeleted && day(it.createdAt) in days }
            .mapNotNull { c -> c.noteId?.let { it to c.createdAt } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> times.max() }

        return data.notes
            .filter { !NoteRelations.isHiddenFromTopLevel(it, data.notes, zone) }
            .mapNotNull { n ->
                val at = if (day(n.createdAt) in days) n.createdAt else latestComment[n.id] ?: return@mapNotNull null
                if (rules.typeMatches(n, filter) && rules.tagMatches(n, tagName)) n to at else null
            }
            .sortedByDescending { it.second }
            .groupBy({ day(it.second) }, { it.first })
            .map { (d, notes) -> MomentsSection(d, notes) }
    }

    /**
     * iOS `MomentsNoteRow.displayLines`: an anchor shows its reference, then its verse text; any other
     * note its non-blank lines. The text stops at 120 characters with "…", the row at 4 lines.
     */
    fun lines(note: NoteEntity, displayContent: String, sermonMark: Boolean): List<MomentLine> {
        val lines = mutableListOf<MomentLine>()
        val ref = note.bibleVerseRef?.trim().orEmpty()
        val isAnchor = note.noteType == NoteType.BibleVerse
        if (isAnchor && ref.isNotEmpty()) lines += MomentLine.Reference(ref, sermonMark)

        val body = if (isAnchor) {
            val text = displayContent.trim()
            if (text.isEmpty() || text == ref || text == NoteVerseAnchor.PLACEHOLDER) "" else truncated(text)
        } else {
            truncated(displayContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"))
        }
        lines += body.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.map(MomentLine::Body)
        if (lines.isEmpty()) lines += MomentLine.Body("")
        return lines.take(MAX_LINES)
    }

    private fun truncated(text: String): String {
        val trimmed = text.trim()
        if (trimmed.codePointCount(0, trimmed.length) <= PREVIEW_LENGTH) return trimmed
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…"
    }
}
