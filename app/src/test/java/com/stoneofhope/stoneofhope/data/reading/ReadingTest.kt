package com.stoneofhope.stoneofhope.data.reading

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.BookProgressEntity
import com.stoneofhope.stoneofhope.data.local.entity.ChapterLogEntity
import com.stoneofhope.stoneofhope.data.sync.MutableClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** iOS `BibleReadingManager` / `BookCarouselView` rules, against real Room. */
@RunWith(AndroidJUnit4::class)
class ReadingTest {

    private val zone = ZoneId.of("America/Los_Angeles")
    private val clock = MutableClock(Instant.parse("2026-10-26T18:00:00Z")) // 11:00 local, Oct 26
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    private val repo = ReadingRepository(db, clock)
    private val today = LocalDate.of(2026, 10, 26)
    private val startOfToday = today.atStartOfDay(zone).toInstant()
    private val john = 43

    @After
    fun tearDown() = db.close()

    private suspend fun progress() = ReadingModel.canonical(db.bookProgressDao().activeFor(john))
    private suspend fun logs(ch: Int) = db.chapterLogDao().activeFor(john, ch)

    @Test
    fun bookMetadataMatchesIos() {
        assertEquals(66, BibleBooks.all.size)
        assertEquals("John", BibleBooks.name(43, BibleLanguage.English))
        assertEquals("约翰福音", BibleBooks.name(43, BibleLanguage.ChineseSimplified))
        assertEquals("約翰福音", BibleBooks.name(43, BibleLanguage.ChineseTraditional))
        assertEquals(150, BibleBooks.book(19)!!.chapterCount)
        assertEquals(1, BibleBooks.book(31)!!.chapterCount) // Obadiah
        assertEquals("?", BibleBooks.name(67))
        assertEquals(BibleLanguage.ChineseTraditional, BibleLanguage.current(Locale.forLanguageTag("zh-Hant-TW")))
        assertEquals(BibleLanguage.ChineseTraditional, BibleLanguage.current(Locale.forLanguageTag("zh-HK")))
        assertEquals(BibleLanguage.ChineseSimplified, BibleLanguage.current(Locale.forLanguageTag("zh-CN")))
        assertEquals(BibleLanguage.English, BibleLanguage.current(Locale.forLanguageTag("en-US")))
    }

    @Test
    fun firstReadStartsACycleAndLogsTodayAtLocalMidnight() = runTest {
        repo.toggleChapterRead(john, 3, zone)
        val p = progress()!!
        assertEquals(21, p.readChapters.size)
        assertEquals(1, p.readChapters[2])
        assertEquals(startOfToday, p.createdOn)
        assertEquals(clock.now, p.lastUpdatedOn)
        assertNull(p.completedOn)
        assertTrue(p.needsSync)
        val log = logs(3).single()
        assertEquals(startOfToday, log.readOn)
        assertTrue(log.needsSync)
    }

    @Test
    fun secondTapTheSameDayUnreadsAndSoftDeletesEmptyCycle() = runTest {
        repo.toggleChapterRead(john, 3, zone)
        clock.now = clock.now.plusSeconds(60)
        repo.toggleChapterRead(john, 3, zone)
        assertTrue(logs(3).isEmpty())
        // The only read chapter was undone → the whole cycle is soft-deleted (iOS "allZero").
        assertNull(progress())
        val dirtyRows = db.bookProgressDao().dirty()
        assertNotNull(dirtyRows.single().deletedAt)
    }

    @Test
    fun unreadTodayKeepsCycleFlagWhenReadEarlierInCycle() = runTest {
        // Read John 3 yesterday, again today, then undo today's read.
        clock.now = Instant.parse("2026-10-25T18:00:00Z")
        repo.toggleChapterRead(john, 3, zone)
        clock.now = Instant.parse("2026-10-26T18:00:00Z")
        repo.toggleChapterRead(john, 3, zone)
        assertEquals(2, logs(3).size)
        repo.toggleChapterRead(john, 3, zone)
        assertEquals(1, logs(3).size)
        assertEquals("still read this cycle", 1, progress()!!.readChapters[2])
    }

    @Test
    fun readingEveryChapterCompletesTheBook() = runTest {
        repo.toggleChapterRead(31, 1, zone) // Obadiah has one chapter
        assertEquals(startOfToday, ReadingModel.canonical(db.bookProgressDao().activeFor(31))!!.completedOn)
    }

