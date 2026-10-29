package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto

/**
 * HKDF-SHA256 (RFC 5869) for outputs of at most one hash block (32 bytes), which is all the
 * vault and backup key hierarchy needs. The intermediate PRK is zeroized before returning;
 * the caller owns (and must wipe) the returned key.
 */
internal object Hkdf {
    const val MAX_LENGTH = 32

    fun sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..MAX_LENGTH)
        require(salt.isNotEmpty())
        return Crypto.hkdfSha256(ikm, salt, info, length)
    }
}
