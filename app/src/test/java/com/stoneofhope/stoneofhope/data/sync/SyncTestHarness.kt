package com.stoneofhope.stoneofhope.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stoneofhope.stoneofhope.data.church.ChurchListSync
import com.stoneofhope.stoneofhope.data.church.OnlineChurches
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.crypto.NoteKeyCache
import com.stoneofhope.stoneofhope.data.crypto.NoteKeyProvider
import com.stoneofhope.stoneofhope.data.crypto.StoneOfHopeCrypto
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.remote.RemoteDocument
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.wire.FakeFirestore
import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireWrite
import com.stoneofhope.stoneofhope.data.remote.wire.WriteMode
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

    override suspend fun fetch(ownerUid: String, collection: String, since: Instant?): List<RemoteDocument> {
        fetches += collection to since
        if (collection in failingCollections) error("fetch $collection failed")
        return server.query(collection, since).map { (id, data) -> RemoteDocument(id, data) }
    }

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

/** `private/{uid}/secureKeys/noteKey`, shared by devices of the same account. */
class FakeKeyVault {
    val keys = mutableMapOf<String, ByteArray>()
    val calls = AtomicInteger()

    fun provider() = object : NoteKeyProvider {
        override suspend fun fetchOrCreate(ownerUid: String): ByteArray {
            calls.incrementAndGet()
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
    override val currentAuthUid: String? get() = uid
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
    override fun instant(): Instant = now
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
    val ops = LocalSyncOps(db)
    val puller = SyncPuller(db, remote, state, session, ops, SyncConflictDetector(db), clock)
    val pusher = SyncPusher(db, remote, session, ops, clock)
    val peers = SyncPeerRegistry(remote, state)
    val churches = OnlineChurches(db, clock)
    val cloudSync = CloudSync(
        identity, network, flags, state, session, puller, pusher, peers,
        AccountEpoch(remote, state), ops, db, ChurchListSync(db, remote, clock), CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    val uid: String get() = identity.uid!!
    fun guard() = SyncGuard(uid, identity)

    fun close() = db.close()
}
