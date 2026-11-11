package com.stoneofhope.stoneofhope.ui.moments

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.FakeKeyVault
import com.stoneofhope.stoneofhope.data.sync.FakeRemoteStore
import com.stoneofhope.stoneofhope.data.sync.TestDevice
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opening Moments syncs quietly, at most every 30 seconds; and Room's dirty counter drives the after-edit upload. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MomentsAutoSyncTest {
    private val remote = FakeRemoteStore()
    private val device = TestDevice("moments", remote, FakeKeyVault())
    private val t0 = Instant.parse("2026-03-01T10:00:00Z")
    private val id = "64a0000000000000000000aa"

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        Dispatchers.resetMain()
        device.close()
    }

    private fun viewModel() = MomentsViewModel(
        device.db, device.clock, device.identity, device.cloudSync,
        BibleVersionPreferences(
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("moments-test", Context.MODE_PRIVATE),
        ) { BibleLanguage.English },
    )

    private suspend fun dirtyNote(content: String) = device.db.noteDao().upsert(
        NoteEntity(id = id, noteType = NoteType.Prayer, content = content, createdAt = t0, updatedAt = t0, needsSync = true),
    )

    private suspend fun until(what: String, check: suspend () -> Boolean) = withTimeout(10_000) {
        while (!check()) delay(20)
    }

    @Test
    fun openingTheTabSyncsAndAnImmediateReopenDoesNot() = runBlocking {
        val vm = viewModel()
        dirtyNote("first")
        vm.autoSyncOnAppear()
        until("uploaded") { remote.server.get(FirestoreSchema.NOTES, id) != null }
        until("clean") { !device.db.noteDao().getById(id)!!.needsSync }
        val commits = remote.commits.get()

        dirtyNote("second")
        device.clock.now = device.clock.now.plus(Duration.ofSeconds(10))
        vm.autoSyncOnAppear() // too soon
        delay(500)
        assertEquals(commits, remote.commits.get())
        assertEquals(true, device.db.noteDao().getById(id)!!.needsSync)

        device.clock.now = device.clock.now.plus(Duration.ofSeconds(25)) // 35 s after the first
        vm.autoSyncOnAppear()
        until("second upload") { !device.db.noteDao().getById(id)!!.needsSync }
        assertNotNull(remote.server.get(FirestoreSchema.NOTES, id))
    }

    @Test
    fun signedOutUsersDoNothing() = runBlocking {
        device.identity.uid = null
        dirtyNote("local only")
        viewModel().autoSyncOnAppear()
        delay(500)
        assertNull(remote.server.get(FirestoreSchema.NOTES, id))
        assertEquals(true, device.db.noteDao().getById(id)!!.needsSync)
    }

    @Test
    fun theDirtyCountsTrackSavesAndPushes() = runBlocking {
        assertEquals(0, device.db.noteDao().observeDirtyCount().first())
        dirtyNote("x")
        assertEquals(1, device.db.noteDao().observeDirtyCount().first())
        device.session.ensureUnlocked(device.uid) // a push-only run never fetches the key itself
        device.cloudSync.push(device.uid)
        assertEquals(0, device.db.noteDao().observeDirtyCount().first())
        assertFalse(device.db.noteDao().getById(id)!!.needsSync)
    }
}
