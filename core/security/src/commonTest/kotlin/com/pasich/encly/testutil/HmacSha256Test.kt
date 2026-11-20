package com.pasich.encly.testutil

import kotlin.test.Test
import kotlin.test.assertContentEquals

/** The test helper [hmacSha256] against RFC 4231, so the golden tests that lean on it stand on a published vector. */
class HmacSha256Test {
    @Test
    fun matchesRfc4231TestCases1And2() {
        assertContentEquals(
            hex("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"),
            hmacSha256(ByteArray(20) { 0x0b }, "Hi There".encodeToByteArray()),
        )
        assertContentEquals(
            hex("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"),
            hmacSha256("Jefe".encodeToByteArray(), "what do ya want for nothing?".encodeToByteArray()),
        )
    }

    @Test
    fun matchesRfc4231TestCase6WithAKeyLongerThanABlock() {
        assertContentEquals(
            hex("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54"),
            hmacSha256(
                ByteArray(131) { 0xaa.toByte() },
                "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray(),
            ),
        )
    }

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}
