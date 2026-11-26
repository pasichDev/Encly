package com.pasich.encly.core.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The PIN policy is what [AuthenticationManager] did itself before the policy existed. */
class PinSecretPolicyTest {
    private val policy = PinSecretPolicy()
    private val salt = ByteArray(16) { (it * 3).toByte() }

    @Test
    fun itIsThePinOfSlotVersion3() {
        assertEquals(SecretKind.PIN, policy.kind)
        assertEquals(3, policy.slotVersion.toInt())
    }

    @Test
    fun theHkdfInfosAreTheVersion3Strings() {
        assertContentEquals("encly/pin/kek/v3".encodeToByteArray(), policy.kekInfo)
        assertContentEquals("encly/pin/wipe/v3".encodeToByteArray(), policy.wipeInfo)
        assertContentEquals("encly/pin/decoy/v3".encodeToByteArray(), policy.decoyInfo)
    }

    @Test
    fun onlySixToTwelveDigitsCanBeAPin() {
        assertNotNull(policy.canonical("123456".toCharArray()))
        assertNotNull(policy.canonical("123456789012".toCharArray()))
        assertNull(policy.canonical("12345".toCharArray()))
        assertNull(policy.canonical("1234567890123".toCharArray()))
        assertNull(policy.canonical("12345a".toCharArray()))
        assertNull(policy.canonical("12345٣".toCharArray()))
        assertNull(policy.canonical(CharArray(0)))
    }

    @Test
    fun theCanonicalFormIsACopyTheCallerOwns() {
        val typed = "123456".toCharArray()

        val canonical = assertNotNull(policy.canonical(typed))
        canonical.fill('0')

        assertNotSame(typed, canonical)
        assertContentEquals("123456".toCharArray(), typed)
    }

    @Test
    fun everyPinThatCanBeTypedMayBeSet() {
        assertEquals(SecretRule.OK, policy.mayBeSet("000000".toCharArray()))
    }

    @Test
    fun theDeviceFactorSignsTheSaltAndOneByteForEveryDigit() {
        val input = policy.factorInput("0123456789".toCharArray(), salt)

        assertContentEquals(salt + "0123456789".encodeToByteArray(), input)
    }

    @Test
    fun aNewHeaderIsAVersion3HeaderAroundTheSaltItWasGiven() {
        val header = policy.newHeader(salt)

        assertEquals(SlotHeader.PIN_VERSION, header.version)
        assertEquals(SlotHeader.KDF_PBKDF2, header.kdfId)
        assertEquals(0L, header.memoryKiB)
        assertTrue(header.salt === salt)
    }

    @Test
    fun itAcceptsVersion3SlotsOnly() {
        assertTrue(policy.accepts(policy.newHeader(salt)))
        assertFalse(policy.accepts(SlotHeader(SlotHeader.PASSWORD_VERSION, 2, 1, 65_536L, 3, 1, salt)))
        assertFalse(policy.accepts(SlotHeader(5, 1, 1, 0L, 0, 0, salt)))
    }
}
