package com.pasich.encly.core.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 (RFC 5869) for outputs of at most one hash block (32 bytes), which is all the
 * vault and backup key hierarchy needs. The intermediate PRK is zeroized before returning;
 * the caller owns (and must wipe) the returned key.
 */
internal object Hkdf {
    private const val ALGORITHM = "HmacSHA256"
    const val MAX_LENGTH = 32

    fun sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..MAX_LENGTH)
        require(salt.isNotEmpty())
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(salt, ALGORITHM))
        val prk = mac.doFinal(ikm)
        return try {
            mac.init(SecretKeySpec(prk, ALGORITHM))
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
}
