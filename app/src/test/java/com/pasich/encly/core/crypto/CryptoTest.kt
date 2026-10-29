package com.pasich.encly.core.crypto

import com.pasich.encly.core.security.GoldenVectors
import com.pasich.encly.core.security.PinSlots
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The internal crypto API against published vectors (RFC 5869, RFC 7914, McGrew and Viega
 * AES-GCM test cases 15 and 16, FIPS 180 SHA-256) and against the exact bytes the app's formats
 * hold on disk, sealed again here with the nonces of those fixtures.
 */
class CryptoTest {
    private val dek = seq(32, 0, 1)
    private val kek = seq(32, 100, 1)
    private val salt = seq(16, 0, 3)
    private val iv = seq(12, 0x20, 1)
    private val version = byteArrayOf(3)

    // --- Published vectors ----------------------------------------------------------------

    @Test
    fun hkdfMatchesRfc5869() {
        val tc1 = Crypto.hkdfSha256(ByteArray(22) { 0x0b }, seq(13, 0, 1), seq(10, 0xf0, 1), 32)
        val tc2 = Crypto.hkdfSha256(seq(80, 0x00, 1), seq(80, 0x60, 1), seq(80, 0xb0, 1), 32)

        assertArrayEquals(GoldenVectors.rfc5869Tc1Okm32, tc1)
        assertArrayEquals(GoldenVectors.rfc5869Tc2Okm32, tc2)
    }

    @Test
    fun hkdfRefusesWhatTheAppNeverUses() {
        assertThrows(IllegalArgumentException::class.java) { Crypto.hkdfSha256(dek, ByteArray(0), salt, 32) }
        assertThrows(IllegalArgumentException::class.java) { Crypto.hkdfSha256(dek, salt, salt, 33) }
        assertThrows(IllegalArgumentException::class.java) { Crypto.hkdfSha256(dek, salt, salt, 0) }
    }

    @Test
    fun pbkdf2MatchesRfc7914() {
        // RFC 7914 section 11, first 32 bytes of the 64-byte outputs.
        assertArrayEquals(
            GoldenVectors.rfc7914C1,
            Crypto.pbkdf2HmacSha256("passwd".toByteArray(), "salt".toByteArray(), 1, 32),
        )
        assertArrayEquals(
            GoldenVectors.rfc7914C80000,
            Crypto.pbkdf2HmacSha256("Password".toByteArray(), "NaCl".toByteArray(), 80_000, 32),
        )
    }

    @Test
    fun pbkdf2KeepsTheAppsPinParameters() {
        val stretched = Crypto.pbkdf2HmacSha256("123456".toByteArray(), salt, 600_000, 32)

        assertArrayEquals(GoldenVectors.pinSw, stretched)
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
        assertArrayEquals(GoldenVectors.gcmTc15, tc15)
        assertArrayEquals(plaintext, Crypto.aesGcmOpen(key, nonce, ByteArray(0), tc15))
        // Test case 16: 60 bytes of plaintext and 20 of AAD.
        val tc16 = Crypto.aesGcmSeal(key, nonce, aad, plaintext.copyOf(60))
        assertArrayEquals(GoldenVectors.gcmTc16, tc16)
        assertArrayEquals(plaintext.copyOf(60), Crypto.aesGcmOpen(key, nonce, aad, tc16))
    }

    @Test
    fun sha256MatchesFips180() {
        assertArrayEquals(GoldenVectors.sha256Abc, Crypto.sha256("abc".toByteArray()))
        assertArrayEquals(GoldenVectors.sha256Abc, Crypto.sha256("xxabcyy".toByteArray(), 2, 5))
        assertEquals(32, Crypto.sha256(ByteArray(0)).size)
    }

    // --- AES-GCM behaviour -----------------------------------------------------------------

    @Test
    fun aGcmOpenIsNullForAnythingThatDoesNotAuthenticate() {
        val sealed = Crypto.aesGcmSeal(kek, iv, salt, dek)

        assertArrayEquals(dek, Crypto.aesGcmOpen(kek, iv, salt, sealed))
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
        assertArrayEquals(ByteArray(0), Crypto.aesGcmOpen(kek, iv, salt, sealed))
    }

