package com.stoneofhope.stoneofhope.data.notes

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `NotePinLogBuilder` (segments, `buildRows`) with pinned runs collapsed to one line. */
class NoteThreadTest {

    private val zone = ZoneId.of("America/Los_Angeles")
    private fun at(day: String, time: String = "10:00") = LocalDate.parse(day).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant()
    private fun comment(id: String, day: String, time: String = "10:00", marker: Boolean = false) =
        ThreadItem(id, if (marker) "x" else "text $id", marker, at(day, time), 0, emptyList(), emptyList())
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun consecutiveDaysFormRuns() {
        val runs = NoteThread.pinSegments(listOf("2026-10-26", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-24", "2026-10-25", "2026-10-21"))
        assertEquals(listOf(PinSegment(d("2026-10-20"), d("2026-10-22")), PinSegment(d("2026-10-24"), d("2026-10-26"))), runs)
    }

    @Test fun runsCrossMonthEndsAndSkipBadKeys() {
        val runs = NoteThread.pinSegments(listOf("2026-09-30", "2026-10-01", "garbage", "2026-10-03"))
        assertEquals(listOf(PinSegment(d("2026-09-30"), d("2026-10-01")), PinSegment(d("2026-10-03"), d("2026-10-03"))), runs)
    }

    @Test fun aRunIsOneLineOnItsLastDayAndPinnedOnlyDaysAppear() {
        val days = NoteThread.build(
            items = listOf(comment("a", "2026-10-21")),
            pinDayKeys = listOf("2026-10-20", "2026-10-21", "2026-10-22", "2026-10-25"),
            answeredAt = null,
            zone = zone,
        )
        assertEquals(listOf(d("2026-10-25"), d("2026-10-22"), d("2026-10-21")), days.map { it.day })
        assertEquals(PinSegment(d("2026-10-25"), d("2026-10-25")), days[0].pinned)
        assertEquals(PinSegment(d("2026-10-20"), d("2026-10-22")), days[1].pinned)
        assertTrue(days[1].items.isEmpty())
        // A commented day inside the run keeps its comments but no second pin line.
        assertNull(days[2].pinned)
        assertEquals(listOf("a"), days[2].items.map { it.id })
    }

    @Test fun answeredDayGetsALineUnlessAMarkerIsThere() {
        val viaMenu = NoteThread.build(listOf(comment("a", "2026-10-20")), emptyList(), at("2026-10-24", "08:00"), zone)
        assertEquals(listOf(d("2026-10-24"), d("2026-10-20")), viaMenu.map { it.day })
        assertTrue(viaMenu[0].answered)
        assertFalse(viaMenu[1].answered)

        val viaComposer = NoteThread.build(
            listOf(comment("c", "2026-10-24", "08:00"), comment("m", "2026-10-24", "08:00", marker = true)),
            emptyList(), at("2026-10-24", "08:00"), zone,
        )
        assertFalse(viaComposer.single().answered)
    }

    @Test fun daysNewestFirstAndCommentsNewestFirstInADay() {
        val days = NoteThread.build(
            listOf(comment("old", "2026-09-30", "09:00"), comment("early", "2026-10-26", "07:00"), comment("late", "2026-10-26", "21:00")),
            emptyList(), null, zone,
        )
        assertEquals(listOf(d("2026-10-26"), d("2026-09-30")), days.map { it.day })
        assertEquals(listOf("late", "early"), days[0].items.map { it.id })
    }

    /** Days are local days: 11 PM Pacific is the next UTC day but stays on its own day here. */
    @Test fun daysUseTheLocalZone() {
        val days = NoteThread.build(listOf(comment("n", "2026-10-25", "23:30")), emptyList(), null, zone)
        assertEquals(d("2026-10-25"), days.single().day)
        assertEquals(Instant.parse("2026-10-26T06:30:00Z"), days.single().items.single().createdAt)
    }
}
