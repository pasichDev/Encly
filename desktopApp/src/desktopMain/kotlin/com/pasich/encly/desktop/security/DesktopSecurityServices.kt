package com.pasich.encly.desktop.security

import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.core.security.BiometricHost
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.core.security.LockoutClock
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.time.TimeSource

/**
 * [MainThreadTimer] on a Swing [Timer]: the action runs on the AWT event dispatch thread, which is the UI
 * thread of the Compose window, like the main Looper on Android.
 */
class SwingMainThreadTimer : MainThreadTimer {
    override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
        val timer = Timer(delayMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()) { action() }
        timer.isRepeats = false
        timer.start()
        val cancel: () -> Unit = {
            if (SwingUtilities.isEventDispatchThread()) timer.stop() else SwingUtilities.invokeLater { timer.stop() }
        }
        return cancel
    }
}

/**
 * [BiometricManager] of a desktop, which has no biometric unlock yet (Windows Hello, Touch ID and fprintd
 * are not wired in): the slot never exists, so the vault opens with the PIN or the recovery phrase only, and
 * Settings reports biometrics as unavailable.
 */
class NoBiometricManager : BiometricManager {
    override fun isStrongBiometricAvailable(): Boolean = false

    override fun strongBiometricStatus(): BiometricStatus = BiometricStatus.UNAVAILABLE

    override fun hasSlot(): Boolean = false

    override fun enroll(host: BiometricHost, dek: ByteArray, onResult: (Boolean) -> Unit) = onResult(false)

    override fun unlock(host: BiometricHost, onResult: (ByteArray?) -> Unit) = onResult(null)

    override fun authenticate(
        host: BiometricHost,
        type: BiometricManager.BiometricType,
        callback: BiometricManager.BiometricCallback,
        customConfig: BiometricManager.PromptConfig?,
    ) = callback.onError(ERROR_UNAVAILABLE, "Biometrics are not available on the desktop")

    override fun disable() = Unit

    private companion object {
        /** BiometricPrompt's ERROR_HW_NOT_PRESENT, the code the callers already treat as "no biometrics". */
        const val ERROR_UNAVAILABLE = 12
    }
}

/** What the operating system says about the time since it booted. Null when it cannot be read. */
interface BootClockSource {
    /** Milliseconds since boot, sleep included where the OS counts it. */
    fun uptimeMillis(): Long?

    /** An id that changes with every boot, or null when the OS gives none. */
    fun bootId(): String?
}

/**
 * [LockoutClock] on the operating system's uptime, so the PIN lockout survives a restart of the app: a clock
 * that starts at zero with the process would let a restart cut a running lockout short.
 *
 * The lockout logic keeps its penalty at an uptime anchor and the boot it was set in. A reboot (another boot
 * id, or an uptime smaller than the anchor) restarts the whole penalty, never shortens it. Where the OS gives
 * no boot id (Windows) the uptime comparison alone does that: after a reboot `uptime - anchor` is never larger
 * than the real time since the anchor.
 *
 * If the uptime cannot be read, the clock falls back to a monotonic one that starts with the process; a
 * lockout is then only counted while the process lives (the weakest case, reported by [exact]).
 */
class OsUptimeClock(private val source: BootClockSource) : LockoutClock {
    private val processStart = TimeSource.Monotonic.markNow()
    private val uptimeWorks: Boolean = source.uptimeMillis() != null

    /** True when the clock follows the OS uptime, false for the process-local fallback. */
    val exact: Boolean get() = uptimeWorks

    override fun elapsedRealtime(): Long =
        (if (uptimeWorks) source.uptimeMillis() else null) ?: processStart.elapsedNow().inWholeMilliseconds

    override fun bootCount(): Int = source.bootId()?.hashCode() ?: -1
}

/** Parses the first field of Linux's /proc/uptime ("12345.67 8910.11") into milliseconds. */
internal fun parseProcUptime(text: String): Long? {
    val seconds = text.trim().substringBefore(' ').toDoubleOrNull() ?: return null
    return (seconds * MILLIS_PER_SECOND).toLong().takeIf { it >= 0 }
}

private const val MILLIS_PER_SECOND = 1000
