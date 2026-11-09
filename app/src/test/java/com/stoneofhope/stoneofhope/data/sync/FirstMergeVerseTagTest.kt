package com.stoneofhope.stoneofhope.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * USER_VERSE_CONTRACT §3.4 (iOS `FirstMergeVerseTagTests`, 2977b9f): two devices tagged the same verse
 * before both signed in to one account. The device that syncs second keeps its own row (local dirty
 * wins) but takes the cloud's tags too. Only on that first merge — never afterwards.
 */
@RunWith(AndroidJUnit4::class)
class FirstMergeVerseTagTest {

    private val remote = FakeRemoteStore()
    private val vault = FakeKeyVault()
    private val devices = mutableListOf<TestDevice>()
    private val t0 = Instant.parse("2026-03-01T10:00:00.123456Z")

    private val mine = "64a0000000000000000000a1"
    private val shared = "64a0000000000000000000a2"
    private val theirs = "64a0000000000000000000a3"
    private val verseId = "CUV_43_3_16"

    private fun device(name: String) = TestDevice(name, remote, vault).also { devices += it }

    @After
    fun tearDown() = devices.forEach { it.close() }

    /** A verse tagged on this device, never pushed to the current cloud copy. */
    private fun verse(vararg tags: String, lastSyncedAt: Instant? = null) = UserVerseEntity(
        id = verseId, versionAbbr = "CUV", bookOrd = 43, chapterOrd = 3, verseOrd = 16,
        content = "神爱世人", tagIds = tags.toList(), createdAt = t0, updatedAt = t0,
        needsSync = true, lastSyncedAt = lastSyncedAt,
    )

    @Suppress("UNCHECKED_CAST")
    private fun cloudTagIds() = remote.server.get(FirestoreSchema.USER_VERSES, verseId)!!["tagIds"] as List<String>

    // --- The rule itself ---

    @Test
    fun firstMergeAppendsOnlyTheMissingIds() {
        val merged = FirstMergeVerseTags.union(verse(mine, shared), mapOf("tagIds" to listOf(shared.uppercase(), theirs, theirs, "not-an-id")))
        assertEquals(listOf(mine, shared, theirs), merged!!.tagIds)
        assertTrue("the merged row still has to be pushed", merged.needsSync)
        assertNull(merged.lastSyncedAt)
    }

    @Test
    fun nothingToAddChangesNothing() {
        assertNull(FirstMergeVerseTags.union(verse(mine, shared), mapOf("tagIds" to listOf(shared))))
        assertNull(FirstMergeVerseTags.union(verse(mine), emptyMap()))
    }

    @Test
    fun alreadySyncedVerseDoesNotMerge() {
        assertNull(FirstMergeVerseTags.union(verse(mine, lastSyncedAt = t0), mapOf("tagIds" to listOf(theirs))))
    }

    @Test
    fun cleanVerseDoesNotMerge() {
        assertNull(FirstMergeVerseTags.union(verse(mine).copy(needsSync = false), mapOf("tagIds" to listOf(theirs))))
    }

    @Test
    fun remoteTombstoneAddsNothing() {
        assertNull(FirstMergeVerseTags.union(verse(mine), mapOf("isDeleted" to true, "tagIds" to listOf(theirs))))
        assertNull(FirstMergeVerseTags.union(verse(mine), mapOf("deletedAt" to t0, "tagIds" to listOf(theirs))))
    }

    // --- Through a real sync ---

    @Test
    fun twoDevicesKeepBothTheirTagsAtTheFirstMerge() = runTest {
        val a = device("a")
        a.db.userVerseDao().upsert(verse(mine, shared))
        a.cloudSync.fullSync(a.uid)

        val b = device("b")
        b.db.userVerseDao().upsert(verse(theirs, shared))
        b.cloudSync.fullSync(b.uid)

        assertEquals("no duplicate of the shared tag", listOf(theirs, shared, mine), cloudTagIds())
        val onB = b.db.userVerseDao().getById(verseId)!!
        assertEquals(listOf(theirs, shared, mine), onB.tagIds)
        assertTrue(!onB.needsSync && onB.lastSyncedAt != null)

        a.cloudSync.fullSync(a.uid)
        assertEquals(listOf(theirs, shared, mine), a.db.userVerseDao().getById(verseId)!!.tagIds)
    }

    @Test
    fun afterSyncingLocalStillWinsWholeDocument() = runTest {
        val a = device("a")
        a.db.userVerseDao().upsert(verse(mine, theirs))
        a.cloudSync.fullSync(a.uid)

        // This device removes a tag after syncing; the cloud still has it.
        val synced = a.db.userVerseDao().getById(verseId)!!
        a.db.userVerseDao().upsert(synced.copy(tagIds = listOf(mine), needsSync = true))
        a.state.resetPullCursor(a.uid) // the next pull sees the cloud copy again
        a.cloudSync.fullSync(a.uid)

        assertEquals("a tag removed here after syncing must not come back", listOf(mine), cloudTagIds())
        assertEquals(listOf(mine), a.db.userVerseDao().getById(verseId)!!.tagIds)
    }

    @Test
    fun afterACloudWipeTheNextSignInIsAFirstMergeAgain() = runTest {
        val a = device("a")
        a.db.userVerseDao().upsert(verse(mine))
        a.cloudSync.fullSync(a.uid)
        val uid = a.uid
        assertTrue(a.db.userVerseDao().getById(verseId)!!.lastSyncedAt != null)

        remote.wipe() // another device chose "登出云"
        a.cloudSync.autoSync(uid)
        val kept = a.db.userVerseDao().getById(verseId)!!
        assertTrue(kept.needsSync)
        assertNull(kept.lastSyncedAt)

        // Another device tags the same verse in the fresh cloud; then this one signs in again.
        val b = device("b")
        b.db.userVerseDao().upsert(verse(theirs))
        b.cloudSync.fullSync(b.uid)
        a.identity.uid = uid
        a.cloudSync.fullSync(uid)

        assertEquals(listOf(mine, theirs), a.db.userVerseDao().getById(verseId)!!.tagIds)
        assertEquals(listOf(mine, theirs), cloudTagIds())
    }
}
