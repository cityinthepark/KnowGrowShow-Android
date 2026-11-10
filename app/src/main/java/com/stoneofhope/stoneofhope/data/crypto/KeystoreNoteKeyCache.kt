package com.stoneofhope.stoneofhope.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.time.Clock
import java.time.Duration
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android counterpart of iOS `CryptoSessionKeychain`: caches `note_key` for [TTL], wrapped with a
 * non-exportable AES key in the Android Keystore. The wrapped blob alone is useless off this device
 * (so a restored backup simply misses the cache and refetches).
 */
@Singleton
class KeystoreNoteKeyCache @Inject constructor(
    @ApplicationContext context: Context,
    private val clock: Clock,
) : NoteKeyCache {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun save(ownerUid: String, key: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val wrapped = cipher.iv + cipher.doFinal(key)
        prefs.edit()
            .putString(blobKey(ownerUid), StoneOfHopeCrypto.base64Encode(wrapped))
            .putLong(expiryKey(ownerUid), clock.millis() + TTL.toMillis())
            .apply()
    }

    override fun load(ownerUid: String): ByteArray? {
        val blob = prefs.getString(blobKey(ownerUid), null) ?: return null
        if (prefs.getLong(expiryKey(ownerUid), 0) <= clock.millis()) {
            clear(ownerUid)
            return null
        }
        return try {
            val wrapped = StoneOfHopeCrypto.base64Decode(blob)
            val iv = wrapped.copyOfRange(0, StoneOfHopeCrypto.IV_BYTE_COUNT)
            val sealed = wrapped.copyOfRange(StoneOfHopeCrypto.IV_BYTE_COUNT, wrapped.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(StoneOfHopeCrypto.TAG_BYTE_COUNT * 8, iv))
            }
            cipher.doFinal(sealed).takeIf { it.size == StoneOfHopeCrypto.KEY_BYTE_COUNT }
                ?: run { clear(ownerUid); null }
        } catch (e: GeneralSecurityException) {
            clear(ownerUid)
            null
        } catch (e: StoneOfHopeCrypto.CryptoException) {
            clear(ownerUid)
            null
        } catch (e: IndexOutOfBoundsException) {
            clear(ownerUid)
            null
        }
    }

    override fun clear(ownerUid: String) {
        prefs.edit().remove(blobKey(ownerUid)).remove(expiryKey(ownerUid)).apply()
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                // Closest match to iOS `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.
                setUnlockedDeviceRequired(true)
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun blobKey(ownerUid: String) = "wrapped_$ownerUid"
    private fun expiryKey(ownerUid: String) = "expiresAt_$ownerUid"

    companion object {
        val TTL: Duration = Duration.ofHours(12)
        private const val PREFS = "note_key_session"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "StoneOfHope.NoteKeySession"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
