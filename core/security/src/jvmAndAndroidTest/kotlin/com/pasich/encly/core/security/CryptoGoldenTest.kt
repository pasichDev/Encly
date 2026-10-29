package com.pasich.encly.core.security

import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.core.backup.BackupKeys
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Byte-for-byte pins of every cryptographic format the app persists: the PIN, wipe, duress and
 * decoy-key slots, the recovery and backup-key slots, the backup file, the slot file's checksum
 * and the key derivations under them. The expected bytes in [GoldenVectors] were produced with
 * the platform JCA over fixed inputs, so these tests hold for any implementation underneath.
 * Seals use random IVs, so they are checked by opening them again with an independent JCA
 * reference ([Reference]) and by their layout.
 */
class CryptoGoldenTest {
    private val dek = seq(32, 0, 1)
    private val kek = seq(32, 100, 1)
    private val salt = seq(16, 0, 3)
    private val wipeKek = seq(32, 150, 1)
    private val decoyDek = seq(32, 0x80, 1)
    private val decoyKek = seq(32, 200, 1)
    private val backupSalt = seq(32, 0x10, 1)
    private val hardwareKey = seq(32, 0x70, 1)
    private val payload = "{\"notes\":[\"secret note body\"]}".toByteArray()

    // --- HKDF -----------------------------------------------------------------------------

    @Test
    fun hkdfMatchesRfc5869() {
        // Test cases 1 and 2, first 32 bytes of the OKM (all this app ever asks for).
        val tc1 = Hkdf.sha256(
            ikm = ByteArray(22) { 0x0b },
            salt = seq(13, 0, 1),
            info = seq(10, 0xf0, 1),
            length = 32,
        )
        val tc2 = Hkdf.sha256(
            ikm = seq(80, 0x00, 1),
            salt = seq(80, 0x60, 1),
            info = seq(80, 0xb0, 1),
            length = 32,
        )

        assertArrayEquals(GoldenVectors.rfc5869Tc1Okm32, tc1)
        assertArrayEquals(GoldenVectors.rfc5869Tc2Okm32, tc2)
    }

    @Test
    fun hkdfShorterOutputIsThePrefixOfTheLongerOne() {
        val full = Hkdf.sha256(dek, salt, "info".toByteArray(), 32)
        val short = Hkdf.sha256(dek, salt, "info".toByteArray(), 16)

        assertArrayEquals(full.copyOf(16), short)
    }

    @Test
    fun hkdfRefusesAnEmptySaltAndAnOutputLongerThanOneBlock() {
        assertThrows(IllegalArgumentException::class.java) { Hkdf.sha256(dek, ByteArray(0), ByteArray(1), 32) }
        assertThrows(IllegalArgumentException::class.java) { Hkdf.sha256(dek, salt, ByteArray(1), 33) }
        assertThrows(IllegalArgumentException::class.java) { Hkdf.sha256(dek, salt, ByteArray(1), 0) }
    }

    @Test
    fun theKeyHierarchyDerivesTheGoldenKeys() {
        val stretchedAndHardware = GoldenVectors.pinSw + GoldenVectors.pinHw

        assertArrayEquals(GoldenVectors.decoyKeyKek, DuressSlots.decoyKeyKek(dek))
        assertArrayEquals(GoldenVectors.pinKek, pinKey(stretchedAndHardware, "encly/pin/kek/v3"))
        assertArrayEquals(GoldenVectors.pinWipeKek, pinKey(stretchedAndHardware, "encly/pin/wipe/v3"))
        assertArrayEquals(GoldenVectors.pinDecoyKek, pinKey(stretchedAndHardware, "encly/pin/decoy/v3"))
    }

    private fun pinKey(ikm: ByteArray, info: String) = Hkdf.sha256(ikm, salt, info.toByteArray(), 32)

    // --- Recovery phrase, SHA-256 ---------------------------------------------------------

    @Test
    fun seedHashIsSha256OfTheNormalizedWords() {
        // FIPS 180-2 "abc".
        assertArrayEquals(GoldenVectors.sha256Abc, Mnemonic.seedHash("abc".toCharArray()))
        assertArrayEquals(GoldenVectors.seedHash, Mnemonic.seedHash(PHRASE.toCharArray()))
    }

