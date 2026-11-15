package com.pasich.encly.ios.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The lockout clock: milliseconds from the OS' continuous clock, and a boot count from the boot session. */
class IosLockoutClockTest {
    @Test
    fun theClockCountsMillisecondsOfTheInjectedNanoseconds() {
        var nanos = 5_000_000_000UL
        val clock = IosLockoutClock(bootSession = "boot", nanos = { nanos })

        assertEquals(5_000L, clock.elapsedRealtime())
        nanos += 1_500_000UL
        assertEquals(5_001L, clock.elapsedRealtime(), "a partial millisecond is not counted")
    }

    @Test
    fun theRealClockOnlyMovesForward() {
        val clock = IosLockoutClock()
        val first = clock.elapsedRealtime()
        val second = clock.elapsedRealtime()

        assertTrue(first > 0)
        assertTrue(second >= first)
    }

    @Test
    fun theBootCountFollowsTheBootSession() {
        val one = IosLockoutClock(bootSession = "session-a")
        val same = IosLockoutClock(bootSession = "session-a")
        val other = IosLockoutClock(bootSession = "session-b")

        assertEquals(one.bootCount(), same.bootCount())
        assertNotEquals(one.bootCount(), other.bootCount())
    }

    @Test
    fun anUnreadableBootSessionIsMinusOne() {
        assertEquals(-1, IosLockoutClock(bootSession = null).bootCount())
    }

    @Test
    fun theSystemBootSessionIsReadAndStable() {
        val session = readBootSession()

        assertTrue(!session.isNullOrBlank(), "kern.bootsessionuuid must be readable")
        assertEquals(session, readBootSession())
        assertEquals(session.hashCode(), IosLockoutClock().bootCount())
    }
}
