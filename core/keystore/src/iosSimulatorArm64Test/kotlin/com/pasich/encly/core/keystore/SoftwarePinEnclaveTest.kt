package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinKeySlot
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Simulator's software key in the real Keychain (under a service name of its own, removed afterwards). A plain
 * test executable has no application identifier and the Simulator then refuses every SecItem call
 * (errSecNotAvailable, -25291); those tests return early (the build log shows them as passed, which here means
 * skipped) and [withoutAKeychainEveryCallIsAStoreFailureThatIsNotLost] checks the refusal instead. Hosted in an
 * app, they run. The Secure Enclave key of a device cannot be tested here at all.
 */
class SoftwarePinEnclaveTest {
    private val service = "com.pasich.encly.test." + NSUUID().UUIDString
    private val enclave = SoftwarePinEnclave(service)
    private val factor = IosPinFactor(enclave)
    private val data = "482913".encodeToByteArray()

    private val keychainAvailable: Boolean = try {
        enclave.exists(PinKeySlot.A)
        true
    } catch (_: SecretStoreException) {
        false
    }

    @AfterTest
    fun cleanUp() {
        if (keychainAvailable) PinKeySlot.entries.forEach { enclave.delete(it) }
    }

    @Test
    fun withoutAKeychainEveryCallIsAStoreFailureThatIsNotLost() {
        if (keychainAvailable) return

        assertFalse(assertFailsWith<SecretStoreException> { enclave.exists(PinKeySlot.A) }.lost)
        assertFalse(assertFailsWith<PinFactorException> { factor.ensureKey(PinKeySlot.A) }.lost)
        assertFalse(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
    }

    @Test
    fun aMissingKeyHasNoAgreement() {
        if (!keychainAvailable) return

        assertFalse(enclave.exists(PinKeySlot.A))
        assertNull(enclave.agree(PinKeySlot.A, PinPoint.derive(PinKeySlot.A, data)))
    }

    @Test
    fun theFactorOnTheRealKeychainFollowsTheContract() {
        if (!keychainAvailable) return

        assertTrue(factor.ensureKey(PinKeySlot.A))
        assertFalse(factor.ensureKey(PinKeySlot.A))
        val before = factor.mac(PinKeySlot.A, data)
        assertContentEquals(before, factor.mac(PinKeySlot.A, data))

        factor.reset(PinKeySlot.A)
        assertFalse(before.contentEquals(factor.mac(PinKeySlot.A, data)))

        factor.delete(PinKeySlot.A)
        assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
        assertTrue(factor.ensureKey(PinKeySlot.A))
    }
}
