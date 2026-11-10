package com.pasich.encly.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The crypto API against published vectors (RFC 5869, RFC 7914, McGrew and Viega AES-GCM test cases 15
 * and 16, FIPS 180 SHA-256). The formats built on it are pinned in :core:security.
 */
class CryptoTest {
    private val dek = seq(32, 0, 1)
    private val kek = seq(32, 100, 1)
    private val salt = seq(16, 0, 3)
    private val iv = seq(12, 0x20, 1)

    // --- Published vectors ----------------------------------------------------------------

    @Test
    fun hkdfMatchesRfc5869() {
        val tc1 = Crypto.hkdfSha256(ByteArray(22) { 0x0b }, seq(13, 0, 1), seq(10, 0xf0, 1), 32)
        val tc2 = Crypto.hkdfSha256(seq(80, 0x00, 1), seq(80, 0x60, 1), seq(80, 0xb0, 1), 32)

        assertContentEquals(GoldenVectors.rfc5869Tc1Okm32, tc1)
        assertContentEquals(GoldenVectors.rfc5869Tc2Okm32, tc2)
    }

    @Test
    fun hkdfRefusesWhatTheAppNeverUses() {
        assertFailsWith<IllegalArgumentException> { Crypto.hkdfSha256(dek, ByteArray(0), salt, 32) }
        assertFailsWith<IllegalArgumentException> { Crypto.hkdfSha256(dek, salt, salt, 33) }
        assertFailsWith<IllegalArgumentException> { Crypto.hkdfSha256(dek, salt, salt, 0) }
    }

    @Test
    fun pbkdf2MatchesRfc7914() {
        // RFC 7914 section 11, first 32 bytes of the 64-byte outputs.
        assertContentEquals(
            GoldenVectors.rfc7914C1,
            Crypto.pbkdf2HmacSha256("passwd".encodeToByteArray(), "salt".encodeToByteArray(), 1, 32),
        )
        assertContentEquals(
            GoldenVectors.rfc7914C80000,
            Crypto.pbkdf2HmacSha256("Password".encodeToByteArray(), "NaCl".encodeToByteArray(), 80_000, 32),
        )
    }

    @Test
    fun pbkdf2KeepsTheAppsPinParameters() {
        val stretched = Crypto.pbkdf2HmacSha256("123456".encodeToByteArray(), salt, 600_000, 32)

        assertContentEquals(GoldenVectors.pinSw, stretched)
    }

    @Test
    fun pbkdf2OfCharactersMatchesPbkdf2OfTheirUtf8Bytes() {
        // PBKDF2-HMAC-SHA-256, "password" and "salt", one iteration (a published vector).
        val expected = GoldenVectors.hex("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b")

        assertContentEquals(
            expected,
            Crypto.pbkdf2HmacSha256("password".toCharArray(), "salt".encodeToByteArray(), 1, 32),
        )
        assertContentEquals(
            expected,
            Crypto.pbkdf2HmacSha256("password".encodeToByteArray(), "salt".encodeToByteArray(), 1, 32),
        )
        // Characters beyond ASCII go through UTF-8: the same bytes give the same key.
        assertContentEquals(
            Crypto.pbkdf2HmacSha256("pin é汉".toCharArray(), salt, 2, 32),
            Crypto.pbkdf2HmacSha256("pin é汉".encodeToByteArray(), salt, 2, 32),
        )
    }

    @Test
    fun pbkdf2NeedsAPositiveIterationCountAndLength() {
        val chars = "123456".toCharArray()
        val bytes = "123456".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> { Crypto.pbkdf2HmacSha256(chars, salt, 0, 32) }
        assertFailsWith<IllegalArgumentException> { Crypto.pbkdf2HmacSha256(chars, salt, 1, 0) }
        assertFailsWith<IllegalArgumentException> { Crypto.pbkdf2HmacSha256(bytes, salt, 0, 32) }
        assertFailsWith<IllegalArgumentException> { Crypto.pbkdf2HmacSha256(bytes, salt, 1, 0) }
        assertFailsWith<IllegalArgumentException> { Crypto.pbkdf2HmacSha256(bytes, salt, -1, 32) }
        assertFailsWith<IllegalArgumentException> { Crypto.pbkdf2HmacSha256(bytes, salt, 1, -1) }
    }

