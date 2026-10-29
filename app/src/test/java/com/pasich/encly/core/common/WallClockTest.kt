package com.pasich.encly.core.common

import org.junit.Assert.assertTrue
import org.junit.Test

class WallClockTest {
    @Test
    fun currentTimeMillisIsTheSystemClockInMilliseconds() {
        val before = System.currentTimeMillis()
        val now = currentTimeMillis()
        val after = System.currentTimeMillis()

        assertTrue("$before <= $now <= $after", now in before..after)
    }
}
