package com.stoneofhope.stoneofhope.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.stoneofhope.stoneofhope.data.sync.CloudAccountFlags
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-launch choice, with the same keys and meaning as iOS `WelcomeOnboardingView` (@AppStorage).
 * "This device only" never touches Firebase; "Register" prepares Firebase and sign-in follows.
 */
@Singleton
class OnboardingPreferences(
    private val prefs: SharedPreferences,
    private val flags: CloudAccountFlags,
) {
    @Inject
    constructor(@ApplicationContext context: Context, flags: CloudAccountFlags) :
        this(context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE), flags)

    var hasCompletedWelcomeOnboarding: Boolean
        get() = prefs.getBoolean(COMPLETED, false)
        private set(value) = prefs.edit().putBoolean(COMPLETED, value).apply()

    /** True when the user chose "Register" (cross-device sync). */
    var welcomeWantsCrossDeviceRegistration: Boolean
        get() = prefs.getBoolean(WANTS_CLOUD, false)
        private set(value) = prefs.edit().putBoolean(WANTS_CLOUD, value).apply()

    fun completeLocalOnly() {
        welcomeWantsCrossDeviceRegistration = false
        hasCompletedWelcomeOnboarding = true
    }

    fun completeWithCloud() {
        welcomeWantsCrossDeviceRegistration = true
        hasCompletedWelcomeOnboarding = true
    }

    /** A local-only user turning on cloud sync later, from the account screen. */
    fun optIntoCloud() {
        welcomeWantsCrossDeviceRegistration = true
    }

    /** iOS `FirebaseBootstrap.shouldConfigureOnLaunch`. */
    val shouldConfigureFirebaseOnLaunch: Boolean
        get() = flags.hasLinkedCloudAccount || (hasCompletedWelcomeOnboarding && welcomeWantsCrossDeviceRegistration)

    private companion object {
        const val COMPLETED = "hasCompletedWelcomeOnboarding"
        const val WANTS_CLOUD = "welcomeWantsCrossDeviceRegistration"
    }
}
