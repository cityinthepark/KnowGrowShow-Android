package com.stoneofhope.stoneofhope.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.ConflictResolution
import com.stoneofhope.stoneofhope.data.model.MarkStyle
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.SyncConflictType
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** The three ways to settle a Settings → Sync Conflicts entry (iOS `SyncConflictResolver`). */
@RunWith(AndroidJUnit4::class)
class SyncConflictResolverTest {
    private val remote = FakeRemoteStore()
    private val vault = FakeKeyVault()
    private val devices = mutableListOf<TestDevice>()
    private val t0 = Instant.parse("2026-03-01T10:00:00.123456Z")
    private val noteId = "64a0000000000000000000aa"
    private val commentId = "64a0000000000000000000ab"
    private val markId = "64a0000000000000000000ac"

    private fun device(name: String) = TestDevice(name, remote, vault).also { devices += it }

    @After fun tearDown() = devices.forEach { it.close() }

    private fun resolver(d: TestDevice) = SyncConflictResolver(d.db, remote, d.session, d.identity, d.clock)

    /** A and B both hold v1; A uploads "A edit"; B has an unpushed "B edit" and its pull logs the conflicts. */
    private suspend fun conflicted(): Pair<TestDevice, TestDevice> {
        val a = device("a")
        val b = device("b")
        a.db.noteDao().upsert(NoteEntity(id = noteId, noteType = NoteType.Prayer, content = "v1", createdAt = t0, updatedAt = t0, needsSync = true))
        a.db.noteCommentDao().upsert(NoteCommentEntity(id = commentId, noteId = noteId, content = "c1", createdAt = t0, needsSync = true))
        a.db.indexedTextDao().upsert(
            IndexedTextEntity(id = markId, sourceId = "John 3:16", startOffset = 0, endOffset = 4, markStyle = MarkStyle.Highlight, indexedText = "God", createdAt = t0, updatedAt = t0, needsSync = true)
        )
        a.cloudSync.fullSync(a.uid)
        b.cloudSync.fullSync(b.uid)

        a.db.noteDao().upsert(a.db.noteDao().getById(noteId)!!.copy(content = "A edit", needsSync = true))
        a.db.noteCommentDao().upsert(a.db.noteCommentDao().getById(commentId)!!.copy(content = "A comment", needsSync = true))
        a.db.indexedTextDao().upsert(a.db.indexedTextDao().getById(markId)!!.copy(endOffset = 9, needsSync = true))
        a.cloudSync.push(a.uid)

        b.db.noteDao().upsert(b.db.noteDao().getById(noteId)!!.copy(content = "B edit", needsSync = true))
        b.db.noteCommentDao().upsert(b.db.noteCommentDao().getById(commentId)!!.copy(content = "B comment", needsSync = true))
        b.db.indexedTextDao().upsert(b.db.indexedTextDao().getById(markId)!!.copy(endOffset = 6, needsSync = true))
        b.session.ensureUnlocked(b.uid)
        b.puller.pull(b.uid, b.guard())
        return a to b
    }

    private suspend fun conflict(d: TestDevice, id: String, type: SyncConflictType) = d.db.syncConflictDao().findUnresolved(id, type)!!

    @Test
    fun pullLogsOneConflictPerKindAndTheCountObservesIt() = runTest {
        val (_, b) = conflicted()
        assertEquals(3, b.db.syncConflictDao().observePendingCount().first())
        assertEquals("A comment", conflict(b, commentId, SyncConflictType.NoteComment).remotePreview)
    }

    @Test
    fun keepLocalNeedsNoNetworkAndTheNextPushOverwritesTheCloud() = runTest {
        val (a, b) = conflicted()
        val c = conflict(b, noteId, SyncConflictType.Note)
        b.identity.uid = null // offline/signed-out: keepLocal must not need the account
        resolver(b).resolve(c.id, ConflictResolution.KeepLocal)

        val saved = b.db.syncConflictDao().getById(c.id)!!
        assertEquals(ConflictResolution.KeepLocal, saved.resolution)
        assertNotNull(saved.resolvedAt)
        assertEquals(2, b.db.syncConflictDao().observePendingCount().first())
        val row = b.db.noteDao().getById(noteId)!!
        assertEquals("B edit", row.content)
        assertTrue(row.needsSync)

        b.identity.uid = "user-1"
        b.cloudSync.push(b.uid)
        a.cloudSync.fullSync(a.uid)
        assertEquals("B edit", a.db.noteDao().getById(noteId)!!.content)
    }

    @Test
    fun keepRemoteTakesTheOtherTextWithoutNetworkAndUploadsItSoEveryoneAgrees() = runTest {
        val (a, b) = conflicted()
        val c = conflict(b, noteId, SyncConflictType.Note)
        // B has already pushed over A by now (iOS order); the other text only survives in the conflict.
        b.cloudSync.push(b.uid)
        b.identity.uid = null // no network needed to take the saved text
        resolver(b).resolve(c.id, ConflictResolution.KeepRemote)

        val row = b.db.noteDao().getById(noteId)!!
        assertEquals("A edit", row.content)
        assertTrue(row.needsSync)
        assertEquals(ConflictResolution.KeepRemote, b.db.syncConflictDao().getById(c.id)!!.resolution)
        assertNull(b.db.syncConflictDao().findUnresolved(noteId, SyncConflictType.Note))

        b.identity.uid = "user-1"
        b.cloudSync.push(b.uid)
        a.cloudSync.fullSync(a.uid)
        assertEquals("A edit", a.db.noteDao().getById(noteId)!!.content)
    }

