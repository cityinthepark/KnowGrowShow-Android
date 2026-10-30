package com.stoneofhope.stoneofhope.data.church

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.reading.ReadingModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer

/** What one import run did. */
sealed interface ChurchImportResult {
    /** Counts are rows that are new or actually changed (iOS counts every row it touched: IOS_ISSUES M4). */
    data class Done(
        val newSeries: Int = 0,
        val updatedSeries: Int = 0,
        val newSermons: Int = 0,
        val updatedSermons: Int = 0,
        /** Files that failed to download / decode, or whose sermons had no series yet; retried next time. */
        val incompleteFiles: List<String> = emptyList(),
    ) : ChurchImportResult {
        val series get() = newSeries + updatedSeries
        val sermons get() = newSermons + updatedSermons
        val nothingNew get() = series == 0 && sermons == 0
    }

    /** `manifest.json` couldn't be fetched or read — offline, most likely (iOS reports "no new sermons": M3). */
    data object Unreachable : ChurchImportResult

    /** No such church, disabled, or no repo URL. */
    data object NotImportable : ChurchImportResult
}

/** Repo file names: `series_yyyyMMdd.json` holds series, `yyyyMMdd.json` holds sermons (iOS `ChurchSermonImportService`). */
object ChurchRepoFiles {
    private val digits8 = Regex("^[0-9]{8}$")

    fun isSeriesFile(name: String) = name.startsWith("series_") && name.endsWith(".json")

    fun isSermonFile(name: String): Boolean {
        if (!name.endsWith(".json") || name.contains("series")) return false
        return digits8.matches(name.dropLast(5))
    }

    fun version(name: String): String? = when {
        isSeriesFile(name) -> name.removePrefix("series_").dropLast(5).takeIf(digits8::matches)
        isSermonFile(name) -> name.dropLast(5)
        else -> null
    }

    /**
     * Files to fetch when the device has imported up to [localVersion] (null = everything). iOS keeps
     * only versions `>` local, so a file re-published under the same day is never fetched again
     * (IOS_ISSUES M2); here it is `>=` — upserts are idempotent, unchanged rows aren't counted.
     */
    fun filesFrom(files: List<String>, localVersion: String?): List<String> =
        files.filter { name -> version(name)?.let { localVersion == null || it >= localVersion } == true }.sorted()

    /**
     * iOS `CalendarDayKey.normalize`: 8 digits, month 1–12, day 1–31 (iOS's lenient calendar
     * accepts e.g. Feb 31 and rolls it over).
     */
    fun normalizeDay(raw: String): String? {
        val t = raw.trim()
        if (!digits8.matches(t)) return null
        val month = t.substring(4, 6).toInt()
        val day = t.substring(6, 8).toInt()
        return t.takeIf { month in 1..12 && day in 1..31 }
    }

    /** The local day for a normalized key, rolled over like iOS `Calendar.date(from:)`. */
    fun day(key: String): LocalDate =
        LocalDate.of(key.substring(0, 4).toInt(), key.substring(4, 6).toInt(), 1).plusDays(key.substring(6, 8).toLong() - 1)
}

/**
 * iOS `ChurchSermonImportService`: downloads a church's sermon repo into Room as `isUserData = false`
 * rows, which never sync. The imported version is device-local (`sermonsLastImported_<churchKey>`,
 * the iOS key). Unlike iOS, it moves forward only past files that fully imported (IOS_ISSUES M1, M5).
 * A church whose sermons are imported joins "My churches" ([OnlineChurches]).
 */
