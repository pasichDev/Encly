package com.pasich.encly.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The P-256 point decoding behind the iOS PIN factor. */
class P256PointsTest {
    private val gx = GoldenVectors.hex("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296")
    private val gy = GoldenVectors.hex("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")

    @Test
    fun theGeneratorsAbscissaDecodesToTheGeneratorOrItsMirror() {
        val point = assertNotNull(P256Points.fromX(gx))

        assertEquals(P256Points.POINT_BYTES, point.size)
        assertEquals(4, point[0].toInt())
        assertContentEquals(gx, point.copyOfRange(1, 33))
        // Either y of the pair: the generator's, or p - y, whose last byte is 0xFF minus the generator's (p ends in 0xFF).
        val y = point.copyOfRange(33, 65)
        assertTrue(y.contentEquals(gy) || y.last() == (0xFF - (gy.last().toInt() and 0xFF)).toByte())
    }

    @Test
    fun anXWithoutAPointIsRefused() {
        // About half of all x have no point; of sixteen small ones both outcomes occur, and ff..ff is above the prime.
        val refused = (0 until 16).count { P256Points.fromX(ByteArray(32).also { x -> x[31] = it.toByte() }) == null }
        assertTrue(refused in 1..15)
        assertNull(P256Points.fromX(ByteArray(32) { 0xFF.toByte() }))
    }

    @Test
    fun theDecodingIsDeterministic() {
        val x = ByteArray(32) { (it * 7 + 3).toByte() }
        val first = P256Points.fromX(x)
        val second = P256Points.fromX(x)
        assertContentEquals(first, second)
    }

    @Test
    fun aWrongLengthIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> { P256Points.fromX(ByteArray(31)) }
    }
}
