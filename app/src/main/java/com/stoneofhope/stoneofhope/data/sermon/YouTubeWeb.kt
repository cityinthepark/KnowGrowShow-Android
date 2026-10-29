package com.stoneofhope.stoneofhope.data.sermon

import android.content.Context
import android.content.SharedPreferences
import android.os.LocaleList
import androidx.core.content.edit
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** What the keyless oEmbed endpoint returns for a video. */
data class OEmbedInfo(val title: String, val authorName: String?, val authorUrl: String?, val thumbnailUrl: String?)

/**
 * The two YouTube calls the app makes; both are free and need no key of ours (§4 question 2 of
 * docs/SERMONS_SERIES_PLAN.md: videos use oEmbed too, so `channelId` stays empty).
 */
interface YouTubeWeb {
    /** iOS `fetchSimpleYouTubeSermonInfo`: `youtube.com/oembed`. Null when offline or the video is gone. */
    suspend fun oEmbed(videoID: String): OEmbedInfo?

    /** iOS `YouTubeTranscriptService.fetchTranscript`: timed captions, or null when there are none. */
    suspend fun transcript(videoID: String): List<TranscriptSegment>?
}

@Singleton
class HttpYouTubeWeb @Inject constructor() : YouTubeWeb {

    override suspend fun oEmbed(videoID: String): OEmbedInfo? = withContext(Dispatchers.IO) {
        val watch = URLEncoder.encode(YouTubeLinkParser.watchUrl(videoID), "UTF-8")
        val body = get("https://www.youtube.com/oembed?url=$watch&format=json") ?: return@withContext null
        val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return@withContext null
        fun field(name: String) = json[name]?.jsonPrimitive?.contentOrNull
        OEmbedInfo(field("title").orEmpty(), field("author_name"), field("author_url"), field("thumbnail_url"))
    }

    override suspend fun transcript(videoID: String): List<TranscriptSegment>? = withContext(Dispatchers.IO) {
        val trackUrl = captionTrackUrl(videoID) ?: return@withContext null
        val url = if (Regex("[?&]fmt=").containsMatchIn(trackUrl)) trackUrl else trackUrl + (if ('?' in trackUrl) "&" else "?") + "fmt=json3"
        val body = get(url) ?: return@withContext null
        TranscriptParser.parseJson3(body) ?: TranscriptParser.parseTimedTextXml(body)
    }

    /** The same InnerTube player request iOS makes (YouTube's own public web key, iOS client). */
    private fun captionTrackUrl(videoID: String): String? {
        val request = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "IOS")
                    put("clientVersion", "20.10.38")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iOS")
                    put("osVersion", "17.5.1")
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoID)
        }
        val body = post(
            "https://www.youtube.com/youtubei/v1/player?key=$INNERTUBE_KEY",
            request.toString(),
            userAgent = "com.google.ios.youtube/20.10.38 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
        ) ?: return null
        val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val tracks = ((json["captions"] as? JsonObject)?.get("playerCaptionsTracklistRenderer") as? JsonObject)
            ?.get("captionTracks") as? JsonArray ?: return null
        val list = tracks.mapNotNull { it as? JsonObject }
        val track = selectTrack(list) ?: return null
        return track["baseUrl"]?.jsonPrimitive?.contentOrNull
    }

    /** Human captions over auto-generated ones, in the device's preferred languages, then English. */
    private fun selectTrack(tracks: List<JsonObject>): JsonObject? {
        val manual = tracks.filter { it["kind"]?.jsonPrimitive?.contentOrNull != "asr" }
        val pool = manual.ifEmpty { tracks }
        for (preferred in preferredLanguageCodes()) {
            pool.firstOrNull { t ->
                val code = t["languageCode"]?.jsonPrimitive?.contentOrNull ?: return@firstOrNull false
                code.equals(preferred, ignoreCase = true) ||
                    code.substringBefore('-').equals(preferred.substringBefore('-'), ignoreCase = true)
            }?.let { return it }
        }
        return pool.firstOrNull()
    }

    private fun preferredLanguageCodes(): List<String> {
        val locales = LocaleList.getDefault()
        val codes = mutableListOf<String>()
        for (i in 0 until locales.size()) {
            val tag = locales[i].toLanguageTag()
            codes += tag
            codes += tag.substringBefore('-')
        }
        codes += "en"
        return codes.distinct()
    }

    private fun get(url: String): String? = request(url, method = "GET", body = null, userAgent = null)

    private fun post(url: String, body: String, userAgent: String): String? = request(url, "POST", body, userAgent)

    private fun request(url: String, method: String, body: String?, userAgent: String?): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            userAgent?.let { conn.setRequestProperty("User-Agent", it) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    }
}

/** The Script button's window (iOS `@AppStorage(scriptCaptureDurationSeconds)`, device-local). */
@Singleton
class ScriptCapturePreferences(private val prefs: SharedPreferences) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))

    var seconds: Int
        get() = ScriptCaptureDuration.clamped(prefs.getInt(ScriptCaptureDuration.KEY, ScriptCaptureDuration.DEFAULT))
        set(value) = prefs.edit { putInt(ScriptCaptureDuration.KEY, ScriptCaptureDuration.clamped(value)) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SermonModule {
    @Binds abstract fun youTubeWeb(impl: HttpYouTubeWeb): YouTubeWeb
}
