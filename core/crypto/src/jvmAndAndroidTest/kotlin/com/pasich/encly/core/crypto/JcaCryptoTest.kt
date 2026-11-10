package com.pasich.encly.core.crypto

import java.nio.CharBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The JCA seam: what [Crypto] cannot reach because it checks its arguments first. */
class JcaCryptoTest {
    private val nonce = ByteArray(12)
    private val aad = ByteArray(4)

    @Test
    fun aProviderRefusalToSealIsACryptoExceptionNotACrash() {
        // Crypto only passes 32-byte keys; the provider itself refuses any other AES key length.
        val failure = assertFailsWith<CryptoException> { JcaCrypto.aesGcmSeal(ByteArray(17), nonce, aad, ByteArray(8)) }

        assertTrue(failure.cause is java.security.GeneralSecurityException)
    }

    @Test
    fun aProviderRefusalToOpenIsNullNotACrash() {
        assertTrue(JcaCrypto.aesGcmOpen(ByteArray(17), nonce, aad, ByteArray(32)) == null)
    }

    @Test
    fun theDecodedCharactersAreWipedWhenTheBufferHasAnArray() {
        val chars = charArrayOf('a', 'b', 'c')

        JcaCrypto.clearBackingArray(CharBuffer.wrap(chars))

        assertContentEquals(CharArray(3), chars)
    }

    @Test
    fun aBufferWithoutAnArrayIsLeftAlone() {
        // A read-only buffer (here over a String) has no accessible array; nothing may throw.
        JcaCrypto.clearBackingArray(CharBuffer.wrap("abc"))
    }
}
