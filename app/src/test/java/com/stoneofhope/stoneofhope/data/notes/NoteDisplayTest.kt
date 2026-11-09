package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.bible.BibleAnchor
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.model.MarkStyle
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `NoteHeaderBadgeView` / `headerBadge*` / `displayContent(for:)` rules. */
class NoteDisplayTest {

    private val t = Instant.parse("2026-10-26T02:00:00Z")
    private fun note(type: NoteType, block: NoteEntity.() -> NoteEntity = { this }) =
        NoteEntity(id = "64a000000000000000000001", noteType = type, content = "text", createdAt = t, updatedAt = t).block()

    private val anchor = note(NoteType.BibleVerse) {
        copy(content = NoteVerseAnchor.PLACEHOLDER, bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 17, versionAbbr = "KJV", refKey = "VS:43:3:16-17")
    }

    @Test fun anchorBadgeShowsTheReference() {
        assertEquals(NoteBadge("✝️", NoteBadgeTitle.Reference("John 3:16-17"), NoteTint.BibleVerse), NoteBadges.badge(anchor, hasLinkedSermon = false))
        assertTrue(NoteBadges.badge(anchor.copy(sermonId = "s"), hasLinkedSermon = true).sermonMark)
    }

    @Test fun cuvAnchorUsesTraditionalNamesLikeIos() {
        assertEquals("約翰福音 3:16-17", anchor.copy(versionAbbr = "CUV").bibleVerseRef)
    }

    /** Android fix: iOS shows an empty title for an anchor without refKey (Web writes ords only). */
    @Test fun anchorWithoutRefKeyFallsBackToOrds() {
        assertEquals("John 3:16-17", anchor.copy(refKey = null).bibleVerseRef)
    }

    @Test fun chapterNoteBadgeReadsChapterDotReference() {
        val chapterNote = note(NoteType.Note) { copy(bookOrd = 1, chapterOrd = 1, chapterKey = "CH:1:1", versionAbbr = "KJV") }
        assertEquals(NoteBadge("✝️", NoteBadgeTitle.Chapter("Genesis 1"), NoteTint.BibleVerse), NoteBadges.badge(chapterNote, false))
    }

    @Test fun looseVerseNoteShowsItsVerse() {
        val loose = note(NoteType.Note) { copy(bookOrd = 1, chapterOrd = 1, verseStart = 3, versionAbbr = "KJV") }
        assertEquals(NoteBadgeTitle.Reference("Genesis 1:3"), NoteBadges.badge(loose, false).title)
    }

    @Test fun otherBadges() {
        val prayer = note(NoteType.Prayer) { copy(bookOrd = 19, chapterOrd = 23, verseStart = 1, verseEnd = 1, refKey = "VS:19:23:1-1", versionAbbr = "KJV") }
        assertEquals(NoteBadge("🌿", NoteBadgeTitle.Label(NoteLabel.Prayers), NoteTint.Prayer), NoteBadges.badge(prayer, false))
        assertEquals(NoteBadge("📝", NoteBadgeTitle.Label(NoteLabel.QuickNote), NoteTint.Notes), NoteBadges.badge(note(NoteType.Note), false))
        assertEquals(NoteBadge("📖", NoteBadgeTitle.Label(NoteLabel.SermonNote), NoteTint.Sermon), NoteBadges.badge(note(NoteType.Note) { copy(sermonId = "s") }, true))
        assertEquals(NoteBadge("▶️", NoteBadgeTitle.Label(NoteLabel.YouTubeVideo), NoteTint.Video), NoteBadges.badge(note(NoteType.Note) { copy(youtubeID = "v") }, false))
        assertEquals(NoteBadgeTitle.Label(NoteLabel.StudyNote), NoteBadges.badge(note(NoteType.StudyNote), false).title)
    }

