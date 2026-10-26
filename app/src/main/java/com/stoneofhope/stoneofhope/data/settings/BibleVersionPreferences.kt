package com.stoneofhope.stoneofhope.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * iOS `BibleVersionHelper` storage: the selected Bible versions, primary first, stored as
 * `"KJV,CUV"` under the same key as iOS (`UserDefaults` there; device-local, never synced).
 */
@Singleton
class BibleVersionPreferences(
    private val prefs: SharedPreferences,
    private val deviceLanguage: () -> BibleLanguage = { BibleLanguage.current() },
) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))

    /**
     * iOS falls back to "KJV", but its first launch imports the bundled text for the device language
     * and then saves `["CUV"]` (Chinese) or `["KJV"]`. Android ships both texts, so that choice is made
     * and saved the first time anything asks.
     */
    fun selectedVersions(): List<String> {
        val raw = prefs.getString(KEY, null)
        if (raw != null) return BibleVersions.parseStored(raw)
        return BibleVersions.firstLaunchDefault(deviceLanguage()).also(::setSelectedVersions)
    }

    fun setSelectedVersions(versions: List<String>) {
        prefs.edit().putString(KEY, versions.joinToString(",") { BibleVersions.toStorage(it) }).apply()
    }

    /** iOS `getPrimaryVersion()`. */
    fun primaryVersion(): String = BibleVersions.primary(selectedVersions())

    /** Languages to try first when matching a book name (iOS `bookMatchingLanguages()`). */
    fun bookMatchingLanguages(): List<BibleLanguage> = BibleVersions.bookMatchingLanguages(selectedVersions())

    private companion object {
        const val KEY = "selectedBibleVersions"
    }
}
