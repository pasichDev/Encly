package com.pasich.encly.testutil

import com.pasich.encly.core.crypto.Crypto

private const val BLOCK_BYTES = 64
private const val INNER_PAD: Byte = 0x36
private const val OUTER_PAD: Byte = 0x5c

/**
 * HMAC-SHA-256 (RFC 2104) written out from [Crypto.sha256], so a test can compute a MAC the code under test
 * must reproduce without reaching for a platform provider. Checked against RFC 4231 in HmacSha256Test.
 */
internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val block = (if (key.size > BLOCK_BYTES) Crypto.sha256(key) else key).copyOf(BLOCK_BYTES)
    val inner = Crypto.sha256(ByteArray(BLOCK_BYTES) { (block[it].toInt() xor INNER_PAD.toInt()).toByte() } + data)
    return Crypto.sha256(ByteArray(BLOCK_BYTES) { (block[it].toInt() xor OUTER_PAD.toInt()).toByte() } + inner)
}
