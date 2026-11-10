package com.pasich.encly.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The platform-free clock the unit tests use for the PIN lockout: it never goes back. */
class MonotonicClockTest {
    @Test
    fun itNeverGoesBackAndAdvances() {
        val first = JvmMonotonicClock.elapsedRealtime()
        Thread.sleep(20)
        val second = JvmMonotonicClock.elapsedRealtime()

        assertTrue("$first -> $second", second >= first + 15)
        assertEquals(-1, JvmMonotonicClock.bootCount())
    }
}
