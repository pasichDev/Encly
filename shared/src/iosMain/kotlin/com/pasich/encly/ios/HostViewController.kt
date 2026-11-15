@file:OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)

package com.pasich.encly.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ExportObjCClass
import platform.UIKit.UIColor
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIUserInterfaceStyle
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
        // The launch screen's colour, until the theme paints the real one: no flash between the two.
        // (the LaunchBackground asset, in code: the app's default light and dark page colours).
        view.backgroundColor =
            if (traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark) {
                UIColor(red = DARK_RED, green = DARK_GREEN, blue = DARK_BLUE, alpha = 1.0)
            } else {
                UIColor(red = LIGHT_RED, green = LIGHT_GREEN, blue = LIGHT_BLUE, alpha = 1.0)
            }
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

// 0xF7F3EA and 0x1C1915 over 255: the default theme's page colours, as in the LaunchBackground asset.
private const val LIGHT_RED = 247.0 / 255
private const val LIGHT_GREEN = 243.0 / 255
private const val LIGHT_BLUE = 234.0 / 255
private const val DARK_RED = 28.0 / 255
private const val DARK_GREEN = 25.0 / 255
private const val DARK_BLUE = 21.0 / 255
