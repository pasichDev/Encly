package com.pasich.encly.core.security

import kotlin.test.Test
import kotlin.test.assertContentEquals

class SensitiveDataCleanerTest {
    @Test
    fun clearZeroesBytesInPlace() {
        val secret = byteArrayOf(1, 2, 3)

        SensitiveDataCleaner.clear(secret)

        assertContentEquals(ByteArray(3), secret)
    }

    @Test
    fun clearZeroesCharactersInPlace() {
        val secret = "123456".toCharArray()

        SensitiveDataCleaner.clear(secret)

        assertContentEquals(CharArray(6), secret)
    }
}
