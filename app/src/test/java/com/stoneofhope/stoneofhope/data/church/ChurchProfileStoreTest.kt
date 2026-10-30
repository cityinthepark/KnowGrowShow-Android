package com.stoneofhope.stoneofhope.data.church

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Catalog first, then the last catalog seen, then the bundled profiles (docs/CHURCH_IMPORT_PLAN.md §2.3). */
@RunWith(AndroidJUnit4::class)
class ChurchProfileStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("church-profile-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val bundledRiverside = ChurchProfile(churchKey = "riverside", name = "Riverside (bundled)")

    private class Catalog(var churches: List<ChurchProfile>?) : ChurchCatalog {
        override suspend fun fetchChurches() = churches
    }

    @Test
    fun bundledAssetMatchesIOSProfile() {
        val store = ChurchProfileStore(Catalog(null), context)
        val riverside = store.cached().single()
        assertEquals("riverside", riverside.churchKey)
        assertEquals("Riverside Calvary Chapel", riverside.name)
        assertEquals("https://raw.githubusercontent.com/stoneofhopeapp/sermons-riverside/main/", riverside.importBaseURL)
        assertEquals("riversideLogo", riverside.logoImageName)
    }

    @Test
    fun catalogWinsAndIsCachedForOffline() = runTest {
        val catalog = Catalog(
            listOf(
                ChurchProfile(churchKey = "zion", name = "Zion"),
                ChurchProfile(churchKey = "riverside", name = "Riverside (cloud)"),
                ChurchProfile(churchKey = "old", name = "Old", enabled = false),
            ),
        )
        val store = ChurchProfileStore(catalog, prefs) { listOf(bundledRiverside) }
        assertEquals(listOf("riverside", "zion"), store.refresh().map { it.churchKey })

        catalog.churches = null // offline
        assertEquals(listOf("Riverside (cloud)", "Zion"), store.refresh().map { it.name })
        assertNull(store.profile("old"))
    }

    @Test
    fun bundledWhenNothingElse() = runTest {
        val store = ChurchProfileStore(Catalog(emptyList()), prefs) { listOf(bundledRiverside) }
        assertEquals(listOf(bundledRiverside), store.refresh())
        assertEquals(bundledRiverside, store.profile("riverside"))
    }

    @Test
    fun catalogDocumentsMapLikeIOS() {
        val doc = mapOf(
            "schemaVersion" to 1L, "churchKey" to "riverside", "name" to "Riverside", "enabled" to true,
            "isUserData" to false, "subtitle" to "Know, Grow, and Show", "sermonImportRepoURL" to "https://r/x",
            "updatedAt" to "2026-06-04T00:00:00Z", "logoURL" to "",
        )
        val p = ChurchProfile.fromCatalog("riverside", doc)!!
        assertEquals("Know, Grow, and Show", p.subtitle)
        assertEquals("https://r/x/", p.importBaseURL)
        assertNull(p.logoURL)
        // YouTube channel rows share the collection but are not churches.
        assertNull(ChurchProfile.fromCatalog("UCabc", doc + ("resourceType" to "youtubeChannel")))
        assertNull(ChurchProfile.fromCatalog("UCabc", doc + ("kind" to "youtubeChannel")))
        assertEquals("riverside", ChurchProfile.fromCatalog("riverside", doc + ("resourceType" to "church"))?.churchKey)
        assertNull(ChurchProfile.fromCatalog("x", mapOf("enabled" to true)))
    }

    private class PagedFeed(val pages: Map<String, String?>) : ChurchFeed {
        val asked = mutableListOf<String>()
        override suspend fun get(url: String): String? {
            asked += url
            return pages[url]
        }
    }

    private val base = "https://firestore.googleapis.com/v1/projects/p/databases/(default)/documents/onlineResourceProfiles"

    // Same shape as the production response (curl of the REST endpoint, 2026-10-30).
    private val page1 = """
        {"documents": [
          {"name": "projects/p/databases/(default)/documents/onlineResourceProfiles/UCabc",
           "fields": {"resourceType": {"stringValue": "youtubeChannel"}, "name": {"stringValue": "Channel"}}},
          {"name": "projects/p/databases/(default)/documents/onlineResourceProfiles/riverside",
           "fields": {"name": {"stringValue": "Riverside Calvary Chapel"}, "enabled": {"booleanValue": true},
             "schemaVersion": {"integerValue": "1"}, "isUserData": {"booleanValue": false},
             "lastSyncError": {"nullValue": null}, "updatedAt": {"timestampValue": "2026-06-04T00:00:00Z"},
             "sermonImportRepoURL": {"stringValue": "https://raw.githubusercontent.com/stoneofhopeapp/sermons-riverside/main/"}},
           "createTime": "2026-06-04T00:00:00Z"}
        ], "nextPageToken": "a b"}
    """.trimIndent()
    private val page2 = """
        {"documents": [{"name": "projects/p/databases/(default)/documents/onlineResourceProfiles/zion",
          "fields": {"name": {"stringValue": "Zion"}, "enabled": {"booleanValue": false}}}]}
    """.trimIndent()

    @Test
    fun restCatalogReadsEveryPageWithoutSignIn() = runTest {
        val feed = PagedFeed(mapOf("$base?pageSize=300" to page1, "$base?pageSize=300&pageToken=a+b" to page2))
        val churches = RestChurchCatalog(feed, base).fetchChurches()!!
        assertEquals(listOf("riverside", "zion"), churches.map { it.churchKey })
        val riverside = churches.first()
        assertEquals("2026-06-04T00:00:00Z", riverside.updatedAt)
        assertEquals("https://raw.githubusercontent.com/stoneofhopeapp/sermons-riverside/main/", riverside.importBaseURL)
        assertEquals(false, churches[1].enabled)
    }

    @Test
    fun restCatalogOfflineFallsBackToBundled() = runTest {
        val failed = PagedFeed(mapOf("$base?pageSize=300" to page1)) // page 2 missing: the whole read fails
        assertNull(RestChurchCatalog(failed, base).fetchChurches())
        assertNull(RestChurchCatalog.parsePage("<html>offline</html>"))
        val store = ChurchProfileStore(RestChurchCatalog(PagedFeed(emptyMap()), base), prefs) { listOf(bundledRiverside) }
        assertEquals(listOf(bundledRiverside), store.refresh())
    }

    @Test
    fun restCatalogURL() {
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/knowgrowshow-d3609/databases/(default)/documents/onlineResourceProfiles",
            RestChurchCatalog.collectionURL(null, null),
        )
        assertEquals(
            "http://10.0.2.2:8080/v1/projects/demo/databases/(default)/documents/onlineResourceProfiles",
            RestChurchCatalog.collectionURL(FirebaseBootstrap.Emulator("10.0.2.2", "demo"), "prod"),
        )
    }
}
