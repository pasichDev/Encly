@file:OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)

package com.pasich.encly.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ExportObjCClass
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController

/**
 * The root of the app: a plain view controller that holds the Compose view controller full-screen and decides the
 * style of the status bar for it, which Compose does not (the page behind the bar is light or dark by Encly's
 * theme, not by the system's).
 */
@ExportObjCClass
internal class HostViewController(private val content: UIViewController) :
    UIViewController(nibName = null, bundle = null) {
    private var darkContent = false

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(content)
        content.view.setFrame(view.bounds)
        content.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(content.view)
    }

    override fun preferredStatusBarStyle(): UIStatusBarStyle =
        if (darkContent) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent

    /** Dark icons for a light page when [dark] is true. */
    fun setDarkStatusBarContent(dark: Boolean) {
        if (darkContent == dark) return
        darkContent = dark
        setNeedsStatusBarAppearanceUpdate()
    }
}
