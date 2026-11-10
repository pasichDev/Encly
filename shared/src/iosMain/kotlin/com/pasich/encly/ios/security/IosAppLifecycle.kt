package com.pasich.encly.ios.security

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

/**
 * The foreground state of the app for [com.pasich.encly.core.security.SessionLockManager], which is a lifecycle
 * observer: Android feeds it from the process lifecycle, the desktop from the window, iOS from the application
 * notifications of UIKit.
 *
 * | UIKit                          | Lifecycle   | Meaning                                           |
 * |--------------------------------|-------------|---------------------------------------------------|
 * | didBecomeActive                | ON_RESUME   | in front and receiving input                      |
 * | willResignActive               | ON_PAUSE    | the app switcher, Control Center, a call, a sheet |
 * | didEnterBackground             | ON_STOP     | the user left the app (Android's ON_STOP)         |
 * | willEnterForeground            | ON_START    | the app comes back, before its first frame        |
 *
 * The lock manager acts on ON_STOP and ON_START only, as on Android: pulling down Control Center or the Face ID
 * prompt pauses the app without locking it, leaving it for another app or the Home screen starts the auto-lock
 * delay. The same owner is given to the UI as its lifecycle, so the navigation guards see the same stops and
 * starts as the lock manager.
 *
 * [onForeground] runs once the app is in the foreground again, after the lock manager has seen the start.
 */
internal class IosAppLifecycle(private val onForeground: () -> Unit = {}) : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this)
    private val observers = mutableListOf<Any>()

    override val lifecycle: Lifecycle get() = registry

    init {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // Created while the app is starting: it is "inactive" until the first didBecomeActive. A start that
        // happens in the background (a prewarm) stays created and is started by willEnterForeground.
        val state = UIApplication.sharedApplication.applicationState
        if (state != UIApplicationState.UIApplicationStateBackground) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            if (state == UIApplicationState.UIApplicationStateActive) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        }
        observe(UIApplicationDidBecomeActiveNotification) { resume() }
        observe(UIApplicationWillResignActiveNotification) { pause() }
        observe(UIApplicationDidEnterBackgroundNotification) { stop() }
        observe(UIApplicationWillEnterForegroundNotification) { start() }
    }

    /** True while the app counts as in front (started). */
    val inForeground: Boolean get() = registry.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun start() {
        if (registry.currentState == Lifecycle.State.CREATED) registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    private fun resume() {
        start()
        if (registry.currentState == Lifecycle.State.STARTED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            onForeground()
        }
    }

    private fun pause() {
        if (registry.currentState == Lifecycle.State.RESUMED) registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    private fun stop() {
        pause()
        if (registry.currentState == Lifecycle.State.STARTED) registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    private fun observe(name: String?, action: () -> Unit) {
        observers += NSNotificationCenter.defaultCenter.addObserverForName(name, null, NSOperationQueue.mainQueue) {
            action()
        }
    }
}
