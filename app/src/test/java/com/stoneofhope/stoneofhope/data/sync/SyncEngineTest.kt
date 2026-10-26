package com.stoneofhope.stoneofhope.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.crypto.StoneOfHopeCrypto
import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.SyncConflictType
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import com.stoneofhope.stoneofhope.data.remote.wire.NoteBodyCodec
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

/**
 * End-to-end sync: real Room (Robolectric SQLite) on each simulated device, one shared fake
 * Firestore. Scenarios mirror FIRESTORE_SYNC_CONTRACT §2–§5 and the iOS pull/push behaviour.
 */
@RunWith(AndroidJUnit4::class)
class SyncEngineTest {

    private val remote = FakeRemoteStore()
    private val vault = FakeKeyVault()
    private val devices = mutableListOf<TestDevice>()
    private val t0 = Instant.parse("2026-03-01T10:00:00.123456Z")

    private fun device(name: String, uid: String? = "user-1") = TestDevice(name, remote, vault, uid).also { devices += it }

    @After
    fun tearDown() = devices.forEach { it.close() }

    private fun note(id: String, content: String = "主啊，我信 🙏", vararg tags: String) = NoteEntity(
        id = id, noteType = NoteType.Prayer, content = content, createdAt = t0, updatedAt = t0,
        tagIds = tags.toList(), needsSync = true,
    )

    @Test
    fun deviceAPushesDeviceBRestoresEverything() = runTest {
        val a = device("a")
        a.db.tagDao().upsert(TagEntity(id = "64a000000000000000000001", name = "盼望", createdAt = t0, needsSync = true))
        a.db.noteDao().upsert(note("64a0000000000000000000aa", "第一条", "64a000000000000000000001"))
        a.db.noteCommentDao().upsert(
            NoteCommentEntity(id = "64a0000000000000000000ab", noteId = "64a0000000000000000000aa", content = "阿们", createdAt = t0, needsSync = true)
        )

        a.cloudSync.fullSync(a.uid)

        // Server: encrypted bodies only, and A registered as having uploaded.
        val doc = remote.server.get(FirestoreSchema.NOTES, "64a0000000000000000000aa")!!
        assertFalse(doc.containsKey("content"))
        assertEquals(6L, doc["schemaVersion"])
        assertNotNull(remote.server.get(FakeRemoteStore.DEVICES, a.state.deviceId)!!["lastUpdatedAt"])
        // A: rows are clean now.
        assertFalse(a.db.noteDao().getById("64a0000000000000000000aa")!!.needsSync)
        assertTrue(a.db.noteDao().dirty().isEmpty())

        val b = device("b")
        b.cloudSync.fullSync(b.uid)
        val restored = b.db.noteDao().getById("64a0000000000000000000aa")!!
        assertEquals("第一条", restored.content)
        assertEquals(listOf("64a000000000000000000001"), restored.tagIds)
        assertEquals("阿们", b.db.noteCommentDao().getById("64a0000000000000000000ab")!!.content)
        assertEquals("盼望", b.db.tagDao().getById("64a000000000000000000001")!!.name)
        assertEquals(1, vault.keys.size)
    }

    @Test
    fun pullNeverOverwritesDirtyRowAndLogsConflict() = runTest {
        val a = device("a")
        val b = device("b")
        a.db.noteDao().upsert(note("64a0000000000000000000aa", "v1"))
        a.cloudSync.fullSync(a.uid)
        b.cloudSync.fullSync(b.uid)

        // Both edit; A uploads first, B's local edit is still unpushed.
        a.db.noteDao().upsert(a.db.noteDao().getById("64a0000000000000000000aa")!!.copy(content = "A edit", needsSync = true))
        a.cloudSync.push(a.uid)
        b.db.noteDao().upsert(b.db.noteDao().getById("64a0000000000000000000aa")!!.copy(content = "B edit", needsSync = true))

        b.session.ensureUnlocked(b.uid)
        b.puller.pull(b.uid, b.guard())
        assertEquals("B edit", b.db.noteDao().getById("64a0000000000000000000aa")!!.content)
        val conflict = b.db.syncConflictDao().findUnresolved("64a0000000000000000000aa", SyncConflictType.Note)!!
        assertEquals("B edit", conflict.localPreview)
        assertEquals("A edit", conflict.remotePreview)

        // Last writer wins once B pushes (whole-document overwrite, no field merge).
        b.cloudSync.push(b.uid)
        a.cloudSync.fullSync(a.uid)
        assertEquals("B edit", a.db.noteDao().getById("64a0000000000000000000aa")!!.content)
    }

