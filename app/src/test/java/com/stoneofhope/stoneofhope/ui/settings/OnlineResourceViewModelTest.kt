package com.stoneofhope.stoneofhope.ui.settings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.church.ChurchCatalog
import com.stoneofhope.stoneofhope.data.church.ChurchFeed
import com.stoneofhope.stoneofhope.data.church.ChurchImportResult
import com.stoneofhope.stoneofhope.data.church.ChurchProfile
import com.stoneofhope.stoneofhope.data.church.ChurchProfileStore
import com.stoneofhope.stoneofhope.data.church.ChurchSermonImporter
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** iOS `OnlineResourceDetailView`: button choice, overlay, and the result dialog (IOS_ISSUES M1, M3, M4). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OnlineResourceViewModelTest {

    private class Feed : ChurchFeed {
        val files = mutableMapOf<String, String>()
        val requested = mutableListOf<String>()
        override suspend fun get(url: String): String? {
            requested += url
            return files[url]
        }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = context.getSharedPreferences("online-vm-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val base = "https://raw.example.com/sermons/"
    private val riverside = ChurchProfile(churchKey = "riverside", name = "Riverside Calvary Chapel", sermonImportRepoURL = base)
    private val store = ChurchProfileStore(object : ChurchCatalog { override suspend fun fetchChurches() = null }, prefs) { listOf(riverside) }
    private val feed = Feed()
    private val importer = ChurchSermonImporter(db, store, feed, prefs)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun viewModel() = OnlineResourceViewModel(SavedStateHandle(mapOf(OnlineResourceViewModel.ARG_KEY to "riverside")), store, importer)

    private fun repo() {
        feed.files[base + "manifest.json"] = """{"latestVersion":"20260802","files":["series_20260802.json","20260701.json","20260802.json"]}"""
        feed.files[base + "20260701.json"] = "[]"
        feed.files[base + "series_20260802.json"] =
            """[{"id":"6a166ac5f315e27a81e5b126","churchKey":"riverside","title":"Romans","startedOn":"20260528","isFinished":false}]"""
        feed.files[base + "20260802.json"] =
            """[{"id":"6a78f9985811ac6f86be436e","churchKey":"riverside","preachedOn":"20260802","seriesId":"6a166ac5f315e27a81e5b126","title":"Romans 1"}]"""
    }

    private fun OnlineResourceViewModel.finish(): OnlineResourceState = runBlocking {
        withTimeout(10_000) { state.first { !it.importing && it.message != null } }
    }

    @Test
    fun importAllThenUpdates() {
        repo()
        val vm = viewModel()
        assertEquals("Riverside Calvary Chapel", vm.state.value.profile?.name)
        assertFalse(vm.state.value.imported) // "Import all Sermons"

        vm.import()
        val first = vm.finish()
        assertEquals(ImportMessage(R.string.online_import_done, 1 to 1), first.message)
        assertTrue(first.imported) // now "Import Latest Updates"

        vm.dismissMessage()
        assertNull(vm.state.value.message)
        feed.requested.clear()
        vm.import()
        assertEquals(ImportMessage(R.string.online_import_nothing_new), vm.finish().message)
        // Updates only: files from the imported version on, not the older 20260701.json.
        assertFalse(feed.requested.contains(base + "20260701.json"))
        assertTrue(feed.requested.contains(base + "20260802.json"))
    }

    @Test
    fun offlineSaysSoAndKeepsImportAll() {
        val vm = viewModel()
        vm.import()
        val state = vm.finish()
        assertEquals(ImportMessage(R.string.online_import_unreachable), state.message)
        assertFalse(state.imported)
    }

    @Test
    fun messages() {
        assertEquals(ImportMessage(R.string.online_import_unavailable), ChurchImportResult.NotImportable.message())
        assertEquals(
            ImportMessage(R.string.online_import_done, 3 to 7),
            ChurchImportResult.Done(newSeries = 1, updatedSeries = 2, newSermons = 5, updatedSermons = 2).message(),
        )
        assertEquals(
            ImportMessage(R.string.online_import_done, 0 to 4, R.string.online_import_incomplete),
            ChurchImportResult.Done(newSermons = 4, incompleteFiles = listOf("20260802.json")).message(),
        )
        assertEquals(
            ImportMessage(R.string.online_import_nothing_new, retryHint = R.string.online_import_incomplete),
            ChurchImportResult.Done(incompleteFiles = listOf("20260802.json")).message(),
        )
    }
}
