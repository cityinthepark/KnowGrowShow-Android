package com.stoneofhope.stoneofhope.data.remote.wire

import com.stoneofhope.stoneofhope.data.crypto.StoneOfHopeCrypto

/** Base64 `iv` + `ciphertext` (ciphertext || tag) for a schema v6 note / comment body. */
data class EncryptedBody(val iv: String, val ciphertext: String)

/** Result of reading a remote note / comment body (iOS `NoteBodyMerge`). */
sealed interface RemoteBody {
    /** The document carries no body fields; keep whatever the local row has. */
    data object Absent : RemoteBody

    data class Readable(val text: String) : RemoteBody

    /**
     * Encrypted body we cannot read right now (no key, or decrypt failed). Pull must skip the
     * whole document and hold the cursor back (iOS "K2"): merging it without a body would leave an
     * empty note that a later local edit could push over the real one.
     */
    data object Unreadable : RemoteBody
}

/** Encrypts / decrypts note and comment bodies per CRYPTO_SYNC_CONTRACT §2.1 and §4.3. */
object NoteBodyCodec {

    fun encrypt(plaintext: String, noteKey: ByteArray): EncryptedBody {
        val sealed = StoneOfHopeCrypto.encrypt(plaintext, noteKey)
        return EncryptedBody(
            iv = StoneOfHopeCrypto.base64Encode(sealed.iv),
            ciphertext = StoneOfHopeCrypto.base64Encode(sealed.sealed),
        )
    }

    /**
     * v6 (`schemaVersion >= 6` with `iv` + `ciphertext`) is decrypted with [noteKey]; otherwise a
     * v5 plaintext `content` is used as-is. A missing `schemaVersion` counts as 5.
     */
    fun decode(data: WireData, noteKey: ByteArray?): RemoteBody {
        val schema = data.int("schemaVersion") ?: FirestoreSchema.SCHEMA_VERSION
        val iv = data.string("iv")
        val ciphertext = data.string("ciphertext")
        if (schema >= FirestoreSchema.ENCRYPTED_BODY_SCHEMA_VERSION && iv != null && ciphertext != null) {
            if (noteKey == null) return RemoteBody.Unreadable
            return try {
                RemoteBody.Readable(
                    StoneOfHopeCrypto.decrypt(
                        StoneOfHopeCrypto.base64Decode(iv),
                        StoneOfHopeCrypto.base64Decode(ciphertext),
                        noteKey,
                    )
                )
            } catch (e: StoneOfHopeCrypto.CryptoException) {
                RemoteBody.Unreadable
            }
        }
        return data.string("content")?.let(RemoteBody::Readable) ?: RemoteBody.Absent
    }
}
