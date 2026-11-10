package com.stoneofhope.stoneofhope.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A photo note's thumbnail travels through Storage (iOS `NoteImageCloudStorage`); its text goes through Firestore. */
@RunWith(AndroidJUnit4::class)
class NoteImageSyncTest {

    private val remote = FakeRemoteStore()
    private val vault = FakeKeyVault()
    private val devices = mutableListOf<TestDevice>()
    private val t0 = Instant.parse("2026-03-01T10:00:00.123456Z")
    private val noteId = "64a0000000000000000000b1"
    private val bytes = byteArrayOf(9, 8, 7, 6)

    private fun device(name: String) = TestDevice(name, remote, vault).also { devices += it }

    @After
    fun tearDown() = devices.forEach { it.close() }

    private fun TestDevice.photoNote(id: String = noteId, thumb: String = "THUMB.jpg"): NoteEntity {
        imageStore.file(thumb).writeBytes(bytes)
        imageStore.file("FULL.jpg").writeBytes(byteArrayOf(1))
        return NoteEntity(
            id = id, noteType = NoteType.Image, content = "", createdAt = t0, updatedAt = t0,
            imagePath = "FULL.jpg", thumbnailPath = thumb, needsSync = true,
        ).also { runBlockingUpsert(it) }
    }

    private fun TestDevice.runBlockingUpsert(note: NoteEntity) = kotlinx.coroutines.runBlocking { db.noteDao().upsert(note) }

    @Test fun aPushedPhotoNoteSendsItsThumbnailAndAnotherDeviceGetsItForBothFiles() = runTest {
        val a = device("a")
        a.photoNote()
        a.cloudSync.fullSync(a.uid)
        assertArrayEquals(bytes, remote.images.objects["${a.uid}/$noteId"])

        val b = device("b")
        b.cloudSync.fullSync(b.uid)
        val pulled = b.db.noteDao().getById(noteId)!!
        assertEquals("THUMB.jpg", pulled.thumbnailPath)
        assertArrayEquals(bytes, b.imageStore.file("THUMB.jpg").readBytes())
        // The original stays on the phone that took it; here the thumbnail stands in for it.
        assertArrayEquals(bytes, b.imageStore.file("FULL.jpg").readBytes())
    }

    @Test fun aFailedUploadKeepsTheNoteDirtyAndTheNextPushRetries() = runTest {
        val a = device("a")
        a.photoNote()
        remote.images.failingUploads += noteId
        a.cloudSync.fullSync(a.uid)
        assertTrue(a.db.noteDao().getById(noteId)!!.needsSync)
        assertTrue(remote.images.objects.isEmpty())

        remote.images.failingUploads.clear()
        a.cloudSync.fullSync(a.uid)
        assertFalse(a.db.noteDao().getById(noteId)!!.needsSync)
        assertArrayEquals(bytes, remote.images.objects["${a.uid}/$noteId"])
    }

    @Test fun oneFailingPhotoDoesNotStopTheOthers() = runTest {
        val a = device("a")
        a.photoNote()
        a.photoNote(id = "64a0000000000000000000b2", thumb = "THUMB2.jpg")
        remote.images.failingUploads += noteId
        a.cloudSync.fullSync(a.uid)
        assertEquals(setOf("${a.uid}/64a0000000000000000000b2"), remote.images.objects.keys)
        assertTrue(a.db.noteDao().getById(noteId)!!.needsSync)
        assertFalse(a.db.noteDao().getById("64a0000000000000000000b2")!!.needsSync)
    }

    @Test fun deletingThePhotoNoteRemovesItsThumbnailFromStorage() = runTest {
        val a = device("a")
        val note = a.photoNote()
        a.cloudSync.fullSync(a.uid)
        assertEquals(1, remote.images.objects.size)

        a.db.noteDao().upsert(note.copy(deletedAt = t0.plusSeconds(60), isDeleted = true, updatedAt = t0.plusSeconds(60), needsSync = true))
        a.cloudSync.fullSync(a.uid)
        assertTrue(remote.images.objects.isEmpty())
    }

    @Test fun aNoteWithoutAThumbnailFileUploadsNothing() = runTest {
        val a = device("a")
        a.db.noteDao().upsert(
            NoteEntity(id = noteId, noteType = NoteType.Image, content = "", createdAt = t0, updatedAt = t0, imagePath = "GONE.jpg", thumbnailPath = "GONE-T.jpg", needsSync = true),
        )
        a.cloudSync.fullSync(a.uid)
        assertTrue(remote.images.objects.isEmpty())
        assertFalse(a.db.noteDao().getById(noteId)!!.needsSync)
    }

    @Test fun textNotesNeverTouchStorage() = runTest {
        val a = device("a")
        a.db.noteDao().upsert(NoteEntity(id = noteId, noteType = NoteType.Note, content = "hi", createdAt = t0, updatedAt = t0, thumbnailPath = "X.jpg", needsSync = true))
        a.imageStore.file("X.jpg").writeBytes(bytes)
        a.cloudSync.fullSync(a.uid)
        assertTrue(remote.images.objects.isEmpty())
    }

    @Test fun aNoteStorageHasNothingForIsNotAskedAgainForAnHour() = runTest {
        val a = device("a")
        a.photoNote()
        a.cloudSync.fullSync(a.uid)
        remote.images.objects.clear() // an older note whose thumbnail never reached Storage

        val b = device("b")
        b.cloudSync.fullSync(b.uid)
        val first = remote.images.downloads.get()
        assertEquals(1, first)
        assertFalse(b.imageStore.exists("THUMB.jpg"))

        b.cloudSync.fullSync(b.uid)
        assertEquals(first, remote.images.downloads.get())

        b.clock.now = b.clock.now.plusSeconds(3601)
        b.cloudSync.fullSync(b.uid)
        assertEquals(first + 1, remote.images.downloads.get())
    }

    @Test fun picturesThisDeviceAlreadyHasAreNotFetched() = runTest {
        val a = device("a")
        a.photoNote()
        a.cloudSync.fullSync(a.uid)
        remote.images.downloads.set(0)
        a.cloudSync.fullSync(a.uid)
        assertEquals(0, remote.images.downloads.get())
    }
}
