package com.stoneofhope.stoneofhope.data.sermon

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.BookChapter
import com.stoneofhope.stoneofhope.data.bible.text.BibleTextDatabase
import com.stoneofhope.stoneofhope.data.bible.text.BibleVerseReader
import com.stoneofhope.stoneofhope.data.bible.text.UnavailableOnDemandVerseSource
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.reading.ReadingRepository
import com.stoneofhope.stoneofhope.data.remote.wire.FakeFirestore
import com.stoneofhope.stoneofhope.data.remote.wire.WireEncoder
import com.stoneofhope.stoneofhope.data.remote.wire.WireValue
import com.stoneofhope.stoneofhope.data.repository.NoteActionRepository
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.repository.UserVerseManager
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.LocalSyncOps
import com.stoneofhope.stoneofhope.data.sync.MutableClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private class FakeYouTubeWeb : YouTubeWeb {
    var info: OEmbedInfo? = null
    var calls = 0
    override suspend fun oEmbed(videoID: String): OEmbedInfo? { calls++; return info }
    override suspend fun transcript(videoID: String): List<TranscriptSegment>? = null
}

/** Sermon and video writes: iOS `NotesManager` sermon / video code and the fixes in IOS_ISSUES D1–D3, K1–K11. */
@RunWith(AndroidJUnit4::class)
class SermonRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = MutableClock(Instant.parse("2026-10-28T02:00:00Z")) // 10:00 local, Wednesday Oct 28
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = context.getSharedPreferences("sermon-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val versions = BibleVersionPreferences(prefs) { BibleLanguage.English }
    private val bibleText = run {
        context.deleteDatabase("sermon-test-bible.db")
        Room.databaseBuilder(context, BibleTextDatabase::class.java, "sermon-test-bible.db")
            .createFromAsset(BibleTextDatabase.ASSET_PATH).allowMainThreadQueries().build()
    }
    private val notes = NoteRepository(
        db, clock, UserVerseManager(db.userVerseDao()),
        BibleVerseReader(bibleText.verseDao(), db.bibleCacheDao(), UnavailableOnDemandVerseSource()), versions,
    )
    private val reading = ReadingRepository(db, clock)
    private val web = FakeYouTubeWeb()
    private val ops = LocalSyncOps(db)
    private val sermons = SermonRepository(db, clock, reading, notes, web, ops, versions)
    private val videos = VideoRepository(db, clock, notes, sermons, web, ops, versions)
    private val actions = NoteActionRepository(db, clock, notes, ops)
    private val defaultLocale = Locale.getDefault().also { Locale.setDefault(Locale.US) }
    private val vid = "dQw4w9WgXcQ"
    private val link = "https://youtu.be/$vid?si=share"

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        db.close()
        bibleText.close()
    }

    private suspend fun readToday(bookOrd: Int, chapter: Int) =
        db.chapterLogDao().activeFor(bookOrd, chapter).any { it.readOn.atZone(zone).toLocalDate() == LocalDate.of(2026, 10, 28) }

    private suspend fun allNotes(): List<NoteEntity> = db.noteDao().observeActive().first()

    // MARK: - Manual

    @Test fun manualSermonWritesIosFields() = runTest {
        val s = sermons.createManual("  Grace  ", "Romans 8", "Pastor Lee", seriesId = null, zone = zone)
        val saved = db.sermonDao().getById(s.id)!!
        assertEquals("Grace", saved.title)
        assertEquals("Pastor Lee", saved.preacher)
        assertEquals("20261028", saved.preachedOn)
        // iOS `Date().onlyDate`: local midnight, not now.
        assertEquals(Instant.parse("2026-10-27T16:00:00Z"), saved.createdAt)
        assertEquals("Romans 8", saved.chapterRange)
        assertEquals(45, saved.bookOrd)
        assertEquals(listOf(8), saved.chapters)
        assertNull(saved.book)
        assertTrue(saved.isUserData && saved.needsSync)
        assertNull(saved.youtubeID)
        assertTrue(readToday(45, 8))

        // An empty reference still writes `chapterRange = ""` (iOS always passes the field's text).
        val bare = sermons.createManual("Untitled", "", "", null, zone)
        assertEquals("", db.sermonDao().getById(bare.id)!!.chapterRange)
        assertNull(db.sermonDao().getById(bare.id)!!.bookOrd)
    }

    @Test fun blankTitleIsTodaysDateMadeUnique() = runTest {
        val a = sermons.createManual("", "John 3", "", null, zone)
        val b = sermons.createManual(" ", "John 4", "", null, zone)
        assertEquals("October 28, 2026", a.title)
        assertEquals("October 28, 2026 - 2", b.title)
    }

    @Test fun savingASermonNeverUnreadsAChapter() = runTest {
        // IOS_ISSUES D1: iOS toggles, so a chapter read this morning becomes unread.
        reading.toggleChapterRead(45, 8, zone)
        assertTrue(readToday(45, 8))
        sermons.createManual("Grace", "Romans 8-9", "", null, zone)
        assertTrue(readToday(45, 8))
        assertTrue(readToday(45, 9))
        assertEquals(1, db.chapterLogDao().activeFor(45, 8).size)
    }

    @Test fun pickedSeriesWinsAndDetectionOnlyUsesTheSameBook() = runTest {
        val romans = SermonSeriesEntity(id = "64a0000000000000000000a1", title = "Romans", bookOrd = 45, startedOn = "20261001")
        val topical = SermonSeriesEntity(id = "64a0000000000000000000a2", title = "Prayer", startedOn = "20261020")
        db.sermonSeriesDao().upsert(romans)
        db.sermonSeriesDao().upsert(topical)

        // IOS_ISSUES D2: the picked topical series stays although Romans matches the book.
        val picked = sermons.createManual("A", "Romans 1", "", topical.id, zone)
        assertEquals(topical.id, db.sermonDao().getById(picked.id)!!.seriesId)
        assertTrue(db.sermonSeriesDao().getById(topical.id)!!.needsSync)

        val detected = sermons.createManual("B", "Romans 2", "", null, zone)
        assertEquals(romans.id, db.sermonDao().getById(detected.id)!!.seriesId)

        // IOS_ISSUES K3: no fallback to the newest topical series for another book.
        val other = sermons.createManual("C", "John 1", "", null, zone)
        assertNull(db.sermonDao().getById(other.id)!!.seriesId)
    }

    // MARK: - YouTube

    @Test fun youTubeSermonFromTheTitleWithItsIndexRow() = runTest {
        web.info = OEmbedInfo("Grace Abounds: Romans 5 | Pastor Lee", "Grace Church", null, "https://i.ytimg.com/vi/$vid/hqdefault.jpg")
        val r = sermons.createFromYouTube(link, zone = zone) as SermonCreateResult.Created
        val s = db.sermonDao().getById(r.sermonId)!!
        assertEquals("Grace Abounds", s.title)
        assertEquals("Pastor Lee", s.preacher)
        assertEquals(vid, s.youtubeID)
        assertEquals("https://www.youtube.com/watch?v=$vid", s.videoUrl)
        assertEquals("Romans 5", s.chapterRange)
        assertEquals("20261028", s.preachedOn)
        assertTrue(readToday(45, 5))

        val index = db.youTubeVideoDao().getById(vid)!!
        assertEquals(s.id, index.sermonId)
        assertEquals("Grace Abounds", index.title)
        assertEquals("Pastor Lee", index.channelName)
        assertEquals(45, index.bookOrd)
        assertEquals(5, index.chapterOrd)
        assertEquals("https://img.youtube.com/vi/$vid/hqdefault.jpg", index.thumbnailUrl)
        // The index row is not a video.
        assertTrue(db.youTubeVideoDao().observePersonalCatalog().first().isEmpty())

        // Same link again: the existing sermon (iOS D3 meant to say so).
        assertEquals(SermonCreateResult.Existing(s.id), sermons.createFromYouTube("https://www.youtube.com/watch?v=$vid", zone = zone))
        assertEquals(1, web.calls)
    }

    @Test fun linkAlreadySavedAsAVideoStaysAVideo() = runTest {
        // IOS_ISSUES K6: iOS turns the video into the sermon's hidden index and the video disappears.
        val video = YouTubeVideoEntity(youtubeID = vid, title = "Saved", createdAt = clock.instant(), updatedAt = clock.instant())
        db.youTubeVideoDao().upsert(video)
        assertEquals(SermonCreateResult.ExistingVideo(vid), sermons.createFromYouTube(link, zone = zone))
        assertEquals(video, db.youTubeVideoDao().getById(vid))
        assertTrue(db.sermonDao().observeActive().first().isEmpty())
    }

    @Test fun linkFailures() = runTest {
        assertEquals(SermonCreateResult.Failed(LinkFailure.Shorts), sermons.createFromYouTube("https://youtube.com/shorts/$vid"))
        assertEquals(SermonCreateResult.Failed(LinkFailure.InvalidLink), sermons.createFromYouTube("https://example.com"))
        web.info = null
        assertEquals(SermonCreateResult.Failed(LinkFailure.Unavailable), sermons.createFromYouTube(link))
    }

    @Test fun typedReferenceReplacesTheTitlesAndDecidesWhatIsRead() = runTest {
        web.info = OEmbedInfo("Grace: Romans 5 | Lee", null, null, null)
        val r = sermons.createFromYouTube(link, refOverride = "John 3", zone = zone) as SermonCreateResult.Created
        val s = db.sermonDao().getById(r.sermonId)!!
        assertEquals("John 3", s.chapterRange)
        assertEquals(listOf(3), s.chapters)
        // IOS_ISSUES K5: iOS marks the title's Romans 5 and never the typed John 3.
        assertTrue(readToday(43, 3))
        assertFalse(readToday(45, 5))
    }

    // MARK: - Edit

    @Test fun editClearsReferenceAndVideoAndMovesTheDay() = runTest {
        web.info = OEmbedInfo("Grace: Romans 5 | Lee", null, null, null)
        val id = (sermons.createFromYouTube(link, zone = zone) as SermonCreateResult.Created).sermonId
        sermons.update(id, SermonEdit("Grace", LocalDate.of(2026, 10, 25), "", "Lee", "", "https://a.mp3", "", ""))
        val s = db.sermonDao().getById(id)!!
        assertEquals("20261025", s.preachedOn)
        assertNull(s.bookOrd)
        assertNull(s.chapterRange)
        assertTrue(s.chapters.isEmpty())
        // IOS_ISSUES K11: iOS keeps `youtubeID` when the link is cleared.
        assertNull(s.youtubeID)
        assertEquals("", s.videoUrl)
        assertEquals("https://a.mp3", s.audioUrl)
        // IOS_ISSUES K7: the old video's index row goes.
        assertNotNull(db.youTubeVideoDao().getById(vid)!!.deletedAt)

        sermons.update(id, s.let { SermonEdit(it.title, LocalDate.of(2026, 10, 25), "Romans 6", "Lee", "https://www.youtube.com/live/$vid?feature=share", "", "", "") })
        assertEquals(vid, db.sermonDao().getById(id)!!.youtubeID)
        assertEquals(id, db.youTubeVideoDao().getById(vid)!!.sermonId)
        assertNull(db.youTubeVideoDao().getById(vid)!!.deletedAt)
    }

    @Test fun clearedFieldsAreDeletedInFirestore() = runTest {
        // IOS_ISSUES K1: iOS leaves nil fields out of a merge write, so the old value survives remotely.
        val s = sermons.createManual("Grace", "Romans 8", "", null, zone)
        val server = FakeFirestore()
        server.apply(WireEncoder.sermon(db.sermonDao().getById(s.id)!!, "uid"))
        assertEquals(45L, server.get("sermons", s.id)!!["bookOrd"])
        sermons.update(s.id, SermonEdit("Grace", LocalDate.of(2026, 10, 28), "", "", "", "", "", ""))
        val write = WireEncoder.sermon(db.sermonDao().getById(s.id)!!, "uid")
        assertEquals(WireValue.DeleteField, write.data["bookOrd"])
        server.apply(write)
        assertFalse(server.get("sermons", s.id)!!.containsKey("bookOrd"))
        assertFalse(server.get("sermons", s.id)!!.containsKey("chapterRange"))
    }

    // MARK: - Delete and merge

    private suspend fun noteOn(sermonId: String, text: String): NoteEntity {
        val n = NoteEntity(
            id = com.stoneofhope.stoneofhope.data.model.ObjectIds.newId(), noteType = NoteType.Note, content = text,
            createdAt = clock.instant(), updatedAt = clock.instant(), sermonId = sermonId,
        )
        db.noteDao().upsert(n)
        return n
    }

    @Test fun deleteTombstonesNotesCommentsPinLogsAndIndex() = runTest {
        web.info = OEmbedInfo("Grace: Romans 5 | Lee", null, null, null)
        val id = (sermons.createFromYouTube(link, zone = zone) as SermonCreateResult.Created).sermonId
        val note = noteOn(id, "hello")
        actions.addComment(note.id, "a comment", zone)
        actions.setPinned(note.id, true, zone = zone)
        assertNotNull(db.pinLogDao().getByNoteId(note.id))

        sermons.delete(id)
        assertNotNull(db.sermonDao().getById(id)!!.deletedAt)
        assertTrue(db.noteDao().getById(note.id)!!.isDeleted)
        assertTrue(db.noteCommentDao().activeForNote(note.id).isEmpty())
        // IOS_ISSUES K7.
        assertNull(db.pinLogDao().getByNoteId(note.id))
        assertNotNull(db.youTubeVideoDao().getById(vid)!!.deletedAt)
    }

    @Test fun mergeMovesNotesAndFillsTheTarget() = runTest {
        web.info = OEmbedInfo("Grace: Romans 5 | Lee", null, null, null)
        val source = (sermons.createFromYouTube(link, zone = zone) as SermonCreateResult.Created).sermonId
        val target = sermons.createManual("Church copy", "", "", null, zone).id
        val note = noteOn(source, "my note")
        actions.addComment(note.id, "comment", zone)

        assertTrue(sermons.merge(source, target))
        assertEquals(target, db.noteDao().getById(note.id)!!.sermonId)
        assertTrue(db.noteDao().getById(note.id)!!.needsSync)
        val comment: NoteCommentEntity = db.noteCommentDao().activeForNote(note.id).single()
        assertEquals(target, comment.sermonId)
        val gone = db.sermonDao().getById(source)!!
        assertNotNull(gone.deletedAt)
        assertNull(gone.seriesId)
        // IOS_ISSUES K9: the empty target takes the video and the reference; K7: the index row follows.
        val t = db.sermonDao().getById(target)!!
        assertEquals(vid, t.youtubeID)
        assertEquals("Romans 5", t.chapterRange)
        assertEquals("Church copy", t.title)
        assertEquals(target, db.youTubeVideoDao().getById(vid)!!.sermonId)
        assertFalse(sermons.merge(source, target))
    }

    // MARK: - Notes on the sermon page

    @Test fun sermonPageNotesCarryTheSermon() = runTest {
        val s = sermons.createManual("Grace", "Genesis 1", "", null, zone)
        val genesis1 = BookChapter(1, 1)
        sermons.saveNote(s.id, "plain thought", "Genesis 1", genesis1, zone)
        sermons.saveNote(s.id, "v3 let there be light", "Genesis 1", genesis1, zone)
        val list = allNotes()
        val plain = list.single { it.content == "plain thought" }
        assertEquals(s.id, plain.sermonId)
        assertEquals(listOf(1, 1, null), listOf(plain.bookOrd, plain.chapterOrd, plain.verseStart))
        val anchor = list.single { it.noteType == NoteType.BibleVerse }
        val reflection = list.single { it.content == "Let there be light" }
        assertEquals(listOf(s.id, s.id), listOf(anchor.sermonId, reflection.sermonId))
        assertEquals(3, anchor.verseStart)

        // Script text: linked, but not put on the chapter.
        sermons.saveScriptText(s.id, "[0:05] some words", genesis1, zone)
        val script = allNotes().single { it.content == "[0:05] some words" }
        assertEquals(s.id, script.sermonId)
        assertNull(script.bookOrd)
    }

    // MARK: - Videos

    @Test fun videoFromOEmbedAnchoredByTitleOrReference() = runTest {
        web.info = OEmbedInfo("\"Where Are You?\" Genesis 3:9", "Channel", null, "https://i.ytimg.com/vi/$vid/hqdefault.jpg")
        assertEquals(VideoCreateResult.Created(vid), videos.create(link, null, zone))
        val v = db.youTubeVideoDao().getById(vid)!!
        assertEquals("Channel", v.channelName)
        assertNull(v.channelId)
        assertNull(v.sermonId)
        assertEquals(listOf(1, 3), listOf(v.bookOrd, v.chapterOrd))
        assertEquals(clock.instant(), v.createdAt)

        assertEquals(VideoCreateResult.Existing(vid), videos.create("https://www.youtube.com/watch?v=$vid", "John 3", zone))
        assertEquals(listOf(43, 3), db.youTubeVideoDao().getById(vid)!!.let { listOf(it.bookOrd, it.chapterOrd) })
    }

    @Test fun videoLinkOfASermonOpensTheSermon() = runTest {
        web.info = OEmbedInfo("Grace: Romans 5 | Lee", null, null, null)
        val id = (sermons.createFromYouTube(link, zone = zone) as SermonCreateResult.Created).sermonId
        assertEquals(VideoCreateResult.ExistingSermon(id), videos.create(link, null, zone))
        // A deleted sermon's index row becomes a plain video again (iOS `resolveStaleSermonLink`).
        sermons.delete(id)
        assertEquals(VideoCreateResult.Created(vid), videos.create(link, null, zone))
        assertNull(db.youTubeVideoDao().getById(vid)!!.sermonId)
    }

    @Test fun reAddingADeletedVideoDatesItToday() = runTest {
        web.info = OEmbedInfo("Old title", null, null, null)
        videos.create(link, null, zone)
        videos.delete(vid)
        clock.now = Instant.parse("2026-10-30T02:00:00Z")
        web.info = OEmbedInfo("New title", null, null, null)
        // IOS_ISSUES K8: iOS revives the row with its first date, so today's activity never shows it.
        assertEquals(VideoCreateResult.Created(vid), videos.create(link, null, zone))
        val v = db.youTubeVideoDao().getById(vid)!!
        assertNull(v.deletedAt)
        assertEquals(clock.instant(), v.createdAt)
        assertEquals("New title", v.title)
    }

    @Test fun deletingAVideoTombstonesItsNotes() = runTest {
        web.info = OEmbedInfo("T", null, null, null)
        videos.create(link, null, zone)
        videos.saveNote(vid, "watching", null, null, zone)
        val note = allNotes().single()
        assertEquals(vid, note.youtubeID)
        videos.delete(vid)
        assertTrue(allNotes().isEmpty())
        assertNotNull(db.youTubeVideoDao().getById(vid)!!.deletedAt)
    }
}
