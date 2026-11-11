package com.stoneofhope.stoneofhope.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stoneofhope.stoneofhope.data.account.AccountService
import com.stoneofhope.stoneofhope.data.account.DeviceRegistry
import com.stoneofhope.stoneofhope.data.church.ChurchListSync
import com.stoneofhope.stoneofhope.data.church.OnlineChurches
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.crypto.NoteKeyCache
import com.stoneofhope.stoneofhope.data.crypto.NoteKeyProvider
import com.stoneofhope.stoneofhope.data.crypto.StoneOfHopeCrypto
import com.stoneofhope.stoneofhope.data.image.NoteImageStore
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.remote.NoteImageCloud
import com.stoneofhope.stoneofhope.data.remote.RemoteDocument
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import com.stoneofhope.stoneofhope.data.remote.wire.FakeFirestore
import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireWrite
import com.stoneofhope.stoneofhope.data.remote.wire.WriteMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** One user's Firestore tree, shared by every simulated device. Each commit gets a later server time. */
class FakeRemoteStore(val server: FakeFirestore = FakeFirestore()) : RemoteStore {
    val fetches = mutableListOf<Pair<String, Instant?>>()
    val commits = AtomicInteger()

    /** Runs before a batch is applied — lets a test edit local rows "while the upload is in flight". */
    var beforeCommit: suspend (List<WireWrite>) -> Unit = {}

    /** Collections whose fetch throws, to simulate a failing read. */
    val failingCollections = mutableSetOf<String>()

    /** Firebase Storage for the same account. */
    val images = FakeNoteImageCloud()

    override suspend fun fetch(ownerUid: String, collection: String, since: Instant?): List<RemoteDocument> {
        fetches += collection to since
        if (collection in failingCollections) error("fetch $collection failed")
        return server.query(collection, since).map { (id, data) -> RemoteDocument(id, data) }
    }

    override suspend fun fetchDocument(ownerUid: String, collection: String, documentId: String): RemoteDocument? =
        server.get(collection, documentId)?.let { RemoteDocument(documentId, it) }

    override suspend fun commit(ownerUid: String, writes: List<WireWrite>) {
        beforeCommit(writes)
        server.serverNow = server.serverNow.plusMillis(1)
        writes.forEach(server::apply)
        commits.incrementAndGet()
    }

    override suspend fun fetchOrCreateAccountEpoch(ownerUid: String, fresh: String): String {
        (server.get(META, ACCOUNT)?.get("epoch") as? String)?.let { return it }
        server.put(META, ACCOUNT, mapOf("epoch" to fresh))
        return fresh
    }

    override suspend fun listDevices(ownerUid: String): List<RemoteDocument> =
        server.query(DEVICES, null).map { (id, data) -> RemoteDocument(id, data) }

    /** Collections whose wipe fails, to simulate a partial delete. */
    val failingWipes = mutableSetOf<String>()

    override suspend fun deleteAllDocuments(ownerUid: String, collection: String): Boolean {
        if (collection in failingWipes) return false
        server.query(collection, null).forEach { (id, _) -> server.apply(WireWrite(collection, id, emptyMap(), WriteMode.Delete)) }
        return true
    }

    override suspend fun deleteDevice(ownerUid: String, deviceId: String) {
        server.apply(WireWrite(DEVICES, deviceId, emptyMap(), WriteMode.Delete))
    }

    override suspend fun mergeDevice(ownerUid: String, deviceId: String, data: WireData) {
        server.serverNow = server.serverNow.plusMillis(1)
        server.apply(WireWrite(DEVICES, deviceId, data, WriteMode.Merge))
    }

    /** `settings/preferences` transactions run. */
    val preferenceReads = AtomicInteger()

    override suspend fun updatePreferences(ownerUid: String, patch: (WireData?) -> WireData?): WireData? {
        preferenceReads.incrementAndGet()
        val current = server.get(PREFS_COLLECTION, PREFS_DOC)
        patch(current)?.let {
            server.serverNow = server.serverNow.plusMillis(1)
            server.apply(WireWrite(PREFS_COLLECTION, PREFS_DOC, it, WriteMode.Merge))
        }
        return current
    }

    /** What another device signing out of the cloud does: the whole tree is gone, epoch included. */
    fun wipe() = server.clear()

    companion object {
        const val META = "meta"
        const val ACCOUNT = "account"
        const val DEVICES = "devices"
        const val PREFS_COLLECTION = "settings"
        const val PREFS_DOC = "preferences"
    }
}

/** Firebase Storage's note thumbnails, shared by every simulated device. */
class FakeNoteImageCloud : NoteImageCloud {
    val objects = mutableMapOf<String, ByteArray>()
    val downloads = AtomicInteger()
    val failingUploads = mutableSetOf<String>()

