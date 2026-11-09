package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `NoteCreationView` list rules: type / tag / day filters, ordering, grouping, hidden reflections. */
class NoteListBuilderTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 26)
    private val yesterday = today.minusDays(1)
    private var n = 0

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant = day.atTime(hour, minute).atZone(zone).toInstant()
    private fun id() = "64a0000000000000000%05x".format(++n)

    private fun note(type: NoteType, created: Instant, block: NoteEntity.() -> NoteEntity = { this }) =
        NoteEntity(id = id(), noteType = type, content = "x$n", createdAt = created, updatedAt = created).block()

    private fun anchor(created: Instant, verse: Int, verseEnd: Int? = verse) = note(NoteType.BibleVerse, created) {
        copy(content = NoteVerseAnchor.PLACEHOLDER, bookOrd = 43, chapterOrd = 3, verseStart = verse, verseEnd = verseEnd, versionAbbr = "KJV")
    }

    private fun reflection(created: Instant, verse: Int, text: String = "thought") = note(NoteType.Note, created) {
        copy(content = text, bookOrd = 43, chapterOrd = 3, verseStart = verse, verseEnd = verse, versionAbbr = "KJV")
    }

    private fun comment(noteId: String, created: Instant, star: Int = 0, tags: List<String> = emptyList(), content: String = "c") =
        NoteCommentEntity(id = id(), noteId = noteId, content = content, createdAt = created, starLevel = star, tagIds = tags)

    private fun build(
        notes: List<NoteEntity>, filter: NoteFilter, day: LocalDate? = today, tag: String? = null,
        comments: List<NoteCommentEntity> = emptyList(), tags: Map<String, String> = emptyMap(),
        sermons: Set<String> = emptySet(), videos: Set<String> = emptySet(),
    ) = NoteListBuilder.build(NoteListData(notes, comments, tags, sermons, videos), filter, tag, day, today, zone)

    private fun List<NoteListSection>.ids() = flatMap { s -> s.notes.map { it.id } }

    @Test fun dayFilterTakesNotesCreatedOrCommentedThatDay() {
        val todays = note(NoteType.Note, at(today, 9))
        val old = note(NoteType.Note, at(yesterday, 9))
        val oldCommentedToday = note(NoteType.Note, at(yesterday, 8))
        val sections = build(listOf(todays, old, oldCommentedToday), NoteFilter.QuickNote, comments = listOf(comment(oldCommentedToday.id, at(today, 7))))
        assertEquals(listOf(today, yesterday), sections.map { it.day })
        assertEquals(listOf(todays.id, oldCommentedToday.id), sections.ids())
    }

    @Test fun pinnedAndAnsweredIgnoreTheDay() {
        val pinnedOld = note(NoteType.Note, at(yesterday.minusDays(5), 9)) { copy(isPinned = true) }
        val answeredOld = note(NoteType.Prayer, at(yesterday, 9)) { copy(isAnswered = true) }
        assertEquals(listOf(pinnedOld.id), build(listOf(pinnedOld, answeredOld), NoteFilter.Pinned).ids())
        assertEquals(listOf(answeredOld.id), build(listOf(pinnedOld, answeredOld), NoteFilter.AnsweredPrayer).ids())
    }

    @Test fun allTodayListsPinnedNotesUnderTodayAfterTodaysActivity() {
        val pinnedOld = note(NoteType.Prayer, at(today.minusDays(3), 9)) { copy(isPinned = true) }
        val todayEarly = note(NoteType.Note, at(today, 8))
        val todayLate = note(NoteType.Note, at(today, 20))
        val sections = build(listOf(pinnedOld, todayEarly, todayLate), NoteFilter.All)
        assertEquals(listOf(today), sections.map { it.day })
        // Pinned first, then newest first.
        assertEquals(listOf(pinnedOld.id, todayLate.id, todayEarly.id), sections.ids())
        // Another day's list doesn't pull pinned notes in.
        assertEquals(emptyList<String>(), build(listOf(pinnedOld), NoteFilter.All, day = yesterday).ids())
    }

    @Test fun quickNoteExcludesChapterAndVerseNotesBibleIncludesAnchorsAndChapterNotes() {
        val plain = note(NoteType.Note, at(today, 9))
        val chapterNote = note(NoteType.Note, at(today, 10)) { copy(bookOrd = 1, chapterOrd = 1) }
        val a = anchor(at(today, 11), 16)
        val loose = reflection(at(today, 12), 20)
        val all = listOf(plain, chapterNote, a, loose)
        assertEquals(listOf(plain.id), build(all, NoteFilter.QuickNote).ids())
        assertEquals(setOf(chapterNote.id, a.id), build(all, NoteFilter.Bible).ids().toSet())
    }

    @Test fun reflectionsWithAnAnchorAreHiddenLooseOnesStay() {
        val a = anchor(at(today, 9), 16)
        val r1 = reflection(at(today, 9, 1), 16)
        val other = reflection(at(yesterday, 9), 16) // anchor is on another day → loose
        val sections = build(listOf(a, r1, other), NoteFilter.All, day = null)
        assertEquals(listOf(a.id, other.id), sections.ids())
    }

    /** IOS_ISSUES F3: an anchor without verseEnd still owns its reflections. */
    @Test fun anchorWithoutVerseEndStillHidesItsReflection() {
        val a = anchor(at(today, 9), 16, verseEnd = null)
        val r = reflection(at(today, 10), 16)
        assertEquals(listOf(a.id), build(listOf(a, r), NoteFilter.All).ids())
        assertEquals(listOf(r.id), NoteRelations.reflections(a, listOf(a, r), zone).map { it.id })
    }

    /** iOS `3175662` reverted the verse grouping: loose notes on one verse are separate rows, newest first. */
    @Test fun looseNotesOnTheSameVerseAreSeparateRowsNewestFirst() {
        val v20a = reflection(at(today, 9), 20, "a")
        val plain = note(NoteType.Note, at(today, 10))
        val v18 = reflection(at(today, 11), 18, "b")
        val v20b = reflection(at(today, 12), 20, "c")
        val notes = build(listOf(v20a, plain, v18, v20b), NoteFilter.All).single().notes
        assertEquals(listOf(v20b.id, v18.id, plain.id, v20a.id), notes.map { it.id })
    }

    @Test fun starredMatchesNoteOrCommentStars() {
        val starred = note(NoteType.Note, at(today, 9)) { copy(starLevel = 2) }
        val viaComment = note(NoteType.Note, at(today, 10))
        val plain = note(NoteType.Note, at(today, 11))
        val ids = build(listOf(starred, viaComment, plain), NoteFilter.Starred, comments = listOf(comment(viaComment.id, at(yesterday, 9), star = 1))).ids()
        assertEquals(setOf(starred.id, viaComment.id), ids.toSet())
    }

    @Test fun tagFilterMatchesNoteOrCommentTagsByExactName() {
        val tags = mapOf("t1" to "Hope", "t2" to "hope")
        val tagged = note(NoteType.Note, at(today, 9)) { copy(tagIds = listOf("t1")) }
        val viaComment = note(NoteType.Note, at(today, 10))
        val otherCase = note(NoteType.Note, at(today, 11)) { copy(tagIds = listOf("t2")) }
        val ids = build(listOf(tagged, viaComment, otherCase), NoteFilter.All, tag = "Hope", tags = tags,
            comments = listOf(comment(viaComment.id, at(today, 9), tags = listOf("t1")))).ids()
        assertEquals(setOf(tagged.id, viaComment.id), ids.toSet())
    }

    @Test fun sermonAndVideoFiltersNeedALiveTarget() {
        val live = note(NoteType.Note, at(today, 9)) { copy(sermonId = "s1") }
        val gone = note(NoteType.Note, at(today, 10)) { copy(sermonId = "s2") }
        val video = note(NoteType.Note, at(today, 11)) { copy(youtubeID = "v1") }
        val videoType = note(NoteType.YouTubeVideo, at(today, 12))
        val all = listOf(live, gone, video, videoType)
        assertEquals(listOf(live.id), build(all, NoteFilter.Sermon, sermons = setOf("s1")).ids())
        assertEquals(setOf(video.id, videoType.id), build(all, NoteFilter.YouTubeVideo, videos = setOf("v1")).ids().toSet())
    }

    @Test fun summaryCountsReflectionsOrCommentsWithoutAnsweredMarkers() {
        val a = anchor(at(today, 9), 16)
        val r1 = reflection(at(today, 10), 16)
        val dot = reflection(at(today, 11), 16, ".")
        val prayer = note(NoteType.Prayer, at(today, 9))
        val comments = listOf(comment(prayer.id, at(today, 9)), comment(prayer.id, at(today, 9), content = "64a0000000000000000000ff"))
        val notes = listOf(a, r1, dot, prayer)
        assertEquals(CompactSummary.Notes(1), NoteRelations.summary(a, notes, comments, zone))
        assertEquals(CompactSummary.Comments(1), NoteRelations.summary(prayer, notes, comments, zone))
        assertNull(NoteRelations.summary(r1, notes, comments, zone))
        assertEquals(CompactSummary.Comments(0), NoteRelations.thread(r1, notes, comments, zone))
        assertTrue(comments[1].isAnsweredMarker)
    }
}