    @Test
    fun deletesPropagateAsTombstonesAndArePurgedLocally() = runTest {
        val a = device("a")
        val b = device("b")
        a.db.noteDao().upsert(note("64a0000000000000000000aa"))
        a.db.noteCommentDao().upsert(NoteCommentEntity(id = "64a0000000000000000000ab", noteId = "64a0000000000000000000aa", content = "c", createdAt = t0, needsSync = true))
        a.db.pinLogDao().upsert(PinLogEntity(noteId = "64a0000000000000000000aa", pinnedDays = listOf("2026-03-01"), needsSync = true))
        a.cloudSync.fullSync(a.uid)
        b.cloudSync.fullSync(b.uid)
        assertNotNull(b.db.pinLogDao().getByNoteId("64a0000000000000000000aa"))

        // A soft-deletes; push writes a slim tombstone and then hard-deletes locally (with cascade).
        a.db.noteDao().upsert(a.db.noteDao().getById("64a0000000000000000000aa")!!.copy(deletedAt = t0, isDeleted = true, needsSync = true))
        a.cloudSync.push(a.uid)
        assertEquals(setOf("isDeleted", "updatedAt"), remote.server.get(FirestoreSchema.NOTES, "64a0000000000000000000aa")!!.keys)
        assertNull(a.db.noteDao().getById("64a0000000000000000000aa"))
        assertNull(a.db.noteCommentDao().getById("64a0000000000000000000ab"))
        assertNull(a.db.pinLogDao().getByNoteId("64a0000000000000000000aa"))
        // The purge runs after the PinLog pass (same order as iOS), so the cascade's queued PinLog
        // delete goes out on the next push.
        a.cloudSync.push(a.uid)
        assertNull(remote.server.get(FirestoreSchema.PIN_LOG, "64a0000000000000000000aa"))

        // B pulls the tombstone and hard-deletes its copy too.
        b.cloudSync.fullSync(b.uid)
        assertNull(b.db.noteDao().getById("64a0000000000000000000aa"))
        assertNull(b.db.noteCommentDao().getById("64a0000000000000000000ab"))
    }

    @Test
    fun editDuringUploadStaysDirty() = runTest {
        val a = device("a")
        a.db.noteDao().upsert(note("64a0000000000000000000aa", "before"))
        a.session.ensureUnlocked(a.uid)
        remote.beforeCommit = {
            remote.beforeCommit = {}
            val n = a.db.noteDao().getById("64a0000000000000000000aa")!!
            a.db.noteDao().upsert(n.copy(isPinned = true)) // UI edit mid-flight; updatedAt unchanged
        }
        a.pusher.push(a.uid, a.guard())
        val after = a.db.noteDao().getById("64a0000000000000000000aa")!!
        assertTrue("an edit made during upload must stay dirty", after.needsSync)
        assertEquals(true, after.isPinned)
    }

    @Test
    fun lockedSessionHoldsNoteBodiesButPushesTheRest() = runTest {
        val a = device("a")
        a.db.tagDao().upsert(TagEntity(id = "64a000000000000000000001", name = "t", createdAt = t0, needsSync = true))
        a.db.noteDao().upsert(note("64a0000000000000000000aa"))
        val result = a.pusher.push(a.uid, a.guard()) // never unlocked
        assertEquals(1, result.bodiesSkipped)
        assertNull(remote.server.get(FirestoreSchema.NOTES, "64a0000000000000000000aa"))
        assertNotNull(remote.server.get(FirestoreSchema.TAGS, "64a000000000000000000001"))
        assertTrue(a.db.noteDao().getById("64a0000000000000000000aa")!!.needsSync)
        assertFalse(a.db.tagDao().getById("64a000000000000000000001")!!.needsSync)
    }