    @Test
    fun aesGcmMatchesTheMcGrewAndViegaVectors() {
        val key = GoldenVectors.hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        val nonce = GoldenVectors.hex("cafebabefacedbaddecaf888")
        val plaintext = GoldenVectors.hex(
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e24" +
                "49a6b525b16aedf5aa0de657ba637b391aafd255",
        )
        val aad = GoldenVectors.hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")

        // Test case 15: no AAD (the tag is the last 16 bytes).
        val tc15 = Crypto.aesGcmSeal(key, nonce, ByteArray(0), plaintext)
        assertContentEquals(GoldenVectors.gcmTc15, tc15)
        assertContentEquals(plaintext, Crypto.aesGcmOpen(key, nonce, ByteArray(0), tc15))
        // Test case 16: 60 bytes of plaintext and 20 of AAD.
        val tc16 = Crypto.aesGcmSeal(key, nonce, aad, plaintext.copyOf(60))
        assertContentEquals(GoldenVectors.gcmTc16, tc16)
        assertContentEquals(plaintext.copyOf(60), Crypto.aesGcmOpen(key, nonce, aad, tc16))
    }

    @Test
    fun sha256MatchesFips180() {
        assertContentEquals(GoldenVectors.sha256Abc, Crypto.sha256("abc".encodeToByteArray()))
        assertContentEquals(GoldenVectors.sha256Abc, Crypto.sha256("xxabcyy".encodeToByteArray(), 2, 5))
        assertEquals(32, Crypto.sha256(ByteArray(0)).size)
    }

    // --- AES-GCM behaviour -----------------------------------------------------------------

    @Test
    fun aGcmOpenIsNullForAnythingThatDoesNotAuthenticate() {
        val sealed = Crypto.aesGcmSeal(kek, iv, salt, dek)

        assertContentEquals(dek, Crypto.aesGcmOpen(kek, iv, salt, sealed))
        assertNull(Crypto.aesGcmOpen(seq(32, 101, 1), iv, salt, sealed))
        assertNull(Crypto.aesGcmOpen(kek, seq(12, 0x21, 1), salt, sealed))
        assertNull(Crypto.aesGcmOpen(kek, iv, seq(16, 1, 3), sealed))
        assertNull(Crypto.aesGcmOpen(kek, iv, salt, sealed.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }))
        assertNull(Crypto.aesGcmOpen(kek, iv, salt, sealed.copyOf().also { it[it.size - 1] = 0 }))
        assertNull(Crypto.aesGcmOpen(kek, iv, salt, sealed.copyOf(sealed.size - 1)))
        assertNull(Crypto.aesGcmOpen(kek, iv, salt, sealed.copyOf(15)))
        assertNull(Crypto.aesGcmOpen(kek, iv, salt, ByteArray(0)))
    }

    @Test
    fun aGcmSealOfNothingIsJustATag() {
        val sealed = Crypto.aesGcmSeal(kek, iv, salt, ByteArray(0))

        assertEquals(16, sealed.size)
        assertContentEquals(ByteArray(0), Crypto.aesGcmOpen(kek, iv, salt, sealed))
    }

    @Test
    fun theKeyAndTheNonceMustHaveTheSizesTheFormatsUse() {
        assertFailsWith<IllegalArgumentException> { Crypto.aesGcmSeal(ByteArray(16), iv, salt, dek) }
        assertFailsWith<IllegalArgumentException> { Crypto.aesGcmSeal(kek, ByteArray(16), salt, dek) }
        assertFailsWith<IllegalArgumentException> { Crypto.aesGcmOpen(ByteArray(31), iv, salt, dek) }
        assertFailsWith<IllegalArgumentException> { Crypto.aesGcmOpen(kek, ByteArray(11), salt, dek) }
    }

    // --- Randomness and comparison ------------------------------------------------------------

    @Test
    fun randomBytesHaveTheRequestedSizeAndDoNotRepeat() {
        assertEquals(0, Crypto.randomBytes(0).size)
        assertEquals(16, Crypto.randomBytes(16).size)
        assertFalse(Crypto.randomBytes(32).contentEquals(Crypto.randomBytes(32)))
        assertFalse(Crypto.randomBytes(32).all { it == 0.toByte() })
        assertNotEquals(Crypto.randomLong(), Crypto.randomLong())
    }

    @Test
    fun constantTimeEqualsComparesEveryByteAndTheLength() {
        val a = seq(32, 0, 1)

        assertTrue(Crypto.constantTimeEquals(a, a.copyOf()))
        assertTrue(Crypto.constantTimeEquals(ByteArray(0), ByteArray(0)))
        assertFalse(Crypto.constantTimeEquals(a, a.copyOf(31)))
        assertFalse(Crypto.constantTimeEquals(a.copyOf(31), a))
        assertFalse(Crypto.constantTimeEquals(a, ByteArray(0)))
        for (index in a.indices) {
            val other = a.copyOf().also { it[index] = (it[index].toInt() xor 0x80).toByte() }
            assertFalse(Crypto.constantTimeEquals(a, other))
        }
    }

    private fun seq(size: Int, offset: Int, step: Int) = ByteArray(size) { (it * step + offset).toByte() }
}
