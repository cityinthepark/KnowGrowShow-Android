package com.stoneofhope.stoneofhope.data.bible.text

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.DownloadedVerseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Port of iOS `BibleVerseRangeReaderTests`, run against the real generated asset (the same file the
 * app ships), so a Room schema mismatch or a broken build-time import fails here.
 */
@RunWith(AndroidJUnit4::class)
class BibleVerseReaderTest {

    private lateinit var bible: BibleTextDatabase
    private lateinit var app: AppDatabase
    private val onDemand = FakeOnDemand()
    private lateinit var reader: BibleVerseReader

    class FakeOnDemand : OnDemandVerseSource {
        val texts = mutableMapOf<Int, String>()
        var calls = 0
        override suspend fun verses(versionAbbr: String, bookOrd: Int, chapterOrd: Int, verseRange: IntRange): Map<Int, String> {
            calls++
            return texts.filterKeys { it in verseRange }
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("reader-test.db")
        bible = Room.databaseBuilder(context, BibleTextDatabase::class.java, "reader-test.db")
            .createFromAsset(BibleTextDatabase.ASSET_PATH).allowMainThreadQueries().build()
        app = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        reader = BibleVerseReader(bible.verseDao(), app.bibleCacheDao(), onDemand)
    }

    @After
    fun tearDown() {
        bible.close()
        app.close()
    }

    @Test fun bundledAssetHasBothVersions() = runTest {
        assertEquals(31_102, bible.verseDao().count("KJV"))
        assertEquals(31_100, bible.verseDao().count("CUV"))
    }

    @Test fun kjvJohn316FromBundle() = runTest {
        val r = reader.resolve("KJV", 43, 3, 16..16)
        val text = "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life."
        assertEquals(listOf(VerseLine(16, "3:16 $text", text)), r.lines)
        assertEquals("3:16 $text", r.mergedText)
        assertEquals(0, onDemand.calls)
    }

    @Test fun cuvCaseInsensitiveQueryFindsBulkRows() = runTest {
        val r = reader.resolve("cuv", 43, 3, 16..17)
        assertEquals("神愛世人，甚至將他的獨生子賜給他們，叫一切信他的，不至滅亡，反得永生。", r.lines[0].content)
        assertTrue(r.mergedText.startsWith("3:16 神愛世人") && r.mergedText.contains(" 3:17 "))
    }

    @Test fun psalmsUseNewlineSeparator() = runTest {
        assertEquals(
            "23:1 The Lord is my shepherd; I shall not want.\n23:2 He maketh me to lie down in green pastures: he leadeth me beside the still waters.",
            reader.resolve("KJV", 19, 23, 1..2).mergedText,
        )
    }

    /** iOS stores the JSON text verbatim, trailing space included; frozen userVerses must match. */
    @Test fun kjvWhitespaceIsKeptVerbatim() = runTest {
        assertTrue(reader.resolve("KJV", 1, 1, 31..31).lines.single().content.endsWith("the sixth day. "))
    }

    @Test fun reversedRangeIsAscendingAndOutOfCanonIsEmpty() = runTest {
        assertEquals(listOf(1, 2, 3), reader.resolve("KJV", 1, 1, 3..1).lines.map { it.verseOrd })
        assertTrue(reader.resolve("KJV", 43, 3, 36..37).isEmpty)
        assertTrue(reader.resolve("KJV", 67, 1, 1..1).isEmpty)
    }

    @Test fun verseMissingFromCuvResolvesEmpty() = runTest {
        // CUV has no John 7:53; the merged block skips it instead of printing "7:53 ".
        val r = reader.resolve("CUV", 43, 7, 52..53)
        assertEquals("", r.lines[1].content)
        assertTrue(r.mergedText.startsWith("7:52 ") && !r.mergedText.contains("7:53"))
        assertEquals(0, onDemand.calls)
    }

    @Test fun onDemandVersionIgnoresDownloadedRowsAndUsesSource() = runTest {
        app.bibleCacheDao().upsertVerses(listOf(DownloadedVerseEntity("NIV_43_3_16", "NIV", 43, 3, 16, "FROM-DOWNLOAD")))
        onDemand.texts[16] = "FROM-API"
        val r = reader.resolve("NIV", 43, 3, 16..16)
        assertEquals("FROM-API", r.lines.single().content)
        assertEquals(1, onDemand.calls)
    }

    @Test fun unavailableOnDemandSourceYieldsNoText() = runTest {
        val r = BibleVerseReader(bible.verseDao(), app.bibleCacheDao(), UnavailableOnDemandVerseSource()).resolve("NKJV", 43, 3, 16..16)
        assertTrue(r.isEmpty)
        assertEquals("", r.lines.single().content)
    }

    @Test fun downloadedRowsFillABulkVersion() = runTest {
        // A future downloaded KJV revision would live in AppDatabase.downloadedVerses (iOS DownloadedVerse).
        app.bibleCacheDao().upsertVerses(listOf(DownloadedVerseEntity("CUV_43_7_53", "CUV", 43, 7, 53, "DOWNLOADED")))
        assertEquals("DOWNLOADED", reader.resolve("CUV", 43, 7, 53..53).lines.single().content)
    }
}
