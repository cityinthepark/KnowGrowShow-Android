package com.stoneofhope.stoneofhope.data.remote.firestore

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.stoneofhope.stoneofhope.data.church.ChurchListWire
import com.stoneofhope.stoneofhope.data.remote.RemoteDocument
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireWrite
import com.stoneofhope.stoneofhope.data.remote.wire.WriteMode
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

@Singleton
class FirestoreRemoteStore @Inject constructor(
    private val bootstrap: FirebaseBootstrap,
) : RemoteStore {

    private val db get() = bootstrap.firestore

    private fun userDoc(ownerUid: String) = db.collection(FirestoreSchema.USERS).document(ownerUid)

    override suspend fun fetch(ownerUid: String, collection: String, since: Instant?): List<RemoteDocument> {
        val ref = userDoc(ownerUid).collection(collection)
        val query = if (since != null) {
            ref.whereGreaterThanOrEqualTo("updatedAt", Timestamp(since.epochSecond, since.nano))
        } else {
            ref
        }
        return query.get(Source.SERVER).await().documents.map {
            RemoteDocument(it.id, FirestoreWireAdapter.fromFirestore(it.data.orEmpty()))
        }
    }

    override suspend fun fetchDocument(ownerUid: String, collection: String, documentId: String): RemoteDocument? {
        val snapshot = userDoc(ownerUid).collection(collection).document(documentId).get(Source.SERVER).await()
        if (!snapshot.exists()) return null
        return RemoteDocument(snapshot.id, FirestoreWireAdapter.fromFirestore(snapshot.data.orEmpty()))
    }

    override suspend fun commit(ownerUid: String, writes: List<WireWrite>) {
        if (writes.isEmpty()) return
        val batch = db.batch()
        for (write in writes) {
            val ref = userDoc(ownerUid).collection(write.collection).document(write.documentId)
            val data = FirestoreWireAdapter.toFirestore(write.data)
            when (write.mode) {
                WriteMode.Delete -> batch.delete(ref)
                WriteMode.Replace -> batch.set(ref, data)
                WriteMode.Merge -> batch.set(ref, data, SetOptions.merge())
            }
        }
        batch.commit().await()
    }

    override suspend fun fetchOrCreateAccountEpoch(ownerUid: String, fresh: String): String {
        val ref = userDoc(ownerUid).collection(META).document(ACCOUNT)
        ref.get(Source.SERVER).await().getString(EPOCH)?.let { return it }
        return createEpoch(ref, fresh)
    }

    /** Transaction: if another device created the epoch first, keep THEIRS. */
    private suspend fun createEpoch(ref: DocumentReference, fresh: String): String =
        db.runTransaction { tx ->
            tx.get(ref).getString(EPOCH) ?: fresh.also {
                tx.set(ref, mapOf(EPOCH to it, "createdAt" to FieldValue.serverTimestamp()))
            }
        }.await()

    override suspend fun listDevices(ownerUid: String): List<RemoteDocument> =
        userDoc(ownerUid).collection(DEVICES).get(Source.SERVER).await().documents.map {
            RemoteDocument(it.id, FirestoreWireAdapter.fromFirestore(it.data.orEmpty()))
        }

    override suspend fun deleteAllDocuments(ownerUid: String, collection: String): Boolean {
        val ref = userDoc(ownerUid).collection(collection)
        return try {
            var empty = false
            while (!empty) {
                // A server read, so a partial local cache can never make a collection look empty.
                val page = ref.limit(WIPE_PAGE).get(Source.SERVER).await().documents
                if (page.isEmpty()) {
                    empty = true
                } else {
                    val batch = db.batch()
                    page.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FirestoreRemoteStore", "delete collection $collection failed", e)
            false
        }
    }

    override suspend fun deleteDevice(ownerUid: String, deviceId: String) {
        userDoc(ownerUid).collection(DEVICES).document(deviceId).delete().await()
    }

    override suspend fun mergeDevice(ownerUid: String, deviceId: String, data: WireData) {
        userDoc(ownerUid).collection(DEVICES).document(deviceId)
            .set(FirestoreWireAdapter.toFirestore(data), SetOptions.merge())
            .await()
    }

    override suspend fun updatePreferences(ownerUid: String, patch: (WireData?) -> WireData?): WireData? {
        val ref = userDoc(ownerUid).collection(ChurchListWire.COLLECTION).document(ChurchListWire.DOCUMENT)
        return db.runTransaction { tx ->
            val snap = tx.get(ref)
            val current = if (snap.exists()) FirestoreWireAdapter.fromFirestore(snap.data.orEmpty()) else null
            patch(current)?.let { tx.set(ref, FirestoreWireAdapter.toFirestore(it), SetOptions.merge()) }
            current
        }.await()
    }

    private companion object {
        const val META = "meta"
        const val ACCOUNT = "account"
        const val EPOCH = "epoch"
        const val DEVICES = "devices"

        /** Firestore allows 500 writes per batch. */
        const val WIPE_PAGE = 500L
    }
}
