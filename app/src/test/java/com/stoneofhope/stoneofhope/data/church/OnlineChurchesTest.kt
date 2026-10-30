package com.stoneofhope.stoneofhope.data.church

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.OnlineChurchEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** "My churches": first import is primary, one primary at a time, the earliest takes over (CHURCH_IMPORT_PLAN §2.6). */
@RunWith(AndroidJUnit4::class)
class OnlineChurchesTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val t0 = Instant.parse("2026-10-30T08:00:00Z")
    private val clock = MutableClock(t0)
    private val churches = OnlineChurches(db, clock)
    private val dao = db.onlineChurchDao()

    @After
    fun tearDown() = db.close()

    private fun at(minutes: Long) { clock.now = t0.plusSeconds(minutes * 60) }

    private suspend fun primary() = churches.followed().filter { it.isPrimary }.map { it.churchKey }

    /** Pretend the last change was pushed. */
    private suspend fun synced(key: String) = dao.upsert(dao.getByKey(key)!!.withSyncState(false, clock.now))

    @Test
    fun firstImportIsPrimaryAndLaterOnesAreNot() = runTest {
        churches.recordImport("riverside", "20260802")
        val r = dao.getByKey("riverside")!!
        assertTrue(r.isPrimary)
        assertEquals(t0, r.importedAt)
        assertEquals("20260802", r.lastImportedVersion)
        assertTrue(r.needsSync)

        at(5)
        churches.recordImport("zion", "20260901")
        assertEquals(listOf("riverside"), primary())
        assertEquals(listOf("riverside", "zion"), churches.followed().map { it.churchKey })
    }

    @Test
    fun setPrimaryMovesTheOnlyPrimary() = runTest {
        churches.recordImport("riverside", "1")
        at(1); churches.recordImport("zion", "1")
        synced("riverside"); synced("zion")

        at(10)
        assertTrue(churches.setPrimary("zion"))
        assertEquals(listOf("zion"), primary())
        for (key in listOf("riverside", "zion")) {
            val row = dao.getByKey(key)!!
            assertTrue(row.needsSync)
            assertEquals(t0.plusSeconds(600), row.updatedAt)
        }

        // Already primary: nothing to change or push.
        synced("riverside"); synced("zion")
        assertTrue(churches.setPrimary("zion"))
        assertTrue(dao.dirty().isEmpty())
        assertFalse(churches.setPrimary("unknown"))
        assertEquals(listOf("zion"), primary())
    }

    @Test
    fun newVersionSyncsWithoutTouchingThePrimaryClock() = runTest {
        churches.recordImport("riverside", "20260802")
        synced("riverside")
        at(30)
        churches.recordImport("riverside", "20260802")
        assertTrue(dao.dirty().isEmpty()) // same version: no push
        churches.recordImport("riverside", "20260809")
        val row = dao.getByKey("riverside")!!
        assertEquals("20260809", row.lastImportedVersion)
        assertTrue(row.needsSync)
        assertEquals(t0, row.updatedAt)
        assertEquals(t0, row.importedAt)
    }

    @Test
    fun removingThePrimaryHandsItToTheEarliest() = runTest {
        churches.recordImport("riverside", "1")
        at(1); churches.recordImport("zion", "1")
        at(2); churches.recordImport("grace", "1")
        at(3); churches.setPrimary("grace")
        synced("grace")

        at(4); churches.remove("grace")
        assertEquals(listOf("riverside"), primary())
        val gone = dao.getByKey("grace")!!
        assertNotNull(gone.deletedAt)
        assertFalse(gone.isPrimary)
        assertTrue(gone.needsSync) // the tombstone is pushed
        assertEquals(listOf("riverside", "zion"), churches.followed().map { it.churchKey })

        churches.remove("riverside"); churches.remove("zion")
        assertTrue(churches.followed().isEmpty())

        // Importing again follows it afresh, and as the only one it is primary.
        at(9); churches.recordImport("grace", "2")
        val back = dao.getByKey("grace")!!
        assertNull(back.deletedAt)
        assertTrue(back.isPrimary)
        assertEquals(t0.plusSeconds(540), back.importedAt)
    }

    @Test
    fun twoPrimariesKeepTheLatestChoice() = runTest {
        // As after merging two devices that each picked a primary (C4).
        dao.upsert(OnlineChurchEntity("riverside", isPrimary = true, importedAt = t0, updatedAt = t0.plusSeconds(60)))
        dao.upsert(OnlineChurchEntity("zion", isPrimary = true, importedAt = t0, updatedAt = t0.plusSeconds(120)))
        churches.recordImport("riverside", "2")
        assertEquals(listOf("zion"), primary())
    }
}
