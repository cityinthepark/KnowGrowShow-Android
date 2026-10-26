package com.stoneofhope.stoneofhope.ui

import androidx.lifecycle.ViewModel
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import com.stoneofhope.stoneofhope.data.settings.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** iOS `AppRootView` onboarding gate: welcome until a choice is made, then the main app. */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val onboarding: OnboardingPreferences,
    private val firebase: FirebaseBootstrap,
) : ViewModel() {

    val showWelcome: Boolean get() = !onboarding.hasCompletedWelcomeOnboarding

    /** "This device only": no Firebase at all; data stays in Room. */
    fun chooseLocalOnly() = onboarding.completeLocalOnly()

    /** "Register": prepare Firebase now; sign-in happens later from Settings → Account & Sync (as on iOS). */
    fun chooseCloud() {
        onboarding.completeWithCloud()
        firebase.configureIfNeeded()
    }
}