    @Test
    fun keepRemoteCoversCommentsAndMarks() = runTest {
        val (_, b) = conflicted()
        resolver(b).resolve(conflict(b, commentId, SyncConflictType.NoteComment).id, ConflictResolution.KeepRemote)
        resolver(b).resolve(conflict(b, markId, SyncConflictType.IndexedText).id, ConflictResolution.KeepRemote)

        assertEquals("A comment", b.db.noteCommentDao().getById(commentId)!!.content)
        assertTrue(b.db.noteCommentDao().getById(commentId)!!.needsSync)
        assertEquals(9, b.db.indexedTextDao().getById(markId)!!.endOffset)
        assertFalse(b.db.indexedTextDao().getById(markId)!!.needsSync)
    }

    @Test
    fun createCopyKeepsTheLocalRowAndAddsTheCloudVersionAsANewDirtyRow() = runTest {
        val (_, b) = conflicted()
        val c = conflict(b, noteId, SyncConflictType.Note)
        resolver(b).resolve(c.id, ConflictResolution.CreateCopy)

        val original = b.db.noteDao().getById(noteId)!!
        assertEquals("B edit", original.content)
        assertTrue(original.needsSync)
        val copy = b.db.noteDao().dirty().single { it.id != noteId }
        assertEquals("A edit", copy.content)
        assertNotEquals(noteId, copy.id)
        assertTrue(copy.needsSync)
        assertNull(copy.lastSyncedAt)

        // Both survive a push, so nothing is lost anywhere.
        b.cloudSync.push(b.uid)
        assertEquals("B edit", b.db.noteDao().getById(noteId)!!.content)
        assertNotNull(remote.server.get(FirestoreSchema.NOTES, copy.id))
        assertEquals(ConflictResolution.CreateCopy, b.db.syncConflictDao().getById(c.id)!!.resolution)
    }

    @Test
    fun cloudVersionGoneLeavesTheConflictOpenWithAnError() = runTest {
        val (_, b) = conflicted()
        val c = conflict(b, markId, SyncConflictType.IndexedText)
        remote.server.apply(com.stoneofhope.stoneofhope.data.remote.wire.WireWrite(FirestoreSchema.INDEXED_TEXTS, markId, emptyMap(), com.stoneofhope.stoneofhope.data.remote.wire.WriteMode.Delete))
        try {
            resolver(b).resolve(c.id, ConflictResolution.KeepRemote)
            fail("expected RemoteDocumentMissing")
        } catch (e: SyncConflictResolver.ResolveException) {
            assertEquals(SyncConflictResolver.ResolveException.RemoteDocumentMissing, e)
        }
        assertNull(b.db.syncConflictDao().getById(c.id)!!.resolvedAt)
        assertEquals(6, b.db.indexedTextDao().getById(markId)!!.endOffset)
    }

    @Test
    fun signedOutCannotFetchTheCloudHighlight() = runTest {
        val (_, b) = conflicted()
        val c = conflict(b, markId, SyncConflictType.IndexedText)
        b.identity.uid = null
        try {
            resolver(b).resolve(c.id, ConflictResolution.CreateCopy)
            fail("expected NotSignedIn")
        } catch (e: SyncConflictResolver.ResolveException) {
            assertEquals(SyncConflictResolver.ResolveException.NotSignedIn, e)
        }
        assertNull(b.db.syncConflictDao().getById(c.id)!!.resolvedAt)
    }

    @Test
    fun aRulingIsNotRaisedAgainWhenThePullRereadsTheSameCloudVersion() = runTest {
        val (_, b) = conflicted()
        val c = conflict(b, noteId, SyncConflictType.Note)
        resolver(b).resolve(c.id, ConflictResolution.KeepLocal)
        b.puller.pull(b.uid, b.guard()) // the row is still dirty and the cursor re-reads the same document
        assertNull(b.db.syncConflictDao().findUnresolved(noteId, SyncConflictType.Note))
        assertEquals(2, b.db.syncConflictDao().observePendingCount().first())
    }

    @Test
    fun aNewerCloudVersionAfterARulingIsANewConflict() = runTest {
        val (a, b) = conflicted()
        resolver(b).resolve(conflict(b, noteId, SyncConflictType.Note).id, ConflictResolution.KeepLocal)
        a.db.noteDao().upsert(a.db.noteDao().getById(noteId)!!.copy(content = "A edit 2", needsSync = true))
        a.cloudSync.push(a.uid)
        b.puller.pull(b.uid, b.guard())
        assertEquals("A edit 2", conflict(b, noteId, SyncConflictType.Note).remotePreview)
    }

    @Test
    fun aRepeatDetectionRefreshesTheOpenConflictInsteadOfAddingOne() = runTest {
        val (_, b) = conflicted()
        b.puller.pull(b.uid, b.guard()) // cursor sits on the doc (>=), so it is seen again
        assertEquals(3, b.db.syncConflictDao().observePendingCount().first())
    }
}
