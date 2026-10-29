@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.core.crypto

import com.pasich.encly.core.crypto.swift.encly_aes_gcm_open
import com.pasich.encly.core.crypto.swift.encly_aes_gcm_seal
import com.pasich.encly.core.security.SensitiveDataCleaner
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.timingsafe_bcmp

/**
 * The iOS implementation behind [Crypto], the only file here that imports Apple's libraries. Every
 * primitive is the platform's own: CommonCrypto (SHA-256, HMAC, PBKDF2), `SecRandomCopyBytes` (the CSPRNG)
 * and CryptoKit's `AES.GCM` through the C functions of EnclyAesGcm.swift. HKDF is RFC 5869 over CommonCrypto's
 * HMAC, exactly as the JCA side builds it. Nothing is cached, and each intermediate array that holds key
 * material is zeroized before returning, as on the JVM.
 *
 * The one thing the Swift side cannot do is wipe CryptoKit's own copy of the key (`SymmetricKey` keeps it in
 * storage CryptoKit releases and zeroizes itself); the arrays this file owns are wiped by their callers.
 */
internal actual object PlatformCrypto {
    private const val TAG_BYTES = Crypto.TAG_BYTES
    private const val SINGLE_BLOCK_COUNTER: Byte = 0x01

    actual fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        val status = bytes.withPointer { SecRandomCopyBytes(kSecRandomDefault, size.convert(), it) }
        if (status != errSecSuccess) throw CryptoException(IllegalStateException("SecRandomCopyBytes: $status"))
        return bytes
    }

    actual fun sha256(data: ByteArray, startIndex: Int, endIndex: Int): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        data.withPointer(startIndex) { input ->
            digest.withPointer { CC_SHA256(input, (endIndex - startIndex).convert(), it) }
        }
        return digest
    }

    /** RFC 5869 for one output block: `T(1) = HMAC(PRK, info ‖ 0x01)`, PRK and T wiped. */
    actual fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmacSha256(salt, ikm)
        return try {
            val block = hmacSha256(prk, info + SINGLE_BLOCK_COUNTER)
            try {
                block.copyOf(length)
            } finally {
                SensitiveDataCleaner.clear(block)
            }
        } finally {
            SensitiveDataCleaner.clear(prk)
        }
    }

    /** [password] goes to the KDF as its UTF-8 bytes; they are wiped, and no String is made. */
    actual fun pbkdf2HmacSha256(password: CharArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val bytes = encodeUtf8(password)
        return try {
            pbkdf2HmacSha256(bytes, salt, iterations, length)
        } finally {
            SensitiveDataCleaner.clear(bytes)
        }
    }

    /**
     * [password] as UTF-8 text, handed to CommonCrypto as it is. For well-formed UTF-8 (all the app produces)
     * this is what the JVM computes; the JVM first decodes the bytes to characters, which turns malformed
     * input into U+FFFD, and that conversion is not repeated here.
     */
    actual fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val derived = ByteArray(length)
        // CommonCrypto rejects a null password pointer even with a length of 0; the JCA accepts an empty password.
        val status = password.withPointer(emptyAs = EMPTY_INPUT) { passwordPointer ->
            salt.withPointer(emptyAs = EMPTY_INPUT) { saltPointer ->
                derived.withPointer {
                    CCKeyDerivationPBKDF(
                        kCCPBKDF2,
                        passwordPointer?.reinterpret(),
                        password.size.convert(),
                        saltPointer,
                        salt.size.convert(),
                        kCCPRFHmacAlgSHA256,
                        iterations.convert(),
                        it,
                        length.convert(),
                    )
                }
            }
        }
        if (status != 0) {
            SensitiveDataCleaner.clear(derived)
            throw CryptoException(IllegalStateException("CCKeyDerivationPBKDF: $status"))
        }
        return derived
    }

    actual fun aesGcmSeal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val sealed = ByteArray(plaintext.size + TAG_BYTES)
        val status = key.withPointer { k ->
            nonce.withPointer { n ->
                aad.withPointer { a ->
                    plaintext.withPointer { p ->
                        sealed.withPointer {
                            encly_aes_gcm_seal(
                                k, key.size.convert(), n, nonce.size.convert(), a, aad.size.convert(),
                                p, plaintext.size.convert(), it,
                            )
                        }
                    }
                }
            }
        }
        if (status != 0) throw CryptoException(IllegalStateException("AES-GCM seal refused"))
        return sealed
    }

    /** Null when the data does not authenticate (or is shorter than a tag). */
    actual fun aesGcmOpen(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray? =
        if (ciphertext.size < TAG_BYTES) null else openSealed(key, nonce, aad, ciphertext)

    private fun openSealed(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray? {
        val plaintext = ByteArray(ciphertext.size - TAG_BYTES)
        val status = key.withPointer { k ->
            nonce.withPointer { n ->
                aad.withPointer { a ->
                    ciphertext.withPointer { c ->
                        plaintext.withPointer {
                            encly_aes_gcm_open(
                                k, key.size.convert(), n, nonce.size.convert(), a, aad.size.convert(),
                                c, ciphertext.size.convert(), it,
                            )
                        }
                    }
                }
            }
        }
        if (status == 0) return plaintext
        SensitiveDataCleaner.clear(plaintext)
        return null
    }

    actual fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        return a.withPointer { pa -> b.withPointer { pb -> timingsafe_bcmp(pa, pb, a.size.convert()) == 0 } }
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = ByteArray(CC_SHA256_DIGEST_LENGTH)
        key.withPointer { k ->
            data.withPointer { d ->
                mac.withPointer { CCHmac(kCCHmacAlgSHA256, k, key.size.convert(), d, data.size.convert(), it) }
            }
        }
        return mac
    }
}

/**
 * Runs [block] with a pointer to the array's bytes, pinned for the call. An empty array has no first element
 * to point at, so the callee gets null (or, for a callee that refuses null, the first byte of [emptyAs]) together
 * with a length of 0.
 */
private inline fun <R> ByteArray.withPointer(
    offset: Int = 0,
    emptyAs: ByteArray? = null,
    block: (CPointer<UByteVar>?) -> R,
): R = when {
    offset < size -> usePinned { block(it.addressOf(offset).reinterpret()) }
    emptyAs != null -> emptyAs.usePinned { block(it.addressOf(0).reinterpret()) }
    else -> block(null)
}

private val EMPTY_INPUT = ByteArray(1)