    private fun key(ownerUid: String, noteId: String) = "$ownerUid/$noteId"

    override suspend fun uploadThumbnail(ownerUid: String, noteId: String, file: File) {
        if (noteId in failingUploads) error("upload $noteId failed")
        objects[key(ownerUid, noteId)] = file.readBytes()
    }

    override suspend fun downloadThumbnail(ownerUid: String, noteId: String): ByteArray? {
        downloads.incrementAndGet()
        return objects[key(ownerUid, noteId)]
    }

    override suspend fun deleteThumbnail(ownerUid: String, noteId: String) {
        objects.remove(key(ownerUid, noteId))
    }

    var deleteAllFails = false

    /** The note ids the last wipe was told about. */
    var wipedKnownIds: Collection<String> = emptyList()

    override suspend fun deleteAllThumbnails(ownerUid: String, knownNoteIds: Collection<String>): Boolean {
        wipedKnownIds = knownNoteIds
        if (deleteAllFails) return false
        objects.keys.removeAll { it.startsWith("$ownerUid/") }
        return true
    }
}

/** `private/{uid}/secureKeys/noteKey`, shared by devices of the same account. */
class FakeKeyVault {
    val keys = mutableMapOf<String, ByteArray>()
    val calls = AtomicInteger()

    /** True = the device cannot reach `private/{uid}/secureKeys/noteKey` (offline). */
    var unreachable = false

    fun provider() = object : NoteKeyProvider {
        override suspend fun fetchOrCreate(ownerUid: String): ByteArray {
            calls.incrementAndGet()
            if (unreachable) throw java.io.IOException("offline")
            return keys.getOrPut(ownerUid) { StoneOfHopeCrypto.generateNoteKey() }
        }
    }
}

class InMemoryKeyCache : NoteKeyCache {
    val entries = mutableMapOf<String, ByteArray>()
    override fun save(ownerUid: String, key: ByteArray) { entries[ownerUid] = key }
    override fun load(ownerUid: String) = entries[ownerUid]
    override fun clear(ownerUid: String) { entries.remove(ownerUid) }
}

class FakeIdentity(var uid: String?) : SyncIdentity {
    var signOuts = 0
    override val currentAuthUid: String? get() = uid.takeIf { !paused }
    override val sessionUid: String? get() = uid

    /** What `CloudAccountFlags.isCloudSyncPaused` does to the real identity. */
    var paused = false
    override fun signOut() {
        uid = null
        signOuts++
    }
}

class FakeNetwork(online: Boolean = true) : NetworkMonitor {
    override val isOnline = MutableStateFlow(online)
}

class MutableClock(var now: Instant = Instant.parse("2026-10-26T09:00:00.000001Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    /** When set, the clock reads this instead of [now] (a phone whose clock agrees with the server). */
    var follow: (() -> Instant)? = null
    override fun instant(): Instant = follow?.invoke() ?: now
}

/** One simulated install: its own Room database, prefs and key session, wired like Hilt would. */
class TestDevice(
    name: String,
    val remote: FakeRemoteStore,
    val vault: FakeKeyVault,
    uid: String? = "user-1",
) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val identity = FakeIdentity(uid)
    val network = FakeNetwork()
    val clock = MutableClock()
    val cache = InMemoryKeyCache()
    val state = SyncStateStore(context.getSharedPreferences("sync_state_$name", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
    val flags = CloudAccountFlags(context.getSharedPreferences("flags_$name", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
    val session = CryptoSession(identity, vault.provider(), cache)
    private val imageDir = File(context.cacheDir, "device_${name}_${System.nanoTime()}").also { it.mkdirs() }

    /** This install's own pictures folder, so two devices in one test do not share files. */
    val imageStore = NoteImageStore(object : ContextWrapper(context) {
        override fun getFilesDir(): File = imageDir
    })
    val ops = LocalSyncOps(db, imageStore)
    val images = NoteImageSync(remote.images, imageStore, clock)
    val puller = SyncPuller(db, remote, state, session, ops, SyncConflictDetector(db), images, clock)
    val pusher = SyncPusher(db, remote, session, ops, images, clock)
    val peers = SyncPeerRegistry(remote, state)
    val churches = OnlineChurches(db, clock)
    val bootstrap = FirebaseBootstrap(context)
    val cloudSync = CloudSync(
        identity, network, flags, state, session, puller, pusher, peers,
        AccountEpoch(remote, state), ops, db, ChurchListSync(db, remote, clock), CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    val account = AccountService(bootstrap, flags, cloudSync, session, remote, remote.images, network, identity, db)
    val devices = DeviceRegistry(remote, state, peers)

    val uid: String get() = identity.uid!!
    fun guard() = SyncGuard(uid, identity)

    fun close() {
        db.close()
        imageDir.deleteRecursively()
    }
}