    @Test
    fun unreadableBodyHoldsCursorButMergesTheRest() = runTest {
        val wrongKey = StoneOfHopeCrypto.generateNoteKey()
        val body = NoteBodyCodec.encrypt("secret", wrongKey)
        remote.server.serverNow = Instant.parse("2026-10-01T00:00:00Z")
        remote.server.put(
            FirestoreSchema.NOTES, "64a0000000000000000000aa",
            mapOf("iv" to body.iv, "ciphertext" to body.ciphertext, "schemaVersion" to 6L, "updatedAt" to remote.server.serverNow),
        )
        remote.server.put(FirestoreSchema.TAGS, "64a000000000000000000001", mapOf("name" to "ok", "updatedAt" to remote.server.serverNow))

        val b = device("b")
        b.session.ensureUnlocked(b.uid)
        val result = b.puller.pull(b.uid, b.guard())
        assertEquals(1, result.bodiesSkipped)
        assertFalse(result.complete)
        assertNull("cursor must not advance past an unreadable body", b.state.lastPullAt(b.uid))
        assertNull(b.db.noteDao().getById("64a0000000000000000000aa"))
        assertEquals("ok", b.db.tagDao().getById("64a000000000000000000001")!!.name)
    }

    @Test
    fun incrementalPullUsesServerWatermark() = runTest {
        val a = device("a")
        val b = device("b")
        a.db.tagDao().upsert(TagEntity(id = "64a000000000000000000001", name = "one", createdAt = t0, needsSync = true))
        a.cloudSync.fullSync(a.uid)
        b.cloudSync.fullSync(b.uid)
        val cursor = b.state.lastPullAt(b.uid)!!
        assertEquals(remote.server.get(FirestoreSchema.TAGS, "64a000000000000000000001")!!["updatedAt"], cursor)

        a.db.tagDao().upsert(TagEntity(id = "64a000000000000000000002", name = "two", createdAt = t0, needsSync = true))
        a.cloudSync.push(a.uid)
        remote.fetches.clear()
        b.cloudSync.fullSync(b.uid)
        assertTrue(remote.fetches.all { it.second == cursor })
        assertEquals("two", b.db.tagDao().getById("64a000000000000000000002")!!.name)
        assertTrue(b.state.lastPullAt(b.uid)!! > cursor)
    }

    @Test
    fun autoSyncPullsOnlyWhenAPeerUploaded() = runTest {
        val a = device("a")
        val b = device("b")
        b.cloudSync.fullSync(b.uid)

        remote.fetches.clear()
        b.cloudSync.autoSync(b.uid)
        assertTrue("no peer changes → no pull", remote.fetches.isEmpty())

        a.db.tagDao().upsert(TagEntity(id = "64a000000000000000000001", name = "new", createdAt = t0, needsSync = true))
        a.cloudSync.push(a.uid)
        b.cloudSync.autoSync(b.uid)
        assertEquals("new", b.db.tagDao().getById("64a000000000000000000001")!!.name)

        // A push that uploads nothing must not wake peers.
        val stamp = remote.server.get(FakeRemoteStore.DEVICES, a.state.deviceId)!!["lastUpdatedAt"]
        a.cloudSync.push(a.uid)
        assertEquals(stamp, remote.server.get(FakeRemoteStore.DEVICES, a.state.deviceId)!!["lastUpdatedAt"])
    }

    @Test
    fun cloudWipedByAnotherDeviceSignsOutButKeepsLocalData() = runTest {
        val a = device("a")
        a.db.noteDao().upsert(note("64a0000000000000000000aa", "keep me"))
        a.db.tagDao().upsert(TagEntity(id = "64a000000000000000000009", name = "gone", createdAt = t0, deletedAt = t0, needsSync = true))
        a.cloudSync.fullSync(a.uid)
        val uid = a.uid
        assertNotNull(a.state.accountEpoch(uid))

        remote.wipe() // another device chose "登出云"
        a.cloudSync.autoSync(uid)

        assertNull("signed out locally", a.identity.uid)
        assertTrue(a.flags.showCloudResetNotice)
        assertNull(a.session.keyFor(uid))
        assertNull(a.state.lastPullAt(uid))
        assertNull(a.state.accountEpoch(uid))
        val kept = a.db.noteDao().getById("64a0000000000000000000aa")!!
        assertEquals("keep me", kept.content)
        assertTrue("everything is queued for re-upload", kept.needsSync)
        assertEquals(0, remote.server.count(FirestoreSchema.NOTES))
    }

