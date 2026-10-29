package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinKeySlot
import org.junit.Assume.assumeTrue
import java.nio.file.Path
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPinFactorTest {
    private val store = InMemorySecretStore()
    private val factor = DesktopPinFactor(store)

    private val salt = ByteArray(16) { (0xA0 + it).toByte() }
    private val pin = "482913".toByteArray(Charsets.UTF_8)
    private val data = salt + pin

    @Test
    fun macIsHMACSHA256OfTheDataUnderTheStoredSecretGolden() {
        store.put(PinKeySlot.A, ByteArray(32) { it.toByte() })

        val hw = factor.mac(PinKeySlot.A, data)

        assertEquals(32, hw.size)
        assertEquals("25b11968d03777c4e2519917fd8757ebaed13eeb6a9378e58e9fab63c4962f36", hw.toHex())
        assertContentEquals(referenceHmac(ByteArray(32) { it.toByte() }, data), hw)
    }

    @Test
    fun aSecondGoldenVectorWithAnotherSecret() {
        store.put(PinKeySlot.B, ByteArray(32) { 0xFF.toByte() })

        assertEquals(
            "412acd40567e42ba035be3fd49c094c6cc015c5a9921e633e52bf0235e0f0755",
            factor.mac(PinKeySlot.B, data).toHex(),
        )
    }

    @Test
    fun macIsDeterministicAndDependsOnTheData() {
        factor.ensureKey(PinKeySlot.A)

        val first = factor.mac(PinKeySlot.A, data)
        assertContentEquals(first, factor.mac(PinKeySlot.A, data))
        assertFalse(first.contentEquals(factor.mac(PinKeySlot.A, salt + "482914".toByteArray())))
    }

    @Test
    fun macOfTheRealRandomSecretMatchesAPlainJavaxCryptoHMAC() {
        factor.ensureKey(PinKeySlot.A)

        assertContentEquals(referenceHmac(store.peek(PinKeySlot.A)!!, data), factor.mac(PinKeySlot.A, data))
    }

    @Test
    fun firstUseCreatesA32ByteSecretAndReportsIt() {
        assertTrue(factor.ensureKey(PinKeySlot.A))
        assertEquals(32, store.peek(PinKeySlot.A)!!.size)
        assertFalse(factor.ensureKey(PinKeySlot.A))
        assertNull(store.peek(PinKeySlot.B))
    }

    @Test
    fun theSlotsHoldSeparateSecrets() {
        factor.ensureKey(PinKeySlot.A)
        factor.ensureKey(PinKeySlot.B)

        assertFalse(store.peek(PinKeySlot.A)!!.contentEquals(store.peek(PinKeySlot.B)!!))
        assertFalse(factor.mac(PinKeySlot.A, data).contentEquals(factor.mac(PinKeySlot.B, data)))
    }

    @Test
    fun resetReplacesTheSecret() {
        factor.ensureKey(PinKeySlot.A)
        val before = factor.mac(PinKeySlot.A, data)

        factor.reset(PinKeySlot.A)

        assertFalse(before.contentEquals(factor.mac(PinKeySlot.A, data)))
    }

    @Test
    fun macWithoutASecretIsLost() {
        val e = assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }
        assertTrue(e.lost)
    }

    @Test
    fun macAfterDeleteIsLostAndEnsureKeyMakesANewSecret() {
        factor.ensureKey(PinKeySlot.A)
        val before = factor.mac(PinKeySlot.A, data)

        factor.delete(PinKeySlot.A)

        assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
        assertTrue(factor.ensureKey(PinKeySlot.A))
        assertNotEquals(before.toHex(), factor.mac(PinKeySlot.A, data).toHex())
    }

    @Test
    fun aSecretOfTheWrongSizeIsLostForMacAndKeptByEnsureKey() {
        store.put(PinKeySlot.A, ByteArray(7))

        assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
        assertFalse(factor.ensureKey(PinKeySlot.A))
        assertEquals(7, store.peek(PinKeySlot.A)!!.size)

        factor.reset(PinKeySlot.A)
        assertEquals(32, store.peek(PinKeySlot.A)!!.size)
        factor.mac(PinKeySlot.A, data)
    }

    @Test
    fun anUnreadableSecretIsLostForMacKeptByEnsureKeyAndReplacedByReset() {
        factor.ensureKey(PinKeySlot.A)
        val before = store.peek(PinKeySlot.A)!!.copyOf()
        store.readFailure = SecretStoreException(lost = true, message = "does not decrypt")

        assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
        assertFalse(factor.ensureKey(PinKeySlot.A))
        assertContentEquals(before, store.peek(PinKeySlot.A))

        factor.reset(PinKeySlot.A)
        store.readFailure = null
        assertFalse(before.contentEquals(store.peek(PinKeySlot.A)))
        factor.mac(PinKeySlot.A, data)
    }

    @Test
    fun anUnavailableStoreIsNotLost() {
        factor.ensureKey(PinKeySlot.A)
        val unavailable = SecretStoreException(lost = false, message = "locked")
        store.readFailure = unavailable

        assertFalse(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
        assertFalse(assertFailsWith<PinFactorException> { factor.ensureKey(PinKeySlot.A) }.lost)
        assertEquals(unavailable, assertFailsWith<PinFactorException> { factor.ensureKey(PinKeySlot.A) }.cause)
    }

    @Test
    fun resetFailureIsNotLost() {
        store.createFailure = SecretStoreException(lost = false, message = "full")

        assertFalse(assertFailsWith<PinFactorException> { factor.reset(PinKeySlot.A) }.lost)
        assertFalse(assertFailsWith<PinFactorException> { factor.ensureKey(PinKeySlot.A) }.lost)
    }

    @Test
    fun deleteNeverThrows() {
        store.deleteFailure = SecretStoreException(lost = false, message = "locked")

        factor.delete(PinKeySlot.A)
    }

    @Test
    fun macWipesItsCopyOfTheSecretAndLeavesTheDataAlone() {
        store.put(PinKeySlot.A, ByteArray(32) { it.toByte() })
        val input = data.copyOf()

        factor.mac(PinKeySlot.A, input)

        assertTrue(store.handedOut.all { copy -> copy.all { it == 0.toByte() } })
        assertContentEquals(data, input)
    }

    @Test
    fun ensureKeyAndResetWipeTheSecretsTheyHandled() {
        factor.ensureKey(PinKeySlot.A)
        factor.ensureKey(PinKeySlot.A)
        factor.reset(PinKeySlot.A)

        assertTrue(store.handedOut.isNotEmpty())
        assertTrue(store.handedOut.all { copy -> copy.all { it == 0.toByte() } })
    }

    @Test
    fun macWipesTheSecretWhenTheSizeIsWrong() {
        store.put(PinKeySlot.A, ByteArray(7) { 1 })

        assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }

        assertTrue(store.handedOut.all { copy -> copy.all { it == 0.toByte() } })
    }

    @Test
    fun selectionPicksTheBackendByOsName() {
        val windows = InMemorySecretStore()
        val macos = InMemorySecretStore()
        val linux = InMemorySecretStore()
        fun pick(os: String) = selectSecretStore(os, { windows }, { macos }, { linux })

        assertEquals(windows, pick("Windows 11"))
        assertEquals(macos, pick("Mac OS X"))
        assertEquals(linux, pick("Linux"))
        assertFailsWith<SecretStoreUnavailableException> { pick("FreeBSD") }
        assertFailsWith<SecretStoreUnavailableException> { pick("") }
    }

    @Test
    fun createBuildsTheFactorOfThisOsWithoutTouchingTheStore() {
        // Constructing a backend loads no native library and reads nothing; only the first operation does.
        val os = System.getProperty("os.name").lowercase()
        assumeTrue(listOf("windows", "mac", "linux").any { os.startsWith(it) })

        DesktopPinFactor.create(Path.of("build", "encly-test-data"))
    }

    @Test
    fun entriesAreNamedPerSlot() {
        assertEquals("pin-factor-v1", PinKeySlot.A.entryName())
        assertEquals("pin-factor-v1-b", PinKeySlot.B.entryName())
    }

    private fun referenceHmac(key: ByteArray, message: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(message)
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
