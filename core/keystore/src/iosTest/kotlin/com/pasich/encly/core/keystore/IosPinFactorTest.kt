package com.pasich.encly.core.keystore

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinKeySlot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The iOS factor's states over a [FakeEnclave], so they are tested without a Keychain or a Secure Enclave. What the
 * real key agreement does is in [PinFactorGoldenTest].
 */
class IosPinFactorTest {
    private val enclave = FakeEnclave()
    private val factor = IosPinFactor(enclave)

    private val salt = ByteArray(16) { (0xA0 + it).toByte() }
    private val pin = "482913".encodeToByteArray()
    private val data = salt + pin

    @Test
    fun macIsDeterministicAndDependsOnTheData() {
        factor.ensureKey(PinKeySlot.A)

        val first = factor.mac(PinKeySlot.A, data)

        assertEquals(32, first.size)
        assertContentEquals(first, factor.mac(PinKeySlot.A, data))
        assertFalse(first.contentEquals(factor.mac(PinKeySlot.A, salt + "482914".encodeToByteArray())))
        assertFalse(first.contentEquals(factor.mac(PinKeySlot.A, "482913".encodeToByteArray() + salt)))
    }

    @Test
    fun everyGuessIsAnAgreementOfTheEnclaveOnAPointOfItsOwn() {
        factor.ensureKey(PinKeySlot.A)

        factor.mac(PinKeySlot.A, data)
        factor.mac(PinKeySlot.A, data)
        factor.mac(PinKeySlot.A, salt + "000000".encodeToByteArray())

        assertEquals(3, enclave.peers.size)
        assertTrue(enclave.peers.all { it.size == 65 && it[0] == 4.toByte() })
        assertContentEquals(enclave.peers[0], enclave.peers[1])
        assertFalse(enclave.peers[0].contentEquals(enclave.peers[2]))
    }

    @Test
    fun theSameDataOnTheOtherSlotIsAnotherPointAndAnotherFactor() {
        factor.ensureKey(PinKeySlot.A)
        factor.ensureKey(PinKeySlot.B)

        val a = factor.mac(PinKeySlot.A, data)
        val b = factor.mac(PinKeySlot.B, data)

        assertFalse(a.contentEquals(b))
        assertFalse(enclave.peers[0].contentEquals(enclave.peers[1]))
    }

    @Test
    fun firstUseCreatesTheKeyAndReportsIt() {
        assertTrue(factor.ensureKey(PinKeySlot.A))
        assertTrue(enclave.has(PinKeySlot.A))
        assertFalse(factor.ensureKey(PinKeySlot.A))
        assertFalse(enclave.has(PinKeySlot.B))
        assertEquals(1, enclave.created)
    }

    @Test
    fun resetReplacesTheKey() {
        factor.ensureKey(PinKeySlot.A)
        val before = factor.mac(PinKeySlot.A, data)

        factor.reset(PinKeySlot.A)

        assertFalse(before.contentEquals(factor.mac(PinKeySlot.A, data)))
        assertEquals(2, enclave.created)
    }

    @Test
    fun resetOfAMissingSlotCreatesIt() {
        factor.reset(PinKeySlot.B)

        assertTrue(enclave.has(PinKeySlot.B))
        factor.mac(PinKeySlot.B, data)
    }

    @Test
    fun macWithoutAKeyIsLostAndAsksNoOneButTheEnclave() {
        assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
        assertEquals(1, enclave.peers.size)
    }

    @Test
    fun macAfterDeleteIsLostAndEnsureKeyMakesANewKey() {
        factor.ensureKey(PinKeySlot.A)
        val before = factor.mac(PinKeySlot.A, data)

        factor.delete(PinKeySlot.A)

        assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
        assertTrue(factor.ensureKey(PinKeySlot.A))
        assertNotEquals(before.toHex(), factor.mac(PinKeySlot.A, data).toHex())
    }

    @Test
    fun aKeyTheEnclaveCallsGoneIsLost() {
        factor.ensureKey(PinKeySlot.A)
        enclave.agreeFailure = SecretStoreException(lost = true, message = "gone")

        assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
    }

    @Test
    fun anEnclaveThatIsBusyOrLockedIsNotLost() {
        factor.ensureKey(PinKeySlot.A)
        val locked = SecretStoreException(lost = false, message = "locked")
        enclave.agreeFailure = locked

        val failure = assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }

