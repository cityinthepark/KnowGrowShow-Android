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

/**
 * iOS `FirebaseBootstrap` + `FirebaseEmulatorBootstrap`.
 *
 * - Production: Firebase is configured only when `google-services.json` was present at build time
 *   (the google-services plugin auto-initialises it). Without it the app is local-only.
 * - Emulator (debug builds with `firebase.useEmulators=true` in `dev.properties`): Firebase is
 *   initialised here with the iOS project id — no `google-services.json` needed — and Auth and
 *   Firestore talk to the Local Emulator Suite on the host Mac.
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

    private var configured = false

    val isConfigured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    /** Call once from `Application.onCreate`, before anything touches Firebase. */
    fun configureIfNeeded() {
        if (configured) return
        configured = true
        val emulator = emulator ?: return
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(emulator.projectId)
                    // Placeholders: the emulator accepts any app id / API key.
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("emulator-api-key")
                    .build(),
            )
        }
        FirebaseAuth.getInstance().useEmulator(emulator.host, emulator.authPort)
        Log.i(TAG, "Firebase emulator — Auth ${emulator.host}:${emulator.authPort} | Firestore ${emulator.host}:${emulator.firestorePort} | project ${emulator.projectId}")
    }

    /** Firestore with a memory-only cache: Room is the offline store, Firestore is transport only. */
    val firestore: FirebaseFirestore by lazy {
        check(isConfigured) { "Firebase is not configured" }
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
            check(isConfigured) { "Firebase is not configured" }
            return FirebaseAuth.getInstance()
        }

    private companion object {
        const val TAG = "FirebaseBootstrap"
    }
}
