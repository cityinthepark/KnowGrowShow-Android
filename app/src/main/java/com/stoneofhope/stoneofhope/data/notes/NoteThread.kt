package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** One comment — or, under a verse anchor, one reflection note — in the details thread. */
data class ThreadItem(
    val id: String,
    val text: String,
    /** The "Answered" timeline marker (its body is an id; the UI shows a label). */
    val isAnsweredMarker: Boolean,
    val createdAt: Instant,
    val starLevel: Int,
    val tagIds: List<String>,
    val tagNames: List<String>,
    /** A reflection is a note of its own; its actions are note actions. */
    val isReflection: Boolean = false,
    /** A marker's paired comment id (its own id when it stands alone): iOS `linkedAnsweredCommentId`. */
    val linkedAnsweredId: String? = null,
    /** A comment an "Answered" marker folded into: 🙌 on its first line (iOS `TimelineCommentEntry.isAnswered`). */
    val isAnswered: Boolean = false,
)

/** A comment as a thread item; an "Answered" marker keeps the id of the comment it is paired with. */
fun NoteCommentEntity.threadItem(tagNames: Map<String, String>) = ThreadItem(
    id, content, isAnsweredMarker, createdAt, starLevel, tagIds, tagIds.mapNotNull(tagNames::get),
    linkedAnsweredId = linkedAnsweredCommentId,
)

/** A run of consecutive pinned days (iOS `PinSegment`). */
data class PinSegment(val start: LocalDate, val end: LocalDate)

/**
 * One day of the thread. [pinned] is set on the last day of a pinned run ("📌 Pinned · Oct 20 – 26");
 * [answered] is the day-level "🙌 Answered" line for a prayer answered without a marker comment.
 */
data class ThreadDay(
    val day: LocalDate,
    val items: List<ThreadItem>,
    val pinned: PinSegment? = null,
    val answered: Boolean = false,
    /** The date column's line runs down to the next (older) day: no unpinned day lies between them. */
    val connectsBelow: Boolean = false,
)

/**
 * iOS `NotePinLogBuilder.buildRows`, laid out for the redesigned thread: the days are comment days,
 * pinned days and the answered day, newest first. iOS gives every pinned day its own row; here a run
 * of consecutive pinned days is one line on its last day (the user's choice), so a day inside a run
 * appears only if it has comments.
 */
object NoteThread {

    /** `PinLog.pinnedDays` keys (`yyyy-MM-dd`) as consecutive runs, oldest first. Bad keys are skipped. */
    fun pinSegments(dayKeys: List<String>): List<PinSegment> {
        val days = dayKeys.mapNotNull { key ->
            try { LocalDate.parse(key) } catch (_: DateTimeParseException) { null }
        }.distinct().sorted()
        if (days.isEmpty()) return emptyList()
        val result = mutableListOf<PinSegment>()
        var start = days.first()
        var previous = start
        for (day in days.drop(1)) {
            if (day != previous.plusDays(1)) {
                result += PinSegment(start, previous)
                start = day
            }
            previous = day
        }
        result += PinSegment(start, previous)
        return result
    }

    /**
     * [answeredAt]: iOS uses `answeredAt ?? updatedAt` while the note is answered, null otherwise. The
     * day-level line is skipped when that day already shows an "Answered" marker comment.
     */
    fun build(items: List<ThreadItem>, pinDayKeys: List<String>, answeredAt: Instant?, zone: ZoneId): List<ThreadDay> {
        val byDay = items.groupBy { it.createdAt.atZone(zone).toLocalDate() }
        val segments = pinSegments(pinDayKeys)
        val segmentsByEnd = segments.associateBy { it.end }
        val answeredDay = answeredAt?.atZone(zone)?.toLocalDate()
        val days = (byDay.keys + segmentsByEnd.keys + listOfNotNull(answeredDay)).toSortedSet(compareByDescending { it }).toList()
        return days.mapIndexed { i, day ->
            val dayItems = byDay[day].orEmpty().sortedByDescending { it.createdAt }
            ThreadDay(
                day = day,
                items = foldAnswered(dayItems),
                pinned = segmentsByEnd[day],
                // Before folding, like iOS: any marker that day already says "Answered".
                answered = day == answeredDay && dayItems.none { it.isAnsweredMarker },
                connectsBelow = days.getOrNull(i + 1)?.let { older -> connects(day, older, segments) } ?: false,
            )
        }
    }

    /**
     * iOS `commentEntries(for:)`: a marker paired with a content comment of the same day folds into that
     * comment's first line; a marker with nothing left to pair with stays ("🙌 Answered Today").
     */
    fun foldAnswered(dayItems: List<ThreadItem>): List<ThreadItem> {
        val contentIds = dayItems.filter { !it.isAnsweredMarker }.map { it.id }.toSet()
        val answeredIds = dayItems.mapNotNull { it.linkedAnsweredId }.toSet()
        return dayItems.mapNotNull { item ->
            when {
                item.isAnsweredMarker && item.linkedAnsweredId in contentIds -> null
                !item.isAnsweredMarker && item.id in answeredIds -> item.copy(isAnswered = true)
                else -> item
            }
        }
    }

    /**
     * iOS `threadItemCount`: every comment and reflection, except a marker whose paired comment exists
     * (that marker is shown on the comment). A marker standing alone counts.
     */
    fun count(items: List<ThreadItem>): Int {
        val contentIds = items.filter { !it.isAnsweredMarker }.map { it.id }.toSet()
        return items.count { !(it.isAnsweredMarker && it.linkedAnsweredId in contentIds) }
    }

    /**
     * iOS links a day to the next only when they are consecutive. Here a pinned run is one row on its
     * last day, so the days in between may be hidden pinned days: link when every day strictly between
     * is pinned. (iOS also breaks the line between consecutive days outside one run when the note has
     * any run — not followed: NOTE_DETAILS_E2E4_PLAN.)
     */
    private fun connects(newer: LocalDate, older: LocalDate, segments: List<PinSegment>): Boolean {
        // A month line sits between them (iOS draws a connector only to a day row right below).
        if (YearMonth.from(newer) != YearMonth.from(older)) return false
        var day = older.plusDays(1)
        while (day < newer) {
            if (segments.none { day >= it.start && day <= it.end }) return false
            day = day.plusDays(1)
        }
        return true
    }
}
