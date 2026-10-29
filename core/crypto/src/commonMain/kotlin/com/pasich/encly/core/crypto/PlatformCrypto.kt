package com.pasich.encly.core.crypto

/**
 * What [Crypto] needs from the platform's crypto provider: the primitives themselves, without the
 * argument checks [Crypto] makes first. The JVM and Android actual is `JcaCrypto`.
 */
internal expect object PlatformCrypto {
    fun randomBytes(size: Int): ByteArray

    fun sha256(data: ByteArray, startIndex: Int, endIndex: Int): ByteArray

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray

    fun pbkdf2HmacSha256(password: CharArray, salt: ByteArray, iterations: Int, length: Int): ByteArray

    fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray

    fun aesGcmSeal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray

    fun aesGcmOpen(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray?

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean
}