    @Test fun anchorBodyComesFromUserVersesInItsOwnVersion() {
        val verses = listOf(
            UserVerseEntity("KJV_43_3_16", "KJV", 43, 3, 16, "For God so loved", createdAt = t, updatedAt = t),
            UserVerseEntity("KJV_43_3_17", "KJV", 43, 3, 17, "For God sent not", createdAt = t, updatedAt = t),
            UserVerseEntity("CUV_43_3_16", "CUV", 43, 3, 16, "神愛世人", createdAt = t, updatedAt = t),
        ).associateBy { it.id }
        assertEquals("For God so loved For God sent not", VerseDisplay.displayContent(anchor, listOf("KJV"), verses))
        assertEquals("John 3:16-17\nFor God so loved For God sent not", VerseDisplay.copyText(anchor, VerseDisplay.displayContent(anchor, listOf("KJV"), verses)))
        // No pool rows yet: the placeholder, which the list hides.
        assertEquals(NoteVerseAnchor.PLACEHOLDER, VerseDisplay.displayContent(anchor, listOf("KJV"), emptyMap()))
        assertFalse(VerseDisplay.showsBody(anchor, NoteVerseAnchor.PLACEHOLDER))
        // A prayer's own text is its body; its verse is resolved separately.
        val prayer = note(NoteType.Prayer) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16, versionAbbr = "CUV") }
        assertEquals("text", VerseDisplay.displayContent(prayer, listOf("KJV", "CUV"), verses))
        assertEquals(ResolvedVerse("約翰福音 3:16", "神愛世人", "CUV", listOf(16 to "神愛世人")), VerseDisplay.resolve(prayer, verses))
        assertNull(VerseDisplay.resolve(note(NoteType.Note), verses))
    }

    // iOS `456b7e6`: a `.bibleVerse` row shows its text in every selected version.

    private val john316 = anchor.copy(verseEnd = 16, refKey = "VS:43:3:16-16")
    private fun verse(version: String, text: String) = UserVerseEntity("${version}_43_3_16", version, 43, 3, 16, text, createdAt = t, updatedAt = t)
    private val kjv = verse("KJV", "For God so loved")
    private val cuv = verse("CUV", "神愛世人")
    private val nkjv = verse("NKJV", "For God so loved the world")
    private fun pool(vararg v: UserVerseEntity) = v.associateBy { it.id }

    @Test fun oneVersionShowsItsTextAlone() {
        assertEquals("For God so loved", VerseDisplay.displayContent(john316, listOf("KJV"), pool(kjv, cuv)))
        assertFalse(VerseDisplay.isMissingVerseText(john316, listOf("KJV"), pool(kjv)))
    }

    @Test fun twoVersionsAreLabelledABlankLineApart() {
        assertEquals("For God so loved [KJV]\n\n神愛世人 [和合本]", VerseDisplay.displayContent(john316, listOf("KJV", "CUV"), pool(kjv, cuv)))
        // The selected order wins, even when the anchor's own version is second.
        assertEquals("神愛世人 [和合本]\n\nFor God so loved [KJV]", VerseDisplay.displayContent(john316, listOf("CUV", "KJV"), pool(kjv, cuv)))
        assertEquals(listOf("KJV_43_3_16", "CUV_43_3_16"), VerseDisplay.userVerseIds(john316, listOf("KJV", "CUV")))
    }

    @Test fun aMissingVersionIsLeftOutAndAsksForBackfill() {
        assertEquals("For God so loved", VerseDisplay.displayContent(john316, listOf("KJV", "CUV"), pool(kjv)))
        assertTrue(VerseDisplay.isMissingVerseText(john316, listOf("KJV", "CUV"), pool(kjv)))
        // No version has text yet: the stored body, and still missing.
        assertEquals(NoteVerseAnchor.PLACEHOLDER, VerseDisplay.displayContent(john316, listOf("KJV", "CUV"), emptyMap()))
        assertTrue(VerseDisplay.isMissingVerseText(john316, listOf("KJV"), emptyMap()))
        assertFalse(VerseDisplay.isMissingVerseText(note(NoteType.Prayer) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, versionAbbr = "KJV") }, listOf("KJV"), emptyMap()))
    }

    @Test fun anAnchorInAVersionNoLongerSelectedComesFirst() {
        val synced = john316.copy(versionAbbr = "NKJV")
        assertEquals(
            "For God so loved the world [NKJV]\n\nFor God so loved [KJV]\n\n神愛世人 [和合本]",
            VerseDisplay.displayContent(synced, listOf("KJV", "CUV"), pool(kjv, cuv, nkjv)),
        )
        assertEquals(listOf("NKJV_43_3_16", "KJV_43_3_16", "CUV_43_3_16"), VerseDisplay.userVerseIds(synced, listOf("KJV", "CUV")))
        // Selected versions are all here, but the anchor's own NKJV text is not.
        assertTrue(VerseDisplay.isMissingVerseText(synced, listOf("KJV", "CUV"), pool(kjv, cuv)))
        // Versions compare without case or spaces.
        assertFalse(VerseDisplay.isMissingVerseText(john316.copy(versionAbbr = " kjv"), listOf("KJV"), pool(kjv)))
    }

    @Test fun marksMoveWithTheirVersionsText() {
        fun mark(source: String, start: Int, end: Int) = IndexedTextEntity(
            id = "m_$source", sourceId = source, startOffset = start, endOffset = end, markStyle = MarkStyle.Highlight, colorKey = "yellow",
            createdAt = t, updatedAt = t,
        )
        val marks = VerseMarks.byVerseId(listOf(mark("KJV_43_3_16", 4, 7), mark("CUV_43_3_16", 0, 1)))
        val body = VerseDisplay.markedBody(john316, listOf("KJV", "CUV"), pool(kjv, cuv), marks)!!
        // "For God so loved [KJV]\n\n" is 24 characters, so CUV's mark starts there.
        assertEquals(listOf(4 to 7, 24 to 25), body.marks.map { it.start to it.end })
        assertEquals("God", body.text.substring(4, 7))
        assertEquals("神", body.text.substring(24, 25))
    }

    @Test fun aMergedVerseRepeatingThePreviousOneIsShownOnce() {
        // iOS e4bb313 `resolveUserVerses`: CUV Ps 8:6–8 carry the same text after CuvVersificationFix.
        fun cuv(v: Int, text: String) = UserVerseEntity("CUV_19_8_$v", "CUV", 19, 8, v, text, createdAt = t, updatedAt = t)
        val verses = pool(cuv(5, "a"), cuv(6, "b"), cuv(7, "b"), cuv(8, "b"), cuv(9, "c"))
        val range = VerseDisplay.resolve(BibleAnchor(19, 8, 5, 9, "CUV"), verses)!!
        assertEquals("a b c", range.combinedContent)
        assertEquals(listOf(5, 6, 9), range.verses.map { it.first })
        // Alone, or starting the range, the merged verse still shows its text.
        assertEquals("b", VerseDisplay.resolve(BibleAnchor(19, 8, 7, 8, "CUV"), verses)!!.combinedContent)
    }

    @Test fun answeredMarkerIsAnObjectIdBody() {
        fun c(content: String) = NoteCommentEntity(id = "64a000000000000000000009", noteId = "n", content = content, createdAt = t)
        assertTrue(c("64a0000000000000000000AF").isAnsweredMarker)
        assertEquals("64a0000000000000000000af", c("64a0000000000000000000AF").linkedAnsweredCommentId)
        assertFalse(c("Thank you, Lord").isAnsweredMarker)
        assertFalse(c("64a0000000000000000000a").isAnsweredMarker)
    }
}