        assertFalse(failure.lost)
        assertEquals(locked, failure.cause)
        enclave.agreeFailure = null
        factor.mac(PinKeySlot.A, data)
    }

    @Test
    fun anAgreementOfTheWrongSizeIsNotLostAndIsWiped() {
        factor.ensureKey(PinKeySlot.A)
        enclave.agreementSize = 31

        assertFalse(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)

        assertTrue(enclave.handedOut.all { copy -> copy.all { it == 0.toByte() } })
    }

    @Test
    fun anEnclaveThatRefusesToMakeAKeyFailsSetupAndNeverFallsBack() {
        enclave.createFailure = SecretStoreException(lost = false, message = "no Secure Enclave")

        assertFalse(assertFailsWith<PinFactorException> { factor.ensureKey(PinKeySlot.A) }.lost)
        assertFalse(assertFailsWith<PinFactorException> { factor.reset(PinKeySlot.A) }.lost)
        assertFalse(enclave.has(PinKeySlot.A))
    }

    @Test
    fun anUnreadableLookupIsNotLost() {
        enclave.existsFailure = SecretStoreException(lost = false, message = "locked")

        assertFalse(assertFailsWith<PinFactorException> { factor.ensureKey(PinKeySlot.A) }.lost)
    }

    @Test
    fun deleteNeverThrows() {
        enclave.deleteFailure = SecretStoreException(lost = false, message = "locked")

        factor.delete(PinKeySlot.A)
    }

    @Test
    fun macWipesThePointAndTheAgreementAndLeavesTheDataAlone() {
        factor.ensureKey(PinKeySlot.A)
        val input = data.copyOf()

        factor.mac(PinKeySlot.A, input)

        assertTrue(enclave.peerArrays.isNotEmpty() && enclave.peerArrays.all { it.all { b -> b == 0.toByte() } })
        assertTrue(enclave.handedOut.isNotEmpty() && enclave.handedOut.all { it.all { b -> b == 0.toByte() } })
        assertContentEquals(data, input)
    }

    @Test
    fun theFailurePathsWipeToo() {
        factor.ensureKey(PinKeySlot.A)
        enclave.agreeFailure = SecretStoreException(lost = false, message = "locked")

        assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }

        assertTrue(enclave.peerArrays.all { it.all { b -> b == 0.toByte() } })
    }

    @Test
    fun entriesAreNamedPerSlot() {
        assertEquals("pin-factor-v1", PinKeySlot.A.entryName())
        assertEquals("pin-factor-v1-b", PinKeySlot.B.entryName())
    }

    @Test
    fun createBuildsTheFactorWithoutTouchingTheKeychain() {
        IosPinFactor.create()
    }
}

/** A [PinEnclave] in memory: a key is a secret seed, and an agreement is a hash of the seed and the point. */
internal class FakeEnclave : PinEnclave {
    private val seeds = mutableMapOf<PinKeySlot, ByteArray>()

    /** Every point [agree] was asked about, as it was then (the factor wipes its own array afterwards). */
    val peers = mutableListOf<ByteArray>()

    /** The very arrays [agree] was handed, so that a test can check that they were wiped. */
    val peerArrays = mutableListOf<ByteArray>()

    /** Every agreement [agree] returned. */
    val handedOut = mutableListOf<ByteArray>()
    var created = 0
    var existsFailure: SecretStoreException? = null
    var createFailure: SecretStoreException? = null
    var deleteFailure: SecretStoreException? = null
    var agreeFailure: SecretStoreException? = null
    var agreementSize = AGREEMENT_BYTES

    fun has(slot: PinKeySlot): Boolean = slot in seeds

    override fun exists(slot: PinKeySlot): Boolean {
        existsFailure?.let { throw it }
        return slot in seeds
    }

    override fun create(slot: PinKeySlot) {
        createFailure?.let { throw it }
        seeds[slot] = Crypto.randomBytes(32)
        created++
    }

    override fun delete(slot: PinKeySlot) {
        deleteFailure?.let { throw it }
        seeds.remove(slot)
    }

    override fun agree(slot: PinKeySlot, peer: ByteArray): ByteArray? {
        peers += peer.copyOf()
        peerArrays += peer
        agreeFailure?.let { throw it }
        val seed = seeds[slot] ?: return null
        val agreement = Crypto.hkdfSha256(seed, "fake".encodeToByteArray(), peer, 32).copyOf(agreementSize)
        handedOut += agreement
        return agreement
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
