package com.pasich.encly.testutil

import android.app.Activity
import com.pasich.encly.platform.AppWindow

/** The window of a test Activity: it says when the Activity is being recreated, as the app's does. */
internal class ActivityTestWindow(private val activity: Activity) : AppWindow by AppWindow.None {
    override val isChangingConfigurations: Boolean get() = activity.isChangingConfigurations
}
