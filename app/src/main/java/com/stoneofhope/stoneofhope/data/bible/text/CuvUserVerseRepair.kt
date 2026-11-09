package com.stoneofhope.stoneofhope.data.bible.text

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS `CUVUserVerseRepair` (revision 2, iOS d9ca2c6 + e4bb313): CUV `userVerses` rows frozen
 * from the text before the versification fix (buildSrc `CuvVersificationFix`) can hold the wrong verse
 * (1 Chr 22:n held 22:n-1), merged text, or the bare "併於上節". Such rows can also arrive from the
 * cloud, written by an install that hadn't been fixed yet.
 *
 * A deliberate exception to "content is frozen" (USER_VERSE_CONTRACT §3.1): the frozen text was wrong,
 * not merely old. Rewrites in place (keeps tags) and marks `needsSync`, so the push overwrites the
 * Firestore doc; tombstones the ids the old text had but the verse table doesn't; drops marks on
 * changed rows, since their offsets point into the old text. Runs once per install.
 */
@Singleton
class CuvUserVerseRepair(
    private val db: AppDatabase,
    private val bundled: BundledVerseDao,
    private val prefs: SharedPreferences,
    private val clock: Clock,
) {
    @Inject
    constructor(db: AppDatabase, bundled: BundledVerseDao, @ApplicationContext context: Context, clock: Clock) :
        this(db, bundled, context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE), clock)

    data class Result(val rewritten: Int = 0, val tombstoned: Int = 0, val marksDropped: Int = 0)

    suspend fun runIfNeeded() {
        if (prefs.getInt(REVISION_KEY, 0) >= REVISION) return
        try {
            val result = apply()
            prefs.edit { putInt(REVISION_KEY, REVISION) }
            Log.i(TAG, "CUV userVerses repair: $result")
        } catch (e: IllegalStateException) {
            // Missing bundled text: try again next launch rather than freeze a gap.
            Log.e(TAG, "CUV userVerses repair failed", e)
        }
    }

    /** Text comes from the (already fixed) bundled CUV; throws if any is missing. */
    suspend fun apply(): Result {
        val now = clock.instant()
        val verseDao = db.userVerseDao()
        val markDao = db.indexedTextDao()
        val keys = AFFECTED + verseDao.withContent(VERSION, MERGED_VERSE_PLACEHOLDER).map { Triple(it.bookOrd, it.chapterOrd, it.verseOrd) }
        val corrected = keys.associate { (b, c, v) ->
            val text = bundled.range(VERSION, b, c, v, v).singleOrNull()?.content.orEmpty()
            check(text.isNotEmpty() && text != MERGED_VERSE_PLACEHOLDER) { "CUV $b:$c:$v has no bundled text to repair from" }
            UserVerseEntity.makeId(VERSION, b, c, v) to text
        }

        var rewritten = 0
        var tombstoned = 0
        var marksDropped = 0
        db.withTransaction {
            suspend fun dropMarks(verseId: String) {
                for (mark in markDao.activeForVerses(listOf(verseId))) {
                    markDao.upsert(mark.copy(isDeleted = true, deletedAt = now, updatedAt = now, needsSync = true))
                    marksDropped++
                }
            }
            for ((id, text) in corrected) {
                val verse = verseDao.getById(id) ?: continue
                if (verse.content == text) continue
                verseDao.upsert(verse.copy(content = text, updatedAt = now, needsSync = true))
                dropMarks(id)
                rewritten++
            }
            for ((b, c, v) in ORPHANS) {
                val id = UserVerseEntity.makeId(VERSION, b, c, v)
                val verse = verseDao.getById(id) ?: continue
                if (verse.isDeleted && verse.deletedAt != null) continue
                verseDao.upsert(verse.copy(isDeleted = true, deletedAt = now, updatedAt = now, needsSync = true))
                dropMarks(id)
                tombstoned++
            }
        }
        return Result(rewritten, tombstoned, marksDropped)
    }

    companion object {
        private const val TAG = "CuvUserVerseRepair"
        const val REVISION = 2
        const val REVISION_KEY = "cuvUserVerseRepairRevision"
        const val VERSION = "CUV"
        const val MERGED_VERSE_PLACEHOLDER = "併於上節"

        /** Verses whose CUV text changed under the versification fix (iOS `affectedKeys`). */
        val AFFECTED: List<Triple<Int, Int, Int>> =
            listOf(Triple(5, 13, 17), Triple(5, 13, 18)) +
                (1..19).map { Triple(13, 22, it) } +
                listOf(Triple(19, 116, 18), Triple(19, 116, 19), Triple(43, 7, 53), Triple(43, 8, 1), Triple(64, 1, 14))

        /** Ids the old text produced that are not in the verse table. */
        val ORPHANS: List<Triple<Int, Int, Int>> = listOf(Triple(13, 21, 31), Triple(64, 1, 15))
    }
}
