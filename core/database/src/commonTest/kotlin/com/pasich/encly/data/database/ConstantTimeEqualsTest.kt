package com.pasich.encly.data.database

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstantTimeEqualsTest {
    @Test
    fun equalArraysMatch() {
        assertTrue(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertTrue(constantTimeEquals(ByteArray(0), ByteArray(0)))
    }

    @Test
    fun aDifferenceAnywhereIsFoundWhereverItIs() {
        val base = ByteArray(32) { it.toByte() }
        for (i in base.indices) {
            val other = base.copyOf().also { it[i] = (it[i] + 1).toByte() }
            assertFalse(constantTimeEquals(base, other), "difference at $i")
            assertFalse(constantTimeEquals(other, base), "difference at $i")
        }
    }

    @Test
    fun arraysOfOtherLengthsNeverMatchEvenWhenOneIsAPrefix() {
        assertFalse(constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 0)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 0), byteArrayOf(1, 2)))
        assertFalse(constantTimeEquals(ByteArray(0), byteArrayOf(0)))
    }
}
