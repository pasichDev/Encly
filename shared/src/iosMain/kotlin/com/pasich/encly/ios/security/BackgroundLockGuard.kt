package com.pasich.encly.ios.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * Keeps the auto-lock honest while the app is in the background. iOS suspends an app a few seconds after it enters
 * the background; a suspended process runs no timer and receives no notification, so the lock manager's delay timer
 * and the device-lock signal would simply not arrive, and the decrypted vault key would stay in memory until the
 * user returns. On Android the process keeps running and the timer closes the vault on time.
 *
 * The guard asks iOS for a short background task when the app is stopped. While it runs the lock manager's timer
 * and the device-lock signal work as on Android. The task is ended as soon as the vault is closed or the app comes
 * back. If iOS takes the task away first (about half a minute), the expiration handler closes the vault before the
 * app is suspended. This is the one explicit difference from Android: an auto-lock delay longer than the time iOS
 * grants locks at the end of that time, never later. That is stricter, never weaker.
 */
internal class BackgroundLockGuard(
    private val isVaultOpen: () -> Boolean,
    private val lockNow: () -> Unit,
    private val tasks: BackgroundTasks = UiKitBackgroundTasks,
) : DefaultLifecycleObserver {
    private var task: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid

    override fun onStop(owner: LifecycleOwner) = begin()

    override fun onStart(owner: LifecycleOwner) = end()

    /**
     * Starts the background task unless one runs or there is no open vault to close. When iOS refuses the task (no
     * background time is left) the app is about to be suspended with nothing to wait for, so the vault is locked at
     * once.
     */
    fun begin() {
        if (task != UIBackgroundTaskInvalid || !isVaultOpen()) return
        val granted = tasks.begin(TASK_NAME, ::onExpired)
        if (granted == UIBackgroundTaskInvalid) {
            lockNow()
            return
        }
        task = granted
    }

    /**
     * Out of time: close the vault while the app still runs. A lock may first save the notes that are open (up to a
     * second), so the vault can still be open when [lockNow] returns; the task is then not given back here but when
     * the lock reports `locked` (MainViewController calls [end] from there). Giving it back first would let iOS
     * suspend the app with the vault open. If the process is killed meanwhile, the key goes with it.
     */
    private fun onExpired() {
        lockNow()
        if (!isVaultOpen()) end()
    }

    /** Gives the background task back, if there is one. */
    fun end() {
        val running = task
        if (running == UIBackgroundTaskInvalid) return
        task = UIBackgroundTaskInvalid
        tasks.end(running)
    }

    private companion object {
        const val TASK_NAME = "encly.auto-lock"
    }
}

/** The part of `UIApplication` that [BackgroundLockGuard] uses, so that a test can stand in for iOS. */
internal interface BackgroundTasks {
    /** A task named [name], or [UIBackgroundTaskInvalid] when iOS refuses; [onExpire] runs when it runs out. */
    fun begin(name: String, onExpire: () -> Unit): UIBackgroundTaskIdentifier

    fun end(task: UIBackgroundTaskIdentifier)
}

internal object UiKitBackgroundTasks : BackgroundTasks {
    override fun begin(name: String, onExpire: () -> Unit): UIBackgroundTaskIdentifier =
        UIApplication.sharedApplication.beginBackgroundTaskWithName(name, onExpire)

    override fun end(task: UIBackgroundTaskIdentifier) = UIApplication.sharedApplication.endBackgroundTask(task)
}
