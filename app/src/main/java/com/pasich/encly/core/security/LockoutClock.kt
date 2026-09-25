package com.pasich.encly.core.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class SystemLockoutClock @Inject constructor(@param:ApplicationContext private val context: Context) : LockoutClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (_: RuntimeException) {
        -1
    }
}

/**
 * A monotonic clock without Android (JVM unit tests). Unlike `elapsedRealtime` it may not
 * count deep sleep, so the app itself always gets [SystemLockoutClock] through Hilt.
 */
object JvmMonotonicClock : LockoutClock {
    private const val NANOS_PER_MILLI = 1_000_000L

    override fun elapsedRealtime(): Long = System.nanoTime() / NANOS_PER_MILLI

    override fun bootCount(): Int = -1
}
