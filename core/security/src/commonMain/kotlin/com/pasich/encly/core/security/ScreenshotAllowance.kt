package com.pasich.encly.core.security

import androidx.annotation.MainThread
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Allow screenshots until the app locks" (Settings → Security).
 *
 * Capture protection is the rule; this is a temporary exception the user makes after
 * re-authenticating. It lives in memory only: it is never stored, so a new process always starts
 * protected, and [SessionLockManager] revokes it on every lock (manual, automatic, device lock).
 *
 * Screens that show a secret (the lock screen, PIN entry, recovery words) hold [protect] while
 * they are on screen: [capturable] is false then, even while the allowance is on. The window
 * follows [capturable] through [observe], synchronously, so the flag is back before the next frame.
 *
 * Main thread only, like the lifecycle callbacks and the composition that use it.
 */
@SingleIn(AppScope::class)
@Inject
public class ScreenshotAllowance {
    private val _allowed = MutableStateFlow(false)

    /** The user turned the allowance on and the app has not locked since. */
    public val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private var protectedScreens = 0
    private val observers = mutableListOf<(Boolean) -> Unit>()

    /** Whether the window may be captured right now: allowed, and no secret is on screen. */
    public val capturable: Boolean get() = _allowed.value && protectedScreens == 0

    /** Called once the user re-authenticated and accepted the warning. */
    @MainThread
    public fun allow(): Unit = change { _allowed.value = true }

    /** Ends the allowance: the user turned it off, or the app locked. */
    @MainThread
    public fun revoke(): Unit = change { _allowed.value = false }

    /**
     * A screen with a secret is showing: the window is not capturable until the returned function
     * is called (once; calling it again does nothing).
     */
    @MainThread
    public fun protect(): () -> Unit {
        change { protectedScreens++ }
        var released = false
        return {
            if (!released) {
                released = true
                change { protectedScreens-- }
            }
        }
    }

    /**
     * Tells [observer] now, and on every change, whether the window is [capturable]. The returned
     * function stops it.
     */
    @MainThread
    public fun observe(observer: (capturable: Boolean) -> Unit): () -> Unit {
        observers += observer
        observer(capturable)
        return { observers -= observer }
    }

    private fun change(update: () -> Unit) {
        val before = capturable
        update()
        val after = capturable
        if (after != before) observers.toList().forEach { it(after) }
    }
}
