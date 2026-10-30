package com.stoneofhope.stoneofhope.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.text.BibleTextDatabase
import com.stoneofhope.stoneofhope.data.bible.text.BibleVerseReader
import com.stoneofhope.stoneofhope.data.bible.text.UnavailableOnDemandVerseSource
import com.stoneofhope.stoneofhope.data.flashcard.FlashCardAddStatus
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.MutableClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Port of the flash card half of iOS `NotesManager` (`addFlashCards`, `addFlashCard(fromBibleVerseNoteId:)`, archive, delete). */
@RunWith(AndroidJUnit4::class)
class FlashCardRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(Instant.parse("2026-10-28T02:00:00Z"))
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = context.getSharedPreferences("flash-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val versions = BibleVersionPreferences(prefs) { BibleLanguage.English }.apply { setSelectedVersions(listOf("KJV")) }
    private val bibleText: BibleTextDatabase = run {
        context.deleteDatabase("flash-test-bible.db")
        Room.databaseBuilder(context, BibleTextDatabase::class.java, "flash-test-bible.db")
            .createFromAsset(BibleTextDatabase.ASSET_PATH).allowMainThreadQueries().build()
    }
    private val notes = NoteRepository(
        db, clock, UserVerseManager(db.userVerseDao()),
        BibleVerseReader(bibleText.verseDao(), db.bibleCacheDao(), UnavailableOnDemandVerseSource()), versions,
    )
    private val cards = FlashCardRepository(db, clock, notes, versions)

    @After
    fun tearDown() {
        db.close()
        bibleText.close()
    }

    private suspend fun live(): List<FlashCardEntity> = cards.observeLive().first()
    private fun later() { clock.now = clock.now.plusSeconds(3600) }

    @Test fun addsOneCardPerSelectedVersionWithTheRangeOnly() = runTest {
        versions.setSelectedVersions(listOf("KJV", "CUV"))
        val result = cards.add("John 3:16", "  love  ")
        assertEquals(FlashCardAddStatus.Created, result.status)
        assertTrue(result.skippedVersions.isEmpty())
        val all = live().sortedBy { it.versionAbbr }
        assertEquals(listOf("CUV", "KJV"), all.map { it.versionAbbr })
        with(all.first { it.versionAbbr == "KJV" }) {
            assertEquals(43, bookOrd); assertEquals(3, chapterOrd); assertEquals(16, verseStart)
            assertNull(verseEnd)
            assertEquals("VS:43:3:16-16", refKey)
            assertEquals("love", topic)
            assertEquals(clock.now, createdAt); assertEquals(clock.now, updatedAt)
            assertTrue(needsSync); assertEquals(false, isArchived); assertNull(deletedAt)
        }
        assertNotNull(db.userVerseDao().getById(UserVerseEntity.makeId("KJV", 43, 3, 16)))
        assertNotNull(db.userVerseDao().getById(UserVerseEntity.makeId("CUV", 43, 3, 16)))
    }

    @Test fun rangeKeepsVerseEndAndKey() = runTest {
        cards.add("John 3:16-17", null)
        with(live().single()) {
            assertEquals(17, verseEnd); assertEquals("VS:43:3:16-17", refKey); assertNull(topic)
        }
    }

    @Test fun chapterOnlyOrUnknownReferenceFails() = runTest {
        assertEquals(FlashCardAddStatus.Failed, cards.add("John 3", null).status)
        assertEquals(FlashCardAddStatus.Failed, cards.add("nonsense", null).status)
        assertTrue(live().isEmpty())
    }

    @Test fun versionWithoutTextIsSkippedAndReported() = runTest {
        versions.setSelectedVersions(listOf("NIV", "KJV"))
        val result = cards.add("John 3:16", null)
        assertEquals(FlashCardAddStatus.Created, result.status)
        assertEquals(listOf("NIV"), result.skippedVersions)
        assertEquals(listOf("KJV"), live().map { it.versionAbbr })
    }

    @Test fun onlyVersionWithoutTextFails() = runTest {
        versions.setSelectedVersions(listOf("NIV"))
        assertEquals(FlashCardAddStatus.Failed, cards.add("John 3:16", null).status)
        assertTrue(live().isEmpty())
    }

    @Test fun existingActiveCardIsOnlyBumped() = runTest {
        cards.add("John 3:16", "first")
        val before = live().single()
        later()
        val result = cards.add("John 3:16", "second")
        assertEquals(FlashCardAddStatus.AlreadyActive, result.status)
        val after = live().single()
        assertEquals(before.id, after.id)
        assertEquals("first", after.topic)
        assertEquals(clock.now, after.updatedAt)
        assertEquals(before.createdAt, after.createdAt)
    }

    @Test fun archivedCardIsRestored() = runTest {
        cards.add("John 3:16", null)
        val id = live().single().id
        cards.toggleArchive(id)
        later()
        assertEquals(FlashCardAddStatus.RestoredToActive, cards.add("John 3:16", null).status)
        with(live().single()) { assertEquals(id, this.id); assertEquals(false, isArchived); assertEquals(clock.now, updatedAt) }
    }

    @Test fun archiveToggleStampsUpdatedAtAndSync() = runTest {
        cards.add("John 3:16", null)
        val card = live().single()
        db.flashCardDao().upsert(card.copy(needsSync = false))
        later()
        cards.toggleArchive(card.id)
        with(db.flashCardDao().getById(card.id)!!) { assertTrue(isArchived); assertTrue(needsSync); assertEquals(clock.now, updatedAt) }
        cards.toggleArchive(card.id)
        assertEquals(false, db.flashCardDao().getById(card.id)!!.isArchived)
    }

    @Test fun deleteIsASoftDeleteAndReAddingMakesANewCard() = runTest {
        cards.add("John 3:16", null)
        val old = live().single()
        later()
        cards.delete(old.id)
        with(db.flashCardDao().getById(old.id)!!) { assertEquals(clock.now, deletedAt); assertEquals(clock.now, updatedAt); assertTrue(needsSync) }
        assertTrue(live().isEmpty())
        assertEquals(FlashCardAddStatus.Created, cards.add("John 3:16", null).status)
        assertNotEquals(old.id, live().single().id)
    }

    @Test fun fromBibleVerseNoteUsesTheNotesOwnVersion() = runTest {
        versions.setSelectedVersions(listOf("CUV", "KJV"))
        val saved = notes.saveVerseReference(43, 3, 16, 16, "KJV", mapOf("KJV" to mapOf(16 to "For God so loved the world")), zone = ZoneId.of("UTC"))!!
        assertEquals(FlashCardAddStatus.Created, cards.addFromBibleVerseNote(saved.anchor.id).status)
        with(live().single()) { assertEquals("KJV", versionAbbr); assertEquals("VS:43:3:16-16", refKey); assertEquals(saved.anchor.verseEnd, verseEnd) }
        later()
        assertEquals(FlashCardAddStatus.AlreadyActive, cards.addFromBibleVerseNote(saved.anchor.id).status)
        cards.toggleArchive(live().single().id)
        assertEquals(FlashCardAddStatus.RestoredToActive, cards.addFromBibleVerseNote(saved.anchor.id).status)
        assertEquals(1, live().size)
    }

    @Test fun fromNoteThatIsNotAVerseAnchorFails() = runTest {
        val note = notes.savePrayerNote("pray", null, null)!!
        assertEquals(FlashCardAddStatus.Failed, cards.addFromBibleVerseNote(note.id).status)
        assertEquals(FlashCardAddStatus.Failed, cards.addFromBibleVerseNote("nope").status)
    }
}
