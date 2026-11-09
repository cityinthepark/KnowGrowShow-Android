package com.stoneofhope.stoneofhope.data.bible.text

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * One verse of a Bible text that ships inside the app (iOS `DownloadedVerse` rows imported from the
 * bundled `KJV.json` / `CUV.xml`). [content] is byte-for-byte what iOS stores in `verseContent`.
 */
@Entity(tableName = "verses", primaryKeys = ["versionAbbr", "bookOrd", "chapterOrd", "verseOrd"])
data class BundledVerseEntity(
    /** Upper case, as iOS stores it: "KJV", "CUV". */
    val versionAbbr: String,
    val bookOrd: Int,
    val chapterOrd: Int,
    val verseOrd: Int,
    val content: String,
)

@Dao
interface BundledVerseDao {
    @Query(
        "SELECT * FROM verses WHERE versionAbbr = :versionAbbr AND bookOrd = :bookOrd AND chapterOrd = :chapterOrd " +
            "AND verseOrd BETWEEN :fromVerse AND :toVerse ORDER BY verseOrd"
    )
    suspend fun range(versionAbbr: String, bookOrd: Int, chapterOrd: Int, fromVerse: Int, toVerse: Int): List<BundledVerseEntity>

    @Query("SELECT COUNT(*) FROM verses WHERE versionAbbr = :versionAbbr")
    suspend fun count(versionAbbr: String): Int
}

/**
 * Read-only, pre-built at compile time from `app/bible-data/` (see `GenerateBibleTextDb` in buildSrc)
 * and opened with `createFromAsset`, so nothing is parsed on first launch. Kept apart from
 * [com.stoneofhope.stoneofhope.data.local.AppDatabase] so user data and its migrations never touch it.
 * Bump [VERSION] whenever the texts change: the asset is then copied again.
 */
@Database(entities = [BundledVerseEntity::class], version = BibleTextDatabase.VERSION, exportSchema = true)
abstract class BibleTextDatabase : RoomDatabase() {
    abstract fun verseDao(): BundledVerseDao

    companion object {
        const val VERSION = 2
        const val NAME = "bible_text.db"
        const val ASSET_PATH = "bible/bible_text.db"
    }
}
