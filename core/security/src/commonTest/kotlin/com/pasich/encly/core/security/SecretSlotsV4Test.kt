package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The slot format v4 (the password): byte-for-byte pins of the main, wipe and duress slots and their AADs, the reader's
 * version dispatch, and that every byte of the header is authenticated by all three slots.
 *
 * What is independent and what is not: the golden slot BYTES below were made out of tree with Python's `cryptography`
 * (AES-256-GCM with a fixed key, IV and AAD, the AAD written out from the layout), never with this module's code. The
 * AAD the tests open them with is built from literals and offsets ([Reference]), not from this module's code either.
 * But OPENING them is this module's own AES-GCM ([Crypto], through [Reference.gcmOpen]): a fault in that primitive
 * would not be seen here. It is pinned elsewhere, by the known-answer tests of [CryptoGoldenTest], and the full v4
 * chain is checked against OpenSSL in `:core:keystore` (`GoldenV4ChainTest`).
 */
class SecretSlotsV4Test {
    private val dek = seq(32, 0, 1)
    private val kek = seq(32, 100, 1)
    private val salt = seq(16, 0, 3)
    private val wipeKek = seq(32, 150, 1)
    private val decoyDek = seq(32, 0x80, 1)
    private val decoyKek = seq(32, 200, 1)
    private val header = SlotHeader(
        SlotHeader.PASSWORD_VERSION,
        SlotHeader.KDF_ARGON2ID,
        SlotHeader.FACTOR_OS_STORE_HMAC,
        memoryKiB = 65_536L,
        passes = 3,
        lanes = 1,
        salt = salt,
    )

    // --- The golden slots -----------------------------------------------------------------------

    @Test
    fun theGoldenMainSlotHasTheDocumentedLayoutAndOpensWithAnIndependentImplementation() {
        assertEquals(85, MAIN.size)
        assertEquals(4, MAIN[0].toInt())
        assertEquals(2, MAIN[1].toInt())
        assertEquals(1, MAIN[2].toInt())
        assertContentEquals(byteArrayOf(0, 1, 0, 0), MAIN.copyOfRange(3, 7))
        assertEquals(3, MAIN[7].toInt())
        assertEquals(1, MAIN[8].toInt())
        assertContentEquals(salt, MAIN.copyOfRange(9, 25))
        assertContentEquals(seq(12, 0x20, 1), MAIN.copyOfRange(25, 37))
        val aad = "encly/secret/slot/v4".encodeToByteArray() + MAIN.copyOf(25)
        assertContentEquals(dek, Reference.gcmOpen(kek, MAIN.copyOfRange(25, 37), aad, MAIN.copyOfRange(37, 85)))
    }

    @Test
    fun theGoldenMainSlotParsesIntoItsHeaderAndOpensWithItsKekOnly() {
        val parsed = assertNotNull(SecretSlots.parse(MAIN))

        assertEquals(SlotHeader.PASSWORD_VERSION, parsed.header.version)
        assertEquals(SlotHeader.KDF_ARGON2ID, parsed.header.kdfId)
        assertEquals(SlotHeader.FACTOR_OS_STORE_HMAC, parsed.header.factorId)
        assertEquals(65_536L, parsed.header.memoryKiB)
        assertEquals(3, parsed.header.passes)
        assertEquals(1, parsed.header.lanes)
        assertContentEquals(salt, parsed.header.salt)
        assertContentEquals(dek, SecretSlots.open(parsed, kek))
        assertNull(SecretSlots.open(parsed, wipeKek))
        assertTrue(parsed.header.sameFormatAs(header))
    }

    @Test
    fun theGoldenWipeAndDuressSlotsOpenWithTheirKeysAndTheMainSlotsHeaderOnly() {
        val aadTail = MAIN.copyOf(25)
        val wipeAad = "encly/secret/wipe-slot/v4".encodeToByteArray() + aadTail
        val duressAad = "encly/secret/decoy-slot/v4".encodeToByteArray() + aadTail

        assertEquals(61, WIPE.size)
        assertEquals(61, DURESS.size)
        assertEquals(4, WIPE[0].toInt())
        assertEquals(4, DURESS[0].toInt())
        assertEquals(32, Reference.gcmOpen(wipeKek, WIPE.copyOfRange(1, 13), wipeAad, WIPE.copyOfRange(13, 61)).size)
        assertContentEquals(
            decoyDek,
            Reference.gcmOpen(decoyKek, DURESS.copyOfRange(1, 13), duressAad, DURESS.copyOfRange(13, 61)),
        )
        assertTrue(SecretSlots.opensWipe(WIPE, wipeKek, header))
        assertContentEquals(decoyDek, SecretSlots.openDuress(DURESS, decoyKek, header))
        // Not the other key, not the other slot's label.
        assertFalse(SecretSlots.opensWipe(WIPE, kek, header))
        assertNull(SecretSlots.openDuress(DURESS, kek, header))
        assertFalse(SecretSlots.opensWipe(DURESS, decoyKek, header))
        assertNull(SecretSlots.openDuress(WIPE, wipeKek, header))
    }

