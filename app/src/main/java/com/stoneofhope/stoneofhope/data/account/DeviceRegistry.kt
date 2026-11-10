package com.stoneofhope.stoneofhope.data.account

import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.wire.instant
import com.stoneofhope.stoneofhope.data.remote.wire.string
import com.stoneofhope.stoneofhope.data.sync.SyncPeerRegistry
import com.stoneofhope.stoneofhope.data.sync.SyncStateStore
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** One row of `users/{uid}/devices` as the Account page shows it (iOS `RegisteredDevice`). */
data class RegisteredDevice(
    val deviceId: String,
    val modelIdentifier: String,
    val systemVersion: String,
    val lastUpdatedAt: Instant?,
    val isCurrentDevice: Boolean,
) {
    val isWeb: Boolean get() = deviceId == WEB_DEVICE_ID || modelIdentifier == WEB_DEVICE_ID

    /** Peers only: not this install, not the web app. */
    val canRemove: Boolean get() = !isCurrentDevice && deviceId != WEB_DEVICE_ID

    /** Last 8 hex characters of the install id, so two phones of the same model can be told apart. */
    val idSuffix: String get() = deviceId.replace("-", "").takeLast(8).uppercase()

    fun isPossiblyInactive(now: Instant): Boolean =
        lastUpdatedAt?.let { Duration.between(it, now).toDays() >= INACTIVE_DAYS } ?: true

    companion object {
        const val WEB_DEVICE_ID = "web"
        const val INACTIVE_DAYS = 30L
    }
}

/** iOS `DeviceRegistryService`: lists and removes the peers that have signed in to this account. */
@Singleton
class DeviceRegistry @Inject constructor(
    private val remote: RemoteStore,
    private val state: SyncStateStore,
    private val peers: SyncPeerRegistry,
) {
    /** This device first (it is added when the cloud has no row for it yet), then web, then the most recently uploaded. */
    suspend fun list(ownerUid: String): List<RegisteredDevice> {
        peers.ensureCurrentDeviceRegistered(ownerUid)
        val me = state.deviceId
        val byId = remote.listDevices(ownerUid).associate { doc ->
            doc.id to RegisteredDevice(
                deviceId = doc.id,
                modelIdentifier = doc.data.string("modelIdentifier") ?: doc.id,
                systemVersion = doc.data.string("systemVersion").orEmpty(),
                lastUpdatedAt = doc.data.instant("lastUpdatedAt"),
                isCurrentDevice = doc.id == me,
            )
        }.toMutableMap()
        byId.getOrPut(me) {
            RegisteredDevice(me, SyncStateStore.modelIdentifier, SyncStateStore.systemVersion, null, isCurrentDevice = true)
        }
        return byId.values.sortedWith(
            compareByDescending<RegisteredDevice> { it.isCurrentDevice }
                .thenByDescending { it.isWeb }
                .thenByDescending { it.lastUpdatedAt ?: Instant.MIN }
                .thenBy { it.deviceId },
        )
    }

    /** Removes a peer's registry row only — never user data. */
    suspend fun remove(ownerUid: String, device: RegisteredDevice) {
        require(device.canRemove) { "This device cannot be removed" }
        remote.deleteDevice(ownerUid, device.deviceId)
        peers.refreshPeerWatermark(ownerUid)
    }
}
