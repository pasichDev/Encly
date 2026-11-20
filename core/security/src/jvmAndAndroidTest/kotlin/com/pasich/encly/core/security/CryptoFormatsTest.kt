package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * The exact bytes the app's formats hold on disk (PIN, wipe, duress, decoy-key, recovery and backup slots,
 * the backup file and the key chain under them), sealed again here with the nonces of the fixtures.
 */
class CryptoFormatsTest {
    private val dek = seq(32, 0, 1)
    private val kek = seq(32, 100, 1)
    private val salt = seq(16, 0, 3)
    private val iv = seq(12, 0x20, 1)
    private val version = byteArrayOf(3)

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

    private fun seq(size: Int, offset: Int, step: Int) = ByteArray(size) { (it * step + offset).toByte() }
}
