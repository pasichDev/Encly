package com.pasich.encly.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The iOS seams that [CryptoTest] reaches only through [Crypto]: empty inputs, short data and the encoder. */
class PlatformCryptoTest {
    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { (it + 1).toByte() }

    @Test
    fun anEmptyPlaintextAndAnEmptyAadSealToJustTheTagAndOpenAgain() {
        val sealed = PlatformCrypto.aesGcmSeal(key, nonce, ByteArray(0), ByteArray(0))

        assertEquals(Crypto.TAG_BYTES, sealed.size)
        assertContentEquals(ByteArray(0), PlatformCrypto.aesGcmOpen(key, nonce, ByteArray(0), sealed))
        assertNull(PlatformCrypto.aesGcmOpen(key, nonce, byteArrayOf(1), sealed))
    }

    @Test
    fun dataShorterThanATagIsRefusedNotCrashed() {
        assertNull(PlatformCrypto.aesGcmOpen(key, nonce, ByteArray(0), ByteArray(0)))
        assertNull(PlatformCrypto.aesGcmOpen(key, nonce, ByteArray(0), ByteArray(Crypto.TAG_BYTES - 1)))
    }

    @Test
    fun aChangedByteOrAWrongKeyDoesNotAuthenticate() {
        val sealed = PlatformCrypto.aesGcmSeal(key, nonce, byteArrayOf(7), "secret".encodeToByteArray())
        val changed = sealed.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertNull(PlatformCrypto.aesGcmOpen(key, nonce, byteArrayOf(7), changed))
        assertNull(PlatformCrypto.aesGcmOpen(ByteArray(32), nonce, byteArrayOf(7), sealed))
        assertContentEquals("secret".encodeToByteArray(), PlatformCrypto.aesGcmOpen(key, nonce, byteArrayOf(7), sealed))
    }

    @Test
    fun aProviderRefusalToSealIsACryptoExceptionNotACrash() {
        // Crypto only passes 32-byte keys and 12-byte nonces; the Swift side refuses anything else.
        kotlin.test.assertFailsWith<CryptoException> {
            PlatformCrypto.aesGcmSeal(ByteArray(17), nonce, ByteArray(0), ByteArray(8))
        }
        assertNull(PlatformCrypto.aesGcmOpen(ByteArray(17), nonce, ByteArray(0), ByteArray(32)))
    }

    @Test
    fun sha256OfARangeAndOfNothing() {
        val data = "xabcx".encodeToByteArray()

        assertContentEquals(GoldenVectors.sha256Abc, PlatformCrypto.sha256(data, 1, 4))
        assertContentEquals(
            GoldenVectors.hex("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
            PlatformCrypto.sha256(data, 2, 2),
        )
        assertContentEquals(
            GoldenVectors.hex("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
            PlatformCrypto.sha256(ByteArray(0), 0, 0),
        )
    }

    @Test
    fun randomBytesAreTheRequestedSizeAndDiffer() {
        assertEquals(0, PlatformCrypto.randomBytes(0).size)
        val first = PlatformCrypto.randomBytes(32)
        assertEquals(32, first.size)
        assertFalse(first.contentEquals(PlatformCrypto.randomBytes(32)))
        assertNotEquals(0L, Crypto.randomLong() or Crypto.randomLong())
    }

    @Test
    fun constantTimeEqualsComparesLengthAndContent() {
        assertTrue(PlatformCrypto.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertTrue(PlatformCrypto.constantTimeEquals(ByteArray(0), ByteArray(0)))
        assertFalse(PlatformCrypto.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 3)))
        assertFalse(PlatformCrypto.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1)))
    }

    @Test
    fun theEncoderMatchesTheJdkOnEveryClassOfCharacter() {
        // Same bytes the JDK's UTF-8 encoder produces: one to four bytes, a pair, and `?` for a lone surrogate.
        assertContentEquals("aé汉😀".encodeToByteArray(), encodeUtf8("aé汉😀".toCharArray()))
        assertContentEquals(byteArrayOf(0x61, 0x3F, 0x62), encodeUtf8(charArrayOf('a', '\uD800', 'b')))
        assertContentEquals(byteArrayOf(0x3F), encodeUtf8(charArrayOf('\uDC00')))
        assertContentEquals(byteArrayOf(0x3F, 0x61), encodeUtf8(charArrayOf('\uD800', 'a')))
        assertContentEquals(byteArrayOf(0x3F), encodeUtf8(charArrayOf('\uD800')))
        assertContentEquals(ByteArray(0), encodeUtf8(CharArray(0)))
    }

    @Test
    fun aPasswordOfCharactersAndOfTheirUtf8BytesDeriveTheSameKey() {
        val salt = ByteArray(16) { it.toByte() }

        assertContentEquals(
            PlatformCrypto.pbkdf2HmacSha256("pin é汉😀".encodeToByteArray(), salt, 3, 32),
            PlatformCrypto.pbkdf2HmacSha256("pin é汉😀".toCharArray(), salt, 3, 32),
        )
        // An empty password and a lone surrogate (`?`) are still passwords; the first value is what the JCA gives.
        assertContentEquals(
            GoldenVectors.hex("8cb314dea8659d2604b8cba0b44e0399deef372bc2ab48585f008fb7686be089"),
            PlatformCrypto.pbkdf2HmacSha256(CharArray(0), byteArrayOf(1, 2, 3), 2, 32),
        )
        assertContentEquals(
            PlatformCrypto.pbkdf2HmacSha256("?".encodeToByteArray(), salt, 1, 32),
            PlatformCrypto.pbkdf2HmacSha256(charArrayOf('\uD800'), salt, 1, 32),
        )
    }
}
