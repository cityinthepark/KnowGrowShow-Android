package com.stoneofhope.stoneofhope.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.ConflictResolution
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.SyncConflictType
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import java.time.Instant
import com.stoneofhope.stoneofhope.data.local.dao.SyncConflictDao
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

/** INTEROP_TEST §6: two-device conflicts, offline edits, and the 12-hour note-key cache running out while offline. */
@RunWith(AndroidJUnit4::class)
class SyncOfflineAndConflictTest {
    private val remote = FakeRemoteStore()
    private val vault = FakeKeyVault()
    private val devices = mutableListOf<TestDevice>()
    private val t0 = Instant.parse("2026-03-01T10:00:00.123456Z")
    private val id1 = "64a0000000000000000000aa"
    private val id2 = "64a0000000000000000000bb"

    private fun device(name: String) = TestDevice(name, remote, vault).also { devices += it; it.clock.follow = { remote.server.serverNow } }

    // Devices' clocks follow the server clock (see device()), as real phones' do to within seconds.
    private suspend fun full(d: TestDevice) = d.cloudSync.fullSync(d.uid)
    private suspend fun push(d: TestDevice) = d.cloudSync.push(d.uid)
    private suspend fun auto(d: TestDevice) = d.cloudSync.autoSync(d.uid)

    @After fun tearDown() = devices.forEach { it.close() }

    private suspend fun SyncConflictDao.unresolved() = observeUnresolved().first()

    private fun note(id: String, content: String) =
        NoteEntity(id = id, noteType = NoteType.Prayer, content = content, createdAt = t0, updatedAt = t0, needsSync = true)

    private suspend fun edit(d: TestDevice, id: String, content: String) =
        d.db.noteDao().upsert(d.db.noteDao().getById(id)!!.copy(content = content, needsSync = true))

    // ---- two devices, same data ----

    @Test
    fun editsToDifferentNotesOnTwoDevicesBothSurvive() = runTest {
        val a = device("a")
        val b = device("b")
        a.db.noteDao().upsert(note(id1, "one"))
        a.db.noteDao().upsert(note(id2, "two"))
        full(a)
        full(b)

        edit(a, id1, "one (A)")
        edit(b, id2, "two (B)")
        push(a)
        full(b)
        full(a)

        for (d in listOf(a, b)) {
            assertEquals("one (A)", d.db.noteDao().getById(id1)!!.content)
            assertEquals("two (B)", d.db.noteDao().getById(id2)!!.content)
            assertTrue(d.db.syncConflictDao().unresolved().isEmpty())
        }
    }

    @Test
    fun sameNoteEditedOnBothSidesLogsOneConflictOnTheLaterDeviceAndLastPushWins() = runTest {
        val a = device("a")
        val b = device("b")
        a.db.noteDao().upsert(note(id1, "v1"))
        full(a)
        full(b)

        edit(a, id1, "A edit")
        edit(b, id1, "B edit")
        full(a) // A uploads first
        full(b) // B pulls (conflict, own row kept), then pushes over A — same as iOS

        val conflicts = b.db.syncConflictDao().unresolved()
        assertEquals(1, conflicts.size)
        assertEquals("B edit", conflicts[0].localPreview)
        assertEquals("A edit", conflicts[0].remotePreview)
        assertTrue("A pushed first and had no conflict", a.db.syncConflictDao().unresolved().isEmpty())

        full(a)
        assertEquals("B edit", a.db.noteDao().getById(id1)!!.content)
        // The cloud no longer has A's text, but the conflict kept it: choosing it restores it everywhere.
        SyncConflictResolver(b.db, remote, b.session, b.identity, b.clock).resolve(conflicts[0].id, ConflictResolution.KeepRemote)
        full(b)
        full(a)
        assertEquals("A edit", b.db.noteDao().getById(id1)!!.content)
        assertEquals("A edit", a.db.noteDao().getById(id1)!!.content)
        assertTrue(b.db.syncConflictDao().unresolved().isEmpty())
    }

    @Test
    fun remoteDeleteAgainstALocalEditIsNotAConflictAndTheEditBringsTheNoteBack() = runTest {
        val a = device("a")
        val b = device("b")
        a.db.noteDao().upsert(note(id1, "v1"))
        full(a)
        full(b)

        a.db.noteDao().upsert(a.db.noteDao().getById(id1)!!.copy(deletedAt = t0, isDeleted = true, needsSync = true))
        push(a)
        edit(b, id1, "B kept writing")
        full(b)

        assertTrue(b.db.syncConflictDao().unresolved().isEmpty())
        assertEquals("B kept writing", b.db.noteDao().getById(id1)!!.content)
        assertNotNull(remote.server.get(FirestoreSchema.NOTES, id1)!!["iv"]) // B's edit replaced the tombstone
    }

    // ---- offline ----

