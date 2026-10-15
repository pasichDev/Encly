package com.pasich.encly.testutil

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGeneratorSpi
import javax.crypto.Mac
import javax.crypto.MacSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * A software stand-in for the "AndroidKeyStore" provider, which the JVM does not have. It holds
 * HMAC-SHA256 keys in memory and lets a test make the Keystore misbehave the ways the real one
 * does: reject StrongBox, fail to generate, forget or invalidate a key, throw from any call.
 * [install] before the test and [uninstall] after it.
 */
internal object FakeAndroidKeyStore {
    private const val NAME = "AndroidKeyStore"
    private const val HMAC = "HmacSHA256"

    private val keys = mutableMapOf<String, Key>()

    /** Every spec a key was generated with, in order. */
    val specs = mutableListOf<KeyGenParameterSpec>()

    /** Made to fail: called with the spec about to be used; throw from it to fail that attempt. */
    var onGenerate: (KeyGenParameterSpec) -> Unit = {}

    /** Throws from `containsAlias` and `deleteEntry`. */
    var aliasFailure: Throwable? = null

    /** Throws from `getKey`. */
    var getKeyFailure: Throwable? = null

    fun install() {
        reset()
        Security.removeProvider(NAME)
        Security.insertProviderAt(FakeProvider(), 1)
    }

    fun uninstall() {
        Security.removeProvider(NAME)
        reset()
    }

    private fun reset() {
        keys.clear()
        specs.clear()
        onGenerate = {}
        aliasFailure = null
        getKeyFailure = null
    }

    fun has(alias: String): Boolean = alias in keys

    fun aliases(): Set<String> = keys.keys.toSet()

    /** Replaces the key under [alias] with one that refuses to be used, as after a new fingerprint. */
    fun invalidate(alias: String) {
        keys[alias] = InvalidatedKey
    }

    /** A key the hardware has given up on: using it throws [KeyPermanentlyInvalidatedException]. */
    private object InvalidatedKey : SecretKey {
        override fun getAlgorithm(): String = HMAC
        override fun getFormat(): String? = null
        override fun getEncoded(): ByteArray? = null
    }

    private class FakeProvider : Provider(NAME, 1.0, "in-memory AndroidKeyStore for JVM tests") {
        init {
            putService(object : Service(this, "KeyStore", NAME, FakeKeyStoreSpi::class.java.name, null, null) {
                override fun newInstance(constructorParameter: Any?): Any = FakeKeyStoreSpi()
            })
            putService(object : Service(this, "KeyGenerator", HMAC, FakeKeyGenerator::class.java.name, null, null) {
                override fun newInstance(constructorParameter: Any?): Any = FakeKeyGenerator()
            })
            putService(object : Service(this, "Mac", HMAC, FakeMac::class.java.name, null, null) {
                override fun newInstance(constructorParameter: Any?): Any = FakeMac()
            })
        }
    }

    private class FakeKeyGenerator : KeyGeneratorSpi() {
        private var spec: KeyGenParameterSpec? = null

        override fun engineInit(random: SecureRandom?) = throw UnsupportedOperationException()

        override fun engineInit(keysize: Int, random: SecureRandom?) = throw UnsupportedOperationException()

        override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
            spec = params as KeyGenParameterSpec
        }

        override fun engineGenerateKey(): SecretKey {
            val current = checkNotNull(spec)
            specs += current
            onGenerate(current)
            val key = SecretKeySpec(ByteArray(current.keySize / 8).also { SecureRandom().nextBytes(it) }, HMAC)
            keys[current.keystoreAlias] = key
            return key
        }
    }

    /** HMAC that refuses an invalidated key the way the Keystore does, and is plain HMAC otherwise. */
    private class FakeMac : MacSpi() {
        private val real = Mac.getInstance(HMAC, "SunJCE")

        override fun engineGetMacLength(): Int = real.macLength

        override fun engineInit(key: Key, params: AlgorithmParameterSpec?) {
            if (key === InvalidatedKey) throw KeyPermanentlyInvalidatedException()
            real.init(key)
        }

        override fun engineUpdate(input: Byte) = real.update(input)

        override fun engineUpdate(input: ByteArray, offset: Int, len: Int) = real.update(input, offset, len)

        override fun engineDoFinal(): ByteArray = real.doFinal()

        override fun engineReset() = real.reset()
    }

    private class FakeKeyStoreSpi : KeyStoreSpi() {
        override fun engineGetKey(alias: String, password: CharArray?): Key? {
            getKeyFailure?.let { throw it }
            return keys[alias]
        }

        override fun engineContainsAlias(alias: String): Boolean {
            aliasFailure?.let { throw it }
            return alias in keys
        }

        override fun engineDeleteEntry(alias: String) {
            aliasFailure?.let { throw it }
            keys.remove(alias)
        }

        override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys.toList())

        override fun engineSize(): Int = keys.size

        override fun engineIsKeyEntry(alias: String): Boolean = alias in keys

        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit

        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null

        override fun engineGetCertificate(alias: String): Certificate? = null

        override fun engineGetCreationDate(alias: String): Date? = null

        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) {
            keys[alias] = key
        }

        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) =
            throw UnsupportedOperationException()

        override fun engineSetCertificateEntry(alias: String, cert: Certificate) = throw UnsupportedOperationException()

        override fun engineIsCertificateEntry(alias: String): Boolean = false

        override fun engineGetCertificateAlias(cert: Certificate): String? = null

        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
    }
}
