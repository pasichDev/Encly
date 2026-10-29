package com.pasich.encly.core.crypto

import com.pasich.encly.core.security.SensitiveDataCleaner
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The platform (JCA) implementation behind [Crypto], and the only file here that imports
 * `javax.crypto` / `java.security`. Every call builds a fresh `Cipher`, `Mac` or key factory and
 * drops it: nothing is cached or pooled, so no key outlives the call inside this code. Each
 * intermediate array that holds key material is zeroized before returning.
 */
internal object JcaCrypto {
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val HMAC = "HmacSHA256"
    private const val PBKDF2 = "PBKDF2WithHmacSHA256"

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun sha256(data: ByteArray, startIndex: Int, endIndex: Int): ByteArray =
        MessageDigest.getInstance("SHA-256").apply { update(data, startIndex, endIndex - startIndex) }.digest()

    /** RFC 5869 for one output block: `T(1) = HMAC(PRK, info ‖ 0x01)`, PRK and T wiped. */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(salt, HMAC))
        val prk = mac.doFinal(ikm)
        return try {
            mac.init(SecretKeySpec(prk, HMAC))
            mac.update(info)
            mac.update(0x01.toByte())
            val block = mac.doFinal()
            try {
                block.copyOf(length)
            } finally {
                SensitiveDataCleaner.clear(block)
            }
        } finally {
            SensitiveDataCleaner.clear(prk)
        }
    }

    /** [password] goes to the platform KDF as characters; the spec's copy is cleared, no String is made. */
    fun pbkdf2HmacSha256(password: CharArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, length * Byte.SIZE_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** [password] as UTF-8 text; the characters made from it are wiped. */
    fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val buffer = Charsets.UTF_8.decode(ByteBuffer.wrap(password))
        val chars = CharArray(buffer.remaining())
        buffer.get(chars)
        if (buffer.hasArray()) SensitiveDataCleaner.clear(buffer.array())
        return try {
            pbkdf2HmacSha256(chars, salt, iterations, length)
        } finally {
            SensitiveDataCleaner.clear(chars)
        }
    }

    fun aesGcmSeal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        cipher.doFinal(plaintext)
    } catch (e: GeneralSecurityException) {
        throw CryptoException(e)
    }

    /** Null when the data does not authenticate (an `AEADBadTagException`, or any other JCA refusal). */
    fun aesGcmOpen(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray? = try {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        cipher.doFinal(ciphertext)
    } catch (_: GeneralSecurityException) {
        null
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    private const val TAG_BITS = Crypto.TAG_BYTES * Byte.SIZE_BITS
}
