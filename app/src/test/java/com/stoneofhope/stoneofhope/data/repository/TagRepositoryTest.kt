package com.stoneofhope.stoneofhope.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.sync.MutableClock
import com.stoneofhope.stoneofhope.data.tag.TagCounts
import com.stoneofhope.stoneofhope.data.tag.TagRenameResult
import java.time.Instant
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

/** Port of iOS `NotesManager` `createTag` / `renameTag` / `deleteTag` / `mergeTags`, with IOS_ISSUES L7–L9 and L11. */
@RunWith(AndroidJUnit4::class)
class TagRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(Instant.parse("2026-10-29T02:00:00Z"))
    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val tags = TagRepository(db, clock)

    @After fun tearDown() = db.close()

    private suspend fun tag(name: String) = tags.create(name)!!

    private suspend fun seed(vararg tagIds: String) {
        db.noteDao().upsert(NoteEntity(id = "n1", noteType = NoteType.Note, content = "a", createdAt = created, updatedAt = created, tagIds = tagIds.toList()))
        db.noteCommentDao().upsert(NoteCommentEntity(id = "c1", noteId = "n1", createdAt = created, tagIds = tagIds.toList()))
        db.userVerseDao().upsert(UserVerseEntity("KJV_43_3_16".lowercase(), "KJV", 43, 3, 16, tagIds = tagIds.toList(), createdAt = created, updatedAt = created))
        db.sermonDao().upsert(SermonEntity(id = "s1", title = "S", createdAt = created, tagIds = tagIds.toList()))
        db.youTubeVideoDao().upsert(YouTubeVideoEntity(youtubeID = "v1", title = "V", createdAt = created, updatedAt = created, tagIds = tagIds.toList()))
    }

    @Test fun createTrimsAndReusesCaseInsensitively() = runTest {
        val a = tag("  Faith ")
        assertEquals("Faith", a.name)
        assertTrue(a.needsSync); assertEquals(clock.now, a.createdAt)
        assertEquals(a.id, tags.create("faith")!!.id)
        assertNull(tags.create("   "))
        assertEquals(1, tags.observeTags().first().size)
    }

    @Test fun renameRejectsBlankAndDuplicatesButAllowsOwnCase() = runTest {
        val a = tag("Faith"); val b = tag("Hope")
        db.tagDao().upsert(a.copy(needsSync = false))
        assertEquals(TagRenameResult.Empty, tags.rename(a.id, "  "))
        assertEquals(TagRenameResult.Duplicate, tags.rename(a.id, "hope"))
        assertEquals("Faith", db.tagDao().getById(a.id)!!.name)
        assertEquals(TagRenameResult.Renamed, tags.rename(a.id, "FAITH"))
        with(db.tagDao().getById(a.id)!!) { assertEquals("FAITH", name); assertTrue(needsSync) }
        assertEquals("Hope", db.tagDao().getById(b.id)!!.name)
    }

    @Test fun mergeMovesEverythingAndTombstonesTheSourceWithoutTouchingUpdatedAt() = runTest {
        val a = tag("Faith"); val b = tag("Hope")
        seed(a.id)
        clock.now = clock.now.plusSeconds(3600)
        assertTrue(tags.merge(a.id, b.id))
        with(db.noteDao().getById("n1")!!) { assertEquals(listOf(b.id), tagIds); assertTrue(needsSync); assertEquals(created, updatedAt) }
        with(db.noteCommentDao().getById("c1")!!) { assertEquals(listOf(b.id), tagIds); assertTrue(needsSync) }
        assertEquals(created, db.noteDao().getById("n1")!!.updatedAt) // the comment's note too (L11)
        assertEquals(listOf(b.id), db.userVerseDao().getById("kjv_43_3_16")!!.tagIds)
        assertEquals(listOf(b.id), db.sermonDao().getById("s1")!!.tagIds)
        assertEquals(listOf(b.id), db.youTubeVideoDao().getById("v1")!!.tagIds)
        with(db.tagDao().getById(a.id)!!) { assertEquals(clock.now, deletedAt); assertTrue(needsSync) }
        assertEquals(listOf(b.id), tags.observeTags().first().map { it.id })
    }

    @Test fun mergeDoesNotDuplicateATargetAlreadyThere() = runTest {
        val a = tag("Faith"); val b = tag("Hope")
        seed(a.id, b.id)
        tags.merge(a.id, b.id)
        assertEquals(listOf(b.id), db.noteDao().getById("n1")!!.tagIds)
    }

    @Test fun mergeRefusesSameDeletedOrUnknown() = runTest {
        val a = tag("Faith"); val b = tag("Hope")
        assertFalse(tags.merge(a.id, a.id))
        assertFalse(tags.merge(a.id, "nope"))
        tags.delete(b.id)
        assertFalse(tags.merge(a.id, b.id))
    }

    @Test fun deleteTakesItOffEverythingAndLeavesOtherTags() = runTest {
        val a = tag("Faith"); val b = tag("Hope")
        seed(a.id, b.id)
        clock.now = clock.now.plusSeconds(60)
        tags.delete(a.id)
        assertEquals(listOf(b.id), db.noteDao().getById("n1")!!.tagIds)
        assertEquals(created, db.noteDao().getById("n1")!!.updatedAt)
        assertEquals(listOf(b.id), db.sermonDao().getById("s1")!!.tagIds)
        assertNotNull(db.tagDao().getById(a.id)!!.deletedAt)
        assertTrue(tags.observeTags().first().none { it.id == a.id })
    }

    @Test fun countsSkipDeletedRowsAndSermonIndexVideos() = runTest {
        val a = tag("Faith")
        seed(a.id)
        db.noteDao().upsert(NoteEntity(id = "n2", noteType = NoteType.Note, content = "gone", createdAt = created, updatedAt = created, tagIds = listOf(a.id), deletedAt = created, isDeleted = true))
        db.youTubeVideoDao().upsert(YouTubeVideoEntity(youtubeID = "v2", title = "idx", sermonId = "s1", createdAt = created, updatedAt = created, tagIds = listOf(a.id)))
        assertEquals(TagCounts(notes = 1, comments = 1, sermons = 1, videos = 1), tags.observeCounts().first()[a.id])
    }
}
