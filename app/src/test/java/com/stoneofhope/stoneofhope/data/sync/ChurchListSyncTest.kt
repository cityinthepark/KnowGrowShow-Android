package com.stoneofhope.stoneofhope.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** "My churches" through `users/{uid}/settings/preferences.churches`, across devices and Web (CHURCH_IMPORT_PLAN §2.6, C4). */
@RunWith(AndroidJUnit4::class)
class ChurchListSyncTest {

    private val remote = FakeRemoteStore()
    private val vault = FakeKeyVault()
    private val devices = mutableListOf<TestDevice>()

    private fun device(name: String, uid: String? = "user-1") = TestDevice(name, remote, vault, uid).also { devices += it }

    @After
    fun tearDown() = devices.forEach { it.close() }

    private fun prefs() = remote.server.get(FakeRemoteStore.PREFS_COLLECTION, FakeRemoteStore.PREFS_DOC)

    @Suppress("UNCHECKED_CAST")
    private fun cloudChurches() = (prefs()?.get("churches") as? List<Map<String, Any?>>).orEmpty()

    private suspend fun TestDevice.followed() = churches.followed().map { it.churchKey to it.isPrimary }

    @Test
    fun importIsPushedBesideWebsFieldsAndRestoredElsewhere() = runTest {
        val webUpdatedAt = Instant.parse("2026-10-01T00:00:00Z")
        remote.server.put(FakeRemoteStore.PREFS_COLLECTION, FakeRemoteStore.PREFS_DOC, mapOf("preferredVersions" to listOf("NIV", "CUV"), "updatedAt" to webUpdatedAt))
        val a = device("a")
        a.churches.recordImport("riverside", "20260802")
        a.cloudSync.fullSync(a.uid)

        val doc = prefs()!!
        assertEquals(listOf("NIV", "CUV"), doc["preferredVersions"]) // Web's fields untouched
        assertEquals(webUpdatedAt, doc["updatedAt"])
        assertEquals("riverside", doc["primaryChurchKey"])
        assertTrue(doc["churchesUpdatedAt"] is Instant) // server time
        assertEquals(
            listOf(mapOf("churchKey" to "riverside", "isPrimary" to true, "importedAt" to "2026-10-26T09:00:00Z", "lastImportedVersion" to "20260802")),
            cloudChurches(),
        )
        assertTrue(a.db.onlineChurchDao().dirty().isEmpty())

        // Another device follows it (not imported there) with the same primary.
        val b = device("b")
        b.cloudSync.fullSync(b.uid)
        assertEquals(listOf("riverside" to true), b.followed())
        assertTrue(b.db.onlineChurchDao().dirty().isEmpty())
    }

    @Test
    fun primaryMovedOnOneDeviceReachesTheOther() = runTest {
        val a = device("a")
        val b = device("b")
        a.churches.recordImport("riverside", "1")
        a.cloudSync.fullSync(a.uid)
        b.cloudSync.fullSync(b.uid)

        b.clock.now = b.clock.now.plusSeconds(60)
        b.churches.recordImport("zion", "2")
        b.churches.setPrimary("zion")
        b.cloudSync.push(b.uid)
        assertEquals("zion", prefs()!!["primaryChurchKey"])

        a.cloudSync.autoSync(a.uid)
        assertEquals(listOf("zion" to true, "riverside" to false), a.followed())
        // Nothing changed now: no rewrite.
        val stamp = prefs()!!["churchesUpdatedAt"]
        a.cloudSync.fullSync(a.uid)
        b.cloudSync.fullSync(b.uid)
        assertEquals(stamp, prefs()!!["churchesUpdatedAt"])
    }

    @Test
    fun unfollowElsewhereRemovesHere() = runTest {
        val a = device("a")
        val b = device("b")
        a.churches.recordImport("riverside", "1")
        a.clock.now = a.clock.now.plusSeconds(60)
        a.churches.recordImport("zion", "1")
        a.cloudSync.fullSync(a.uid)
        b.cloudSync.fullSync(b.uid)
        assertEquals(listOf("riverside" to true, "zion" to false), b.followed())

        a.clock.now = a.clock.now.plusSeconds(60)
        a.churches.remove("riverside")
        a.cloudSync.push(a.uid)
        assertEquals(listOf("zion"), cloudChurches().map { it["churchKey"] })
        assertNull(a.db.onlineChurchDao().getByKey("riverside")) // tombstone gone once pushed

        b.cloudSync.fullSync(b.uid)
        assertEquals(listOf("zion" to true), b.followed())
        assertNull(b.db.onlineChurchDao().getByKey("riverside"))
    }

    @Test
    fun firstSignInAddsLocalChurchesWithoutTakingThePrimary() = runTest {
        val a = device("a")
        a.churches.recordImport("riverside", "1")
        a.cloudSync.fullSync(a.uid)

        val b = device("b")
        b.clock.now = b.clock.now.plusSeconds(300)
        b.churches.recordImport("grace", "3") // imported before signing in: primary on b alone
        b.cloudSync.fullSync(b.uid)
        assertEquals(listOf("riverside" to true, "grace" to false), b.followed())
        assertEquals(listOf("riverside", "grace"), cloudChurches().map { it["churchKey"] })
    }

    @Test
    fun webChangesArriveOnAutoSync() = runTest {
        val a = device("a")
        a.churches.recordImport("riverside", "1")
        a.cloudSync.fullSync(a.uid)

        // Web adds a church and makes it primary, without stamping any device.
        remote.server.put(
            FakeRemoteStore.PREFS_COLLECTION, FakeRemoteStore.PREFS_DOC,
            prefs()!! + mapOf(
                "churches" to listOf(
                    mapOf("churchKey" to "riverside", "isPrimary" to false, "importedAt" to "2026-10-26T09:00:00Z"),
                    mapOf("churchKey" to "zion", "isPrimary" to true, "importedAt" to "2026-10-27T09:00:00Z"),
                ),
                "primaryChurchKey" to "zion",
            ),
        )
        a.cloudSync.autoSync(a.uid)
        assertEquals(listOf("zion" to true, "riverside" to false), a.followed())
        assertEquals("1", a.churches.followed().first { it.churchKey == "riverside" }.lastImportedVersion) // kept the newer local version
    }

    @Test
    fun pushReadsTheCloudListOnlyWhenSomethingChanged() = runTest {
        val a = device("a")
        a.cloudSync.push(a.uid)
        assertEquals(0, remote.preferenceReads.get())
        assertNull(prefs()) // nothing followed, nothing written

        a.churches.recordImport("riverside", "1")
        a.cloudSync.push(a.uid)
        assertEquals(1, remote.preferenceReads.get())
        a.cloudSync.push(a.uid)
        assertEquals(1, remote.preferenceReads.get())
    }

    @Test
    fun cloudResetMarksChurchesForReupload() = runTest {
        val a = device("a")
        a.churches.recordImport("riverside", "1")
        a.cloudSync.fullSync(a.uid)
        assertTrue(a.db.onlineChurchDao().dirty().isEmpty())
        a.ops.markAllUserScopedDataForResync()
        assertEquals(listOf("riverside"), a.db.onlineChurchDao().dirty().map { it.churchKey })
        // After a wipe the re-uploaded row is kept, not mistaken for "removed elsewhere".
        remote.wipe()
        a.ops.markAllUserScopedDataForResync()
        a.cloudSync.push(a.uid)
        assertEquals(listOf("riverside"), cloudChurches().map { it["churchKey"] })
        assertFalse(a.followed().isEmpty())
    }
}
