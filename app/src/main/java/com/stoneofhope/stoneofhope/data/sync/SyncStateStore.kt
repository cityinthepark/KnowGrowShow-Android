package com.stoneofhope.stoneofhope.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.stoneofhope.stoneofhope.data.local.Converters
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** iOS `CloudAccountFlags` (UserDefaults there). Synchronous because [SyncIdentity] reads it inline. */
@Singleton
class CloudAccountFlags(private val prefs: SharedPreferences) {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences("cloud_account_flags", Context.MODE_PRIVATE))

    var hasLinkedCloudAccount: Boolean
        get() = prefs.getBoolean("hasLinkedCloudAccount", false)
        set(value) = prefs.edit().putBoolean("hasLinkedCloudAccount", value).apply()

    /** Set while a cloud sign-out / wipe is in progress; makes [SyncIdentity] report signed-out. */
    var isCloudSyncPaused: Boolean
        get() = prefs.getBoolean("isCloudSyncPaused", false)
        set(value) = prefs.edit().putBoolean("isCloudSyncPaused", value).apply()

    /** One-time notice: "signed out because another device cleared the cloud; local data kept". */
    var showCloudResetNotice: Boolean
        get() = prefs.getBoolean("showCloudResetNotice", false)
        set(value) = prefs.edit().putBoolean("showCloudResetNotice", value).apply()
}

/**
 * Per-account sync bookkeeping that iOS keeps in UserDefaults: pull cursor
 * (`FirestoreSyncCursor`), peer watermark (`SyncPeerRegistry`), account epoch (`AccountEpoch`).
 * Instants are stored as epoch micros, like Room, so server timestamps keep full precision.
 */
@Singleton
class SyncStateStore(private val prefs: SharedPreferences) {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences("sync_state", Context.MODE_PRIVATE))

    // Pull cursor (FIRESTORE_SYNC_CONTRACT §3.1)

    fun lastPullAt(ownerUid: String): Instant? = instant("firestoreLastPullAt_$ownerUid")

    /** Advance only (never backwards); a null watermark (no documents seen) leaves the cursor alone. */
    fun markPullCompleted(ownerUid: String, watermark: Instant?) {
        if (watermark == null) return
        val existing = lastPullAt(ownerUid)
        if (existing != null && existing >= watermark) return
        putInstant("firestoreLastPullAt_$ownerUid", watermark)
    }

    fun resetPullCursor(ownerUid: String) = remove("firestoreLastPullAt_$ownerUid")

    // Peer watermark (FIRESTORE_SYNC_CONTRACT §5.2)

    fun lastKnownPeerUpdate(ownerUid: String): Instant? = instant("syncLastKnownPeerUpdate_$ownerUid")

    /** Monotonic. */
    fun markPeersSynced(ownerUid: String, upTo: Instant) {
        val known = lastKnownPeerUpdate(ownerUid)
        if (known == null || upTo > known) putInstant("syncLastKnownPeerUpdate_$ownerUid", upTo)
    }

    fun resetPeerWatermark(ownerUid: String) = remove("syncLastKnownPeerUpdate_$ownerUid")

    // Account epoch (FIRESTORE_SYNC_CONTRACT §4.1)

    fun accountEpoch(ownerUid: String): String? = prefs.getString("accountEpoch_$ownerUid", null)

    fun setAccountEpoch(ownerUid: String, epoch: String) = prefs.edit().putString("accountEpoch_$ownerUid", epoch).apply()

    fun clearAccountEpoch(ownerUid: String) = remove("accountEpoch_$ownerUid")

    // Device identity (iOS `DeviceIdentity`)

    /** Stable per-install id; independent of the signed-in account. Reset only by reinstall / clear data. */
    val deviceId: String
        get() = prefs.getString(DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(DEVICE_ID, it).apply()
        }

    private fun instant(key: String): Instant? =
        if (prefs.contains(key)) Converters.fromEpochMicros(prefs.getLong(key, 0)) else null

    private fun putInstant(key: String, value: Instant) =
        prefs.edit().putLong(key, Converters.toEpochMicros(value)).apply()

    private fun remove(key: String) = prefs.edit().remove(key).apply()

    companion object {
        private const val DEVICE_ID = "kgsDeviceId"

        /** `devices.modelIdentifier`: e.g. "Pixel 8". iOS shows it as the title of a peer device. */
        val modelIdentifier: String get() = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

        /** `devices.systemVersion`, e.g. "Android 15". */
        val systemVersion: String get() = "Android ${Build.VERSION.RELEASE}"
    }
}
