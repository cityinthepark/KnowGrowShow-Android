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
import com.stoneofhope.stoneofhope.data.local.entity.DownloadedVerseEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.MutableClock
import java.time.Duration
import java.time.Instant
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

/**
 * Port of iOS `SaveVerseReferenceTests` and the save half of `UserVerseTests`, plus the note-sheet
 * priority rules of NOTE_INPUT_BIBLE_REFERENCE_DETECTION.md §3 / §5.1 / §5.5.
 */
@RunWith(AndroidJUnit4::class)
class VerseReferenceSaveTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = MutableClock(Instant.parse("2026-10-26T02:00:00Z")) // 10:00 local
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = context.getSharedPreferences("verse-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val versions = BibleVersionPreferences(prefs) { BibleLanguage.English }
    private var bibleText: BibleTextDatabase? = null

    /** [bundled]: the real shipped KJV/CUV asset; otherwise empty, so only seeded downloaded rows exist (like iOS tests). */
    private fun repo(bundled: Boolean = false): NoteRepository {
        val text = if (bundled) {
            context.deleteDatabase("save-test-bible.db")
            Room.databaseBuilder(context, BibleTextDatabase::class.java, "save-test-bible.db")
                .createFromAsset(BibleTextDatabase.ASSET_PATH).allowMainThreadQueries().build()
        } else {
            Room.inMemoryDatabaseBuilder(context, BibleTextDatabase::class.java).allowMainThreadQueries().build()
        }
        bibleText = text
        val reader = BibleVerseReader(text.verseDao(), db.bibleCacheDao(), UnavailableOnDemandVerseSource())
        return NoteRepository(db, clock, UserVerseManager(db.userVerseDao()), reader, versions)
    }

    @After
    fun tearDown() {
        db.close()
        bibleText?.close()
    }

    private val gen11 = "In the beginning God created the heaven and the earth."
    private val genesis11Contents = mapOf("KJV" to mapOf(1 to gen11))

    private suspend fun NoteRepository.saveGenesis11(reflection: String? = null, sermonId: String? = null, youtubeID: String? = null) =
        saveVerseReference(1, 1, 1, 1, "KJV", genesis11Contents, reflection, sermonId = sermonId, youtubeID = youtubeID, zone = zone)!!

    private suspend fun notes(): List<NoteEntity> = db.noteDao().observeActive().first()
    private suspend fun anchors() = notes().filter { it.noteType == NoteType.BibleVerse }
    private suspend fun reflections() = notes().filter { it.noteType == NoteType.Note }

    private suspend fun seedDownloaded(version: String, book: Int, chapter: Int, verse: Int, text: String) =
        db.bibleCacheDao().upsertVerses(listOf(DownloadedVerseEntity("${version}_${book}_${chapter}_$verse", version, book, chapter, verse, text)))

    // MARK: - saveVerseReference (iOS SaveVerseReferenceTests)

    @Test fun placeholderIsMiddleDot() = assertEquals("·", NoteVerseAnchor.PLACEHOLDER)

    @Test fun versePlusReflectionCreatesAnchorAndIndependentNote() = runTest {
        val result = repo().saveGenesis11("first thought")
        assertTrue(result.didCreateAnchor)
        with(result.anchor) {
            assertEquals(NoteType.BibleVerse, noteType)
            assertEquals(NoteVerseAnchor.PLACEHOLDER, content)
            assertEquals(listOf(1, 1, 1, 1), listOf(bookOrd, chapterOrd, verseStart, verseEnd))
            assertEquals("KJV", versionAbbr)
            // iOS code still writes the legacy keys (the contract says it no longer does).
            assertEquals("VS:1:1:1-1", refKey)
            assertEquals("CH:1:1", chapterKey)
            assertTrue(needsSync)
            assertEquals(clock.now, createdAt)
        }
        with(result.reflection!!) {
            assertEquals(NoteType.Note, noteType)
            assertEquals("first thought", content)
            assertEquals(listOf(1, 1, 1, 1), listOf(bookOrd, chapterOrd, verseStart, verseEnd))
            assertEquals("VS:1:1:1-1", refKey)
            assertEquals("CH:1:1", chapterKey)
            assertTrue(tagIds.isEmpty())
        }
        val uv = db.userVerseDao().getById("KJV_1_1_1")!!
        assertEquals(gen11, uv.content)
        assertTrue(uv.needsSync && uv.matchesIdInvariant)
        assertTrue(db.noteCommentDao().dirty().isEmpty())
    }

    @Test fun sameDaySameBucketDedupesAnchorAndAlwaysCreatesNewReflection() = runTest {
        val r = repo()
        val first = r.saveGenesis11("first thought")
        val second = r.saveGenesis11("second thought")
        assertTrue(first.didCreateAnchor)
        assertFalse(second.didCreateAnchor)
        assertEquals(first.anchor.id, second.anchor.id)
        assertEquals(1, anchors().size)
        assertEquals(setOf("first thought", "second thought"), reflections().map { it.content }.toSet())
    }

    @Test fun verseOnlyTwiceSameDayCreatesSingleAnchorAndNoReflection() = runTest {
        val r = repo()
        assertNull(r.saveGenesis11().reflection)
        assertFalse(r.saveGenesis11().didCreateAnchor)
        assertEquals(1, anchors().size)
        assertEquals(0, reflections().size)
    }

    @Test fun threeLinkBucketsCreateSeparateAnchorsOnTheSameDay() = runTest {
        val r = repo()
        val sermonA = "64a0000000000000000000a1"
        val sermonB = "64a0000000000000000000b1"
        r.saveGenesis11()
        r.saveGenesis11(sermonId = sermonA)
        r.saveGenesis11(sermonId = sermonB)
        r.saveGenesis11(youtubeID = " dQw4w9WgXcQ ")
        val all = anchors()
        assertEquals(4, all.size)
        assertEquals(1, all.count { it.sermonId == null && it.youtubeID == null })
        assertEquals("dQw4w9WgXcQ", all.single { it.youtubeID != null }.youtubeID)
        assertNull(all.single { it.youtubeID != null }.sermonId)
    }

    @Test fun sameSermonSameDayDoesNotDuplicateAnchor() = runTest {
        val r = repo()
        val sermon = "64a0000000000000000000a1"
        val first = r.saveGenesis11("a", sermonId = sermon)
        val second = r.saveGenesis11("b", sermonId = sermon)
        assertEquals(first.anchor.id, second.anchor.id)
        assertEquals(1, anchors().size)
        assertEquals(2, reflections().size)
        assertEquals(sermon, second.reflection!!.sermonId)
    }

    @Test fun nextLocalDayCreatesANewAnchor() = runTest {
        val r = repo()
        val first = r.saveGenesis11()
        clock.now = Instant.parse("2026-10-26T15:59:00Z") // 23:59 Shanghai, same day
        assertEquals(first.anchor.id, r.saveGenesis11().anchor.id)
        clock.now = Instant.parse("2026-10-26T16:01:00Z") // 00:01 Shanghai, next day (still Oct 26 UTC)
        val nextDay = r.saveGenesis11()
        assertTrue(nextDay.didCreateAnchor)
        assertEquals(2, anchors().size)
    }

    @Test fun differentVersionIsASeparateAnchor() = runTest {
        val r = repo()
        r.saveGenesis11()
        r.saveVerseReference(1, 1, 1, 1, "cuv", emptyMap(), zone = zone)
        assertEquals(2, anchors().size)
        // Version comparison is case-insensitive, like iOS.
        assertFalse(r.saveVerseReference(1, 1, 1, 1, "CUV", emptyMap(), zone = zone)!!.didCreateAnchor)
    }

    @Test fun softDeletedAnchorIsRestoredAsTodays() = runTest {
        val r = repo()
        val old = r.saveGenesis11().anchor
        db.noteDao().upsert(old.copy(deletedAt = clock.now, isDeleted = true, needsSync = true))
        clock.now = clock.now.plus(Duration.ofDays(3))
        val again = r.saveGenesis11()
        assertFalse(again.didCreateAnchor)
        with(db.noteDao().getById(old.id)!!) {
            assertNull(deletedAt)
            assertFalse(isDeleted)
            assertEquals(clock.now, createdAt)
            assertEquals(NoteVerseAnchor.PLACEHOLDER, content)
            assertTrue(needsSync)
        }
        assertEquals(1, anchors().size)
    }

    /** Android fix: iOS compares verseEnd exactly, so an anchor without verseEnd is duplicated. */
    @Test fun anchorWithoutVerseEndStillDedupes() = runTest {
        val r = repo()
        val legacy = NoteEntity(
            id = "64a0000000000000000000c1", noteType = NoteType.BibleVerse, content = NoteVerseAnchor.PLACEHOLDER,
            bookOrd = 1, chapterOrd = 1, verseStart = 1, verseEnd = null, versionAbbr = "KJV",
            createdAt = clock.now, updatedAt = clock.now,
        )
        db.noteDao().upsert(legacy)
        assertEquals(legacy.id, r.saveGenesis11().anchor.id)
    }

    @Test fun anchorCarriesTagButReflectionDoesNot() = runTest {
        val r = repo()
        db.tagDao().upsert(TagEntity(id = "64a0000000000000000000d1", name = "Hope", createdAt = clock.now))
        val result = r.saveVerseReference(1, 1, 1, 1, "KJV", genesis11Contents, "thought", tagName = "hope", zone = zone)!!
        assertEquals(listOf("64a0000000000000000000d1"), result.anchor.tagIds)
        assertTrue(result.reflection!!.tagIds.isEmpty())
    }

    // MARK: - userVerses (iOS UserVerseTests)

    @Test fun userVerseContentIsFrozenRestoredAndFilled() = runTest {
        val m = UserVerseManager(db.userVerseDao())
        val t = clock.now
        val created = m.getOrCreate("NKJV", 60, 1, 3, "Blessed be the God", t)
        assertEquals("NKJV_60_1_3", created.id)
        assertTrue(created.needsSync)
        assertEquals("Blessed be the God", m.getOrCreate("NKJV", 60, 1, 3, "OTHER", t).content)

        m.getOrCreate("KJV", 1, 1, 2, "  ", t)
        assertEquals("FILLED", m.getOrCreate("KJV", 1, 1, 2, "FILLED", t).content)

        db.userVerseDao().upsert(created.copy(deletedAt = t, isDeleted = true, needsSync = false))
        val restored = m.getOrCreate("NKJV", 60, 1, 3, "NEW", t.plusSeconds(5))
        assertEquals("Blessed be the God", restored.content)
        assertNull(restored.deletedAt)
        assertFalse(restored.isDeleted)
        assertTrue(restored.needsSync)
        assertEquals(t.plusSeconds(5), restored.updatedAt)
    }

    @Test fun resolvedContentsPrefersFrozenUserVerseOverDownloadedVerse() = runTest {
        val r = repo()
        UserVerseManager(db.userVerseDao()).getOrCreate("KJV", 1, 1, 1, "FROZEN-POOL", clock.now)
        seedDownloaded("KJV", 1, 1, 1, "FROM-DOWNLOAD")
        assertEquals("FROZEN-POOL", r.resolvedUserVerseContents(listOf("KJV"), 1, 1, 1, 1)["KJV"]!![1])
    }

    @Test fun resolvedContentsFallsThroughToDownloadedWhenPoolMisses() = runTest {
        val r = repo()
        seedDownloaded("KJV", 1, 1, 1, "FROM-DOWNLOAD")
        assertEquals("FROM-DOWNLOAD", r.resolvedUserVerseContents(listOf("KJV"), 1, 1, 1, 1)["KJV"]!![1])
    }

    @Test fun resolvedContentsIgnoresEmptyPoolRowAndRefetches() = runTest {
        val r = repo()
        UserVerseManager(db.userVerseDao()).getOrCreate("KJV", 1, 1, 1, "   ", clock.now)
        seedDownloaded("KJV", 1, 1, 1, "FROM-DOWNLOAD")
        assertEquals("FROM-DOWNLOAD", r.resolvedUserVerseContents(listOf("KJV"), 1, 1, 1, 1)["KJV"]!![1])
    }

    @Test fun resolvedContentsFillsOnlyMissingVersesInARange() = runTest {
        val r = repo()
        UserVerseManager(db.userVerseDao()).getOrCreate("KJV", 1, 1, 1, "FROZEN-1", clock.now)
        seedDownloaded("KJV", 1, 1, 1, "DOWNLOAD-1")
        seedDownloaded("KJV", 1, 1, 2, "DOWNLOAD-2")
        assertEquals(mapOf(1 to "FROZEN-1", 2 to "DOWNLOAD-2"), r.resolvedUserVerseContents(listOf("KJV"), 1, 1, 1, 2)["KJV"])
    }

    // MARK: - Note sheet (saveSeparatedNoteInput priority rules)

    @Test fun saveNoteFromContentUsesUnifiedEntryAndDedupes() = runTest {
        val r = repo()
        seedDownloaded("KJV", 1, 1, 1, gen11)
        r.saveNoteFromContent("Gen 1:1 first", null, zone = zone)
        r.saveNoteFromContent("Gen 1:1 second", null, zone = zone)
        assertEquals(1, anchors().size)
        assertEquals(NoteVerseAnchor.PLACEHOLDER, anchors().single().content)
        assertEquals(setOf("first", "second"), reflections().map { it.content }.toSet())
        assertEquals(gen11, db.userVerseDao().getById("KJV_1_1_1")!!.content)
    }

    /** iOS doc §7: the body's reference wins even when the field holds a different, valid one. */
    @Test fun bodyReferenceWinsOverReferenceField() = runTest {
        val r = repo(bundled = true)
        r.saveNoteFromContent("John 3:16 so loved", "Gen 1:1", zone = zone)
        assertEquals(listOf(43 to 3), anchors().map { it.bookOrd to it.chapterOrd })
        assertEquals("so loved", reflections().single().content)
        assertNull(db.userVerseDao().getById("KJV_1_1_1"))
        assertTrue(db.userVerseDao().getById("KJV_43_3_16")!!.content.startsWith("For God so loved"))
    }

    @Test fun verseLevelFieldSavesAnchorAndReflection() = runTest {
        val r = repo(bundled = true)
        val result = r.saveNoteFromContent("  my thought  ", "  创 1:1-2 ", zone = zone) as NoteInputSaveResult.Verse
        with(result.result.anchor) {
            assertEquals(listOf(1, 1, 1, 2), listOf(bookOrd, chapterOrd, verseStart, verseEnd))
            assertEquals("VS:1:1:1-2", refKey)
        }
        assertEquals("my thought", result.result.reflection!!.content)
        assertEquals(listOf("KJV_1_1_1", "KJV_1_1_2"), db.userVerseDao().dirty().map { it.id }.sorted())
    }

    @Test fun referenceOnlyWithEmptyBodySavesAnchorOnly() = runTest {
        val r = repo(bundled = true)
        r.saveNoteFromContent("   ", "Jude 5", zone = zone)
        assertEquals(listOf(65 to 5), anchors().map { it.bookOrd to it.verseStart })
        assertEquals(0, reflections().size)
    }

    @Test fun chapterOnlyFieldIsDroppedAndBodySavedPlain() = runTest {
        val r = repo(bundled = true)
        val plain = (r.saveNoteFromContent("a thought", "Genesis 1", zone = zone) as NoteInputSaveResult.Plain).note
        assertEquals("a thought", plain.content)
        assertNull(plain.bookOrd)
        assertNull(plain.refKey)
        assertNull(plain.chapterKey)
        assertEquals(0, anchors().size)
    }

    @Test fun verseShortcutPromotesChapterOnlyField() = runTest {
        val r = repo(bundled = true)
        r.saveNoteFromContent("v2 and the earth was without form", "Genesis 1", zone = zone)
        assertEquals(listOf(1, 1, 2, 2), anchors().single().let { listOf(it.bookOrd, it.chapterOrd, it.verseStart, it.verseEnd) })
        assertEquals("And the earth was without form", reflections().single().content)
    }

    /** Android fix: iOS would rewrite to "Genesis 1:99", drop it as invalid, and save "Hello" (losing "v99"). */
    @Test fun verseShortcutPastChapterEndKeepsTheBody() = runTest {
        val r = repo(bundled = true)
        r.saveNoteFromContent("v99 hello", "Genesis 1", zone = zone)
        assertEquals(0, anchors().size)
        assertEquals("v99 hello", reflections().single().content)
    }

    /** Android fix: iOS's body detector only knows the ASCII colon. */
    @Test fun fullWidthColonInBodyIsDetected() = runTest {
        val r = repo(bundled = true)
        r.saveNoteFromContent("创世记1：1 感恩", null, zone = zone)
        assertEquals(listOf(1 to 1), anchors().map { it.bookOrd to it.verseStart })
        assertEquals("感恩", reflections().single().content)
    }

    @Test fun referenceInsideProseIsNotDetected() = runTest {
        val r = repo(bundled = true)
        r.saveNoteFromContent("Today I read John 3:16", null, zone = zone)
        assertEquals(0, anchors().size)
        assertEquals("Today I read John 3:16", reflections().single().content)
    }

    @Test fun nothingToSaveWritesNothing() = runTest {
        val r = repo(bundled = true)
        assertNull(r.saveNoteFromContent("  ", "Genesis 1", zone = zone))
        assertNull(r.saveNoteFromContent("", "zzz 1:1", zone = zone))
        assertTrue(notes().isEmpty())
    }

    @Test fun everySelectedVersionGetsUserVersesAnchorUsesPrimary() = runTest {
        versions.setSelectedVersions(listOf("和合本", "KJV")) // display name maps to CUV, like iOS
        val r = repo(bundled = true)
        r.saveNoteFromContent("约 3:16 神爱世人", null, zone = zone)
        assertEquals("CUV", anchors().single().versionAbbr)
        assertTrue(db.userVerseDao().getById("CUV_43_3_16")!!.content.startsWith("神愛世人"))
        assertTrue(db.userVerseDao().getById("KJV_43_3_16")!!.content.startsWith("For God"))
    }

    /** Like iOS: a range touching a verse CUV lacks still gets that row, with empty content. */
    @Test fun verseMissingFromAVersionGetsAnEmptyRow() = runTest {
        versions.setSelectedVersions(listOf("KJV", "CUV"))
        val r = repo(bundled = true)
        r.saveNoteFromContent("", "John 7:52-53", zone = zone)
        assertEquals("", db.userVerseDao().getById("CUV_43_7_53")!!.content)
        assertTrue(db.userVerseDao().getById("KJV_43_7_53")!!.content.isNotEmpty())
    }

    @Test fun unavailableVersionStillSavesTheAnchorWithoutUserVerses() = runTest {
        versions.setSelectedVersions(listOf("NIV"))
        val r = repo(bundled = true)
        r.saveNoteFromContent("John 3:16 love", null, zone = zone)
        assertEquals("NIV", anchors().single().versionAbbr)
        assertEquals("love", reflections().single().content)
        assertTrue(db.userVerseDao().dirty().isEmpty())
    }

    @Test fun firstReadPicksTheDeviceLanguageDefault() {
        assertEquals(listOf("KJV"), versions.selectedVersions())
        val zh = BibleVersionPreferences(context.getSharedPreferences("verse-test-zh", Context.MODE_PRIVATE).apply { edit().clear().commit() }) {
            BibleLanguage.ChineseSimplified
        }
        assertEquals(listOf("CUV"), zh.selectedVersions())
        assertEquals("CUV", zh.primaryVersion())
    }

    // MARK: - Prayer (savePrayerNote)

    @Test fun prayerWithVerseReferenceStoresMetadataAndUserVerses() = runTest {
        val r = repo(bundled = true)
        val prayer = r.savePrayerNote("  Lord, John 3:16 is true  ", "Family", "Jn 3:16")!!
        with(prayer) {
            assertEquals(NoteType.Prayer, noteType)
            assertEquals("Lord, John 3:16 is true", content)
            assertEquals(listOf(43, 3, 16, 16), listOf(bookOrd, chapterOrd, verseStart, verseEnd))
            assertEquals("VS:43:3:16-16", refKey)
            assertEquals("CH:43:3", chapterKey)
            assertEquals("KJV", versionAbbr)
            assertEquals(1, tagIds.size)
        }
        assertTrue(db.userVerseDao().getById("KJV_43_3_16")!!.content.startsWith("For God"))
        assertEquals("the prayer body is never scanned for references", 1, notes().size)
    }

    @Test fun prayerWithChapterOnlyReferenceHasNoAnchor() = runTest {
        val r = repo(bundled = true)
        val prayer = r.savePrayerNote("Guide us", null, "John 3")!!
        assertNull(prayer.bookOrd)
        assertNull(prayer.refKey)
        assertNull(prayer.versionAbbr)
        assertNull(r.savePrayerNote("   ", null, "John 3:16"))
        assertTrue(db.userVerseDao().dirty().isEmpty())
    }

    @Test fun ensureUserVersesFillsMissingVersionOnAPulledNote() = runTest {
        val r = repo(bundled = true)
        val pulled = NoteEntity(
            id = "64a0000000000000000000e1", noteType = NoteType.Prayer, content = "pulled",
            bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 17, versionAbbr = "CUV",
            createdAt = clock.now, updatedAt = clock.now,
        )
        db.noteDao().upsert(pulled)
        r.ensureUserVerses(pulled.id)
        val ids = db.userVerseDao().dirty().map(UserVerseEntity::id).sorted()
        // Selected (KJV) plus the note's own version (CUV).
        assertEquals(listOf("CUV_43_3_16", "CUV_43_3_17", "KJV_43_3_16", "KJV_43_3_17"), ids)
        assertFalse(db.noteDao().getById(pulled.id)!!.needsSync)
    }
}
