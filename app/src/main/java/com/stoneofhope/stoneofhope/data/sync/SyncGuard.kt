package com.stoneofhope.stoneofhope.data.sync

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * iOS `SyncRunner.isValid(ownerUid:)`: a run must stop the moment it is cancelled or the account it
 * started for is no longer the active, unpaused one. Called after every remote await that precedes
 * a local write or a flag change.
 */
class SyncGuard(private val ownerUid: String, private val identity: SyncIdentity) {

    class AccountChangedException : CancellationException("Signed-in account changed during sync")

    val isValid: Boolean get() = identity.currentAuthUid == ownerUid

    suspend fun check() {
        currentCoroutineContext().ensureActive()
        if (!isValid) throw AccountChangedException()
    }
}
