package com.pasich.encly.ios.security

import com.pasich.encly.core.security.DeviceLockWatcher
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationProtectedDataWillBecomeUnavailable
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * [DeviceLockWatcher] on iOS: the device locking is the moment its protected data becomes unavailable, which
 * UIKit announces (`protectedDataWillBecomeUnavailable`) and answers (`isProtectedDataAvailable`). This needs a
 * device passcode, as the Keychain items of the vault do.
 *
 * The announcement can arrive just before the app is told it entered the background, i.e. before the lock manager
 * starts watching. The watcher therefore listens from the start and remembers a lock until the app is in front
 * again ([appReturned]): a watch that begins after the device locked is told at once (on the next turn of the main
 * queue), and [isDeviceLocked] stays true for a start that follows a lock even though the device is unlocked by
 * then. So a device lock always closes the vault, whatever the order of the events and the auto-lock delay.
 */
internal class IosDeviceLockWatcher : DeviceLockWatcher {
    private var lockedSinceForeground = false
    private val listeners = mutableListOf<() -> Unit>()
    private val observer: Any

    init {
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationProtectedDataWillBecomeUnavailable,
            null,
            NSOperationQueue.mainQueue,
        ) {
            lockedSinceForeground = true
            listeners.toList().forEach { it() }
        }
    }

    override fun isDeviceLocked(): Boolean =
        lockedSinceForeground || !UIApplication.sharedApplication.protectedDataAvailable

    override fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit {
        listeners += onScreenOff
        if (lockedSinceForeground) {
            dispatch_async(dispatch_get_main_queue()) {
                if (onScreenOff in
                    listeners
                ) {
                    onScreenOff()
                }
            }
        }
        return { listeners -= onScreenOff }
    }

    /** The app is in front and has handled its start: a lock from before is history. */
    fun appReturned() {
        lockedSinceForeground = false
    }

    /** Calls [action] every time the device locks, as long as the app runs (in front or not). */
    fun onLocked(action: () -> Unit): () -> Unit = watchScreenOff(action)
}