    @Test
    fun offlineEditsStayDirtyThenUploadWhenTheNetworkReturns() = runTest {
        val a = device("a")
        val b = device("b")
        a.db.noteDao().upsert(note(id1, "v1"))
        full(a)
        full(b)

        a.network.isOnline.value = false
        edit(a, id1, "written offline")
        a.db.noteDao().upsert(note(id2, "created offline"))
        val commitsBefore = remote.commits.get()
        full(a)
        push(a)
        assertEquals("nothing leaves the device offline", commitsBefore, remote.commits.get())
        assertTrue(a.db.noteDao().getById(id1)!!.needsSync)
        assertTrue(a.db.noteDao().getById(id2)!!.needsSync)

        a.network.isOnline.value = true
        auto(a) // what the network-regained trigger runs
        assertFalse(a.db.noteDao().getById(id1)!!.needsSync)
        assertFalse(a.db.noteDao().getById(id2)!!.needsSync)

        auto(b) // peer stamp changed → B pulls
        assertEquals("written offline", b.db.noteDao().getById(id1)!!.content)
        assertEquals("created offline", b.db.noteDao().getById(id2)!!.content)
    }

    @Test
    fun uploadFailingMidwayKeepsRowsDirtyAndTheRetrySucceeds() = runTest {
        val a = device("a")
        a.db.noteDao().upsert(note(id1, "v1"))
        remote.beforeCommit = { error("connection lost") }
        full(a)
        assertTrue(a.db.noteDao().getById(id1)!!.needsSync)
        assertNull(remote.server.get(FirestoreSchema.NOTES, id1))

        remote.beforeCommit = {}
        full(a)
        assertFalse(a.db.noteDao().getById(id1)!!.needsSync)
        assertNotNull(remote.server.get(FirestoreSchema.NOTES, id1))
    }

    // ---- note-key cache expired while offline ----

    @Test
    fun keyCacheExpiredAndOfflineKeepsNotesDirtyAndHoldsThePullCursorUntilTheKeyReturns() = runTest {
        val a = device("a")
        val b = device("b")
        a.db.noteDao().upsert(note(id1, "from A"))
        full(a)
        full(b)
        assertEquals("from A", b.db.noteDao().getById(id1)!!.content)
        val cursorBefore = b.state.lastPullAt(b.uid)

        // Next day, no connection to the key: the 12 h cache is gone and the key cannot be fetched.
        b.session.lock(clearCache = true)
        vault.unreachable = true
        assertEquals(CryptoSession.UnlockResult.Failed, b.session.ensureUnlocked(b.uid))

        edit(a, id1, "A again")
        push(a)
        b.db.noteDao().upsert(note(id2, "B new"))
        full(b)

        // Bodies cannot be sealed or opened without the key: B's new note stays dirty (not uploaded), A's
        // update is not applied, and the cursor does not move past it.
        assertEquals("from A", b.db.noteDao().getById(id1)!!.content)
        assertTrue(b.db.noteDao().getById(id2)!!.needsSync)
        assertNull(remote.server.get(FirestoreSchema.NOTES, id2))
        assertEquals(cursorBefore, b.state.lastPullAt(b.uid))

        // The key comes back: the same notes upload (B's edit also conflicts with A's), the cursor moves.
        vault.unreachable = false
        full(b)
        assertFalse(b.db.noteDao().getById(id2)!!.needsSync)
        assertNotNull(remote.server.get(FirestoreSchema.NOTES, id2))
        assertTrue(b.state.lastPullAt(b.uid)!! > cursorBefore!!)
        assertEquals("A again", b.db.noteDao().getById(id1)!!.content)
        assertTrue(b.db.syncConflictDao().unresolved().isEmpty())
    }

    @Test
    fun conflictFoundWhileTheKeyIsMissingKeepsAPlaceholderPreviewButStillResolves() = runTest {
        val a = device("a")
        val b = device("b")
        a.db.noteDao().upsert(note(id1, "v1"))
        full(a)
        full(b)
        edit(a, id1, "A edit")
        push(a)
        edit(b, id1, "B edit")
        b.session.lock(clearCache = true)
        vault.unreachable = true
        full(b)

        val c = b.db.syncConflictDao().findUnresolved(id1, SyncConflictType.Note)!!
        assertEquals(SyncConflictDetector.UNREADABLE_PREVIEW, c.remotePreview)

        vault.unreachable = false
        b.session.ensureUnlocked(b.uid)
        SyncConflictResolver(b.db, remote, b.session, b.identity, b.clock).resolve(c.id, ConflictResolution.KeepRemote)
        assertEquals("A edit", b.db.noteDao().getById(id1)!!.content)
    }

    @Test
    fun theKeyStillInMemoryOrCacheMeansOfflineEditsKeepWorkingUntilExpiry() = runTest {
        val a = device("a")
        a.db.noteDao().upsert(note(id1, "v1"))
        full(a) // key fetched and cached
        vault.unreachable = true
        a.session.lock() // process restart: memory empty, cache (still within 12 h) intact
        assertEquals(CryptoSession.UnlockResult.Unlocked, a.session.ensureUnlocked(a.uid))
        edit(a, id1, "offline but unlocked")
        push(a)
        assertFalse(a.db.noteDao().getById(id1)!!.needsSync)
    }
}
