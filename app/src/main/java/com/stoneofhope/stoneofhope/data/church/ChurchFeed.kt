package com.stoneofhope.stoneofhope.data.church

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Plain GET of the church's sermon repo files (`manifest.json`, `series_*.json`, `yyyyMMdd.json`). */
interface ChurchFeed {
    /** The body, or null when offline, the server answered non-2xx, or the URL is bad. */
    suspend fun get(url: String): String?
}

@Singleton
class HttpChurchFeed @Inject constructor() : ChurchFeed {
    override suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}

/** iOS `SermonManifest`. */
@Serializable
data class ChurchManifest(val latestVersion: String, val files: List<String> = emptyList())

/** iOS `SeriesDTO`. The repo also writes `lastPreachedOn`, which iOS ignores too. */
@Serializable
data class ChurchSeriesDto(
    val id: String,
    val churchKey: String,
    val title: String,
    val bookOrd: Int? = null,
    val startedOn: String,
    val isFinished: Boolean,
)

/** iOS `SermonDTO`. The repo also writes `date` and `thumbnailUrl`, which iOS ignores too. */
@Serializable
data class ChurchSermonDto(
    val id: String,
    val churchKey: String,
    val preachedOn: String,
    val seriesId: String,
    val title: String,
    val preacher: String? = null,
    val intro: String? = null,
    val audioUrl: String? = null,
    val videoUrl: String? = null,
    val youtubeId: String? = null,
    val pdfUrl: String? = null,
    val book: String? = null,
    val bookOrd: Int? = null,
    val chapterRange: String? = null,
    val chapters: List<Int>? = null,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class ChurchModule {
    @Binds abstract fun churchFeed(impl: HttpChurchFeed): ChurchFeed

    @Binds abstract fun churchCatalog(impl: RestChurchCatalog): ChurchCatalog
}
