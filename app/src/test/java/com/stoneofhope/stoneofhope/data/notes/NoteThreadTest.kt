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
    private fun comment(id: String, day: String, time: String = "10:00", marker: Boolean = false, pairedWith: String? = null) =
        ThreadItem(id, if (marker) "x" else "text $id", marker, at(day, time), 0, emptyList(), emptyList(), linkedAnsweredId = if (marker) pairedWith ?: id else null)
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

    // --- E3 (iOS 373d3a5): "Answered" folds into the comment's first line ---

    @Test fun aPairedMarkerFoldsIntoItsCommentOnTheSameDay() {
        val days = NoteThread.build(
            listOf(comment("c", "2026-10-24", "08:00"), comment("m", "2026-10-24", "08:00", marker = true, pairedWith = "c"), comment("other", "2026-10-24", "09:00")),
            emptyList(), null, zone,
        )
        val items = days.single().items
        assertEquals(listOf("other", "c"), items.map { it.id })
        assertTrue(items.single { it.id == "c" }.isAnswered)
        assertFalse(items.single { it.id == "other" }.isAnswered)
    }

    @Test fun aMarkerWithNothingToPairWithStaysOnItsOwn() {
        val alone = NoteThread.build(listOf(comment("m", "2026-10-24", marker = true)), emptyList(), null, zone).single().items
        assertEquals(listOf("m"), alone.map { it.id })
        // Paired with a comment on another day (or one since deleted): not folded either.
        val elsewhere = NoteThread.build(
            listOf(comment("c", "2026-10-23"), comment("m", "2026-10-24", marker = true, pairedWith = "c")), emptyList(), null, zone,
        )
        assertEquals(listOf(listOf("m"), listOf("c")), elsewhere.map { d -> d.items.map { it.id } })
        assertFalse(elsewhere[1].items.single().isAnswered)
    }

    @Test fun answeredDayLineLooksAtMarkersBeforeFolding() {
        val day = NoteThread.build(
            listOf(comment("c", "2026-10-24"), comment("m", "2026-10-24", marker = true, pairedWith = "c")), emptyList(), at("2026-10-24"), zone,
        ).single()
        assertFalse(day.answered)
        assertEquals(listOf("c"), day.items.map { it.id })
    }

    @Test fun countLeavesOutOnlyMarkersWhoseCommentExists() {
        val items = listOf(
            comment("c", "2026-10-23"),
            comment("paired", "2026-10-24", marker = true, pairedWith = "c"), // folded (or shown on c): not counted
            comment("alone", "2026-10-24", marker = true), // stands alone: counted, like iOS
            comment("gone", "2026-10-24", marker = true, pairedWith = "64a0000000000000000000ff"),
        )
        assertEquals(3, NoteThread.count(items))
        assertEquals(0, NoteThread.count(emptyList()))
    }

    // --- E2: the date column's connecting line ---

    @Test fun consecutiveDaysConnectAndGapsDoNot() {
        val days = NoteThread.build(
            listOf(comment("a", "2026-10-24"), comment("b", "2026-10-23"), comment("c", "2026-10-20")), emptyList(), null, zone,
        )
        assertEquals(listOf(true, false, false), days.map { it.connectsBelow })
    }

    @Test fun hiddenPinnedDaysKeepTheLineGoing() {
        // Run Oct 20–22 is one row on the 22nd; the 19th sits right below the run; the 25th is two days above it.
        val days = NoteThread.build(
            listOf(comment("x", "2026-10-25"), comment("a", "2026-10-21"), comment("b", "2026-10-19")),
            listOf("2026-10-20", "2026-10-21", "2026-10-22"), null, zone,
        )
        assertEquals(listOf(d("2026-10-25"), d("2026-10-22"), d("2026-10-21"), d("2026-10-19")), days.map { it.day })
        assertEquals(listOf(false, true, true, false), days.map { it.connectsBelow })
    }

    @Test fun aMonthLineBreaksTheConnection() {
        val days = NoteThread.build(listOf(comment("a", "2026-11-01"), comment("b", "2026-10-31")), emptyList(), null, zone)
        assertEquals(listOf(false, false), days.map { it.connectsBelow })
    }
}
