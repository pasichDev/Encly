package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinKeySlot
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three OS backends against the real secret store of the machine the tests run on. Each test runs only on
 * its own operating system and only when `ENCLY_KEYSTORE_IT=1` is set (the `keystore-it` CI job sets it; on a
 * development machine it uses the real login Keychain / keyring, under a test namespace that is not Encly's).
 */
class RealBackendTest {
    @Test
    fun windowsDPAPIRoundTrip() {
        assumeBackend("windows")
        val directory = Files.createTempDirectory("encly-dpapi-it")
        try {
            roundTrip(WindowsDpapiSecretStore(directory = directory))
            damagedBlobIsLost(WindowsDpapiSecretStore(directory = directory), directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun macOSKeychainRoundTrip() {
        assumeBackend("mac")
        roundTrip(MacKeychainSecretStore(service = TEST_NAMESPACE))
    }

    @Test
    fun linuxSecretServiceRoundTrip() {
        assumeBackend("linux")
        roundTrip(LinuxSecretServiceStore(application = TEST_NAMESPACE))
    }

    /** A damaged DPAPI blob must be "lost" (so that the lock screen offers the recovery phrase). */
    private fun damagedBlobIsLost(store: SecretStore, directory: Path) {
        val factor = DesktopPinFactor(store)
        try {
            assertTrue(factor.ensureKey(PinKeySlot.A))
            val file = directory.resolve(PinKeySlot.A.entryName() + ".dpapi")
            val bytes = Files.readAllBytes(file)
            bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x55).toByte()
            Files.write(file, bytes)

            assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, ByteArray(4)) }.lost)
            assertFalse(factor.ensureKey(PinKeySlot.A))
            factor.reset(PinKeySlot.A)
            factor.mac(PinKeySlot.A, ByteArray(4))
        } finally {
            runCatching { store.delete(PinKeySlot.A) }
        }
    }

    private fun assumeBackend(osPrefix: String) {
        assumeTrue("ENCLY_KEYSTORE_IT is not set", System.getenv("ENCLY_KEYSTORE_IT") == "1")
        assumeTrue(System.getProperty("os.name").lowercase().startsWith(osPrefix))
    }

    /** create, read, HMAC, replace, delete: the whole life of the secret, ending with nothing left behind. */
    private fun roundTrip(store: SecretStore) {
        val factor = DesktopPinFactor(store)
        val data = ByteArray(16) { it.toByte() } + "123456".toByteArray()
        try {
            store.delete(PinKeySlot.A)
            store.delete(PinKeySlot.B)

            assertTrue(factor.ensureKey(PinKeySlot.A))
            assertFalse(factor.ensureKey(PinKeySlot.A))

            val secret = store.read(PinKeySlot.A)!!
            assertTrue(secret.size == SecretStore.SECRET_SIZE)
            val expected = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(secret, "HmacSHA256"))
                doFinal(data)
            }
            assertContentEquals(expected, factor.mac(PinKeySlot.A, data))

            factor.reset(PinKeySlot.A)
            assertFalse(expected.contentEquals(factor.mac(PinKeySlot.A, data)))

            assertTrue(factor.ensureKey(PinKeySlot.B))
            factor.delete(PinKeySlot.B)
            assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.B, data) }.lost)

            factor.delete(PinKeySlot.A)
            assertTrue(assertFailsWith<PinFactorException> { factor.mac(PinKeySlot.A, data) }.lost)
        } finally {
            runCatching { store.delete(PinKeySlot.A) }
            runCatching { store.delete(PinKeySlot.B) }
        }
    }

    private companion object {
        const val TEST_NAMESPACE = "com.pasich.encly.test"
    }
}
