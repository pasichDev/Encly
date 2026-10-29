package com.pasich.encly.testutil

import com.pasich.encly.core.security.LockoutClock

/** A [LockoutClock] the test moves by hand. */
internal class FakeLockoutClock(var elapsed: Long = 1_000_000L, var boot: Int = 1) : LockoutClock {
    override fun elapsedRealtime(): Long = elapsed
    override fun bootCount(): Int = boot
}
