package com.pasich.encly.core.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The slot byte formats: what is accepted, what is refused, and that nothing is opened by the wrong key. */
class PinSlotsTest {
    private val dek = ByteArray(32) { it.toByte() }
    private val kek = ByteArray(32) { (it + 100).toByte() }
    private val otherKek = ByteArray(32) { (it + 7).toByte() }
    private val salt = ByteArray(16) { (it * 3).toByte() }

    // --- PIN slot -----------------------------------------------------------------------

    @Test
    fun aSealedSlotParsesAndOpensWithItsKekOnly() {
        val slot = PinSlots.sealSlot(dek, kek, salt)

        val parsed = PinSlots.parseSlot(slot)!!

        assertContentEquals(salt, parsed.salt)
        assertContentEquals(dek, PinSlots.openSlotOrNull(parsed, kek))
        assertNull(PinSlots.openSlotOrNull(parsed, otherKek))
    }

    @Test
    fun aSlotOfTheWrongLengthDoesNotParse() {
        val slot = PinSlots.sealSlot(dek, kek, salt)

        assertNull(PinSlots.parseSlot(slot.copyOf(slot.size - 1)))
        assertNull(PinSlots.parseSlot(slot + 0))
        assertNull(PinSlots.parseSlot(ByteArray(0)))
    }

    @Test
    fun aSlotOfAnotherVersionDoesNotParse() {
        val slot = PinSlots.sealSlot(dek, kek, salt).also { it[0] = (PinSlots.SLOT_VERSION + 1).toByte() }

        assertNull(PinSlots.parseSlot(slot))
    }

    @Test
    fun aSlotMovedToAnotherSaltIsNotOpened() {
        val slot = PinSlots.sealSlot(dek, kek, salt)
        // The salt is authenticated: swapping it for another one must break the tag.
        slot[1] = (slot[1] + 1).toByte()

        assertNull(PinSlots.openSlotOrNull(PinSlots.parseSlot(slot)!!, kek))
    }

    @Test
    fun aFlippedCiphertextBitIsNotOpened() {
        val slot = PinSlots.sealSlot(dek, kek, salt)
        slot[slot.size - 1] = (slot.last().toInt() xor 1).toByte()

        assertNull(PinSlots.openSlotOrNull(PinSlots.parseSlot(slot)!!, kek))
    }

    // --- wipe slot ------------------------------------------------------------------------

    @Test
    fun aWipeSlotOpensOnlyWithItsKekAndSalt() {
        val slot = PinSlots.sealWipeSlot(kek, salt)

        assertTrue(PinSlots.opensWipeSlot(slot, kek, salt))
        assertFalse(PinSlots.opensWipeSlot(slot, otherKek, salt))
        assertFalse(PinSlots.opensWipeSlot(slot, kek, ByteArray(16)))
    }

    @Test
    fun aDecoyWipeSlotLooksLikeARealOneButNothingOpensIt() {
        val real = PinSlots.sealWipeSlot(kek, salt)
        val decoy = PinSlots.decoyWipeSlot()

        assertEquals(real.size, decoy.size)
        assertEquals(real[0], decoy[0])
        assertFalse(PinSlots.opensWipeSlot(decoy, kek, salt))
    }

    @Test
    fun aMalformedWipeSlotIsNeverOpened() {
        val slot = PinSlots.sealWipeSlot(kek, salt)

        assertFalse(PinSlots.opensWipeSlot(slot.copyOf(slot.size - 1), kek, salt))
        assertFalse(PinSlots.opensWipeSlot(slot.copyOf().also { it[0] = 99 }, kek, salt))
        assertFalse(PinSlots.opensWipeSlot(ByteArray(0), kek, salt))
    }

    @Test
    fun theWipeAndPinSlotsAreBoundToDifferentContexts() {
        assertFalse(PinSlots.aad(salt).contentEquals(PinSlots.wipeAad(salt)))
    }

    // --- duress slot ----------------------------------------------------------------------

    @Test
    fun aDecoySlotOpensWithItsKekAndAadOnly() {
        val aad = DuressSlots.decoySlotAad(salt)
        val sealed = DuressSlots.sealDecoy(dek, kek, aad)

        assertContentEquals(dek, DuressSlots.openDecoy(sealed, kek, aad))
        assertNull(DuressSlots.openDecoy(sealed, otherKek, aad))
        assertNull(DuressSlots.openDecoy(sealed, kek, DuressSlots.decoySlotAad(ByteArray(16))))
    }

    @Test
    fun aMalformedDecoySlotIsNeverOpened() {
        val aad = DuressSlots.decoySlotAad(salt)
        val sealed = DuressSlots.sealDecoy(dek, kek, aad)

        assertNull(DuressSlots.openDecoy(sealed.copyOf(sealed.size - 1), kek, aad))
        assertNull(DuressSlots.openDecoy(sealed.copyOf().also { it[0] = 99 }, kek, aad))
    }

    @Test
    fun theDecoyKeyKekDependsOnTheVaultKeyAndIsNotTheKeyItself() {
        val first = DuressSlots.decoyKeyKek(dek)

        assertEquals(32, first.size)
        assertFalse(first.contentEquals(dek))
        assertContentEquals(first, DuressSlots.decoyKeyKek(dek.copyOf()))
        assertFalse(first.contentEquals(DuressSlots.decoyKeyKek(otherKek)))
    }

    // --- HKDF -----------------------------------------------------------------------------

    @Test
    fun hkdfMatchesRfc5869TestCase1ForTheFirst32Bytes() {
        val ikm = ByteArray(22) { 0x0b }
        val hkdfSalt = (0..12).map { it.toByte() }.toByteArray()
        val info = (0xf0..0xf9).map { it.toByte() }.toByteArray()

        val okm = Hkdf.sha256(ikm, hkdfSalt, info, 32)

        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf",
            okm.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') },
        )
    }

    @Test
    fun hkdfRefusesALengthOutsideOneHashBlockAndAnEmptySalt() {
        val ikm = ByteArray(32)

        assertFailsWith<IllegalArgumentException> { Hkdf.sha256(ikm, salt, ByteArray(0), 0) }
        assertFailsWith<IllegalArgumentException> { Hkdf.sha256(ikm, salt, ByteArray(0), 33) }
        assertFailsWith<IllegalArgumentException> { Hkdf.sha256(ikm, ByteArray(0), ByteArray(0), 16) }
    }

    @Test
    fun differentInfoGivesIndependentKeys() {
        val a = Hkdf.sha256(dek, salt, "a".encodeToByteArray(), 32)
        val b = Hkdf.sha256(dek, salt, "b".encodeToByteArray(), 32)

        assertFalse(a.contentEquals(b))
        assertEquals(16, Hkdf.sha256(dek, salt, "a".encodeToByteArray(), 16).size)
    }
}
