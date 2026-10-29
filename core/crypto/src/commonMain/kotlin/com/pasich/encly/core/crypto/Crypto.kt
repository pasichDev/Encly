package com.pasich.encly.core.crypto

/**
 * The one door to cryptographic primitives: everything the app needs, nothing else. Callers
 * have no `javax.crypto` / `java.security` in them; the platform implementation is `PlatformCrypto`
 * (JCA on the JVM and Android), the only place to replace when another platform needs another one.
 *
 * Fixed here, for every caller: AES-256-GCM with a 12-byte nonce and a 16-byte tag, HKDF and
 * PBKDF2 over HMAC-SHA-256, SHA-256. Randomness is only ever the platform CSPRNG.
 *
 * Keys and passwords are handed in as arrays and never retained. The arrays that cross this API
 * in either direction stay the caller's to wipe.
 */
public object Crypto {
    public const val KEY_BYTES: Int = 32
    public const val NONCE_BYTES: Int = 12
    public const val TAG_BYTES: Int = 16
    public const val SHA256_BYTES: Int = 32

    /** [size] bytes from the platform CSPRNG. */
    public fun randomBytes(size: Int): ByteArray = PlatformCrypto.randomBytes(size)

    /** A uniformly random `Long` from the platform CSPRNG. */
    public fun randomLong(): Long {
        var value = 0L
        for (byte in randomBytes(Long.SIZE_BYTES)) value = (value shl Byte.SIZE_BITS) or (byte.toLong() and BYTE_MASK)
        return value
    }

    /** SHA-256 of `data[startIndex until endIndex]`, without copying the range. */
    public fun sha256(data: ByteArray, startIndex: Int = 0, endIndex: Int = data.size): ByteArray =
        PlatformCrypto.sha256(data, startIndex, endIndex)

    /**
     * HKDF-SHA-256 (RFC 5869) of [ikm]. [salt] must not be empty (RFC 5869 would substitute zeros;
     * every key in the hierarchy has a fixed, non-empty one) and [length] is at most one hash block.
     */
    public fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(salt.isNotEmpty())
        require(length in 1..SHA256_BYTES)
        return PlatformCrypto.hkdfSha256(ikm, salt, info, length)
    }

    /**
     * PBKDF2-HMAC-SHA-256 (RFC 8018) of [password]. The characters go to the KDF as they are and
     * no `String` is made from them; the caller wipes [password].
     */
    public fun pbkdf2HmacSha256(password: CharArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        require(iterations > 0 && length > 0)
        return PlatformCrypto.pbkdf2HmacSha256(password, salt, iterations, length)
    }

    /** [pbkdf2HmacSha256] of [password] read as UTF-8 text (the characters made from it are wiped). */
    public fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        require(iterations > 0 && length > 0)
        return PlatformCrypto.pbkdf2HmacSha256(password, salt, iterations, length)
    }

    /** AES-256-GCM: `ciphertext ‖ tag` of [plaintext] under [key] and [nonce], with [aad] authenticated. */
    public fun aesGcmSeal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        requireKeyAndNonce(key, nonce)
        return PlatformCrypto.aesGcmSeal(key, nonce, aad, plaintext)
    }

    /**
     * Opens `ciphertext ‖ tag` sealed by [aesGcmSeal]: the plaintext, which the caller wipes, or
     * null when it does not authenticate (wrong key, nonce or [aad], changed or truncated data).
     */
    public fun aesGcmOpen(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray? {
        requireKeyAndNonce(key, nonce)
        return PlatformCrypto.aesGcmOpen(key, nonce, aad, ciphertext)
    }

    /** True when [a] and [b] are equal; the time taken depends on their lengths, not on where they differ. */
    public fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = PlatformCrypto.constantTimeEquals(a, b)

    private fun requireKeyAndNonce(key: ByteArray, nonce: ByteArray) {
        require(key.size == KEY_BYTES)
        require(nonce.size == NONCE_BYTES)
    }

    private const val BYTE_MASK = 0xFFL
}