    @Test
    fun theKeyAndTheNonceMustHaveTheSizesTheFormatsUse() {
        assertThrows(IllegalArgumentException::class.java) { Crypto.aesGcmSeal(ByteArray(16), iv, salt, dek) }
        assertThrows(IllegalArgumentException::class.java) { Crypto.aesGcmSeal(kek, ByteArray(16), salt, dek) }
        assertThrows(IllegalArgumentException::class.java) { Crypto.aesGcmOpen(ByteArray(31), iv, salt, dek) }
        assertThrows(IllegalArgumentException::class.java) { Crypto.aesGcmOpen(kek, ByteArray(11), salt, dek) }
    }

    // --- The formats on disk, sealed again with the fixtures' nonces ---------------------------

    @Test
    fun sealingReproducesEveryGoldenSlot() {
        val wipeKek = seq(32, 150, 1)
        val marker = seq(32, 0x40, 1)
        val decoyDek = seq(32, 0x80, 1)
        val decoyKek = seq(32, 200, 1)

        assertArrayEquals(
            GoldenVectors.pinSlot,
            version + salt + iv + Crypto.aesGcmSeal(kek, iv, PinSlots.aad(salt), dek),
        )
        assertArrayEquals(
            GoldenVectors.wipeSlot,
            version + iv + Crypto.aesGcmSeal(wipeKek, iv, PinSlots.wipeAad(salt), marker),
        )
        assertArrayEquals(
            GoldenVectors.duressSlot,
            version + iv +
                Crypto.aesGcmSeal(decoyKek, iv, "encly/pin/decoy-slot/v3".toByteArray() + version + salt, decoyDek),
        )
        assertArrayEquals(
            GoldenVectors.decoyKeyActive,
            version + iv +
                Crypto.aesGcmSeal(
                    GoldenVectors.decoyKeyKek,
                    iv,
                    "encly/decoy/key/v1".toByteArray() + version,
                    decoyDek,
                ),
        )
        assertArrayEquals(
            GoldenVectors.decoyKeyOff,
            version + iv +
                Crypto.aesGcmSeal(
                    GoldenVectors.decoyKeyKek,
                    iv,
                    "encly/decoy/key/off/v1".toByteArray() + version,
                    decoyDek,
                ),
        )
        assertArrayEquals(
            GoldenVectors.recoverySlot,
            iv + Crypto.aesGcmSeal(GoldenVectors.recoveryKek, iv, "encly/recovery/slot/v3".toByteArray(), dek),
        )
        assertArrayEquals(
            GoldenVectors.backupSlot,
            iv +
                Crypto.aesGcmSeal(
                    GoldenVectors.backupSlotKek,
                    iv,
                    "encly/backup/slot/v1".toByteArray(),
                    GoldenVectors.backupRoot,
                ),
        )
    }

    @Test
    fun sealingReproducesTheGoldenBackupFile() {
        val header = GoldenVectors.backupFile.copyOf(61)
        val nonce = header.copyOfRange(45, 57)
        val payload = "{\"notes\":[\"secret note body\"]}".toByteArray()

        val sealed = Crypto.aesGcmSeal(GoldenVectors.backupFileKey, nonce, header, payload)

        assertArrayEquals(GoldenVectors.backupFile, header + sealed)
    }

    @Test
    fun theKeyChainReproducesTheGoldenPinKeys() {
        val ikm = Crypto.pbkdf2HmacSha256("123456".toByteArray(), salt, 600_000, 32) + GoldenVectors.pinHw

        assertArrayEquals(GoldenVectors.pinKek, Crypto.hkdfSha256(ikm, salt, "encly/pin/kek/v3".toByteArray(), 32))
        assertArrayEquals(GoldenVectors.pinWipeKek, Crypto.hkdfSha256(ikm, salt, "encly/pin/wipe/v3".toByteArray(), 32))
        assertArrayEquals(
            GoldenVectors.pinDecoyKek,
            Crypto.hkdfSha256(ikm, salt, "encly/pin/decoy/v3".toByteArray(), 32),
        )
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
