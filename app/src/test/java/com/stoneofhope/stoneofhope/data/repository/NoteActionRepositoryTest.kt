package com.stoneofhope.stoneofhope.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.text.BibleTextDatabase
import com.stoneofhope.stoneofhope.data.bible.text.BibleVerseReader
import com.stoneofhope.stoneofhope.data.bible.text.UnavailableOnDemandVerseSource
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import com.stoneofhope.stoneofhope.data.notes.isAnsweredMarker
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.LocalSyncOps
import com.stoneofhope.stoneofhope.data.sync.MutableClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** iOS `NotesManager` note actions (pin / answered / stars / tags / edit / delete / comments). */
@RunWith(AndroidJUnit4::class)
class NoteActionRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = MutableClock(Instant.parse("2026-10-26T02:00:00Z")) // 10:00 local, Oct 26
    private val today = LocalDate.of(2026, 10, 26)
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val bible = Room.inMemoryDatabaseBuilder(context, BibleTextDatabase::class.java).allowMainThreadQueries().build()
    private val notes = NoteRepository(
        db, clock, UserVerseManager(db.userVerseDao()),
        BibleVerseReader(bible.verseDao(), db.bibleCacheDao(), UnavailableOnDemandVerseSource()),
        BibleVersionPreferences(context.getSharedPreferences("actions-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }) { BibleLanguage.English },
    )
    private val actions = NoteActionRepository(db, clock, notes, LocalSyncOps(db))

    @After
    fun tearDown() {
        db.close()
        bible.close()
    }

    private var n = 0
    private fun id() = "64a0000000000000000%05x".format(++n)

    private suspend fun insert(type: NoteType, created: Instant = clock.now, block: NoteEntity.() -> NoteEntity = { this }): NoteEntity =
        NoteEntity(id = id(), noteType = type, content = "body", createdAt = created, updatedAt = created).block().also { db.noteDao().upsert(it) }

    private suspend fun get(id: String) = db.noteDao().getById(id)!!
    private suspend fun comments(noteId: String) = db.noteCommentDao().activeForNote(noteId)

    @Test fun pinLogsTodayAndUnpinOnTheCreationDayDropsTheLog() = runTest {
        val note = insert(NoteType.Note)
        clock.now = clock.now.plusSeconds(60)
        actions.setPinned(note.id, true, zone = zone)
        with(get(note.id)) {
            assertEquals(true, isPinned)
            assertTrue(needsSync)
            assertEquals(clock.now, updatedAt)
        }
        assertEquals(listOf("2026-10-26"), db.pinLogDao().getByNoteId(note.id)!!.pinnedDays)
        actions.setPinned(note.id, false, zone = zone)
        assertEquals(false, get(note.id).isPinned)
        assertNull(db.pinLogDao().getByNoteId(note.id))
        assertEquals(listOf(note.id), db.pinLogDao().pendingDeletes())
    }

    @Test fun unpinningAnOlderNoteKeepsItsPinHistory() = runTest {
        val note = insert(NoteType.Note, created = clock.now.minus(Duration.ofDays(3)))
        actions.setPinned(note.id, true, zone = zone)
        actions.setPinned(note.id, false, zone = zone)
        assertEquals(listOf("2026-10-26"), db.pinLogDao().getByNoteId(note.id)!!.pinnedDays)
        // The list passes its own day: unpinning from the note's creation-day list drops the log.
        actions.setPinned(note.id, true, zone = zone)
        actions.setPinned(note.id, false, unmarkReferenceDay = today.minusDays(3), zone = zone)
        assertNull(db.pinLogDao().getByNoteId(note.id))
    }

    @Test fun answeredCommentLogsMarkerPairedWithTheText() = runTest {
        val prayer = insert(NoteType.Prayer)
        actions.addAnsweredComment(prayer.id, "  He provided  ")
        val cs = comments(prayer.id)
        assertEquals(2, cs.size)
        val (text, marker) = cs
        assertEquals("He provided", text.content)
        assertTrue(marker.isAnsweredMarker)
        assertEquals(text.id, marker.content)
        assertEquals(text.createdAt.plusMillis(1), marker.createdAt)
        assertNull(text.bookOrd)
        with(get(prayer.id)) {
            assertEquals(true, isAnswered)
            assertEquals(clock.now, answeredAt)
        }
        // With no text the marker points at itself.
        val other = insert(NoteType.Prayer)
        actions.addAnsweredComment(other.id, " ")
        val only = comments(other.id).single()
        assertEquals(only.id, only.content)
    }

    @Test fun unansweringRemovesTodaysMarkersButKeepsOlderOnes() = runTest {
        val prayer = insert(NoteType.Prayer)
        clock.now = clock.now.minus(Duration.ofDays(2))
        actions.addAnsweredComment(prayer.id, "old")
        clock.now = Instant.parse("2026-10-26T02:00:00Z")
        actions.addAnsweredComment(prayer.id, "today")
        actions.setAnswered(prayer.id, false, zone = zone)
        assertEquals(listOf("old"), comments(prayer.id).filterNot { it.isAnsweredMarker }.map { it.content })
        assertEquals(1, comments(prayer.id).count { it.isAnsweredMarker })
        with(get(prayer.id)) {
            assertEquals(false, isAnswered)
            assertNull(answeredAt)
        }
        actions.setAnswered(prayer.id, true, zone = zone)
        assertEquals(clock.now, get(prayer.id).answeredAt)
    }

    @Test fun commentCopiesTheAnchorExceptOnPrayers() = runTest {
        val chapterNote = insert(NoteType.Note) { copy(bookOrd = 1, chapterOrd = 1, refKey = "CH:1:1", versionAbbr = "KJV", sermonId = "s1", youtubeID = "") }
        clock.now = clock.now.plusSeconds(30)
        actions.addComment(chapterNote.id, "  a thought ", zone)
        with(comments(chapterNote.id).single()) {
            assertEquals("a thought", content)
            assertEquals(listOf<Any?>(1, 1, "CH:1:1", "CH:1:1", "KJV", "s1", null), listOf(bookOrd, chapterOrd, refKey, chapterKey, versionAbbr, sermonId, youtubeID))
            assertTrue(needsSync)
        }
        assertEquals(clock.now, get(chapterNote.id).updatedAt)

        val prayer = insert(NoteType.Prayer) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16, refKey = "VS:43:3:16-16", versionAbbr = "KJV") }
        actions.addComment(prayer.id, "amen", zone)
        with(comments(prayer.id).single()) {
            assertNull(bookOrd)
            assertNull(refKey)
            assertNull(chapterKey)
        }
        actions.addComment(prayer.id, "   ", zone)
        assertEquals(1, comments(prayer.id).size)
    }

    @Test fun commentOnAVerseAnchorIsAReflectionNote() = runTest {
        val anchor = insert(NoteType.BibleVerse) {
            copy(content = NoteVerseAnchor.PLACEHOLDER, bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 16, versionAbbr = "KJV", refKey = "VS:43:3:16-16")
        }
        actions.addComment(anchor.id, "so loved", zone)
        assertTrue(comments(anchor.id).isEmpty())
        val all = db.noteDao().observeActive().first()
        assertEquals(1, all.count { it.noteType == NoteType.BibleVerse })
        with(all.single { it.noteType == NoteType.Note }) {
            assertEquals("so loved", content)
            assertEquals(listOf(43, 3, 16, 16), listOf(bookOrd, chapterOrd, verseStart, verseEnd))
        }
    }

    @Test fun starsAndTags() = runTest {
        db.tagDao().upsert(TagEntity(id = "64a0000000000000000000b1", name = "Hope", createdAt = clock.now))
        val note = insert(NoteType.Note)
        actions.setStarLevel(note.id, 2)
        assertEquals(2, get(note.id).starLevel)
        actions.addTag(note.id, " hope ")
        actions.addTag(note.id, "HOPE")
        actions.addTag(note.id, "Joy")
        val tags = db.tagDao().observeActive().first().associateBy { it.name }
        assertEquals(listOf(tags.getValue("Hope").id, tags.getValue("Joy").id), get(note.id).tagIds)
        assertTrue(tags.getValue("Joy").needsSync)
        actions.removeTag(note.id, tags.getValue("Hope").id)
        assertEquals(listOf(tags.getValue("Joy").id), get(note.id).tagIds)
    }

    /** Android fix (IOS_ISSUES G1): iOS turns an edited "John 3:16 …" text into a verse anchor. */
    @Test fun editChangesOnlyTheText() = runTest {
        val prayer = insert(NoteType.Prayer)
        actions.updateContent(prayer.id, "John 3:16 is my prayer")
        with(get(prayer.id)) {
            assertEquals(NoteType.Prayer, noteType)
            assertEquals("John 3:16 is my prayer", content)
            assertNull(bookOrd)
            assertTrue(needsSync)
        }
        actions.updateContent(prayer.id, "  ")
        assertEquals("John 3:16 is my prayer", get(prayer.id).content)
        val anchor = insert(NoteType.BibleVerse) { copy(content = NoteVerseAnchor.PLACEHOLDER) }
        actions.updateContent(anchor.id, "new")
        assertEquals(NoteVerseAnchor.PLACEHOLDER, get(anchor.id).content)
    }

    @Test fun deleteSoftDeletesTheNoteItsCommentsAndPinLog() = runTest {
        val note = insert(NoteType.Note)
        actions.addComment(note.id, "c1", zone)
        actions.setPinned(note.id, true, zone = zone)
        clock.now = clock.now.plusSeconds(5)
        actions.delete(note.id)
        with(get(note.id)) {
            assertEquals(clock.now, deletedAt)
            assertTrue(isDeleted)
            assertTrue(needsSync)
        }
        assertTrue(comments(note.id).isEmpty())
        assertTrue(db.noteCommentDao().dirty().all { it.deletedAt == clock.now && it.isDeleted })
        assertNull(db.pinLogDao().getByNoteId(note.id))
        assertFalse(db.noteDao().observeActive().first().any { it.id == note.id })
    }
}
