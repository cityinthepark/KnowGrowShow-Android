package com.stoneofhope.stoneofhope.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.account.AccountService.SignOutResult
import com.stoneofhope.stoneofhope.data.account.RegisteredDevice
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Account page's actions: "Sign Out of Cloud" (iOS `CloudAccountService`) and the linked devices (iOS `DeviceRegistryService`). */
@RunWith(AndroidJUnit4::class)
class AccountCloudTest {

    private val remote = FakeRemoteStore()
    private val vault = FakeKeyVault()
    private val devices = mutableListOf<TestDevice>()
    private val t0 = Instant.parse("2026-03-01T10:00:00.123456Z")
    private val noteId = "64a0000000000000000000c1"

    private fun device(name: String) = TestDevice(name, remote, vault).also { devices += it }

    @After
    fun tearDown() = devices.forEach { it.close() }

    private suspend fun syncedDevice(): TestDevice {
        val a = device("a")
        a.db.noteDao().upsert(NoteEntity(id = noteId, noteType = NoteType.Note, content = "hello", createdAt = t0, updatedAt = t0, needsSync = true))
        a.photo()
        a.cloudSync.fullSync(a.uid)
        assertEquals(2, remote.server.count(FirestoreSchema.NOTES))
        assertEquals(1, remote.images.objects.size)
        return a
    }

    private suspend fun TestDevice.photo() {
        imageStore.file("T.jpg").writeBytes(byteArrayOf(1, 2))
        db.noteDao().upsert(
            NoteEntity(id = "64a0000000000000000000c2", noteType = NoteType.Image, content = "", createdAt = t0, updatedAt = t0, imagePath = "F.jpg", thumbnailPath = "T.jpg", needsSync = true),
        )
    }

    @Test fun signingOutOfTheCloudClearsTheCloudCopyAndKeepsThisDevicesData() = runTest {
        val a = syncedDevice()
        assertEquals(SignOutResult.Success, a.account.signOutOfCloud())

        assertEquals(0, remote.server.count(FirestoreSchema.NOTES))
        assertEquals(0, remote.server.count(FakeRemoteStore.DEVICES))
        assertEquals(0, remote.server.count(FakeRemoteStore.META))
        assertTrue(remote.images.objects.isEmpty())

        // Everything stays on this device, marked for a fresh upload to whichever account signs in next.
        val kept = a.db.noteDao().getById(noteId)!!
        assertEquals("hello", kept.content)
        assertTrue(kept.needsSync)
        assertNull(a.identity.uid)
        assertNull(a.flags.pendingWipeUid)
        assertFalse(a.flags.isCloudSyncPaused)
        assertFalse(a.flags.hasLinkedCloudAccount)
        assertTrue(a.imageStore.exists("T.jpg"))
    }

    @Test fun theWipeNamesEveryPhotoNoteSoThumbnailsGoEvenWhenStorageCannotBeListed() = runTest {
        val a = syncedDevice()
        a.account.signOutOfCloud()
        assertEquals(listOf("64a0000000000000000000c2"), remote.images.wipedKnownIds.toList())
    }

    @Test fun offlineChangesNothing() = runTest {
        val a = syncedDevice()
        a.network.isOnline.value = false
        assertEquals(SignOutResult.NeedsNetwork, a.account.signOutOfCloud())

        assertEquals(2, remote.server.count(FirestoreSchema.NOTES))
        assertNotNull(a.identity.uid)
        assertNull(a.flags.pendingWipeUid)
        assertFalse(a.flags.isCloudSyncPaused)
    }

    @Test fun aPartialWipeKeepsSyncPausedAndTheMarkerUntilARetryFinishes() = runTest {
        val a = syncedDevice()
        remote.failingWipes += FirestoreSchema.NOTE_COMMENTS
        assertEquals(SignOutResult.Incomplete, a.account.signOutOfCloud())

        // Not signed out, sync paused, and the unfinished clean-up is remembered.
        assertNotNull(a.identity.uid)
        assertEquals(a.uid, a.flags.pendingWipeUid)
        assertTrue(a.flags.isCloudSyncPaused)
        assertTrue(a.account.hasPendingWipe)

        remote.failingWipes.clear()
        assertEquals(SignOutResult.Success, a.account.signOutOfCloud())
        assertNull(a.flags.pendingWipeUid)
        assertFalse(a.flags.isCloudSyncPaused)
        assertNull(a.identity.uid)
        assertEquals(0, remote.server.count(FirestoreSchema.NOTES))
    }

    @Test fun aFailedThumbnailWipeIsIncompleteToo() = runTest {
        val a = syncedDevice()
        remote.images.deleteAllFails = true
        assertEquals(SignOutResult.Incomplete, a.account.signOutOfCloud())
        assertNotNull(a.identity.uid)
    }

