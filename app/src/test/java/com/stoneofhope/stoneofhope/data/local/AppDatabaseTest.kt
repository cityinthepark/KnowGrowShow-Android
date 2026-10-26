package com.stoneofhope.stoneofhope.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogPendingDeleteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private val t0 = Instant.parse("2026-03-01T23:30:00.123456Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun note(id: String = ObjectIds.newId(), vararg tagIds: String) = NoteEntity(
        id = id, noteType = NoteType.Prayer, content = "🙏 平安", createdAt = t0, updatedAt = t0,
        tagIds = tagIds.toList(), isPinned = null,
    )

    @Test
    fun noteRoundTripsExactlyIncludingMicroseconds() = runTest {
        val n = note(ObjectIds.newId(), ObjectIds.newId()).copy(answeredAt = t0, verseStart = 3)
        db.noteDao().upsert(n)
        assertEquals(n, db.noteDao().getById(n.id))
    }

    @Test
    fun tagMembershipQueryMatchesWholeIdsOnly() = runTest {
        val tagA = "64a000000000000000000001"
        val tagB = "64a000000000000000000002"
        db.noteDao().upsertAll(listOf(note("64a0000000000000000000a1", tagA), note("64a0000000000000000000a2", tagA, tagB), note("64a0000000000000000000a3")))
        assertEquals(setOf("64a0000000000000000000a1", "64a0000000000000000000a2"), db.noteDao().observeActiveWithTag(tagA).first().map { it.id }.toSet())
        assertEquals(listOf("64a0000000000000000000a2"), db.noteDao().observeActiveWithTag(tagB).first().map { it.id })
        // A prefix of an id must not match.
        assertTrue(db.noteDao().observeActiveWithTag("64a00000000000000000000").first().isEmpty())
    }

    @Test
    fun activeQueriesHideSoftDeletedRows() = runTest {
        db.noteDao().upsertAll(
            listOf(note("64a0000000000000000000a1"), note("64a0000000000000000000a2").copy(deletedAt = t0), note("64a0000000000000000000a3").copy(isDeleted = true))
        )
        assertEquals(listOf("64a0000000000000000000a1"), db.noteDao().observeActive().first().map { it.id })
    }

    @Test
    fun dirtyAndPurgeFollowSyncRules() = runTest {
        val dao = db.tagDao()
        dao.upsertAll(
            listOf(
                TagEntity(id = "64a000000000000000000001", name = "clean", createdAt = t0),
                TagEntity(id = "64a000000000000000000002", name = "dirty", createdAt = t0, needsSync = true),
                // Tombstone not yet pushed: must survive purge so other devices can learn about it.
                TagEntity(id = "64a000000000000000000003", name = "unpushed tombstone", createdAt = t0, deletedAt = t0, needsSync = true),
                TagEntity(id = "64a000000000000000000004", name = "pushed tombstone", createdAt = t0, deletedAt = t0),
            )
        )
        assertEquals(setOf("64a000000000000000000002", "64a000000000000000000003"), dao.dirty().map { it.id }.toSet())
        assertEquals(1, dao.purgeSyncedTombstones())
        assertNull(dao.getById("64a000000000000000000004"))
        assertEquals("unpushed tombstone", dao.getById("64a000000000000000000003")?.name)
    }

    @Test
    fun churchImportsNeverReportDirty() = runTest {
        db.sermonSeriesDao().upsert(SermonSeriesEntity(id = "64a0000000000000000000c1", title = "Church", isUserData = false, needsSync = true))
        db.sermonDao().upsert(SermonEntity(id = "64a0000000000000000000c2", title = "Church", createdAt = t0, isUserData = false, needsSync = true))
        assertTrue(db.sermonSeriesDao().dirty().isEmpty())
        assertTrue(db.sermonDao().dirty().isEmpty())
    }

    @Test
    fun seriesMembershipComesFromSermonSeriesId() = runTest {
        val series = "64a0000000000000000000c1"
        db.sermonDao().upsert(SermonEntity(id = "64a0000000000000000000c3", title = "b", preachedOn = "20260308", createdAt = t0, seriesId = series))
        db.sermonDao().upsert(SermonEntity(id = "64a0000000000000000000c2", title = "a", preachedOn = "20260301", createdAt = t0, seriesId = series))
        db.sermonDao().upsert(SermonEntity(id = "64a0000000000000000000c4", title = "other", createdAt = t0))
        assertEquals(listOf("64a0000000000000000000c2", "64a0000000000000000000c3"), db.sermonDao().idsInSeries(series))
    }

    @Test
    fun marksAttachToVerseBySourceId() = runTest {
        db.indexedTextDao().upsert(
            IndexedTextEntity(id = "64a0000000000000000000d1", sourceId = "NKJV_60_1_3", startOffset = 4, endOffset = 9, createdAt = t0, updatedAt = t0)
        )
        db.indexedTextDao().upsert(
            IndexedTextEntity(id = "64a0000000000000000000d2", sourceId = "NKJV_60_1_3", startOffset = 0, endOffset = 3, createdAt = t0, updatedAt = t0, isDeleted = true)
        )
        assertEquals(listOf("64a0000000000000000000d1"), db.indexedTextDao().observeMarksForVerse("NKJV_60_1_3").first().map { it.id })
    }

    @Test
    fun pinLogDeleteOutbox() = runTest {
        val dao = db.pinLogDao()
        dao.enqueueDelete(PinLogPendingDeleteEntity("64a0000000000000000000a1"))
        dao.enqueueDelete(PinLogPendingDeleteEntity("64a0000000000000000000a1"))
        dao.enqueueDelete(PinLogPendingDeleteEntity("64a0000000000000000000a2"))
        assertEquals(2, dao.pendingDeletes().size)
        dao.clearPendingDeletes(listOf("64a0000000000000000000a1"))
        assertEquals(listOf("64a0000000000000000000a2"), dao.pendingDeletes())
    }
}
