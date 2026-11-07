package com.pasich.encly.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.desktop.security.CoarseUptime
import com.pasich.encly.desktop.security.IdleLock
import com.pasich.encly.desktop.security.OsUptimeClock
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener

private const val NANOS_PER_MILLI = 1_000_000L

private const val INPUT_EVENTS: Long = AWTEvent.KEY_EVENT_MASK or
    AWTEvent.MOUSE_EVENT_MASK or
    AWTEvent.MOUSE_MOTION_EVENT_MASK or
    AWTEvent.MOUSE_WHEEL_EVENT_MASK

/**
 * One idle timer for the process: the AWT listener sees the input of every window. The time is measured on the OS
 * uptime, which counts the time asleep, so a machine that slept past the limit locks when it wakes. Like the window
 * host, it needs real AWT and is outside the coverage gate; [IdleLock] and [CoarseUptime] are tested.
 */
@Composable
internal fun IdleLockWhileOpen(graph: DesktopAppGraph) {
    DisposableEffect(graph) {
        val uptime = OsUptimeClock(graph.environment.bootClock)
        val coarse = CoarseUptime(uptime::elapsedRealtime)
        val idle = IdleLock(
            graph.environment.timer,
            graph.autoLockPolicy,
            lockNow = graph.sessionLockManager::lockNow,
            // Every input event only reads the cheap clock; the check itself asks the OS.
            clock = coarse::nanos,
            checkClock = { uptime.elapsedRealtime() * NANOS_PER_MILLI },
        )
        val listener = AWTEventListener { idle.touch() }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, INPUT_EVENTS)
        idle.start()
        onDispose {
            idle.stop()
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
    }
}
