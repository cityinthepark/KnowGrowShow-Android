package com.stoneofhope.stoneofhope.data.remote.firestore

import com.google.firebase.firestore.FieldValue
import com.stoneofhope.stoneofhope.data.crypto.NoteKeyProvider
import com.stoneofhope.stoneofhope.data.crypto.StoneOfHopeCrypto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Port of iOS `SecureKeyService`: `private/{uid}/secureKeys/noteKey` (temporary location until the
 * key moves behind a Cloud Function). Clients may read/create but never update/delete it.
 */
@Singleton
class FirestoreNoteKeyProvider @Inject constructor(
    private val bootstrap: FirebaseBootstrap,
) : NoteKeyProvider {

    class InvalidKeyDataException : Exception("note_key document is missing or malformed")

    private fun ref(ownerUid: String) = bootstrap.firestore
        .collection("private").document(ownerUid)
        .collection("secureKeys").document("noteKey")

    override suspend fun fetchOrCreate(ownerUid: String): ByteArray {
        check(bootstrap.auth.currentUser?.uid == ownerUid) { "Not signed in as $ownerUid" }
        fetch(ownerUid)?.let { return it }

        // Create atomically: re-read inside the transaction and keep an existing key (another
        // device or path may have just created it). A plain set() could overwrite it and brick
        // every note already encrypted with the first key.
        val fresh = StoneOfHopeCrypto.generateNoteKey()
        val doc = mapOf(
            "noteKey" to StoneOfHopeCrypto.base64Encode(fresh),
            "encVersion" to StoneOfHopeCrypto.ENC_VERSION,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = ref(ownerUid)
        bootstrap.firestore.runTransaction { tx ->
            if (!tx.get(ref).exists()) tx.set(ref, doc)
            null
        }.await()

        // Whoever won the race, read back the stored key.
        return fetch(ownerUid) ?: throw InvalidKeyDataException()
    }

    private suspend fun fetch(ownerUid: String): ByteArray? {
        val snap = ref(ownerUid).get().await()
        if (!snap.exists()) return null
        val encoded = snap.getString("noteKey") ?: throw InvalidKeyDataException()
        val key = try {
            StoneOfHopeCrypto.base64Decode(encoded)
        } catch (e: StoneOfHopeCrypto.CryptoException) {
            throw InvalidKeyDataException()
        }
        if (key.size != StoneOfHopeCrypto.KEY_BYTE_COUNT) throw InvalidKeyDataException()
        return key
    }
}