    @Test
    fun seriesMembershipFollowsRemoteSermonIds() = runTest {
        val a = device("a")
        a.db.sermonSeriesDao().upsert(SermonSeriesEntity(id = "64a0000000000000000000c1", title = "Romans", needsSync = true))
        a.db.sermonDao().upsert(SermonEntity(id = "64a0000000000000000000c2", title = "Grace", createdAt = t0, seriesId = "64a0000000000000000000c1", needsSync = true))
        a.cloudSync.fullSync(a.uid)
        assertEquals(listOf("64a0000000000000000000c2"), remote.server.get(FirestoreSchema.SERMON_SERIES, "64a0000000000000000000c1")!!["sermonIds"])

        val b = device("b")
        b.db.sermonDao().upsert(SermonEntity(id = "64a0000000000000000000c3", title = "Local only", createdAt = t0, seriesId = "64a0000000000000000000c1"))
        b.cloudSync.fullSync(b.uid)
        assertEquals("64a0000000000000000000c1", b.db.sermonDao().getById("64a0000000000000000000c2")!!.seriesId)
        // Not in the clean remote series' sermonIds → detached, like iOS.
        assertNull(b.db.sermonDao().getById("64a0000000000000000000c3")!!.seriesId)
    }

    @Test
    fun legacyFlashCardContentBackfillsUserVerses() = runTest {
        remote.server.put(
            FirestoreSchema.FLASH_CARDS, "64a0000000000000000000e1",
            mapOf(
                "bookOrd" to 43L, "chapterOrd" to 3L, "verseStart" to 16L, "verseEnd" to 17L, "versionAbbr" to "NKJV",
                "content" to "3:16 For God so loved the world 3:17 For God did not send His Son",
                "createdAt" to t0, "updatedAt" to t0, "schemaVersion" to 5L,
            ),
        )
        val b = device("b")
        b.cloudSync.fullSync(b.uid)
        assertNotNull(b.db.flashCardDao().getById("64a0000000000000000000e1"))
        assertEquals("For God so loved the world", b.db.userVerseDao().getById("NKJV_43_3_16")!!.content)
        assertEquals("For God did not send His Son", b.db.userVerseDao().getById("NKJV_43_3_17")!!.content)
        // Backfilled verses are uploaded on the same run's push.
        assertNotNull(remote.server.get(FirestoreSchema.USER_VERSES, "NKJV_43_3_16"))
    }

    @Test
    fun accountSwitchMidRunStopsWithoutTouchingFlags() = runTest {
        val a = device("a")
        a.db.tagDao().upsert(TagEntity(id = "64a000000000000000000001", name = "t", createdAt = t0, needsSync = true))
        remote.beforeCommit = { a.identity.uid = "someone-else" }
        a.cloudSync.push("user-1")
        assertTrue("rows must stay dirty when the account changed mid-push", a.db.tagDao().getById("64a000000000000000000001")!!.needsSync)
    }

    @Test
    fun signedOutDeviceNeverSyncs() = runTest {
        val local = device("local", uid = null)
        local.db.noteDao().upsert(note("64a0000000000000000000aa"))
        local.cloudSync.fullSync("user-1")
        assertEquals(0, remote.commits.get())
        assertTrue(remote.fetches.isEmpty())
        assertEquals(1, local.db.noteDao().observeActive().first().size)
    }

    @Test
    fun legacyFlashCardWithoutMarkersBackfillsNothing() = runTest {
        assertTrue(FlashCardUserVerseBackfill.verses("no markers here", 43, 3, 16, 17, "NKJV").isEmpty())
        assertTrue(FlashCardUserVerseBackfill.verses("·", 43, 3, 16, null, "NKJV").isEmpty())
        assertEquals(1, FlashCardUserVerseBackfill.verses("3:16 text", 43, 3, 16, null, "NKJV").size)
    }

    @Test
    fun cardPushStaysCleanAfterRoundTrip() = runTest {
        val a = device("a")
        a.db.flashCardDao().upsert(FlashCardEntity(id = "64a0000000000000000000e2", topic = "Faith", createdAt = t0, updatedAt = t0, needsSync = true))
        a.cloudSync.fullSync(a.uid)
        a.cloudSync.fullSync(a.uid) // pulls its own write back (read-your-writes) — harmless
        val card = a.db.flashCardDao().getById("64a0000000000000000000e2")!!
        assertFalse(card.needsSync)
        assertEquals("Faith", card.topic)
    }
}
