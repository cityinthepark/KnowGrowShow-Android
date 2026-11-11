package com.stoneofhope.stoneofhope.data.notes

import kotlin.math.abs

/**
 * iOS `NoteSwipeSequence`: which note comes next when swiping in note details, and what counts as a swipe.
 *
 * The list a note was opened from hands its order over with [offer]; note details takes it once with
 * [take] (so a note opened from somewhere else, like a chapter page, has no sequence and no swiping).
 */
object NoteSwipeSequence {
    private var offered: List<String> = emptyList()

    /** Remembers the order of the notes the next opened note belongs to. */
    @Synchronized
    fun offer(ids: List<String>) { offered = ids }

    /** The offered order, once: it is cleared so the next screen that opens a note starts clean. */
    @Synchronized
    fun take(): List<String> = offered.also { offered = emptyList() }

    /** The note [step] places from [id] (+1 = next in the list, -1 = previous); null at either end. */
    fun neighbor(id: String, ids: List<String>, step: Int): String? {
        val index = ids.indexOf(id)
        if (index < 0) return null
        return ids.getOrNull(index + step)
    }

    /**
     * A clearly horizontal drag that doesn't start at a screen edge (those are the system "back" gesture):
     * left = next note (+1), right = previous (-1). All values in dp; [screenWidth] is the page width.
     * (iOS only reserves the left edge; Android's back gesture works from both.)
     */
    fun swipeStep(dx: Float, dy: Float, startX: Float, screenWidth: Float): Int? {
        if (startX <= EDGE || startX >= screenWidth - EDGE) return null
        if (abs(dx) < MIN_DISTANCE || abs(dx) < abs(dy) * 1.5f) return null
        return if (dx < 0) 1 else -1
    }

    private const val EDGE = 24f
    private const val MIN_DISTANCE = 70f
}
