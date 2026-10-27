package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
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
        assertEquals("For God so loved For God sent not", VerseDisplay.displayContent(anchor, verses))
        assertEquals("John 3:16-17\nFor God so loved For God sent not", VerseDisplay.copyText(anchor, VerseDisplay.displayContent(anchor, verses)))
        // No pool rows yet: the placeholder, which the list hides.
        assertEquals(NoteVerseAnchor.PLACEHOLDER, VerseDisplay.displayContent(anchor, emptyMap()))
        assertFalse(VerseDisplay.showsBody(anchor, NoteVerseAnchor.PLACEHOLDER))
        // A prayer's own text is its body; its verse is resolved separately.
        val prayer = note(NoteType.Prayer) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16, versionAbbr = "CUV") }
        assertEquals("text", VerseDisplay.displayContent(prayer, verses))
        assertEquals(ResolvedVerse("約翰福音 3:16", "神愛世人", "CUV", listOf(16 to "神愛世人")), VerseDisplay.resolve(prayer, verses))
        assertNull(VerseDisplay.resolve(note(NoteType.Note), verses))
    }

    @Test fun answeredMarkerIsAnObjectIdBody() {
        fun c(content: String) = NoteCommentEntity(id = "64a000000000000000000009", noteId = "n", content = content, createdAt = t)
        assertTrue(c("64a0000000000000000000AF").isAnsweredMarker)
        assertEquals("64a0000000000000000000af", c("64a0000000000000000000AF").linkedAnsweredCommentId)
        assertFalse(c("Thank you, Lord").isAnsweredMarker)
        assertFalse(c("64a0000000000000000000a").isAnsweredMarker)
    }
}
