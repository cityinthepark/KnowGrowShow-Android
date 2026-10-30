package com.stoneofhope.stoneofhope.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** iOS `DailyBibleVerseCardsLaunchPreference`: "Show Bible Verse on Launch", on unless turned off. Device-local. */
@Singleton
class FlashCardPreferences(private val prefs: SharedPreferences) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))

    var showOnLaunch: Boolean
        get() = prefs.getBoolean(KEY, true)
        set(value) = prefs.edit { putBoolean(KEY, value) }

    companion object {
        const val KEY = "showDailyBibleVerseCardsOnLaunch"
    }
}
