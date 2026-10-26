package com.stoneofhope.stoneofhope.data.crypto

import android.util.Log
import com.stoneofhope.stoneofhope.data.sync.SyncIdentity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Port of iOS `CryptoSession` + `CryptoSessionBootstrap`: holds `note_key` in memory after
 * sign-in. There is no separate encryption password — Firebase sign-in unlocks it.
 */
@Singleton
class CryptoSession @Inject constructor(
    private val identity: SyncIdentity,
    private val provider: NoteKeyProvider,
    private val cache: NoteKeyCache,
) {
    enum class UnlockResult { NotRequired, Unlocked, Failed }

    private class Unlocked(val ownerUid: String, val key: ByteArray)

    private val state = MutableStateFlow<Unlocked?>(null)
    private val _unlockedUid = MutableStateFlow<String?>(null)

    /** Uid whose key is loaded, or null when locked. */
    val unlockedUid: StateFlow<String?> = _unlockedUid.asStateFlow()

    private val mutex = Mutex()
    private var inFlight: Pair<String, CompletableDeferred<UnlockResult>>? = null

    /** The key, only if it belongs to [ownerUid]. */
    fun keyFor(ownerUid: String): ByteArray? = state.value?.takeIf { it.ownerUid == ownerUid }?.key

    /**
     * iOS `canSyncEncryptedBodies`: signed-out (local-only) users need no key; signed-in users need
     * theirs loaded before note bodies can be pushed or pulled.
     */
    fun canSyncEncryptedBodies(): Boolean {
        val uid = identity.currentAuthUid ?: return true
        return keyFor(uid) != null
    }

    /** Memory → device cache → provider. Idempotent; concurrent callers share one fetch-or-create. */
    suspend fun ensureUnlocked(ownerUid: String): UnlockResult {
        if (identity.currentAuthUid != ownerUid) return UnlockResult.NotRequired
        if (keyFor(ownerUid) != null) return UnlockResult.Unlocked

        cache.load(ownerUid)?.let {
            set(ownerUid, it)
            return UnlockResult.Unlocked
        }

        // Single flight (iOS K1): the sign-in listener and the post-login full sync arrive together;
        // without this both would see "no key anywhere" and each create one.
        val (deferred, owner) = mutex.withLock {
            inFlight?.takeIf { it.first == ownerUid }?.let { return@withLock it.second to false }
            val d = CompletableDeferred<UnlockResult>()
            inFlight = ownerUid to d
            d to true
        }
        if (!owner) return deferred.await()

        var result = UnlockResult.Failed
        try {
            val key = provider.fetchOrCreate(ownerUid)
            require(key.size == StoneOfHopeCrypto.KEY_BYTE_COUNT)
            set(ownerUid, key)
            try {
                cache.save(ownerUid, key)
            } catch (e: Exception) {
                // Best effort: the session still works, the next cold start just refetches.
                Log.w(TAG, "note_key cache save failed", e)
            }
            result = UnlockResult.Unlocked
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ensureUnlocked failed", e)
        } finally {
            withContext(NonCancellable) { mutex.withLock { inFlight = null } }
            deferred.complete(result)
        }
        return result
    }

    /** Clears the in-memory key. Pass `clearCache = true` on sign-out or account switch. */
    fun lock(clearCache: Boolean = false) {
        val current = state.value
        if (clearCache && current != null) cache.clear(current.ownerUid)
        state.value = null
        _unlockedUid.value = null
    }

    private fun set(ownerUid: String, key: ByteArray) {
        state.value = Unlocked(ownerUid, key)
        _unlockedUid.value = ownerUid
    }

    private companion object {
        const val TAG = "CryptoSession"
    }
}
