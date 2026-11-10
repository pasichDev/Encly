package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.SensitiveDataCleaner
import org.freedesktop.dbus.types.Variant
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.DHParameterSpec
import javax.crypto.spec.DHPublicKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// The encrypted transfer algorithm of the freedesktop Secret Service, `dh-ietf1024-sha256-aes128-cbc-pkcs7`
// (https://specifications.freedesktop.org/secret-service-spec/latest/transfer-secrets.html): a Diffie-Hellman
// exchange in the 1024-bit Oakley group 2 (RFC 2409, generator 2), the shared secret as 128 big-endian bytes, a
// 16-byte AES key from HKDF-SHA256 over it (no salt, no info), and AES-128-CBC with PKCS#7 padding for the secret.
// Only the JDK's DH, HMAC and AES-CBC are used; the code here is the composition the specification describes.

/** The name of the algorithm, as `OpenSession` takes it. */
internal const val DH_ALGORITHM = "dh-ietf1024-sha256-aes128-cbc-pkcs7"

private const val PUBLIC_KEY_SIZE = 128
private const val AES_KEY_SIZE = 16
private const val IV_SIZE = 16
private const val SHA256_SIZE = 32

// RFC 2409 section 6.2, "Second Oakley Group": 2^1024 - 2^960 - 1 + 2^64 * { [2^894 pi] + 129093 }.
private const val HEX = 16

private val OAKLEY_GROUP_2_PRIME = BigInteger(
    "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
        "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
        "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381FFFFFFFFFFFFFFFF",
    HEX,
)
private val OAKLEY_GROUP_2_GENERATOR = BigInteger.TWO

/** Our half of the key exchange of one Secret Service session. */
internal class SecretServiceDh private constructor(private val keyPair: KeyPair) {
    /** Our public key: 128 bytes, unsigned big-endian (what `OpenSession` sends). */
    val publicKey: ByteArray = unsigned(
        (keyPair.public as javax.crypto.interfaces.DHPublicKey).y,
        PUBLIC_KEY_SIZE,
    )

    /**
     * The session cipher, once the service's public key [serverPublic] (128 bytes, unsigned big-endian) is known.
     * Throws [GeneralSecurityException] for a key that is not a valid element of the group.
     */
    fun cipher(serverPublic: ByteArray): SecretServiceCipher {
        val y = BigInteger(1, serverPublic)
        // 1 < y < p - 1: anything else gives a shared secret an observer could guess.
        if (serverPublic.isEmpty() || y <= BigInteger.ONE || y >= OAKLEY_GROUP_2_PRIME - BigInteger.ONE) {
            throw GeneralSecurityException("The Secret Service sent an invalid public key")
        }
        val peer = KeyFactory.getInstance("DH")
            .generatePublic(DHPublicKeySpec(y, OAKLEY_GROUP_2_PRIME, OAKLEY_GROUP_2_GENERATOR))
        val agreement = KeyAgreement.getInstance("DH").apply {
            init(keyPair.private)
            doPhase(peer, true)
        }
        val raw = agreement.generateSecret()
        val shared = unsigned(BigInteger(1, raw), PUBLIC_KEY_SIZE)
        SensitiveDataCleaner.clear(raw)
        val key = hkdfSha256NoSalt(shared, AES_KEY_SIZE)
        SensitiveDataCleaner.clear(shared)
        return SecretServiceCipher(key)
    }

    companion object {
        fun create(random: SecureRandom = SecureRandom()): SecretServiceDh {
            val generator = KeyPairGenerator.getInstance("DH")
            generator.initialize(DHParameterSpec(OAKLEY_GROUP_2_PRIME, OAKLEY_GROUP_2_GENERATOR), random)
            return SecretServiceDh(generator.generateKeyPair())
        }
    }
}

/** AES-128-CBC with PKCS#7 padding under the session key; [key] is wiped by [close]. */
internal class SecretServiceCipher(private val key: ByteArray) : AutoCloseable {
    /** What goes on the wire: the random [iv] (the struct's parameters) and the [encrypted] secret (its value). */
    class Sealed(val iv: ByteArray, val encrypted: ByteArray)

    fun encrypt(secret: ByteArray, random: SecureRandom = SecureRandom()): Sealed {
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return Sealed(iv, cipher.doFinal(secret))
    }

    /** The secret, or a [GeneralSecurityException] for a wrong IV length or damaged padding. */
    fun decrypt(iv: ByteArray, encrypted: ByteArray): ByteArray {
        if (iv.size != IV_SIZE) throw GeneralSecurityException("The Secret Service sent an invalid IV")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(encrypted)
    }

    override fun close() = SensitiveDataCleaner.clear(key)
}

/**
 * The service's public key from the output of `OpenSession` (`ay`, however the D-Bus library typed it); throws
 * [GeneralSecurityException] when the service did not give one.
 */
internal fun serverPublicKeyOf(output: Variant<*>): ByteArray {
    val value = output.value
    val bytes = when (value) {
        is ByteArray -> value

        is Array<*> -> value.filterIsInstance<Byte>().toByteArray().takeIf { it.size == value.size }

        // dbus-java hands an `ay` inside a variant over as a list of boxed bytes.
        is List<*> -> value.filterIsInstance<Byte>().toByteArray().takeIf { it.size == value.size }

        else -> null
    }
    return bytes ?: throw GeneralSecurityException(
        "The Secret Service did not answer the key exchange (${output.sig}: ${value?.javaClass?.name})",
    )
}

/**
 * HKDF (RFC 5869) with SHA-256 for one block of output, without salt (the RFC then uses [SHA256_SIZE] zero bytes)
 * and without info, as the Secret Service specification asks.
 */
internal fun hkdfSha256NoSalt(secret: ByteArray, length: Int): ByteArray {
    require(length in 1..SHA256_SIZE)
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(ByteArray(SHA256_SIZE), "HmacSHA256"))
    val pseudoRandomKey = mac.doFinal(secret)
    mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
    val block = mac.doFinal(byteArrayOf(1))
    SensitiveDataCleaner.clear(pseudoRandomKey)
    return block.copyOf(length).also { SensitiveDataCleaner.clear(block) }
}

/** [value] as [size] unsigned big-endian bytes: no sign byte, zero-padded on the left. */
internal fun unsigned(value: BigInteger, size: Int): ByteArray {
    val bytes = value.toByteArray()
    val significant = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    require(significant.size <= size) { "The number does not fit in $size bytes" }
    return ByteArray(size).also { significant.copyInto(it, size - significant.size) }
}
