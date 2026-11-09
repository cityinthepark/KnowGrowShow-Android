package com.stoneofhope.stoneofhope.data.bible.text

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** iOS `CUVUserVerseRepairTests`, against the real bundled (fixed) CUV text. */
@RunWith(AndroidJUnit4::class)
class CuvUserVerseRepairTest {

    private lateinit var bible: BibleTextDatabase
    private lateinit var db: AppDatabase
    private lateinit var repair: CuvUserVerseRepair
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("cuv-repair-test", Context.MODE_PRIVATE)
    private val t0 = Instant.parse("2026-03-01T10:00:00Z")
    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val tag = "64a0000000000000000000a1"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("cuv-repair-test.db")
        bible = Room.databaseBuilder(context, BibleTextDatabase::class.java, "cuv-repair-test.db")
            .createFromAsset(BibleTextDatabase.ASSET_PATH).allowMainThreadQueries().build()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        prefs.edit().clear().commit()
        repair = CuvUserVerseRepair(db, bible.verseDao(), prefs, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() {
        bible.close()
        db.close()
    }

    private suspend fun frozen(b: Int, c: Int, v: Int, content: String, version: String = "CUV"): String {
        val id = UserVerseEntity.makeId(version, b, c, v)
        db.userVerseDao().upsert(
            UserVerseEntity(id, version, b, c, v, content, listOf(tag), createdAt = t0, updatedAt = t0, needsSync = false, lastSyncedAt = t0)
        )
        return id
    }

    private suspend fun mark(verseId: String) = db.indexedTextDao().upsert(
        IndexedTextEntity(id = "64a0000000000000000000f${verseId.length % 10}", sourceId = verseId, startOffset = 0, endOffset = 2, createdAt = t0, updatedAt = t0)
    )

    private suspend fun bundled(b: Int, c: Int, v: Int) = bible.verseDao().range("CUV", b, c, v, v).single().content

    @Test
    fun shiftedVerseIsRewrittenKeepingTagsAndItsMarksDropped() = runTest {
        // Before the fix 1 Chr 22:2 held what is now 22:1's text.
        val id = frozen(13, 22, 2, bundled(13, 22, 1))
        mark(id)

        val result = repair.apply()

        val row = db.userVerseDao().getById(id)!!
        assertEquals(bundled(13, 22, 2), row.content)
        assertEquals(listOf(tag), row.tagIds)
        assertTrue("pushed so the cloud copy is fixed too", row.needsSync)
        assertEquals(now, row.updatedAt)
        assertTrue(db.indexedTextDao().activeForVerses(listOf(id)).isEmpty())
        assertEquals(CuvUserVerseRepair.Result(rewritten = 1, tombstoned = 0, marksDropped = 1), result)
    }

    @Test
    fun frozenPlaceholderTakesTheMergedText() = runTest {
        val id = frozen(4, 1, 21, "併於上節")
        repair.apply()
        assertEquals(bundled(4, 1, 20), db.userVerseDao().getById(id)!!.content)
    }

    @Test
    fun orphanIdsAreTombstoned() = runTest {
        val id = frozen(64, 1, 15, "願你平安。")
        mark(id)
        val result = repair.apply()
        val row = db.userVerseDao().getById(id)!!
        assertTrue(row.isDeleted && row.deletedAt != null && row.needsSync)
        assertEquals(1, result.tombstoned)
        assertEquals(1, result.marksDropped)
    }

    @Test
    fun correctRowsAndOtherVersionsAreLeftAlone() = runTest {
        val right = frozen(13, 22, 3, bundled(13, 22, 3))
        val kjv = frozen(13, 22, 2, "whatever", version = "KJV")
        val elsewhere = frozen(43, 3, 16, "old")
        mark(right)

        assertEquals(CuvUserVerseRepair.Result(), repair.apply())
        assertFalse(db.userVerseDao().getById(right)!!.needsSync)
        assertEquals("whatever", db.userVerseDao().getById(kjv)!!.content)
        assertEquals("old", db.userVerseDao().getById(elsewhere)!!.content)
        assertEquals(1, db.indexedTextDao().activeForVerses(listOf(right)).size)
    }

    @Test
    fun runsOncePerInstall() = runTest {
        repair.runIfNeeded()
        assertEquals(CuvUserVerseRepair.REVISION, prefs.getInt(CuvUserVerseRepair.REVISION_KEY, 0))
        val id = frozen(4, 1, 21, "併於上節")
        repair.runIfNeeded()
        assertEquals("併於上節", db.userVerseDao().getById(id)!!.content)
        assertNotNull(db.userVerseDao().getById(id))
    }
}
