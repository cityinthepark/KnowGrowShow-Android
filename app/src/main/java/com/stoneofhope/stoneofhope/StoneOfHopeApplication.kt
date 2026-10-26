package com.stoneofhope.stoneofhope

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import com.stoneofhope.stoneofhope.data.settings.OnboardingPreferences
import com.stoneofhope.stoneofhope.data.sync.SyncTriggers
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StoneOfHopeApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var firebase: FirebaseBootstrap
    @Inject lateinit var onboarding: OnboardingPreferences
    @Inject lateinit var syncTriggers: SyncTriggers

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Like iOS: only users who opted into cloud sync start Firebase at launch.
        if (onboarding.shouldConfigureFirebaseOnLaunch) firebase.configureIfNeeded()
        syncTriggers.start()
    }
}
