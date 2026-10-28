package com.stoneofhope.stoneofhope.data.repository

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
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.MutableClock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The chapter page's saves: iOS `addNote(_:to:bibleRefText:separatedReference: true)` (chapter-anchored
 * note sheet) and `ChapterNoteDetailsView`'s reflection / prayer, with IOS_ISSUES H5.
 */
@RunWith(AndroidJUnit4::class)
class ChapterNoteSaveTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = MutableClock(Instant.parse("2026-10-27T02:00:00Z")) // 10:00 local
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = context.getSharedPreferences("chapter-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val versions = BibleVersionPreferences(prefs) { BibleLanguage.English }
    private val bibleText = run {
        context.deleteDatabase("chapter-test-bible.db")
        Room.databaseBuilder(context, BibleTextDatabase::class.java, "chapter-test-bible.db")
            .createFromAsset(BibleTextDatabase.ASSET_PATH).allowMainThreadQueries().build()
    }
    private val repo = NoteRepository(
        db, clock, UserVerseManager(db.userVerseDao()),
        BibleVerseReader(bibleText.verseDao(), db.bibleCacheDao(), UnavailableOnDemandVerseSource()), versions,
    )
    private val defaultLocale = Locale.getDefault()
    private val genesis2 = BookChapter(1, 2)

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        db.close()
        bibleText.close()
    }

    private suspend fun notes(): List<NoteEntity> = db.noteDao().observeActive().first()
    private suspend fun anchors() = notes().filter { it.noteType == NoteType.BibleVerse }
    private suspend fun plainNotes() = notes().filter { it.noteType == NoteType.Note }
    private fun NoteEntity.place() = listOf(bookOrd, chapterOrd, verseStart, verseEnd)

    private suspend fun saveOnGenesis2(content: String, ref: String?) =
        repo.saveNoteFromContent(content, ref, anchorChapter = genesis2, zone = zone)

    // MARK: - Chapter note sheet

    @Test fun prefilledChapterSavesAChapterNoteWithIosFields() = runTest {
        val note = (saveOnGenesis2("  a thought  ", "Genesis 2") as NoteInputSaveResult.Plain).note
        with(note) {
            assertEquals(NoteType.Note, noteType)
            assertEquals("a thought", content)
            assertEquals(listOf(1, 2, null, null), place())
            assertEquals("CH:1:2", refKey)
            assertEquals("CH:1:2", chapterKey)
            assertNull(versionAbbr)
            assertTrue(needsSync)
        }
        assertTrue(anchors().isEmpty())
    }

    @Test fun clearedFieldStillAnchorsToThePagesChapter() = runTest {
        assertEquals(listOf(1, 2, null, null), (saveOnGenesis2("a thought", null) as NoteInputSaveResult.Plain).note.place())
        assertEquals(listOf(1, 2, null, null), (saveOnGenesis2("another", "zzz 9") as NoteInputSaveResult.Plain).note.place())
    }

    /** H5: iOS would put it on Genesis 2 while "vN" in the same save follows the field. */
    @Test fun aChapterTypedIntoTheFieldWins() = runTest {
        val note = (saveOnGenesis2("a thought", "Exodus 3") as NoteInputSaveResult.Plain).note
        assertEquals(listOf(2, 3, null, null), note.place())
        assertEquals("CH:2:3", note.refKey)
    }

    @Test fun verseShortcutWithAnEmptyFieldUsesThePagesChapter() = runTest {
        saveOnGenesis2("v7 formed man of the dust", null)
        assertEquals(listOf(1, 2, 7, 7), anchors().single().place())
        assertEquals(listOf(1, 2, 7, 7), plainNotes().single().place())
        assertTrue(db.userVerseDao().getById("KJV_1_2_7")!!.content.startsWith("And the Lord God formed man"))
    }

    @Test fun verseShortcutFollowsTheFieldsChapter() = runTest {
        saveOnGenesis2("v14 I AM", "Exodus 3")
        assertEquals(listOf(2, 3, 14, 14), anchors().single().place())
    }

    @Test fun verseLevelFieldSavesAnchorAndReflectionNotAChapterNote() = runTest {
        val result = (saveOnGenesis2("rest", "Genesis 2:2-3") as NoteInputSaveResult.Verse).result
        assertEquals(listOf(1, 2, 2, 3), result.anchor.place())
        assertEquals("rest", result.reflection!!.content)
        assertEquals(1, plainNotes().size)
    }

    @Test fun nothingToSaveWritesNothing() = runTest {
        assertNull(saveOnGenesis2("   ", "Genesis 2"))
        assertTrue(notes().isEmpty())
    }

    @Test fun todayStillSavesAnUnanchoredNote() = runTest {
        val note = (repo.saveNoteFromContent("plain", null, zone = zone) as NoteInputSaveResult.Plain).note
        assertEquals(listOf(null, null, null, null), note.place())
        assertNull(note.refKey)
    }

    /** "创世记 2:7" (app language) with KJV only: book names always also match in English and Chinese. */
    @Test fun verseShortcutWorksWhenTheAppLanguageIsNotAVersionLanguage() = runTest {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        saveOnGenesis2("v7 formed", null)
        assertEquals(listOf(1, 2, 7, 7), anchors().single().place())
        assertEquals("Formed", plainNotes().single().content)
    }

    // MARK: - Verse page (ChapterNoteDetailsView)

    @Test fun reflectionOnAVerseUsesTodaysPrimaryAnchor() = runTest {
        val first = repo.saveVerseReflection(1, 2, 7, 7, " first ", zone)!!
        assertTrue(first.didCreateAnchor)
        assertEquals("KJV", first.anchor.versionAbbr)
        assertEquals("first", first.reflection!!.content)
        val second = repo.saveVerseReflection(1, 2, 7, 7, "second", zone)!!
        assertEquals(first.anchor.id, second.anchor.id)
        clock.now = clock.now + Duration.ofDays(1)
        val nextDay = repo.saveVerseReflection(1, 2, 7, 7, "third", zone)!!
        assertNotEquals(first.anchor.id, nextDay.anchor.id)
        assertNull(repo.saveVerseReflection(1, 2, 7, 7, "  ", zone))
        assertEquals(2, anchors().size)
        assertEquals(3, plainNotes().size)
    }

    @Test fun prayerOnAVerseStoresItsVersesDirectly() = runTest {
        val prayer = repo.savePrayerOnVerses("  Lord, form us  ", 1, 2, 7, 8)!!
        with(prayer) {
            assertEquals(NoteType.Prayer, noteType)
            assertEquals("Lord, form us", content)
            assertEquals(listOf(1, 2, 7, 8), place())
            assertEquals("VS:1:2:7-8", refKey)
            assertEquals("CH:1:2", chapterKey)
            assertEquals("KJV", versionAbbr)
        }
        assertEquals(listOf("KJV_1_2_7", "KJV_1_2_8"), db.userVerseDao().dirty().map { it.id }.sorted())
        assertNull(repo.savePrayerOnVerses(" ", 1, 2, 7, 7))
    }
}
