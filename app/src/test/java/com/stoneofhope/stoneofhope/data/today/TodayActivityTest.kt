package com.stoneofhope.stoneofhope.data.today

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.text.BibleTextDatabase
import com.stoneofhope.stoneofhope.data.bible.text.BibleVerseReader
import com.stoneofhope.stoneofhope.data.bible.text.UnavailableOnDemandVerseSource
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.reading.ReadingRepository
import com.stoneofhope.stoneofhope.data.repository.NoteInputSaveResult
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.repository.UserVerseManager
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.MutableClock
import com.stoneofhope.stoneofhope.data.today.TodayActivity.TileKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** iOS `TodayActivityBuilder` counting rules, end to end through Room. */
@RunWith(AndroidJUnit4::class)
class TodayActivityTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = MutableClock(Instant.parse("2026-10-26T02:00:00Z")) // 10:00 local, Oct 26
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    private val today = LocalDate.of(2026, 10, 26)
    private val repo = TodayRepository(db, ReadingRepository(db, clock))
    private val notes = NoteRepository(
        db, clock, UserVerseManager(db.userVerseDao()),
        BibleVerseReader(
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BibleTextDatabase::class.java).build().verseDao(),
            db.bibleCacheDao(), UnavailableOnDemandVerseSource(),
        ),
        BibleVersionPreferences(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("today-test", Context.MODE_PRIVATE)) { BibleLanguage.English },
    )
    private val inDay = Instant.parse("2026-10-26T03:00:00Z")
    private val yesterday = Instant.parse("2026-10-25T03:00:00Z")
    private var n = 0

    @After
    fun tearDown() = db.close()

    private fun id() = "64a0000000000000000%05x".format(++n)

    private fun note(type: NoteType, at: Instant = inDay, block: NoteEntity.() -> NoteEntity = { this }) =
        NoteEntity(id = id(), noteType = type, content = "x", createdAt = at, updatedAt = at).block()

    private suspend fun activity() = repo.observeActivity(today, zone, isToday = true).first()

    private fun TodayActivity.count(kind: TileKind) = tiles.firstOrNull { it.kind == kind }?.count ?: 0

    @Test
    fun emptyDayHidesTheSection() = runTest {
        db.noteDao().upsert(note(NoteType.Note, yesterday))
        assertTrue(activity().isEmpty)
    }

    @Test
    fun noteCategoriesMatchIos() = runTest {
        db.noteDao().upsertAll(
            listOf(
                note(NoteType.Note),
                note(NoteType.Note) { copy(bookOrd = 43, chapterOrd = 3) }, // chapter note → Bible
                note(NoteType.Note) { copy(bookOrd = 43, chapterOrd = 3, verseStart = 16) }, // verse reflection → neither
                note(NoteType.StudyNote),
                note(NoteType.BibleVerse) { copy(content = "·") },
                note(NoteType.Prayer),
                note(NoteType.Prayer) { copy(isAnswered = true) },
                note(NoteType.Image),
                note(NoteType.Note) { copy(deletedAt = inDay, isDeleted = true) },
            )
        )
        val a = activity()
        assertEquals(1, a.count(TileKind.QuickNote))
        assertEquals(1, a.count(TileKind.StudyNote))
        assertEquals(2, a.count(TileKind.Bible))
        assertEquals(2, a.count(TileKind.Prayer))
        assertEquals(1, a.count(TileKind.AnsweredPrayer))
        assertEquals(1, a.count(TileKind.Image))
        assertTrue(a.showReview)
        assertEquals(
            listOf(TileKind.QuickNote, TileKind.StudyNote, TileKind.Bible, TileKind.Prayer, TileKind.AnsweredPrayer, TileKind.Image),
            a.tiles.map { it.kind },
        )
    }

    @Test
    fun commentsAndStarsBringOlderNotesIntoToday() = runTest {
        val old = note(NoteType.Prayer, yesterday)
        val oldStarredViaComment = note(NoteType.Note, yesterday)
        db.noteDao().upsertAll(listOf(old, oldStarredViaComment, note(NoteType.Note) { copy(starLevel = 2) }))
        db.noteCommentDao().upsertAll(
            listOf(
                NoteCommentEntity(id = id(), noteId = old.id, content = "c", createdAt = inDay),
                NoteCommentEntity(id = id(), noteId = oldStarredViaComment.id, content = "c", createdAt = inDay, starLevel = 1),
            )
        )
        val a = activity()
        assertEquals("yesterday's prayer got a comment today", 1, a.count(TileKind.Prayer))
        assertEquals(2, a.count(TileKind.QuickNote))
        assertEquals(2, a.count(TileKind.Starred))
    }

    @Test
    fun pinnedCountsAnyDateAndSermonsVideosCards() = runTest {
        val tag = TagEntity(id = id(), name = "Hope", createdAt = inDay)
        db.tagDao().upsert(tag)
        val sermon = SermonEntity(id = id(), title = "Grace", createdAt = inDay, preachedOn = "20261026", chapterRange = "Rom 5", tagIds = listOf(tag.id))
        db.sermonDao().upsert(sermon)
        db.sermonDao().upsert(SermonEntity(id = id(), title = "Other day", createdAt = inDay, preachedOn = "20261025"))
        db.youTubeVideoDao().upsert(YouTubeVideoEntity(youtubeID = "abc", title = "Talk", createdAt = inDay, updatedAt = inDay))
        db.youTubeVideoDao().upsert(YouTubeVideoEntity(youtubeID = "idx", title = "Sermon index", sermonId = sermon.id, createdAt = inDay, updatedAt = inDay))
        db.noteDao().upsertAll(
            listOf(
                note(NoteType.Note, Instant.parse("2026-01-01T00:00:00Z")) { copy(isPinned = true) },
                note(NoteType.SermonNote) { copy(sermonId = sermon.id) },
                note(NoteType.YouTubeVideo) { copy(youtubeID = "abc") },
            )
        )
        val a = activity()
        assertEquals(1, a.count(TileKind.Pinned))
        assertEquals(listOf("Grace"), a.sermons.map { it.sermon.title })
        assertEquals(1, a.sermons.single().noteCount)
        assertEquals(listOf("Hope"), a.sermons.single().tagNames)
        assertEquals("hidden sermon-index rows stay out", listOf("abc"), a.videos.map { it.video.youtubeID })
        assertEquals(1, a.videos.single().noteCount)
        assertFalse(repo.observeActivity(today, zone, isToday = false).first().tiles.any { it.kind == TileKind.Pinned })
    }

    @Test
    fun prayerAndNoteSavesMatchIos() = runTest {
        db.tagDao().upsert(TagEntity(id = id(), name = "Family", createdAt = inDay))
        val prayer = notes.savePrayerNote("  Lord, guide us  ", "family", null)!!
        assertEquals("Lord, guide us", prayer.content)
        assertEquals(NoteType.Prayer, prayer.noteType)
        assertTrue(prayer.needsSync)
        assertEquals("existing tag matched case-insensitively", 1, db.tagDao().observeActive().first().size)
        assertEquals(db.tagDao().findActiveByName("FAMILY")!!.id, prayer.tagIds.single())

        val withNewTag = notes.savePrayerNote("Thanks", "Joy", null)!!
        val joy = db.tagDao().findActiveByName("Joy")!!
        assertTrue(joy.needsSync)
        assertEquals(listOf(joy.id), withNewTag.tagIds)

        assertEquals(null, notes.savePrayerNote("   ", null, null))
        val quick = (notes.saveNoteFromContent(" hello ", null, zone = zone) as NoteInputSaveResult.Plain).note
        assertEquals("hello", quick.content)
        assertEquals(NoteType.Note, quick.noteType)
        assertEquals(3, activity().let { it.count(TileKind.Prayer) + it.count(TileKind.QuickNote) })
    }
}
