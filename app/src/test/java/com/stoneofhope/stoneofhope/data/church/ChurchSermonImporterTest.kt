package com.stoneofhope.stoneofhope.data.church

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private class FakeFeed : ChurchFeed {
    val files = mutableMapOf<String, String>()
    val requested = mutableListOf<String>()
    override suspend fun get(url: String): String? {
        requested += url
        return files[url]
    }
}

private class FakeCatalog(var churches: List<ChurchProfile>? = null) : ChurchCatalog {
    override suspend fun fetchChurches() = churches
}

/** iOS `ChurchSermonImportService` and the fixes in IOS_ISSUES M1–M5, M7. */
@RunWith(AndroidJUnit4::class)
class ChurchSermonImporterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = context.getSharedPreferences("church-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val base = "https://raw.example.com/sermons/"
    private val riverside = ChurchProfile(churchKey = "riverside", name = "Riverside Calvary Chapel", sermonImportRepoURL = base.dropLast(1))
    private val catalog = FakeCatalog()
    private val profiles = ChurchProfileStore(catalog, prefs) { listOf(riverside) }
    private val feed = FakeFeed()
    private val churches = OnlineChurches(db, Clock.fixed(Instant.parse("2026-10-30T08:00:00Z"), ZoneOffset.UTC))
    private val importer = ChurchSermonImporter(db, profiles, feed, prefs, churches)

    private val seriesA = "6a166ac5f315e27a81e5b126"
    private val seriesB = "6a78f7c09523fa297872371f"
    private val sermon1 = "6a78f9985811ac6f86be436e"
    private val sermon2 = "6a78f9985811ac6f86be4370"

    @After
    fun tearDown() = db.close()

    private fun manifest(latest: String, vararg files: String) {
        feed.files[base + "manifest.json"] =
            """{"latestVersion":"$latest","files":[${files.joinToString(",") { "\"$it\"" }}]}"""
    }

    private fun series(id: String, title: String, startedOn: String = "20260528", extra: String = "") =
        """{"id":"$id","churchKey":"riverside","title":"$title","bookOrd":55,"startedOn":"$startedOn","lastPreachedOn":"20260604","isFinished":false$extra}"""

    private fun sermon(id: String, seriesId: String, title: String, preachedOn: String = "20260802", extra: String = "") =
        """{"id":"$id","churchKey":"riverside","preachedOn":"$preachedOn","seriesId":"$seriesId","title":"$title","date":"Aug 02/26","thumbnailUrl":"https://x/t.jpg"$extra}"""

    private fun file(name: String, vararg rows: String) {
        feed.files[base + name] = "[${rows.joinToString(",")}]"
    }

    @Test
    fun repoFileNames() {
        assertTrue(ChurchRepoFiles.isSeriesFile("series_20260528.json"))
        assertTrue(ChurchRepoFiles.isSermonFile("20260518.json"))
        assertFalse(ChurchRepoFiles.isSermonFile("series_20260528.json"))
        assertFalse(ChurchRepoFiles.isSermonFile("2026051.json"))
        assertFalse(ChurchRepoFiles.isSermonFile("readme.md"))
        assertEquals("20260528", ChurchRepoFiles.version("series_20260528.json"))
        assertNull(ChurchRepoFiles.version("series_latest.json"))
        assertEquals(
            listOf("20260802.json", "series_20260802.json"),
            ChurchRepoFiles.filesFrom(listOf("series_20260802.json", "20260518.json", "20260802.json", "notes.txt"), "20260802"),
        )
        assertEquals(3, ChurchRepoFiles.filesFrom(listOf("series_x.json", "20260518.json", "a.json", "20260802.json", "series_20260528.json"), null).size)
    }

    @Test
    fun dayKeysFollowIOS() {
        assertEquals("20260802", ChurchRepoFiles.normalizeDay(" 20260802 "))
        assertNull(ChurchRepoFiles.normalizeDay("20261302"))
        assertNull(ChurchRepoFiles.normalizeDay("20260800"))
        assertNull(ChurchRepoFiles.normalizeDay("2026-08-02"))
        // iOS's lenient calendar rolls Feb 31 over to March 3.
        assertEquals("2026-03-03", ChurchRepoFiles.day("20260231").toString())
    }

    @Test
    fun importAllWritesLocalOnlyRowsSeriesFirst() = runTest {
        manifest("20260802", "20260802.json", "series_20260528.json")
        file("series_20260528.json", series(seriesA, "2 Timothy"))
        file(
            "20260802.json",
            sermon(sermon1, seriesA, "Unashamed", extra = ""","preacher":"  Brent Smith ","intro":"  ","youtubeId":"HI5VElimL_I","chapters":[1],"chapterRange":"2 Timothy 1:8-18","bookOrd":55,"book":"2 Timothy""""),
        )

        val result = importer.importAll("riverside", zone) as ChurchImportResult.Done

        assertEquals(1, result.newSeries)
        assertEquals(1, result.newSermons)
        assertTrue(result.incompleteFiles.isEmpty())
        // Series file fetched before the sermon file even though the manifest lists it second.
        assertEquals(listOf("manifest.json", "series_20260528.json", "20260802.json"), feed.requested.map { it.removePrefix(base) })
        val s = db.sermonDao().getById(sermon1)!!
        assertEquals("Brent Smith", s.preacher)
        assertNull(s.intro)
        assertEquals("HI5VElimL_I", s.youtubeID)
        assertEquals(listOf(1), s.chapters)
        assertEquals(seriesA, s.seriesId)
        assertEquals("riverside", s.churchKey)
        assertFalse(s.isUserData)
        assertFalse(s.needsSync)
        assertEquals(Instant.parse("2026-08-01T16:00:00Z"), s.createdAt) // start of Aug 2 in Shanghai
        val series = db.sermonSeriesDao().getById(seriesA)!!
        assertEquals("20260528", series.startedOn)
        assertFalse(series.isUserData)
        // Church imports never reach the push queue.
        assertTrue(db.sermonDao().dirty().isEmpty())
        assertTrue(db.sermonSeriesDao().dirty().isEmpty())
        assertEquals("20260802", importer.lastImportedVersion("riverside"))
        assertTrue(importer.hasImported("riverside"))
    }

    @Test
    fun badRowsAreSkippedAndSermonWithoutSeriesRetries() = runTest {
        manifest("20260802", "series_20260528.json", "20260802.json")
        file("series_20260528.json", series(seriesA, "A"), series("nothex", "Bad id"), series(seriesB, "Bad day", startedOn = "2026-05-28"))
        file("20260802.json", sermon(sermon1, seriesA, "Ok"), sermon(sermon2, seriesB, "No series yet"))

        val result = importer.importAll("riverside", zone) as ChurchImportResult.Done

        assertEquals(1, result.newSeries)
        assertEquals(1, result.newSermons)
        assertNull(db.sermonDao().getById(sermon2))
        // The sermon file is incomplete, so the saved version stays at it and it is fetched again (M5).
        assertEquals(listOf("20260802.json"), result.incompleteFiles)
        assertEquals("20260802", importer.lastImportedVersion("riverside"))
    }

    @Test
    fun failedFileHoldsVersionBackAndIsRetried() = runTest {
        manifest("20260802", "series_20260528.json", "20260518.json", "20260802.json")
        file("series_20260528.json", series(seriesA, "A"))
        file("20260802.json", sermon(sermon2, seriesA, "Later"))
        // 20260518.json is missing (download fails). iOS would still save 20260802 and never retry (M1).

        val first = importer.importAll("riverside", zone) as ChurchImportResult.Done
        assertEquals(listOf("20260518.json"), first.incompleteFiles)
        assertEquals("20260518", importer.lastImportedVersion("riverside"))

        file("20260518.json", sermon(sermon1, seriesA, "Earlier", preachedOn = "20260518"))
        feed.requested.clear()
        val second = importer.importUpdates("riverside", zone) as ChurchImportResult.Done

        assertEquals(1, second.newSermons)
        assertEquals(0, second.updatedSermons) // 20260802.json came again but nothing in it changed
        assertTrue(second.incompleteFiles.isEmpty())
        assertEquals("Earlier", db.sermonDao().getById(sermon1)!!.title)
        assertEquals(0, second.series) // series_20260528.json is at/after the saved version, so it came again, unchanged
        assertEquals("20260802", importer.lastImportedVersion("riverside"))
    }

    @Test
    fun updatesRefetchSameDayFileAndCountOnlyChanges() = runTest {
        manifest("20260802", "series_20260528.json", "20260802.json")
        file("series_20260528.json", series(seriesA, "A"))
        file("20260802.json", sermon(sermon1, seriesA, "Unashamed"))
        importer.importAll("riverside", zone)

        // Nothing changed upstream: the latest file is fetched again but counts are zero.
        val same = importer.importUpdates("riverside", zone) as ChurchImportResult.Done
        assertTrue(same.nothingNew)

        // Same-day re-publish with an extra sermon and an edited title (iOS misses both: M2).
        file("20260802.json", sermon(sermon1, seriesA, "Unashamed (edited)"), sermon(sermon2, seriesA, "Second"))
        val again = importer.importUpdates("riverside", zone) as ChurchImportResult.Done
        assertEquals(1, again.newSermons)
        assertEquals(1, again.updatedSermons)
        assertEquals("Unashamed (edited)", db.sermonDao().getById(sermon1)!!.title)
    }

    @Test
    fun importKeepsUserTagsAndCreatedAt() = runTest {
        manifest("20260802", "series_20260528.json", "20260802.json")
        file("series_20260528.json", series(seriesA, "A"))
        file("20260802.json", sermon(sermon1, seriesA, "Unashamed"))
        importer.importAll("riverside", zone)
        val tagged = db.sermonDao().getById(sermon1)!!.copy(tagIds = listOf("6a0000000000000000000001"), createdAt = Instant.EPOCH)
        db.sermonDao().upsert(tagged)

        file("20260802.json", sermon(sermon1, seriesA, "Renamed"))
        importer.importUpdates("riverside", zone)

        val after: SermonEntity = db.sermonDao().getById(sermon1)!!
        assertEquals("Renamed", after.title)
        assertEquals(listOf("6a0000000000000000000001"), after.tagIds)
        assertEquals(Instant.EPOCH, after.createdAt)
    }

    @Test
    fun unreachableIsNotNothingNew() = runTest {
        // No manifest at all (offline). iOS shows "No new sermons" (M3).
        assertEquals(ChurchImportResult.Unreachable, importer.importAll("riverside", zone))
        feed.files[base + "manifest.json"] = "<html>rate limited</html>"
        assertEquals(ChurchImportResult.Unreachable, importer.importUpdates("riverside", zone))
        assertNull(importer.lastImportedVersion("riverside"))
    }

    @Test
    fun notImportableWithoutRepoOrWhenDisabled() = runTest {
        assertEquals(ChurchImportResult.NotImportable, importer.importAll("unknown", zone))
        val disabled = ChurchSermonImporter(db, ChurchProfileStore(catalog, prefs) { listOf(riverside.copy(enabled = false)) }, feed, prefs, churches)
        assertEquals(ChurchImportResult.NotImportable, disabled.importAll("riverside", zone))
        val noRepo = ChurchSermonImporter(db, ChurchProfileStore(catalog, prefs) { listOf(riverside.copy(sermonImportRepoURL = " ")) }, feed, prefs, churches)
        assertEquals(ChurchImportResult.NotImportable, noRepo.importAll("riverside", zone))
        assertTrue(feed.requested.isEmpty())
    }

    @Test
    fun savedVersionWithoutSermonsMeansNotImported() = runTest {
        // iOS treats the saved version alone as "imported" and then finds nothing new (M7).
        prefs.edit().putString(ChurchSermonImporter.versionKey("riverside"), "20260802").commit()
        assertFalse(importer.hasImported("riverside"))
    }
}
