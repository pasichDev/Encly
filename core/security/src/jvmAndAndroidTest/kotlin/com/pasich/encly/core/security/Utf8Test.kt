package com.pasich.encly.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.CharBuffer
import kotlin.random.Random

/**
 * The PIN reaches its KDF through [Utf8.encode]. It was the JDK encoder; the hand-coded one must
 * give the same bytes for every input, unpaired surrogates (which both turn into `?`) included.
 */
class Utf8Test {
    private fun jdk(chars: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun check(chars: CharArray) = assertArrayEquals(String(chars), jdk(chars), Utf8.encode(chars))

    @Test
    fun commonTextEncodesLikeTheJdk() {
        listOf("", "1234", "pin éè", "пін", "汉字", "😀 ok", "\u0000")
            .forEach { check(it.toCharArray()) }
    }

    @Test
    fun unpairedSurrogatesEncodeLikeTheJdk() {
        listOf("\uD83D", "\uDE00", "a\uD83Db", "\uD83D😀", "\uDE00\uD83D", "\uD83D\uD83D", "x\uDE00\uDE00")
            .forEach { check(it.toCharArray()) }
    }

    @Test
    fun randomCharactersEncodeLikeTheJdk() {
        val random = Random(42)
        repeat(2_000) {
            check(CharArray(random.nextInt(0, 12)) { random.nextInt(0, 0x10000).toChar() })
        }
    }

    @Test
    fun everyBmpCharacterEncodesLikeTheJdk() {
        for (code in 0..0xFFFF) check(charArrayOf(code.toChar()))
    }
}
