package com.stoneofhope.stoneofhope.ui.flashcard

import androidx.lifecycle.ViewModel
import com.stoneofhope.stoneofhope.data.flashcard.LaunchDeckPolicy
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.settings.FlashCardPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject

/** Decides when the verse deck opens by itself (iOS `presentActiveFlashCardsIfNeeded`, IOS_ISSUES L4). */
@HiltViewModel
class LaunchDeckViewModel @Inject constructor(
    private val db: AppDatabase,
    private val preferences: FlashCardPreferences,
    private val clock: Clock,
) : ViewModel() {
    private val policy = LaunchDeckPolicy()

    fun onStop() = policy.onStop(clock.millis())

    suspend fun shouldShow(): Boolean =
        policy.shouldShow(clock.millis(), preferences.showOnLaunch, db.flashCardDao().activeCount() > 0)
}
