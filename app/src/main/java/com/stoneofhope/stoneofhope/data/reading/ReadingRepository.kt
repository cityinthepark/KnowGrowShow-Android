package com.stoneofhope.stoneofhope.data.reading

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.BookProgressEntity
import com.stoneofhope.stoneofhope.data.local.entity.ChapterLogEntity
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Reading progress for screens (iOS `BibleReadingManager`); every write marks rows `needsSync`. */
@Singleton
class ReadingRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    fun observeCarousel(
        today: LocalDate,
        zone: ZoneId,
        sort: ReadingModel.Sort,
        windowStarts: MutableMap<Int, Int> = mutableMapOf(),
    ): Flow<List<ReadingModel.CarouselBook>> =
        combine(db.bookProgressDao().observeActive(), db.chapterLogDao().observeActive()) { progress, logs ->
            ReadingModel.carousel(progress, logs, today, zone, sort, windowStarts)
        }

    /** Today's reads, by book then chapter (iOS `getChapterReadingLogs(on:)`). */
    fun observeLogsOn(day: LocalDate, zone: ZoneId): Flow<List<ChapterLogEntity>> =
        db.chapterLogDao().observeActive().map { logs ->
            logs.filter { ReadingModel.localDay(it.readOn, zone) == day }
                .sortedWith(compareBy({ it.bookOrd }, { it.chapter }))
        }

    /**
     * iOS `toggleChapterRead`: un-reads a chapter read today, otherwise marks it read and logs it.
     * Same rows and fields as iOS, so the result syncs both ways.
     */
    suspend fun toggleChapterRead(bookOrd: Int, chapter: Int, zone: ZoneId = ZoneId.systemDefault()) {
        val chapterCount = BibleBooks.book(bookOrd)?.chapterCount ?: return
        if (chapter !in 1..chapterCount) return
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val startOfToday = ReadingModel.startOfDay(today, zone)
        val progressDao = db.bookProgressDao()
        val logDao = db.chapterLogDao()

        db.withTransaction {
            val progress = ReadingModel.canonical(progressDao.activeFor(bookOrd))
            if (progress == null || progress.readChapters.size < chapter) {
                // No current cycle for this book: start one today.
                val read = MutableList(chapterCount) { 0 }.also { it[chapter - 1] = 1 }
                progressDao.upsert(
                    BookProgressEntity(
                        id = ObjectIds.newId(), bookOrd = bookOrd, readChapters = read,
                        createdOn = startOfToday, lastUpdatedOn = now,
                        // iOS leaves this nil here; a one-chapter book is complete as soon as it's read.
                        completedOn = if (0 in read) null else startOfToday,
                        needsSync = true,
                    )
                )
                logDao.upsert(newLog(bookOrd, chapter, startOfToday))
                return@withTransaction
            }

            val logs = logDao.activeFor(bookOrd, chapter)
            val todayLog = logs.firstOrNull { ReadingModel.localDay(it.readOn, zone) == today }
            val read = progress.readChapters.toMutableList()
            if (todayLog != null) {
                // Un-read today. Keep the cycle flag if the chapter was also read earlier in this cycle.
                val readEarlierThisCycle = logs.any { it.readOn >= progress.createdOn && it.readOn < startOfToday }
                var completedOn = progress.completedOn
                if (!readEarlierThisCycle) {
                    read[chapter - 1] = 0
                    completedOn = null
                }
                progressDao.upsert(
                    progress.copy(
                        readChapters = read,
                        completedOn = completedOn,
                        // Full timestamp so books read on the same day still sort by recency.
                        lastUpdatedOn = now,
                        deletedAt = if (read.all { it == 0 }) now else progress.deletedAt,
                        needsSync = true,
                    )
                )
                logDao.upsert(todayLog.copy(deletedAt = now, needsSync = true))
            } else {
                read[chapter - 1] = 1
                progressDao.upsert(
                    progress.copy(
                        readChapters = read,
                        completedOn = if (0 in read) progress.completedOn else startOfToday,
                        lastUpdatedOn = now,
                        needsSync = true,
                    )
                )
                logDao.upsert(newLog(bookOrd, chapter, startOfToday))
            }
        }
    }

    private fun newLog(bookOrd: Int, chapter: Int, readOn: Instant) =
        ChapterLogEntity(id = ObjectIds.newId(), bookOrd = bookOrd, chapter = chapter, readOn = readOn, needsSync = true)
}
