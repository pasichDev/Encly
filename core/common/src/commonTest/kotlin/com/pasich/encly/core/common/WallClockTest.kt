package com.pasich.encly.core.common

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

class WallClockTest {
    @Test
    fun currentTimeMillisIsTheSystemClockInMilliseconds() {
        val before = Clock.System.now().toEpochMilliseconds()
        val now = currentTimeMillis()
        val after = Clock.System.now().toEpochMilliseconds()

        assertTrue(now in before..after, "$before <= $now <= $after")
    }
}
