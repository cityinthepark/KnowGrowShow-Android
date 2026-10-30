package com.stoneofhope.stoneofhope.data.church

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.firebase.FirebaseOptions
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The public church catalog `onlineResourceProfiles` (read: true for everyone). */
interface ChurchCatalog {
    /** Every church document, enabled or not, or null when the catalog can't be read right now. */
    suspend fun fetchChurches(): List<ChurchProfile>?
}

/**
 * Reads the catalog through the Firestore REST API, which needs no sign-in for a public collection.
 * So "This device only" users get it too, without starting Firebase (FirebaseBootstrap: starting it
 * means "cloud on" to the account page and sync). iOS reads only its bundled profile.
 */
@Singleton
class RestChurchCatalog(private val feed: ChurchFeed, private val collectionURL: String?) : ChurchCatalog {
    @Inject
    constructor(feed: ChurchFeed, bootstrap: FirebaseBootstrap, @ApplicationContext context: Context) :
        this(feed, collectionURL(bootstrap.emulator, FirebaseOptions.fromResource(context)?.projectId))

    override suspend fun fetchChurches(): List<ChurchProfile>? {
        val base = collectionURL ?: return null
        val churches = mutableListOf<ChurchProfile>()
        var pageToken: String? = null
        do {
            val url = "$base?pageSize=300" + pageToken?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
            val page = feed.get(url)?.let(::parsePage) ?: return null
            churches += page.first
            pageToken = page.second
        } while (pageToken != null)
        return churches
    }

    companion object {
        private const val COLLECTION = "onlineResourceProfiles"
        /** The iOS / Web Firebase project, for builds without google-services.json. */
        private const val PROJECT_ID = "knowgrowshow-d3609"

        fun collectionURL(emulator: FirebaseBootstrap.Emulator?, projectId: String?): String =
            if (emulator != null) {
                "http://${emulator.host}:${emulator.firestorePort}/v1/projects/${emulator.projectId}/databases/(default)/documents/$COLLECTION"
            } else {
                "https://firestore.googleapis.com/v1/projects/${projectId ?: PROJECT_ID}/databases/(default)/documents/$COLLECTION"
            }

        /** One REST page: the church documents in it and the next page token. Null if it isn't a page. */
        fun parsePage(text: String): Pair<List<ChurchProfile>, String?>? = runCatching {
            val root = ChurchProfile.json.parseToJsonElement(text).jsonObject
            val churches = root["documents"]?.jsonArray.orEmpty().mapNotNull { element ->
                val doc = element.jsonObject
                val id = doc["name"]?.jsonPrimitive?.content?.substringAfterLast('/') ?: return@mapNotNull null
                val fields = doc["fields"]?.jsonObject.orEmpty().mapValues { (_, value) -> restValue(value) }
                ChurchProfile.fromCatalog(id, fields)
            }
            churches to root["nextPageToken"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        /** A REST `Value` as the SDK would give it; maps and arrays aren't used by profiles. */
        private fun restValue(value: JsonElement): Any? {
            val (type, raw) = value.jsonObject.entries.firstOrNull() ?: return null
            val p = raw as? JsonPrimitive ?: return null
            return when (type) {
                "stringValue", "timestampValue" -> p.content
                "booleanValue" -> p.booleanOrNull
                "integerValue" -> p.content.toLongOrNull()
                "doubleValue" -> p.doubleOrNull
                else -> null
            }
        }
    }
}

/**
 * iOS `OnlineChurchProfileStore`, reading the Firestore catalog first (docs/CHURCH_IMPORT_PLAN.md
 * §2.3). Offline it falls back to the last catalog it saw, then to the profiles bundled in
 * `assets/churches/`, so the import page works without the cloud.
 */
@Singleton
class ChurchProfileStore(
    private val catalog: ChurchCatalog,
    private val prefs: SharedPreferences,
    private val bundled: () -> List<ChurchProfile>,
) {
    @Inject
    constructor(catalog: ChurchCatalog, @ApplicationContext context: Context) : this(
        catalog,
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE),
        { loadBundled(context) },
    )

    /** Enabled churches, by name. Asks the catalog; stores what it got for next time. */
    suspend fun refresh(): List<ChurchProfile> {
        val remote = catalog.fetchChurches()
        if (remote != null && remote.isNotEmpty()) {
            prefs.edit { putString(CACHE_KEY, ChurchProfile.json.encodeToString(listSerializer, remote)) }
            return visible(remote)
        }
        return cached()
    }

    /** Without the network: the last catalog seen, else the bundled profiles. */
    fun cached(): List<ChurchProfile> {
        val saved = prefs.getString(CACHE_KEY, null)
            ?.let { runCatching { ChurchProfile.json.decodeFromString(listSerializer, it) }.getOrNull() }
        return visible(saved?.takeIf { it.isNotEmpty() } ?: bundled())
    }

    fun profile(churchKey: String): ChurchProfile? = cached().firstOrNull { it.churchKey == churchKey }

    private fun visible(all: List<ChurchProfile>) = all.filter { it.enabled }.sortedBy { it.name.lowercase() }

    companion object {
        const val CACHE_KEY = "onlineResourceProfilesCache"
        private const val ASSET_DIR = "churches"
        private val listSerializer = ListSerializer(ChurchProfile.serializer())

        private fun loadBundled(context: Context): List<ChurchProfile> {
            val names = runCatching { context.assets.list(ASSET_DIR) }.getOrNull().orEmpty()
            return names.filter { it.endsWith(".json") }.mapNotNull { name ->
                runCatching { context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() } }
                    .getOrNull()?.let(ChurchProfile::decode)
            }
        }
    }
}
