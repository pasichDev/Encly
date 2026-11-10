@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.ios.security

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.QuartzCore.CATransaction
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UILabel
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenCapturedDidChangeNotification
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelAlert
import platform.UIKit.UIWindowScene
import platform.UIKit.secondaryLabelColor
import platform.UIKit.systemBackgroundColor

/**
 * What Android's `FLAG_SECURE` does for the window, as far as iOS lets an app do it. `FLAG_SECURE` keeps the app out
 * of screenshots, recordings, casts and the recents snapshot. iOS gives an app no way to refuse a screenshot, but
 * the other three can be covered:
 * - the app switcher snapshot: the system takes it when the app goes to the background, and the cover is up from
 *   `willResignActive` on, before that, so the snapshot shows the cover and not a note;
 * - screen recording, AirPlay and wired mirroring (`UIScreen.isCaptured`): while the screen is captured the cover
 *   hides the content from the captured stream too (and from the user's own screen, as black frames do on Android);
 * - the cover is a window of its own above every other window, so it also covers a document picker or any other
 *   sheet that is on screen, which a view inside the app would not.
 *
 * Not possible on iOS, so not done: preventing a screenshot taken by the user while the content is shown. The
 * vault locks whenever the app is left, as the screenshot is the user's own act.
 *
 * [install] is idempotent; [enforce] re-asserts the cover state (what `AppWindow.enforceSecure` asks for).
 */
internal class PrivacyShield {
    private var cover: UIWindow? = null
    private var inactive = false
    private var installed = false

    /** Starts watching the application and the screen capture state. */
    fun install() {
        if (installed) return
        installed = true
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
            inactive = true
            enforce()
        }
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
            inactive = false
            enforce()
        }
        center.addObserverForName(UIScreenCapturedDidChangeNotification, null, NSOperationQueue.mainQueue) {
            enforce()
        }
        // The app may start with the screen already being captured.
        enforce()
    }

    /** Shows or hides the cover for the current state. */
    fun enforce() {
        if (inactive || screenIsCaptured()) show() else hide()
    }

    private fun screenIsCaptured(): Boolean = UIScreen.mainScreen.captured

    private fun show() {
        val window = cover ?: newCover().also { cover = it }
        window.hidden = false
        window.layoutIfNeeded()
        // The system snapshot is taken as soon as the notification handlers return: draw now.
        CATransaction.flush()
    }

    private fun hide() {
        cover?.hidden = true
    }

    private fun newCover(): UIWindow {
        val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().firstOrNull()
        val window = if (scene != null) UIWindow(windowScene = scene) else UIWindow(frame = UIScreen.mainScreen.bounds)
        window.windowLevel = UIWindowLevelAlert + ABOVE_ALERTS
        window.rootViewController = UIViewController().apply {
            view.backgroundColor = UIColor.systemBackgroundColor
            view.addSubview(brandLabel(view))
        }
        return window
    }

    private fun brandLabel(parent: UIView): UILabel = UILabel(frame = parent.bounds).apply {
        text = "Encly"
        textAlignment = NSTextAlignmentCenter
        font = UIFont.systemFontOfSize(LABEL_SIZE, weight = UIFontWeightSemibold)
        textColor = UIColor.secondaryLabelColor
        autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
    }

    private companion object {
        const val LABEL_SIZE = 28.0
        const val ABOVE_ALERTS = 1000.0
    }
}