    @Test fun signingOutWhenNobodyIsSignedInIsAlreadyDone() = runTest {
        val a = device("a")
        a.identity.uid = null
        assertEquals(SignOutResult.Success, a.account.signOutOfCloud())
    }

    @Test fun whenTheSessionIsGoneThePendingWipeFinishesLocally() = runTest {
        val a = syncedDevice()
        remote.failingWipes += FirestoreSchema.NOTE_COMMENTS
        a.account.signOutOfCloud()
        // The Firebase session expired meanwhile: the rules would refuse the delete, so finish locally.
        a.identity.uid = null
        remote.failingWipes.clear()
        assertEquals(SignOutResult.Success, a.account.signOutOfCloud())
        assertNull(a.flags.pendingWipeUid)
        assertFalse(a.flags.isCloudSyncPaused)
        assertTrue(a.db.noteDao().getById(noteId)!!.needsSync)
    }

    @Test fun anotherDeviceFollowsASignOutByLeavingTheCloudWithItsDataKept() = runTest {
        val a = syncedDevice()
        val b = device("b")
        b.cloudSync.fullSync(b.uid)
        assertEquals("hello", b.db.noteDao().getById(noteId)!!.content)

        a.account.signOutOfCloud()
        b.cloudSync.fullSync(b.uid)
        assertNull(b.identity.uid)
        assertEquals("hello", b.db.noteDao().getById(noteId)!!.content)
    }

    // MARK: Linked devices

    @Test fun theDeviceListShowsThisDeviceFirstThenWebThenTheMostRecentPeers() = runTest {
        val a = device("a")
        val old = Instant.parse("2026-01-01T00:00:00Z")
        remote.mergeDevice(a.uid, "web", mapOf("modelIdentifier" to "web", "lastUpdatedAt" to old))
        remote.mergeDevice(a.uid, "PEER-OLD", mapOf("modelIdentifier" to "iPhone17,1", "systemVersion" to "iOS 26", "lastUpdatedAt" to old))
        remote.mergeDevice(a.uid, "PEER-NEW", mapOf("modelIdentifier" to "iPad", "systemVersion" to "iOS 26", "lastUpdatedAt" to old.plusSeconds(86_400)))

        val list = a.devices.list(a.uid)
        assertEquals(listOf(a.state.deviceId, "web", "PEER-NEW", "PEER-OLD"), list.map { it.deviceId })
        assertTrue(list[0].isCurrentDevice)
        assertTrue(list[1].isWeb)
        assertEquals(listOf(false, false, true, true), list.map { it.canRemove }.let { listOf(it[0], it[1], it[2], it[3]) })
    }

    @Test fun thisDeviceIsListedEvenBeforeTheCloudHasARowForIt() = runTest {
        val a = device("a")
        a.devices.list(a.uid)
        assertNotNull(remote.server.get(FakeRemoteStore.DEVICES, a.state.deviceId)) // registered on the way
    }

    @Test fun removingAPeerDeletesOnlyItsRegistryRow() = runTest {
        val a = device("a")
        remote.mergeDevice(a.uid, "PEER", mapOf("modelIdentifier" to "iPad", "lastUpdatedAt" to Instant.parse("2026-01-01T00:00:00Z")))
        val peer = a.devices.list(a.uid).single { it.deviceId == "PEER" }
        a.devices.remove(a.uid, peer)
        assertEquals(listOf(a.state.deviceId), a.devices.list(a.uid).map { it.deviceId })
    }

    @Test fun thisDeviceAndWebCannotBeRemoved() = runTest {
        val a = device("a")
        remote.mergeDevice(a.uid, "web", mapOf("modelIdentifier" to "web"))
        val list = a.devices.list(a.uid)
        for (device in list) {
            val failed = runCatching { a.devices.remove(a.uid, device) }.isFailure
            assertTrue("${device.deviceId} must not be removable", failed)
        }
        assertEquals(2, a.devices.list(a.uid).size)
    }

    @Test fun aPeerNotHeardFromForThirtyDaysMayBeInactive() {
        val now = Instant.parse("2026-03-01T00:00:00Z")
        fun peer(at: Instant?) = RegisteredDevice("P", "iPad", "iOS", at, isCurrentDevice = false)
        assertTrue(peer(null).isPossiblyInactive(now))
        assertTrue(peer(now.minusSeconds(30L * 86_400)).isPossiblyInactive(now))
        assertFalse(peer(now.minusSeconds(29L * 86_400)).isPossiblyInactive(now))
    }
}