    @Test
    fun seedHashIgnoresCaseAndWhitespaceShape() {
        val messy = "  Abandon\tabandon   ABANDON abandon abandon abandon abandon abandon abandon abandon" +
            " abandon\n\nAbout \n"

        assertArrayEquals(GoldenVectors.seedHash, Mnemonic.seedHash(messy.toCharArray()))
    }

    @Test
    fun seedHashOfNonAsciiWordsIsSha256OfTheirUtf8() {
        val words = "café über"

        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(words.toByteArray(Charsets.UTF_8)),
            Mnemonic.seedHash(words.toCharArray()),
        )
    }

    @Test
    fun backupKeysDeriveTheGoldenRootAndFileKey() {
        val root = BackupKeys.rootFromMnemonic(PHRASE.toCharArray())

        assertArrayEquals(GoldenVectors.backupRoot, root)
        assertArrayEquals(GoldenVectors.backupFileKey, BackupKeys.fileKeyFromRoot(root, backupSalt))
    }

    // --- PIN, wipe, duress and decoy-key slots --------------------------------------------

    @Test
    fun theGoldenPinSlotOpensWithItsKek() {
        val parsed = PinSlots.parseSlot(GoldenVectors.pinSlot)!!

        assertArrayEquals(salt, parsed.salt)
        assertArrayEquals(dek, PinSlots.openSlotOrNull(parsed, kek))
        assertNull(PinSlots.openSlotOrNull(parsed, wipeKek))
    }

    @Test
    fun aSealedPinSlotHasTheDocumentedLayoutAndOpensWithAnIndependentImplementation() {
        val slot = PinSlots.sealSlot(dek, kek, salt)

        assertEquals(1 + 16 + 12 + 32 + 16, slot.size)
        assertEquals(3, slot[0].toInt())
        assertArrayEquals(salt, slot.copyOfRange(1, 17))
        val aad = "encly/pin/slot/v3".toByteArray() + byteArrayOf(3) + salt
        assertArrayEquals(dek, Reference.gcmOpen(kek, slot.copyOfRange(17, 29), aad, slot.copyOfRange(29, slot.size)))
        assertFalse(slot.contentEquals(PinSlots.sealSlot(dek, kek, salt)))
    }

    @Test
    fun theGoldenWipeSlotOpensWithItsKekAndTheSaltItIsBoundTo() {
        assertTrue(PinSlots.opensWipeSlot(GoldenVectors.wipeSlot, wipeKek, salt))
        assertFalse(PinSlots.opensWipeSlot(GoldenVectors.wipeSlot, kek, salt))
        assertFalse(PinSlots.opensWipeSlot(GoldenVectors.wipeSlot, wipeKek, seq(16, 1, 3)))
        assertTrue(PinSlots.opensWipeSlot(GoldenVectors.chainWipeSlot, GoldenVectors.pinWipeKek, salt))
    }

    @Test
    fun aSealedWipeSlotHasTheDocumentedLayoutAndOpensWithAnIndependentImplementation() {
        val slot = PinSlots.sealWipeSlot(wipeKek, salt)

        assertEquals(1 + 12 + 32 + 16, slot.size)
        assertEquals(3, slot[0].toInt())
        val aad = "encly/pin/wipe-slot/v3".toByteArray() + byteArrayOf(3) + salt
        val marker = Reference.gcmOpen(wipeKek, slot.copyOfRange(1, 13), aad, slot.copyOfRange(13, slot.size))
        assertEquals(32, marker.size)
        assertTrue(PinSlots.opensWipeSlot(slot, wipeKek, salt))
        assertFalse(slot.contentEquals(PinSlots.sealWipeSlot(wipeKek, salt)))
    }

    @Test
    fun aDecoyWipeSlotIsShapedLikeARealOneAndOpensWithNoKey() {
        val decoy = PinSlots.decoyWipeSlot()

        assertEquals(PinSlots.sealWipeSlot(wipeKek, salt).size, decoy.size)
        assertEquals(3, decoy[0].toInt())
        assertFalse(PinSlots.opensWipeSlot(decoy, wipeKek, salt))
        assertFalse(decoy.contentEquals(PinSlots.decoyWipeSlot()))
    }

    @Test
    fun theGoldenDuressSlotOpensWithItsKekAndSalt() {
        val aad = DuressSlots.decoySlotAad(salt)

        assertArrayEquals("encly/pin/decoy-slot/v3".toByteArray() + byteArrayOf(3) + salt, aad)
        assertArrayEquals(decoyDek, DuressSlots.openDecoy(GoldenVectors.duressSlot, decoyKek, aad))
        assertNull(DuressSlots.openDecoy(GoldenVectors.duressSlot, kek, aad))
        assertNull(DuressSlots.openDecoy(GoldenVectors.duressSlot, decoyKek, DuressSlots.decoySlotAad(seq(16, 1, 3))))
    }

    @Test
    fun theGoldenDecoyKeyOpensOnlyUnderItsOwnAad() {
        val kek = GoldenVectors.decoyKeyKek

        assertArrayEquals(decoyDek, DuressSlots.openDecoy(GoldenVectors.decoyKeyActive, kek, DuressSlots.DECOY_KEY_AAD))
        assertNull(DuressSlots.openDecoy(GoldenVectors.decoyKeyActive, kek, DuressSlots.DECOY_KEY_OFF_AAD))
        assertArrayEquals(
            decoyDek,
            DuressSlots.openDecoy(GoldenVectors.decoyKeyOff, kek, DuressSlots.DECOY_KEY_OFF_AAD),
        )
        assertNull(DuressSlots.openDecoy(GoldenVectors.decoyKeyOff, kek, DuressSlots.DECOY_KEY_AAD))
        assertArrayEquals("encly/decoy/key/v1".toByteArray() + byteArrayOf(3), DuressSlots.DECOY_KEY_AAD)
        assertArrayEquals("encly/decoy/key/off/v1".toByteArray() + byteArrayOf(3), DuressSlots.DECOY_KEY_OFF_AAD)
    }

    @Test
    fun aSealedDecoyHasTheDocumentedLayoutAndOpensWithAnIndependentImplementation() {
        val aad = DuressSlots.decoySlotAad(salt)
        val slot = DuressSlots.sealDecoy(decoyDek, decoyKek, aad)

        assertEquals(1 + 12 + 32 + 16, slot.size)
        assertEquals(3, slot[0].toInt())
        assertArrayEquals(decoyDek, Reference.gcmOpen(decoyKek, slot.copyOfRange(1, 13), aad, slot.copyOfRange(13, 61)))
        assertEquals(slot.size, DuressSlots.randomSlot().size)
        assertEquals(3, DuressSlots.randomSlot()[0].toInt())
    }

    @Test
    fun aTamperedOrMisSizedSlotIsRefusedWithoutAnException() {
        val slot = GoldenVectors.duressSlot.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
        val aad = DuressSlots.decoySlotAad(salt)

        assertNull(DuressSlots.openDecoy(slot, decoyKek, aad))
        assertNull(DuressSlots.openDecoy(GoldenVectors.duressSlot.copyOf(60), decoyKek, aad))
        assertFalse(PinSlots.opensWipeSlot(GoldenVectors.wipeSlot.copyOf(40), wipeKek, salt))
    }

    // --- Recovery and backup-key slots (SeedPhraseManager) ---------------------------------

    @Test
    fun theGoldenRecoveryAndBackupSlotsOpenInAVault() {
        val manager = vaultWith(
            "recovery.slot" to GoldenVectors.recoverySlot,
            "backup.slot" to GoldenVectors.backupSlot,
        )

        assertArrayEquals(dek, manager.unlockWithSeed(PHRASE.toCharArray()))
        assertArrayEquals(dek, manager.unlockWithSeed(MESSY_PHRASE.toCharArray()))
        assertNull(manager.unlockWithSeed(OTHER_PHRASE.toCharArray()))
        assertArrayEquals(GoldenVectors.backupRoot, manager.unwrapBackupRoot(dek))
        assertNull(manager.unwrapBackupRoot(seq(32, 1, 1)))
    }

    @Test
    fun theSlotsAVaultWritesOpenWithAnIndependentImplementation() {
        val store = VaultStore(tempVaultFile())
        val manager = SeedPhraseManager(store)
        assertTrue(manager.initializeVault(PHRASE.toCharArray()))
        val bootstrap = manager.copyBootstrapKey()!!

        val recovery = store.getBytes("recovery.slot")!!
        val backup = store.getBytes("backup.slot")!!

        assertEquals(12 + 32 + 16, recovery.size)
        assertEquals(12 + 32 + 16, backup.size)
        assertArrayEquals(
            bootstrap,
            Reference.gcmOpen(
                GoldenVectors.recoveryKek,
                recovery.copyOf(12),
                "encly/recovery/slot/v3".toByteArray(),
                recovery.copyOfRange(12, recovery.size),
            ),
        )
        val slotKek = Reference.hkdf(bootstrap, "encly/backup/slot/salt/v1", "encly/backup/slot/kek/v1")
        assertArrayEquals(
            GoldenVectors.backupRoot,
            Reference.gcmOpen(
                slotKek,
                backup.copyOf(12),
                "encly/backup/slot/v1".toByteArray(),
                backup.copyOfRange(12, backup.size),
            ),
        )
    }

    @Test
    fun aVaultOpensItsOwnBackupKeyAndAddsOneForTheRightPhraseOnly() {
        val store = VaultStore(tempVaultFile())
        val manager = SeedPhraseManager(store)
        assertTrue(manager.initializeVault(PHRASE.toCharArray()))
        val bootstrap = manager.copyBootstrapKey()!!

        assertArrayEquals(GoldenVectors.backupRoot, manager.unwrapBackupRoot(bootstrap))
        assertFalse(manager.createBackupKey(OTHER_PHRASE.toCharArray(), bootstrap))
        assertTrue(manager.createBackupKey(PHRASE.toCharArray(), bootstrap))
        assertArrayEquals(GoldenVectors.backupRoot, manager.unwrapBackupRoot(bootstrap))
    }

    // --- Backup file ----------------------------------------------------------------------

    @Test
    fun theGoldenBackupFileOpensWithThePhraseAndWithTheRoot() {
        assertArrayEquals(
            payload,
            BackupCipher.open(GoldenVectors.backupFile.copyOf(), BackupSecret.RecoveryPhrase(PHRASE.toCharArray())),
        )
        assertArrayEquals(
            payload,
            BackupCipher.open(goldenFile(), BackupSecret.RecoveryPhrase(MESSY_PHRASE.toCharArray())),
        )
        assertArrayEquals(
            payload,
            BackupCipher.open(goldenFile(), BackupSecret.RecoveryRoot(GoldenVectors.backupRoot.copyOf())),
        )
    }

    @Test
    fun theGoldenBackupFileIsRefusedWithAWrongSecretOrAnyChangedByte() {
        val phrase = { BackupSecret.RecoveryPhrase(PHRASE.toCharArray()) }

        assertWrongSecret { BackupCipher.open(goldenFile(), BackupSecret.RecoveryPhrase(OTHER_PHRASE.toCharArray())) }
        // One byte in the salt, the nonce, the ciphertext and the tag of the file.
        for (index in intArrayOf(14, 50, 70, GoldenVectors.backupFile.size - 1)) {
            val file = GoldenVectors.backupFile.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertWrongSecret { BackupCipher.open(file, phrase()) }
        }
    }

    @Test
    fun aSealedBackupHasTheDocumentedLayoutAndOpensWithAnIndependentImplementation() {
        val file = BackupCipher.seal(payload, BackupSecret.RecoveryPhrase(PHRASE.toCharArray()))

        assertEquals(BackupFormat.HEADER_LENGTH + payload.size + 16, file.size)
        assertArrayEquals("ENCLYBAK".toByteArray(), file.copyOf(8))
        assertEquals(1, file[9].toInt())
        assertEquals(1, file[10].toInt())
        assertEquals(32, file[11].toInt())
        assertEquals(12, file[44].toInt())
        val header = file.copyOf(61)
        val fileKey = Reference.hkdfBytes(GoldenVectors.backupRoot, file.copyOfRange(12, 44), "encly/backup/file/v1")
        val opened = Reference.gcmOpen(fileKey, file.copyOfRange(45, 57), header, file.copyOfRange(61, file.size))
        assertArrayEquals(payload, opened)
        val root = BackupSecret.RecoveryRoot(GoldenVectors.backupRoot.copyOf())
        assertArrayEquals(payload, BackupCipher.open(file, root))
    }

    @Test
    fun twoSealsOfTheSamePayloadShareNeitherSaltNorNonce() {
        val first = BackupCipher.seal(payload, BackupSecret.RecoveryPhrase(PHRASE.toCharArray()))
        val second = BackupCipher.seal(payload, BackupSecret.RecoveryPhrase(PHRASE.toCharArray()))

        assertFalse(first.copyOfRange(12, 44).contentEquals(second.copyOfRange(12, 44)))
        assertFalse(first.copyOfRange(45, 57).contentEquals(second.copyOfRange(45, 57)))
    }

    // --- Slot file (SHA-256 checksum) -----------------------------------------------------

    @Test
    fun theSlotFileIsTheGoldenBytes() {
        val file = tempVaultFile()
        val store = VaultStore(file)

        assertTrue(
            store.edit {
                putBytes("a", byteArrayOf(1, 2, 3))
                putBytes("bc", ByteArray(0))
            },
        )

        assertArrayEquals(GoldenVectors.storeFile, file.readBytes())
    }

    @Test
    fun theGoldenSlotFileIsReadBackAndAChangedByteMakesItCorrupt() {
        val intact = tempVaultFile().also { it.writeBytes(GoldenVectors.storeFile) }
        val store = VaultStore(intact)

        assertFalse(store.isCorrupt())
        assertArrayEquals(byteArrayOf(1, 2, 3), store.getBytes("a"))
        assertArrayEquals(ByteArray(0), store.getBytes("bc"))

        for (index in intArrayOf(10, GoldenVectors.storeFile.size - 1)) {
            val damaged = tempVaultFile().also {
                it.writeBytes(GoldenVectors.storeFile.copyOf().also { bytes -> bytes[index] = flipped(bytes[index]) })
            }
            assertTrue(VaultStore(damaged).isCorrupt())
        }
    }

    // --- The whole PIN chain: PBKDF2 (600 000) -> HKDF -> AES-GCM ----------------------------

    @Test
    fun theGoldenPinAndDuressSlotsUnlockThroughTheFullKdfChain() {
        val store = VaultStore(tempVaultFile())
        store.edit {
            putBytes("pin.slot", GoldenVectors.chainPinSlot)
            putBytes("pin.decoy.slot", GoldenVectors.chainDuressSlot)
        }
        val manager = AuthenticationManager(store, FixedKeyFactor(hardwareKey), FakeLockoutClock())

        val real = manager.unlockWithPin("123456".toCharArray())
        val duress = manager.unlockWithPin("654321".toCharArray())
        val wrong = manager.unlockWithPin("000000".toCharArray())

        assertArrayEquals(dek, (real as PinUnlock.Success).dek)
        assertArrayEquals(decoyDek, (duress as PinUnlock.Decoy).dek)
        assertEquals(PinUnlock.WrongPin, wrong)
    }

    private class FixedKeyFactor(private val key: ByteArray) : PinHardwareFactor {
        override fun ensureKey(slot: PinKeySlot) = false

        override fun reset(slot: PinKeySlot) = Unit

        override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

        override fun delete(slot: PinKeySlot) = Unit
    }

    // --- Helpers --------------------------------------------------------------------------

    private fun vaultWith(vararg slots: Pair<String, ByteArray>): SeedPhraseManager {
        val store = VaultStore(tempVaultFile())
        store.edit {
            putInt("vault.version", 3)
            putBoolean("recovery.enabled", true)
            slots.forEach { (key, value) -> putBytes(key, value) }
        }
        return SeedPhraseManager(store)
    }

    private fun assertWrongSecret(block: () -> Unit) {
        val e = assertThrows(BackupException::class.java) { block() }
        assertEquals(BackupError.WRONG_SECRET, e.error)
    }

    /** Plain JCA, to open what the code under test sealed. */
    private object Reference {
        fun gcmOpen(key: ByteArray, iv: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            return cipher.doFinal(ciphertext)
        }

        fun hkdf(ikm: ByteArray, salt: String, info: String): ByteArray = hkdfBytes(ikm, salt.toByteArray(), info)

        fun hkdfBytes(ikm: ByteArray, salt: ByteArray, info: String): ByteArray {
            val prk = hmac(salt, ikm)
            return hmac(prk, info.toByteArray() + byteArrayOf(1))
        }

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
    }

    private fun goldenFile() = GoldenVectors.backupFile.copyOf()

    private fun flipped(byte: Byte) = (byte.toInt() xor 1).toByte()

    private fun seq(size: Int, offset: Int, step: Int) = ByteArray(size) { (it * step + offset).toByte() }

    private companion object {
        const val PHRASE =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val MESSY_PHRASE =
            "  ABANDON  abandon\tabandon abandon abandon abandon abandon abandon abandon abandon abandon\nAbout "
        const val OTHER_PHRASE = "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
    }
}
