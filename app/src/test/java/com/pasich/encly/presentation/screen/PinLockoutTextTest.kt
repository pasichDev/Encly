package com.pasich.encly.presentation.screen

import com.pasich.encly.R
import com.pasich.encly.presentation.screen.pincode.lockoutUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lockouts now run up to a day: the countdown switches to minutes and hours, rounded up. */
class PinLockoutTextTest {
    @Test
    fun shortLockoutsCountSecondsLongOnesMinutesThenHours() {
        assertEquals(R.plurals.lock_lockout_seconds to 30, lockoutUnit(30))
        assertEquals(R.plurals.lock_lockout_seconds to 119, lockoutUnit(119))
        assertEquals(R.plurals.lock_lockout_minutes to 2, lockoutUnit(120))
        assertEquals(R.plurals.lock_lockout_minutes to 16, lockoutUnit(15 * 60 + 1))
        assertEquals(R.plurals.lock_lockout_hours to 2, lockoutUnit(2 * 3600))
        assertEquals(R.plurals.lock_lockout_hours to 24, lockoutUnit(24 * 3600))
    }
}
