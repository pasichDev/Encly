package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinKeySlot
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real Keychain of the iOS Simulator (an item under a service name of its own, removed afterwards). The
 * Simulator has no Secure Enclave, so these tests run the Keychain-only path of [KeychainSecretStore]; the
 * Enclave-wrapped path (`FORMAT_ENCLAVE`) is compiled but can only be exercised on a device, and is not.
 */
class KeychainSecretStoreTest {
    private val service = "com.pasich.encly.test." + NSUUID().UUIDString
    private val keychainOnly = KeychainSecretStore(service, enclave = null)
    private val defaultStore = KeychainSecretStore(service)

    /**
     * Whether the Keychain answers at all. A plain test executable has no application identifier, and the Simulator
     * then refuses every SecItem call with errSecNotAvailable (-25291); such a run cannot touch the Keychain, so the
     * tests that need one return early (the build log shows them as passed, which here means skipped) and
     * [withoutAKeychainEveryCallIsAStoreFailureThatIsNotLost] checks the refusal instead. Hosted in an app, they run.
     */
    private val keychainAvailable: Boolean = try {
        keychainOnly.read(PinKeySlot.A)
        true
    } catch (_: SecretStoreException) {
        false
    }

    @AfterTest
    fun cleanUp() {
        if (keychainAvailable) PinKeySlot.entries.forEach { keychainOnly.delete(it) }
    }

    @Test
    fun withoutAKeychainEveryCallIsAStoreFailureThatIsNotLost() {
        if (keychainAvailable) return

        assertFalse(assertFailsWith<SecretStoreException> { keychainOnly.read(PinKeySlot.A) }.lost)
        assertFalse(assertFailsWith<SecretStoreException> { keychainOnly.create(PinKeySlot.A) }.lost)
        assertFalse(assertFailsWith<SecretStoreException> { keychainOnly.delete(PinKeySlot.A) }.lost)
        val factor = IosPinFactor(keychainOnly)
        assertFalse(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, ByteArray(4)) }.lost)
    }

    @Test
    fun aMissingItemReadsAsNull() {
        if (!keychainAvailable) return

        assertNull(keychainOnly.read(PinKeySlot.A))
    }

    @Test
    fun createStoresASecretThatReadReturns() {
        if (!keychainAvailable) return

        val created = keychainOnly.create(PinKeySlot.A)

        assertEquals(SecretStore.SECRET_SIZE, created.size)
        assertContentEquals(created, keychainOnly.read(PinKeySlot.A))
        assertNull(keychainOnly.read(PinKeySlot.B))
    }

    @Test
    fun createReplacesAnEarlierSecretAndDeleteRemovesIt() {
        if (!keychainAvailable) return

        val first = keychainOnly.create(PinKeySlot.A)
        val second = keychainOnly.create(PinKeySlot.A)

        assertFalse(first.contentEquals(second))
        assertContentEquals(second, keychainOnly.read(PinKeySlot.A))

        keychainOnly.delete(PinKeySlot.A)
        assertNull(keychainOnly.read(PinKeySlot.A))
        keychainOnly.delete(PinKeySlot.A)
    }

    @Test
    fun theSlotsHoldSeparateSecrets() {
        if (!keychainAvailable) return

        val a = keychainOnly.create(PinKeySlot.A)
        val b = keychainOnly.create(PinKeySlot.B)

        assertFalse(a.contentEquals(b))
        assertContentEquals(a, keychainOnly.read(PinKeySlot.A))
        assertContentEquals(b, keychainOnly.read(PinKeySlot.B))
    }

    @Test
    fun theDefaultStoreFallsBackToTheKeychainWhereTheEnclaveIsMissing() {
        if (!keychainAvailable) return

        val created = defaultStore.create(PinKeySlot.A)

        val format = assertNotNull(defaultStore.recordFormat(PinKeySlot.A))
        val simulator = NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
        // The Simulator has no Secure Enclave, so the secret is in the Keychain as it is.
        if (simulator) assertEquals(KeychainSecretStore.FORMAT_KEYCHAIN, format)
        assertContentEquals(created, defaultStore.read(PinKeySlot.A))
    }

    @Test
    fun theFactorOnTheRealKeychainFollowsTheDesktopContract() {
        if (!keychainAvailable) return

        val factor = IosPinFactor(keychainOnly)
        val data = ByteArray(22) { it.toByte() }

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
