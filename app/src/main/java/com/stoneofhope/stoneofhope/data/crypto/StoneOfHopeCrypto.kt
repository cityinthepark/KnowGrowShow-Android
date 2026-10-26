package com.stoneofhope.stoneofhope.data.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure AES-256-GCM helpers for note bodies. No Room / Firestore dependencies.
 *
 * Mirrors iOS `StoneOfHopeCrypto.swift`; parameters are fixed by the iOS repo's
 * `docs/CRYPTO_SYNC_CONTRACT.md` §2 and must stay byte-compatible with iOS and Web.
 */
object StoneOfHopeCrypto {

    const val KEY_BYTE_COUNT = 32
    const val IV_BYTE_COUNT = 12
    const val TAG_BYTE_COUNT = 16
    const val ENC_VERSION = 1

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val secureRandom = SecureRandom()

    sealed class CryptoException(message: String) : Exception(message) {
        class InvalidKeyLength(expected: Int, actual: Int) :
            CryptoException("Invalid key length: expected $expected, got $actual")
        class InvalidIvLength(expected: Int, actual: Int) :
            CryptoException("Invalid IV length: expected $expected, got $actual")
        class InvalidSealedLength(minimum: Int, actual: Int) :
            CryptoException("Invalid sealed length: minimum $minimum, got $actual")
        class InvalidBase64 : CryptoException("Invalid Base64")
        class EncryptionFailed : CryptoException("Encryption failed")
        class AuthenticationFailed : CryptoException("GCM authentication failed")
        class InvalidPlaintextEncoding : CryptoException("Decrypted bytes are not valid UTF-8")
    }

    /** IV plus `sealed` = ciphertext || 16-byte tag (the layout `Cipher` already produces). */
    class Sealed(val iv: ByteArray, val sealed: ByteArray)

    /** 32-byte random note_key (production). */
    fun generateNoteKey(): ByteArray = ByteArray(KEY_BYTE_COUNT).also(secureRandom::nextBytes)

    /**
     * AES-256-GCM encrypt. Pass [iv] only in tests; production must pass `null` so every
     * call gets a fresh CSPRNG IV (IV reuse under the same key breaks GCM).
     */
    fun encrypt(plaintext: String, key: ByteArray, iv: ByteArray? = null): Sealed {
        requireKey(key)
        val nonce = iv ?: ByteArray(IV_BYTE_COUNT).also(secureRandom::nextBytes)
        requireIv(nonce)

        val sealed = try {
            cipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(plaintext.toByteArray(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            throw CryptoException.EncryptionFailed()
        }
        return Sealed(nonce.copyOf(), sealed)
    }

    /** AES-256-GCM decrypt. [sealed] = ciphertext || 16-byte tag. */
    fun decrypt(iv: ByteArray, sealed: ByteArray, key: ByteArray): String {
        requireKey(key)
        requireIv(iv)
        if (sealed.size < TAG_BYTE_COUNT) {
            throw CryptoException.InvalidSealedLength(TAG_BYTE_COUNT, sealed.size)
        }

        val plain = try {
            cipher(Cipher.DECRYPT_MODE, key, iv).doFinal(sealed)
        } catch (e: AEADBadTagException) {
            throw CryptoException.AuthenticationFailed()
        } catch (e: GeneralSecurityException) {
            throw CryptoException.AuthenticationFailed()
        }

        // Strict decode: never surface mojibake or partially decoded bytes to the UI (contract §5).
        return try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(plain)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw CryptoException.InvalidPlaintextEncoding()
        }
    }

    /** RFC 4648 standard Base64, no line breaks. */
    fun base64Encode(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

    /** RFC 4648 standard Base64 decode; rejects URL-safe alphabet and whitespace like iOS. */
    fun base64Decode(string: String): ByteArray = try {
        Base64.getDecoder().decode(string)
    } catch (e: IllegalArgumentException) {
        throw CryptoException.InvalidBase64()
    }

    private fun cipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTE_COUNT * 8, iv))
        }

    private fun requireKey(key: ByteArray) {
        if (key.size != KEY_BYTE_COUNT) throw CryptoException.InvalidKeyLength(KEY_BYTE_COUNT, key.size)
    }

    private fun requireIv(iv: ByteArray) {
        if (iv.size != IV_BYTE_COUNT) throw CryptoException.InvalidIvLength(IV_BYTE_COUNT, iv.size)
    }
}
