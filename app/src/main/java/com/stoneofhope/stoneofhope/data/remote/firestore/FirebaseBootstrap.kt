package com.stoneofhope.stoneofhope.data.remote.firestore

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * iOS `FirebaseBootstrap`. Firebase is configured only when `google-services.json` was present at
 * build time; otherwise the app is local-only and every cloud entry point is a no-op.
 */
@Singleton
class FirebaseBootstrap @Inject constructor(@param:ApplicationContext private val context: Context) {

    val isConfigured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    /** Firestore with a memory-only cache: Room is the offline store, Firestore is transport only. */
    val firestore: FirebaseFirestore by lazy {
        check(isConfigured) { "Firebase is not configured" }
        FirebaseFirestore.getInstance().apply {
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
}
