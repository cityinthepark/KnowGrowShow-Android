package com.stoneofhope.stoneofhope.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `NoteSwipeTests`: the next / previous note and what counts as a swipe in note details. */
class NoteSwipeSequenceTest {
    private val ids = listOf("a", "b", "c")

    @Test fun theNeighborIsTheNextOrPreviousNoteOfTheList() {
        assertEquals("c", NoteSwipeSequence.neighbor("b", ids, +1))
        assertEquals("a", NoteSwipeSequence.neighbor("b", ids, -1))
    }

    @Test fun thereIsNoNeighborPastEitherEndOrForAnUnknownNote() {
        assertNull(NoteSwipeSequence.neighbor("c", ids, +1))
        assertNull(NoteSwipeSequence.neighbor("a", ids, -1))
        assertNull(NoteSwipeSequence.neighbor("zzz", ids, +1))
        assertNull(NoteSwipeSequence.neighbor("a", emptyList(), +1))
    }

    @Test fun aClearHorizontalDragIsASwipe_leftNextRightPrevious() {
        assertEquals(1, NoteSwipeSequence.swipeStep(dx = -120f, dy = 10f, startX = 200f, screenWidth = 400f))
        assertEquals(-1, NoteSwipeSequence.swipeStep(dx = 120f, dy = -10f, startX = 200f, screenWidth = 400f))
    }

    @Test fun shortOrMostlyVerticalDragsAreNotSwipes() {
        assertNull("too short", NoteSwipeSequence.swipeStep(dx = -60f, dy = 0f, startX = 200f, screenWidth = 400f))
        assertNull("scrolling", NoteSwipeSequence.swipeStep(dx = -100f, dy = 100f, startX = 200f, screenWidth = 400f))
        assertEquals("diagonal enough", 1, NoteSwipeSequence.swipeStep(dx = -150f, dy = 90f, startX = 200f, screenWidth = 400f))
    }

    @Test fun aDragFromAScreenEdgeIsTheBackGestureNotASwipe() {
        assertNull(NoteSwipeSequence.swipeStep(dx = 150f, dy = 0f, startX = 10f, screenWidth = 400f))
        assertNull(NoteSwipeSequence.swipeStep(dx = -150f, dy = 0f, startX = 392f, screenWidth = 400f))
        assertEquals(-1, NoteSwipeSequence.swipeStep(dx = 150f, dy = 0f, startX = 30f, screenWidth = 400f))
    }

    @Test fun theOfferedOrderIsTakenOnce() {
        NoteSwipeSequence.offer(ids)
        assertEquals(ids, NoteSwipeSequence.take())
        assertEquals("a note opened from somewhere else starts clean", emptyList<String>(), NoteSwipeSequence.take())
    }
}
