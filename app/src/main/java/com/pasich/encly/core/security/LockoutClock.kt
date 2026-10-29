package com.pasich.encly.core.security

import kotlin.time.TimeSource

/**
 * Time for the PIN lockout. Monotonic, so changing the wall clock does not end a lockout.
 * `elapsedRealtime` restarts at boot, so the lockout also needs to know which boot it is in.
 */
interface LockoutClock {
    /** Milliseconds since boot, deep sleep included. */
    fun elapsedRealtime(): Long

    /** Changes on every reboot; -1 when unknown. */
    fun bootCount(): Int
}

/**
 * A monotonic clock without Android (unit tests). Unlike `elapsedRealtime` it may not
 * count deep sleep, so the app itself always gets [SystemLockoutClock] from the graph.
 */
object JvmMonotonicClock : LockoutClock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun elapsedRealtime(): Long = origin.elapsedNow().inWholeMilliseconds

    override fun bootCount(): Int = -1
}