    @Test
    fun theGoldenSlotsShareTheCiphertextOfVersion3WhereOnlyTheAadDiffers() {
        // Same key, IV and plaintext as the v3 golden slots: the ciphertext is the same, only the tag follows the AAD.
        assertContentEquals(
            GoldenVectors.pinSlot.copyOfRange(29, 29 + 32),
            MAIN.copyOfRange(37, 37 + 32),
        )
        assertFalse(GoldenVectors.pinSlot.copyOfRange(61, 77).contentEquals(MAIN.copyOfRange(69, 85)))
    }

    @Test
    fun flippingAnyByteOfTheHeaderMakesEverySlotRefuseToOpen() {
        for (index in 0 until HEADER_LENGTH) {
            val flipped = MAIN.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            val parsed = SecretSlots.parse(flipped)
            // A flipped version byte is no slot at all; any other flip parses and fails authentication.
            if (parsed == null) continue
            assertNull(SecretSlots.open(parsed, kek), "main slot, header byte $index")
            assertFalse(SecretSlots.opensWipe(WIPE, wipeKek, parsed.header), "wipe slot, header byte $index")
            assertNull(SecretSlots.openDuress(DURESS, decoyKek, parsed.header), "duress slot, header byte $index")
        }
    }

    @Test
    fun flippingAnyByteOfTheIvOrTheCiphertextMakesTheMainSlotRefuseToOpen() {
        for (index in HEADER_LENGTH until MAIN.size) {
            val flipped = MAIN.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }

            assertNull(SecretSlots.open(assertNotNull(SecretSlots.parse(flipped)), kek), "byte $index")
        }
    }

    // --- What this code seals -------------------------------------------------------------------

    @Test
    fun aSealedMainSlotHasTheDocumentedLayoutAndOpensWithAnIndependentImplementation() {
        val slot = SecretSlots.seal(dek, kek, header)

        assertEquals(85, slot.size)
        assertContentEquals(MAIN.copyOf(25), slot.copyOf(25))
        val aad = "encly/secret/slot/v4".encodeToByteArray() + slot.copyOf(25)
        assertContentEquals(dek, Reference.gcmOpen(kek, slot.copyOfRange(25, 37), aad, slot.copyOfRange(37, 85)))
        assertFalse(slot.contentEquals(SecretSlots.seal(dek, kek, header)))
    }

    @Test
    fun aSealedWipeSlotAndDuressSlotHaveTheDocumentedLayoutAndOpenWithAnIndependentImplementation() {
        val wipe = SecretSlots.sealWipe(wipeKek, header)
        val duress = SecretSlots.sealDuress(decoyDek, decoyKek, header)
        val tail = MAIN.copyOf(25)

        assertEquals(61, wipe.size)
        assertEquals(61, duress.size)
        assertEquals(4, wipe[0].toInt())
        assertEquals(4, duress[0].toInt())
        val wipeAad = "encly/secret/wipe-slot/v4".encodeToByteArray() + tail
        val duressAad = "encly/secret/decoy-slot/v4".encodeToByteArray() + tail
        assertEquals(32, Reference.gcmOpen(wipeKek, wipe.copyOfRange(1, 13), wipeAad, wipe.copyOfRange(13, 61)).size)
        assertContentEquals(
            decoyDek,
            Reference.gcmOpen(decoyKek, duress.copyOfRange(1, 13), duressAad, duress.copyOfRange(13, 61)),
        )
        assertTrue(SecretSlots.opensWipe(wipe, wipeKek, header))
        assertFalse(wipe.contentEquals(SecretSlots.sealWipe(wipeKek, header)))
    }

    @Test
    fun theLargestCostsAreWrittenAndReadAsUnsignedValues() {
        val extreme = SlotHeader(SlotHeader.PASSWORD_VERSION, 255, 255, 0xFFFF_FFFFL, 255, 255, seq(16, 5, 1))

        val slot = SecretSlots.seal(dek, kek, extreme)
        val parsed = assertNotNull(SecretSlots.parse(slot))

        assertContentEquals(byteArrayOf(-1, -1, -1, -1), slot.copyOfRange(3, 7))
        assertEquals(0xFFFF_FFFFL, parsed.header.memoryKiB)
        assertEquals(255, parsed.header.kdfId)
        assertEquals(255, parsed.header.factorId)
        assertEquals(255, parsed.header.passes)
        assertEquals(255, parsed.header.lanes)
        assertContentEquals(dek, SecretSlots.open(parsed, kek))
    }

    @Test
    fun aHeaderThatDoesNotFitTheFormatIsNeverWritten() {
        val bad = listOf(
            SlotHeader(SlotHeader.PASSWORD_VERSION, 256, 1, 65_536L, 3, 1, salt),
            SlotHeader(SlotHeader.PASSWORD_VERSION, 2, -1, 65_536L, 3, 1, salt),
            SlotHeader(SlotHeader.PASSWORD_VERSION, 2, 1, 0x1_0000_0000L, 3, 1, salt),
            SlotHeader(SlotHeader.PASSWORD_VERSION, 2, 1, -1L, 3, 1, salt),
            SlotHeader(SlotHeader.PASSWORD_VERSION, 2, 1, 65_536L, 256, 1, salt),
            SlotHeader(SlotHeader.PASSWORD_VERSION, 2, 1, 65_536L, 3, 256, salt),
            SlotHeader(SlotHeader.PASSWORD_VERSION, 2, 1, 65_536L, 3, 1, salt.copyOf(15)),
            SlotHeader(5, 2, 1, 65_536L, 3, 1, salt),
        )

        bad.forEach { assertFailsWith<IllegalArgumentException> { SecretSlots.seal(dek, kek, it) } }
    }

    // --- The reader -----------------------------------------------------------------------------

    @Test
    fun theReaderDispatchesOnTheVersionByteAndTheExactLength() {
        val v3 = assertNotNull(SecretSlots.parse(GoldenVectors.pinSlot))
        val v4 = assertNotNull(SecretSlots.parse(MAIN))

        assertEquals(SlotHeader.PIN_VERSION, v3.header.version)
        assertEquals(SlotHeader.KDF_PBKDF2, v3.header.kdfId)
        assertEquals(SlotHeader.FACTOR_OS_STORE_HMAC, v3.header.factorId)
        assertEquals(0L, v3.header.memoryKiB)
        assertEquals(SlotHeader.PASSWORD_VERSION, v4.header.version)
    }

    @Test
    fun aSlotOfAnyOtherVersionOrLengthIsNoSlot() {
        val refused = listOf(
            ByteArray(0),
            MAIN.copyOf(84),
            MAIN + 0,
            MAIN.copyOf(77),
            GoldenVectors.pinSlot.copyOf(85).also { it[0] = 3 },
            MAIN.copyOf().also { it[0] = 3 },
            MAIN.copyOf().also { it[0] = 5 },
            MAIN.copyOf().also { it[0] = 0 },
            GoldenVectors.pinSlot.copyOf().also { it[0] = 4 },
        )

        refused.forEachIndexed { index, slot -> assertNull(SecretSlots.parse(slot), "case $index") }
    }

    @Test
    fun theReaderCopiesOnlyFixedSizesWhateverTheHeaderSays() {
        // The costs of the slot are numbers in the header: the parts cut out are 16, 12 and 48 bytes regardless.
        val hostile = MAIN.copyOf().also {
            it[3] = -1
            it[4] = -1
            it[5] = -1
            it[6] = -1
            it[7] = -1
            it[8] = -1
        }

        val parsed = assertNotNull(SecretSlots.parse(hostile))

        assertEquals(16, parsed.header.salt.size)
        assertEquals(12, parsed.iv.size)
        assertEquals(48, parsed.ciphertext.size)
        assertEquals(0xFFFF_FFFFL, parsed.header.memoryKiB)
    }

    // --- Version 3 goes through the code it always went through ----------------------------------

    @Test
    fun version3SlotsAreReadAndWrittenByThePinCode() {
        val v3Header = SlotHeader(SlotHeader.PIN_VERSION, SlotHeader.KDF_PBKDF2, 1, 0L, 0, 0, salt)
        val parsed = assertNotNull(SecretSlots.parse(GoldenVectors.pinSlot))

        assertContentEquals(dek, SecretSlots.open(parsed, kek))
        assertTrue(SecretSlots.opensWipe(GoldenVectors.wipeSlot, wipeKek, v3Header))
        assertContentEquals(decoyDek, SecretSlots.openDuress(GoldenVectors.duressSlot, decoyKek, v3Header))
        val sealed = SecretSlots.seal(dek, kek, v3Header)
        assertEquals(77, sealed.size)
        assertContentEquals(dek, PinSlots.openSlotOrNull(assertNotNull(PinSlots.parseSlot(sealed)), kek))
        assertTrue(PinSlots.opensWipeSlot(SecretSlots.sealWipe(wipeKek, v3Header), wipeKek, salt))
        val duress = SecretSlots.sealDuress(decoyDek, decoyKek, v3Header)
        assertContentEquals(decoyDek, DuressSlots.openDecoy(duress, decoyKek, DuressSlots.decoySlotAad(salt)))
    }

    // --- Fillers --------------------------------------------------------------------------------

    @Test
    fun aFillerHasTheShapeOfARealSlotOfItsVersionAndNothingOpensIt() {
        val v3Header = SlotHeader(SlotHeader.PIN_VERSION, SlotHeader.KDF_PBKDF2, 1, 0L, 0, 0, salt)
        for (h in listOf(v3Header, header)) {
            val wipeFiller = SecretSlots.wipeFiller(h.version)
            val duressFiller = DuressSlots.randomSlot(h.version)

            assertEquals(WIPE.size, wipeFiller.size)
            assertEquals(DURESS.size, duressFiller.size)
            assertEquals(h.version, wipeFiller[0])
            assertEquals(h.version, duressFiller[0])
            assertFalse(SecretSlots.opensWipe(wipeFiller, wipeKek, h))
            assertNull(SecretSlots.openDuress(duressFiller, decoyKek, h))
            assertFalse(wipeFiller.contentEquals(SecretSlots.wipeFiller(h.version)))
        }
    }

    // --- SlotHeader -----------------------------------------------------------------------------

    @Test
    fun aHeaderComparesFormatsWithoutTheSaltAndCopiesItsSalt() {
        val other = SlotHeader(4, 2, 1, 65_536L, 3, 1, seq(16, 9, 1))
        val copy = header.copy()

        assertTrue(header.sameFormatAs(other))
        assertFalse(header.sameFormatAs(SlotHeader(4, 2, 1, 65_536L, 4, 1, salt)))
        assertFalse(header.sameFormatAs(SlotHeader(4, 2, 1, 65_537L, 3, 1, salt)))
        assertFalse(header.sameFormatAs(SlotHeader(4, 2, 1, 65_536L, 3, 2, salt)))
        assertFalse(header.sameFormatAs(SlotHeader(4, 1, 1, 65_536L, 3, 1, salt)))
        assertFalse(header.sameFormatAs(SlotHeader(4, 2, 2, 65_536L, 3, 1, salt)))
        assertFalse(header.sameFormatAs(SlotHeader(3, 2, 1, 65_536L, 3, 1, salt)))
        assertTrue(header.sameFormatAs(copy))
        assertContentEquals(salt, copy.salt)
        copy.salt.fill(0)
        assertContentEquals(seq(16, 0, 3), header.salt)
    }

    /** Opens what the code under test sealed with [Crypto] alone, the AAD written out by the test. */
    private object Reference {
        fun gcmOpen(key: ByteArray, iv: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray =
            checkNotNull(Crypto.aesGcmOpen(key, iv, aad, ciphertext))
    }

    private fun seq(size: Int, offset: Int, step: Int) = ByteArray(size) { (it * step + offset).toByte() }

    private companion object {
        const val HEADER_LENGTH = 25

        /** Main slot v4: m = 65536 KiB, t = 3, p = 1, salt 0,3,6..45, IV 0x20.., DEK 0,1,2.., KEK 100,101,... */
        val MAIN = GoldenVectors.hex(
            "040201000100000301000306090c0f1215181b1e2124272a2d202122232425262728292a2b58a9c03c53e5da37e57c6d" +
                "6bd7b79ab45c8f05ff14a64ca347f3ca7ad54a01259392f37fa8b53e8333b9130ee938d3bd",
        )

        /** Wipe slot v4 for [MAIN]: wipe KEK 150,151,..., marker 0x40,0x41,... */
        val WIPE = GoldenVectors.hex(
            "04202122232425262728292a2b27b18aa6b5910246745d4d3702abbff4370bbe364d253f08b6fdf78894aa997d679320" +
                "e0ba15d446e381eb239eb41857",
        )

        /** Duress slot v4 for [MAIN]: decoy KEK 200,201,..., decoy DEK 0x80,0x81,... */
        val DURESS = GoldenVectors.hex(
            "04202122232425262728292a2b84ee003088b65199cf851f23dbd85498733e690ab0a0ab22184d78fbcd9ab2ca9cc2a7" +
                "e32a62eb83dd265c2637a3a009",
        )
    }
}