@Singleton
class ChurchSermonImporter(
    private val db: AppDatabase,
    private val profiles: ChurchProfileStore,
    private val feed: ChurchFeed,
    private val prefs: SharedPreferences,
    private val churches: OnlineChurches,
) {
    @Inject
    constructor(db: AppDatabase, profiles: ChurchProfileStore, feed: ChurchFeed, @ApplicationContext context: Context, churches: OnlineChurches) :
        this(db, profiles, feed, context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE), churches)

    private val sermonDao get() = db.sermonDao()
    private val seriesDao get() = db.sermonSeriesDao()

    fun lastImportedVersion(churchKey: String): String? = prefs.getString(versionKey(churchKey), null)

    /**
     * Whether the page offers "Import Latest Updates" rather than "Import all Sermons": this device
     * has the church's sermons. iOS also says yes when only the saved version is left (its sermons
     * gone), and then imports nothing new (IOS_ISSUES M7).
     */
    suspend fun hasImported(churchKey: String): Boolean = sermonDao.countActiveForChurch(churchKey) > 0

    /** iOS `importInitial`: every file in the manifest. */
    suspend fun importAll(churchKey: String, zone: ZoneId = ZoneId.systemDefault()): ChurchImportResult =
        run(churchKey, zone) { null }

    /** iOS `importUpdates`: files from the saved version on; everything when there is none. */
    suspend fun importUpdates(churchKey: String, zone: ZoneId = ZoneId.systemDefault()): ChurchImportResult =
        run(churchKey, zone) { lastImportedVersion(churchKey) }

    private suspend fun run(churchKey: String, zone: ZoneId, since: () -> String?): ChurchImportResult {
        val base = profiles.profile(churchKey)?.importBaseURL ?: return ChurchImportResult.NotImportable
        val manifest = feed.get(base + "manifest.json")
            ?.let { runCatching { ChurchProfile.json.decodeFromString(ChurchManifest.serializer(), it) }.getOrNull() }
            ?: return ChurchImportResult.Unreachable

        val files = ChurchRepoFiles.filesFrom(manifest.files, since())
        val counts = Counts()
        val incomplete = mutableListOf<String>()
        // Series first so every sermon can find its series (iOS order).
        for (file in files.filter(ChurchRepoFiles::isSeriesFile)) {
            if (!importSeriesFile(base + file, counts)) incomplete += file
        }
        for (file in files.filter(ChurchRepoFiles::isSermonFile)) {
            if (!importSermonFile(base + file, counts, zone)) incomplete += file
        }

        // Next run starts at the oldest incomplete file, or past everything when all went in.
        val next = incomplete.mapNotNull(ChurchRepoFiles::version).minOrNull()
            ?: manifest.latestVersion.takeIf { Regex("^[0-9]{8}$").matches(it) }
            ?: files.mapNotNull(ChurchRepoFiles::version).maxOrNull()
        next?.let { prefs.edit { putString(versionKey(churchKey), it) } }
        // Followed once its sermons are here; the first church followed becomes primary.
        if (hasImported(churchKey)) churches.recordImport(churchKey, next)

        return ChurchImportResult.Done(
            counts.newSeries, counts.updatedSeries, counts.newSermons, counts.updatedSermons, incomplete.sorted(),
        )
    }

    private class Counts {
        var newSeries = 0
        var updatedSeries = 0
        var newSermons = 0
        var updatedSermons = 0
    }

    /** False when the file couldn't be fetched or decoded. One transaction per file, as iOS. */
    private suspend fun importSeriesFile(url: String, counts: Counts): Boolean {
        val rows = decode(url, ListSerializer(ChurchSeriesDto.serializer())) ?: return false
        db.withTransaction {
            for (dto in rows) {
                when (upsertSeries(dto)) {
                    Change.New -> counts.newSeries++
                    Change.Updated -> counts.updatedSeries++
                    else -> Unit
                }
            }
        }
        return true
    }

    /** False also when a sermon's series isn't here yet, so the file is fetched again next time. */
    private suspend fun importSermonFile(url: String, counts: Counts, zone: ZoneId): Boolean {
        val rows = decode(url, ListSerializer(ChurchSermonDto.serializer())) ?: return false
        var complete = true
        db.withTransaction {
            for (dto in rows) {
                when (upsertSermon(dto, zone)) {
                    Change.New -> counts.newSermons++
                    Change.Updated -> counts.updatedSermons++
                    Change.MissingSeries -> complete = false
                    else -> Unit
                }
            }
        }
        return complete
    }

    private suspend fun <T> decode(url: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        feed.get(url)?.let { runCatching { ChurchProfile.json.decodeFromString(serializer, it) }.getOrNull() }

    private enum class Change { New, Updated, Unchanged, Invalid, MissingSeries }

    /** iOS `upsertSeries`: skips a bad id or `startedOn`; never syncs. */
    private suspend fun upsertSeries(dto: ChurchSeriesDto): Change {
        val id = ObjectIds.parse(dto.id) ?: return Change.Invalid
        val startedOn = ChurchRepoFiles.normalizeDay(dto.startedOn) ?: return Change.Invalid
        val existing = seriesDao.getById(id)
        val row = (existing ?: SermonSeriesEntity(id = id, title = dto.title)).copy(
            title = dto.title, bookOrd = dto.bookOrd, startedOn = startedOn, isFinished = dto.isFinished,
            churchKey = dto.churchKey, isUserData = false, needsSync = false,
        )
        if (row == existing) return Change.Unchanged
        seriesDao.upsert(row)
        return if (existing == null) Change.New else Change.Updated
    }

    /**
     * iOS `upsertSermon`: optional text trimmed (blank → null), `chapters` replaced, the series must
     * already exist. A new row's `createdAt` is the start of its preached-on day. The user's tags stay.
     */
    private suspend fun upsertSermon(dto: ChurchSermonDto, zone: ZoneId): Change {
        val id = ObjectIds.parse(dto.id) ?: return Change.Invalid
        val preachedOn = ChurchRepoFiles.normalizeDay(dto.preachedOn) ?: return Change.Invalid
        val seriesId = ObjectIds.parse(dto.seriesId)?.takeIf { seriesDao.getById(it) != null } ?: return Change.MissingSeries
        val existing = sermonDao.getById(id)
        val base = existing ?: SermonEntity(
            id = id, title = dto.title, createdAt = ReadingModel.startOfDay(ChurchRepoFiles.day(preachedOn), zone),
        )
        val row = base.copy(
            title = dto.title,
            preacher = nonEmpty(dto.preacher),
            intro = nonEmpty(dto.intro),
            audioUrl = nonEmpty(dto.audioUrl),
            videoUrl = nonEmpty(dto.videoUrl),
            pdfUrl = nonEmpty(dto.pdfUrl),
            youtubeID = nonEmpty(dto.youtubeId),
            book = nonEmpty(dto.book),
            bookOrd = dto.bookOrd,
            chapterRange = nonEmpty(dto.chapterRange),
            chapters = dto.chapters.orEmpty(),
            preachedOn = preachedOn,
            churchKey = dto.churchKey,
            seriesId = seriesId,
            isUserData = false,
            needsSync = false,
        )
        if (row == existing) return Change.Unchanged
        sermonDao.upsert(row)
        return if (existing == null) Change.New else Change.Updated
    }

    private fun nonEmpty(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        fun versionKey(churchKey: String) = "sermonsLastImported_$churchKey"
    }
}
