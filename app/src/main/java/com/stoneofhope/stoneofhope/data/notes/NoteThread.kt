package com.stoneofhope.stoneofhope.data.notes

import java.time.Instant
import java.time.LocalDate
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
        val segmentsByEnd = pinSegments(pinDayKeys).associateBy { it.end }
        val answeredDay = answeredAt?.atZone(zone)?.toLocalDate()
        val days = (byDay.keys + segmentsByEnd.keys + listOfNotNull(answeredDay)).toSortedSet(compareByDescending { it })
        return days.map { day ->
            val dayItems = byDay[day].orEmpty().sortedByDescending { it.createdAt }
            ThreadDay(
                day = day,
                items = dayItems,
                pinned = segmentsByEnd[day],
                answered = day == answeredDay && dayItems.none { it.isAnsweredMarker },
            )
        }
    }
}
