package com.stoneofhope.stoneofhope.data.remote.firestore

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.stoneofhope.stoneofhope.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS `FirebaseBootstrap` + `FirebaseEmulatorBootstrap`.
 *
 * Like iOS, Firebase is **not** started at process launch (auto-init is removed in the manifest).
 * [configureIfNeeded] runs only when the user opted into cloud sync — "Register" on the welcome
 * screen, turning sync on later, or a returning signed-in user. "This device only" never touches it.
 *
 * - Production: initialised from `google-services.json` resources. Without the file, it can't
 *   be configured and the app stays local-only.
 * - Emulator (debug builds with `firebase.useEmulators=true` in `dev.properties`): initialised with
 *   the iOS project id, no `google-services.json` needed; Auth and Firestore use the Local Emulator
 *   Suite on the host Mac.
 */
@Singleton
class FirebaseBootstrap @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** Emulator connection, or null for production / local-only. Always null in release builds. */
    data class Emulator(val host: String, val projectId: String) {
        val authPort = 9099
        val firestorePort = 8080
    }

    val emulator: Emulator? =
        if (BuildConfig.FIREBASE_USE_EMULATORS) {
            Emulator(BuildConfig.FIREBASE_EMULATOR_HOST, BuildConfig.FIREBASE_EMULATOR_PROJECT_ID)
        } else {
            null
        }

    private val _configured = MutableStateFlow(false)

    /** Becomes true once Firebase is up; sign-in and sync listeners attach on that edge. */
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    val isConfigured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    /** Starts Firebase if this build can. Returns false when neither production config nor emulator exists. */
    @Synchronized
    fun configureIfNeeded(): Boolean {
        if (_configured.value) return true
        val emulator = emulator
        if (!isConfigured) {
            if (emulator != null) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setProjectId(emulator.projectId)
                        // Placeholders: the emulator accepts any app id / API key.
                        .setApplicationId("1:000000000000:android:0000000000000000")
                        .setApiKey("emulator-api-key")
                        .build(),
                )
            } else {
                // Reads google-services.json resources; returns null when the file wasn't there.
                FirebaseApp.initializeApp(context)
            }
        }
        if (!isConfigured) return false
        if (emulator != null) {
            FirebaseAuth.getInstance().useEmulator(emulator.host, emulator.authPort)
            Log.i(TAG, "Firebase emulator — Auth ${emulator.host}:${emulator.authPort} | Firestore ${emulator.host}:${emulator.firestorePort} | project ${emulator.projectId}")
        }
        _configured.value = true
        return true
    }

    /** Firestore with a memory-only cache: Room is the offline store, Firestore is transport only. */
    val firestore: FirebaseFirestore by lazy {
        check(_configured.value) { "Firebase is not configured" }
        FirebaseFirestore.getInstance().apply {
            // Must precede any other Firestore call, settings included.
            emulator?.let { useEmulator(it.host, it.firestorePort) }
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }

    val auth: FirebaseAuth
        get() {
            check(_configured.value) { "Firebase is not configured" }
            return FirebaseAuth.getInstance()
        }

    private companion object {
        const val TAG = "FirebaseBootstrap"
    }
}
