@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.ios.security

import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.core.security.LockoutClock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.sysctlbyname
import platform.posix.CLOCK_MONOTONIC_RAW
import platform.posix.clock_gettime_nsec_np
import platform.posix.size_tVar
import kotlin.concurrent.Volatile

private const val NANOS_PER_MILLI = 1_000_000UL
private const val BOOT_ID_BUFFER = 64

/**
 * [MainThreadTimer] on a dispatch timer on the main queue, the UI thread, like the main Looper on Android. A timer
 * does not fire while the process is suspended in the background; the lock manager compares the clock when the app
 * comes back, so the delay still counts (see [IosLockoutClock]).
 */
internal class IosMainThreadTimer : MainThreadTimer {
    override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
        val state = TimerState()
        val time = dispatch_time(DISPATCH_TIME_NOW, delayMs.coerceAtLeast(0L) * NSEC_PER_MSEC.toLong())
        dispatch_after(time, dispatch_get_main_queue()) {
            if (!state.cancelled) action()
        }
        return { state.cancelled = true }
    }

    private class TimerState {
        @Volatile
        var cancelled = false
    }
}

/**
 * [LockoutClock] on the continuous clock of the OS: it counts the time the device sleeps (unlike the uptime
 * clock), restarts only at boot, and a change of the wall clock does not move it, so the PIN lockout and the
 * auto-lock delay cannot be cut short by sleeping or by setting the date. [bootCount] is derived from the
 * boot-session UUID the kernel makes at every boot, so the lockout also knows a reboot happened.
 */
internal class IosLockoutClock : LockoutClock {
    private val bootId: Int = readBootSession()?.hashCode() ?: -1

    override fun elapsedRealtime(): Long =
        (clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW.convert()) / NANOS_PER_MILLI).toLong()

    override fun bootCount(): Int = bootId

    private fun readBootSession(): String? = memScoped {
        val buffer = ByteArray(BOOT_ID_BUFFER)
        val size = alloc<size_tVar>()
        size.value = buffer.size.convert()
        val status = buffer.usePinned {
            sysctlbyname("kern.bootsessionuuid", it.addressOf(0), size.ptr, null, 0.convert())
        }
        if (status != 0) null else buffer.toKString()
    }
}
