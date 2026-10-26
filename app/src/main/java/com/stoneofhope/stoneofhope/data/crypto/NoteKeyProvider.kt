package com.stoneofhope.stoneofhope.data.crypto

/**
 * Source of the per-account `note_key`. Today: `FirestoreNoteKeyProvider` reads / creates
 * `private/{uid}/secureKeys/noteKey` directly. After Blaze: swap in a Cloud Function-backed
 * provider (`getNoteEncryptionKey`); nothing else changes.
 */
interface NoteKeyProvider {
    /** The 32-byte key, creating it atomically on first use. Throws when it can't be obtained. */
    suspend fun fetchOrCreate(ownerUid: String): ByteArray
}

/** Device-local cache so a cold start doesn't need the network to unlock (iOS Keychain TTL cache). */
interface NoteKeyCache {
    fun save(ownerUid: String, key: ByteArray)

    /** Null when missing, expired, or unreadable (expired / unreadable entries are cleared). */
    fun load(ownerUid: String): ByteArray?

    fun clear(ownerUid: String)
}
