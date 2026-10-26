package com.stoneofhope.stoneofhope.data.crypto

import com.stoneofhope.stoneofhope.data.crypto.StoneOfHopeCrypto.CryptoException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectors from the iOS repo's `docs/CRYPTO_SYNC_CONTRACT.md` §6 (same as iOS `StoneOfHopeCryptoTests`). */
class StoneOfHopeCryptoTest {

    private val testNoteKeyBase64 = "x6rXPx3Wd+qfUilhSN61MIjS+s5tIHFHI+HoXxq6yX4="
    private val testIvBase64 = "MTIzNDU2Nzg5MDEy"
    private val testPlaintext = "🙏 2026 祈祷与重构：愿心中有平安。🌟"
    private val expectedKeyHex = "c7aad73f1dd677ea9f52296148deb53088d2face6d20714723e1e85f1abac97e"
    private val expectedCiphertextBase64 =
        "Jqcecad3HWv41Xj0bXlgcQbIfk4ZeB7Tx/jNLAOCJ2Gbj9WayputB6lb2hNrVx4TIbgI//s3MwHQN0IvLw6NoQiXA+59"

    private val contractNoteKey: ByteArray
        get() = StoneOfHopeCrypto.base64Decode(testNoteKeyBase64).also {
            assertEquals(StoneOfHopeCrypto.KEY_BYTE_COUNT, it.size)
        }

    @Test
    fun contractNoteKeyMatchesHex() {
        assertEquals(expectedKeyHex, contractNoteKey.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun contractIvDecodesTo12AsciiBytes() {
        val iv = StoneOfHopeCrypto.base64Decode(testIvBase64)
        assertEquals("123456789012", String(iv, Charsets.US_ASCII))
    }

    @Test
    fun fixedIvEncryptionMatchesContractVector() {
        val iv = StoneOfHopeCrypto.base64Decode(testIvBase64)
        val result = StoneOfHopeCrypto.encrypt(testPlaintext, contractNoteKey, iv)
        assertArrayEquals(iv, result.iv)
        assertEquals(expectedCiphertextBase64, StoneOfHopeCrypto.base64Encode(result.sealed))
    }

    @Test
    fun fixedIvDecryptRoundTrip() {
        val iv = StoneOfHopeCrypto.base64Decode(testIvBase64)
        val sealed = StoneOfHopeCrypto.base64Decode(expectedCiphertextBase64)
        assertEquals(testPlaintext, StoneOfHopeCrypto.decrypt(iv, sealed, contractNoteKey))
    }

    @Test
    fun randomIvRoundTripChineseAndEmoji() {
        val key = StoneOfHopeCrypto.generateNoteKey()
        val encrypted = StoneOfHopeCrypto.encrypt(testPlaintext, key)
        assertEquals(StoneOfHopeCrypto.IV_BYTE_COUNT, encrypted.iv.size)
        assertTrue(encrypted.sealed.size >= StoneOfHopeCrypto.TAG_BYTE_COUNT)
        assertEquals(testPlaintext, StoneOfHopeCrypto.decrypt(encrypted.iv, encrypted.sealed, key))
    }

    @Test
    fun emptyPlaintextRoundTrip() {
        val key = StoneOfHopeCrypto.generateNoteKey()
        val encrypted = StoneOfHopeCrypto.encrypt("", key)
        assertEquals(StoneOfHopeCrypto.TAG_BYTE_COUNT, encrypted.sealed.size)
        assertEquals("", StoneOfHopeCrypto.decrypt(encrypted.iv, encrypted.sealed, key))
    }

    @Test
    fun productionEncryptUsesFreshIvEachCall() {
        val key = StoneOfHopeCrypto.generateNoteKey()
        val a = StoneOfHopeCrypto.encrypt(testPlaintext, key)
        val b = StoneOfHopeCrypto.encrypt(testPlaintext, key)
        assertFalse(a.iv.contentEquals(b.iv))
        assertFalse(a.sealed.contentEquals(b.sealed))
    }

    @Test
    fun wrongKeyThrowsAuthenticationFailed() {
        val iv = StoneOfHopeCrypto.base64Decode(testIvBase64)
        val sealed = StoneOfHopeCrypto.base64Decode(expectedCiphertextBase64)
        val wrongKey = ByteArray(StoneOfHopeCrypto.KEY_BYTE_COUNT) { 0xAB.toByte() }
        assertThrows(CryptoException.AuthenticationFailed::class.java) {
            StoneOfHopeCrypto.decrypt(iv, sealed, wrongKey)
        }
    }

    @Test
    fun tamperedCiphertextThrowsAuthenticationFailed() {
        val iv = StoneOfHopeCrypto.base64Decode(testIvBase64)
        val sealed = StoneOfHopeCrypto.base64Decode(expectedCiphertextBase64)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        assertThrows(CryptoException.AuthenticationFailed::class.java) {
            StoneOfHopeCrypto.decrypt(iv, sealed, contractNoteKey)
        }
    }

    @Test
    fun invalidLengthsThrow() {
        val iv = StoneOfHopeCrypto.base64Decode(testIvBase64)
        assertThrows(CryptoException.InvalidKeyLength::class.java) {
            StoneOfHopeCrypto.encrypt("x", ByteArray(16))
        }
        assertThrows(CryptoException.InvalidIvLength::class.java) {
            StoneOfHopeCrypto.encrypt("x", contractNoteKey, ByteArray(16))
        }
        assertThrows(CryptoException.InvalidIvLength::class.java) {
            StoneOfHopeCrypto.decrypt(ByteArray(11), ByteArray(32), contractNoteKey)
        }
        assertThrows(CryptoException.InvalidSealedLength::class.java) {
            StoneOfHopeCrypto.decrypt(iv, ByteArray(15), contractNoteKey)
        }
    }

    @Test
    fun generateNoteKeyProduces32UniqueBytes() {
        val a = StoneOfHopeCrypto.generateNoteKey()
        val b = StoneOfHopeCrypto.generateNoteKey()
        assertEquals(StoneOfHopeCrypto.KEY_BYTE_COUNT, a.size)
        assertEquals(StoneOfHopeCrypto.KEY_BYTE_COUNT, b.size)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun base64RoundTrip() {
        val original = byteArrayOf(0x00, 0xFF.toByte(), 0x12, 0x34, 0xAB.toByte(), 0xCD.toByte())
        assertArrayEquals(original, StoneOfHopeCrypto.base64Decode(StoneOfHopeCrypto.base64Encode(original)))
    }

    @Test
    fun invalidBase64Throws() {
        assertThrows(CryptoException.InvalidBase64::class.java) {
            StoneOfHopeCrypto.base64Decode("not!!!valid")
        }
    }
}
