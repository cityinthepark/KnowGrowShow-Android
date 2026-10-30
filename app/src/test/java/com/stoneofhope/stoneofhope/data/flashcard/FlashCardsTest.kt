package com.stoneofhope.stoneofhope.data.flashcard

import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashCardsTest {
    private fun card(id: String, book: Int?, chapter: Int?, verse: Int?, version: String?, updated: Long) = FlashCardEntity(
        id = id, bookOrd = book, chapterOrd = chapter, verseStart = verse, versionAbbr = version,
        refKey = if (book != null && chapter != null && verse != null) "VS:$book:$chapter:$verse-$verse" else null,
        createdAt = Instant.ofEpochSecond(0), updatedAt = Instant.ofEpochSecond(updated),
    )

    private val a = card("a", 43, 3, 16, "KJV", 10)
    private val b = card("b", 1, 1, 1, "KJV", 5)
    private val c = card("c", 43, 3, 16, "CUV", 20)
    private val d = card("d", null, null, null, null, 30)
    private val e = card("e", 43, 3, 2, "KJV", 1)

    @Test fun byDateIsNewestUpdatedFirst() =
        assertEquals(listOf("d", "c", "a", "b", "e"), FlashCards.sorted(listOf(a, b, c, d, e), FlashCardSort.ByDate).map { it.id })

    @Test fun byBookOrdersBookChapterVerseVersionThenNewest() =
        // no book last; same verse: version "CUV" < "KJV"
        assertEquals(listOf("b", "e", "c", "a", "d"), FlashCards.sorted(listOf(a, b, c, d, e), FlashCardSort.ByBook).map { it.id })

    @Test fun byBookBreaksAFullTieWithTheNewestFirst() {
        val older = card("old", 43, 3, 16, "KJV", 5)
        val newer = card("new", 43, 3, 16, "KJV", 50)
        assertEquals(listOf("new", "old"), FlashCards.sorted(listOf(older, newer), FlashCardSort.ByBook).map { it.id })
    }

    @Test fun displayRefReadsRefKeyAndVersion() {
        assertEquals("John 3:16 (KJV)", FlashCards.displayRef(a))
        assertNull(FlashCards.displayRef(d))
    }

    @Test fun anchorNeedsBookChapterVerseAndVersion() {
        assertEquals(43, FlashCards.anchor(a)?.bookOrd)
        assertNull(FlashCards.anchor(d))
        assertNull(FlashCards.anchor(a.copy(versionAbbr = " ")))
    }

    @Test fun storageVersionIsUpperCased() = assertEquals("CUV", FlashCards.storageVersion("cuv"))

    // MARK: launch policy (IOS_ISSUES L4)

    private val min = 60_000L

    @Test fun showsOnTheFirstForegroundOnly() {
        val p = LaunchDeckPolicy()
        assertTrue(p.shouldShow(0, enabled = true, hasActiveCards = true))
        p.onStop(1 * min)
        assertFalse(p.shouldShow(2 * min, enabled = true, hasActiveCards = true))
    }

    @Test fun showsAgainAfterThirtyMinutesAway() {
        val p = LaunchDeckPolicy()
        p.shouldShow(0, true, true)
        p.onStop(10 * min)
        assertFalse(p.shouldShow(10 * min + LaunchDeckPolicy.AWAY_MILLIS - 1, true, true))
        p.onStop(100 * min)
        assertTrue(p.shouldShow(100 * min + LaunchDeckPolicy.AWAY_MILLIS, true, true))
    }

    @Test fun neverShowsWhenOffOrWithoutActiveCards() {
        val off = LaunchDeckPolicy()
        assertFalse(off.shouldShow(0, enabled = false, hasActiveCards = true))
        val none = LaunchDeckPolicy()
        assertFalse(none.shouldShow(0, enabled = true, hasActiveCards = false))
    }
}