    @Test
    fun outOfRangeChapterIsIgnored() = runTest {
        repo.toggleChapterRead(john, 22, zone)
        repo.toggleChapterRead(99, 1, zone)
        assertTrue(db.bookProgressDao().dirty().isEmpty())
    }

    @Test
    fun canonicalPicksLatestUpdateThenSyncThenId() {
        val t = Instant.parse("2026-10-01T00:00:00Z")
        fun row(id: String, updated: Instant, synced: Instant? = null) =
            BookProgressEntity(id = id, bookOrd = john, readChapters = listOf(1), createdOn = t, lastUpdatedOn = updated, lastSyncedAt = synced)
        assertEquals("b", ReadingModel.canonical(listOf(row("a", t), row("b", t.plusSeconds(1))))!!.id)
        assertEquals("a", ReadingModel.canonical(listOf(row("a", t, t.plusSeconds(5)), row("b", t, t)))!!.id)
        assertEquals("b", ReadingModel.canonical(listOf(row("a", t), row("b", t)))!!.id)
        assertNull(ReadingModel.canonical(listOf(row("a", t).copy(deletedAt = t))))
    }

    @Test
    fun chipWindowCentresOnLastReadAndClipsAtEdges() {
        assertEquals(listOf(1, 2, 3), ReadingModel.chipWindow(null, 21))
        assertEquals(listOf(1, 2, 3), ReadingModel.chipWindow(1, 21))
        assertEquals(listOf(9, 10, 11), ReadingModel.chipWindow(10, 21))
        assertEquals(listOf(19, 20, 21), ReadingModel.chipWindow(21, 21))
        assertEquals(listOf(1), ReadingModel.chipWindow(1, 1))
        assertEquals(listOf(1, 2), ReadingModel.chipWindow(2, 2))
        assertEquals(null, ReadingModel.readCountBadge(1))
        assertEquals("5", ReadingModel.readCountBadge(5))
        assertEquals("99+", ReadingModel.readCountBadge(120))
    }

    @Test
    fun carouselStatesAndOrder() = runTest {
        clock.now = Instant.parse("2026-10-25T18:00:00Z")
        repo.toggleChapterRead(john, 3, zone) // yesterday
        repo.toggleChapterRead(1, 1, zone) // Genesis, yesterday
        clock.now = Instant.parse("2026-10-26T18:00:00Z")
        repo.toggleChapterRead(john, 4, zone) // today → John is the most recent book

        val byLastRead = repo.observeCarousel(today, zone, ReadingModel.Sort.LastRead).first()
        assertEquals(listOf(john, 1), byLastRead.map { it.bookOrd })
        val johnCard = byLastRead.first()
        assertEquals(2, johnCard.chaptersRead)
        assertEquals(listOf(3, 4, 5), johnCard.chips.map { it.chapter })
        assertEquals(
            listOf(ReadingModel.ChipState.Read, ReadingModel.ChipState.ReadToday, ReadingModel.ChipState.Unread),
            johnCard.chips.map { it.state },
        )
        assertEquals(listOf(1, john), repo.observeCarousel(today, zone, ReadingModel.Sort.BookOrder).first().map { it.bookOrd })
    }

    @Test
    fun chipWindowStaysPutWhileTapping() = runTest {
        val windows = mutableMapOf<Int, Int>()
        repo.toggleChapterRead(john, 10, zone)
        assertEquals(listOf(9, 10, 11), repo.observeCarousel(today, zone, ReadingModel.Sort.LastRead, windows).first().single().chips.map { it.chapter })
        // Reading 11 would re-centre on 11 (10, 11, 12); the window must not slide under the finger.
        repo.toggleChapterRead(john, 11, zone)
        val chips = repo.observeCarousel(today, zone, ReadingModel.Sort.LastRead, windows).first().single().chips
        assertEquals(listOf(9, 10, 11), chips.map { it.chapter })
        assertEquals(ReadingModel.ChipState.ReadToday, chips.last().state)
    }

    @Test
    fun todayLogsAreMatchedByLocalDayNotExactTimestamp() = runTest {
        // A log written by a device in another timezone (start of *its* day) still counts as today.
        db.chapterLogDao().upsert(ChapterLogEntity(id = "64a0000000000000000000f1", bookOrd = john, chapter = 1, readOn = Instant.parse("2026-10-26T04:00:00Z")))
        val logs = repo.observeLogsOn(today, ZoneId.of("America/New_York")).first()
        assertEquals(1, logs.size)
    }
}
