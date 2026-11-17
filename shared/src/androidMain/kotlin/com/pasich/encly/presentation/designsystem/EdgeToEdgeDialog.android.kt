package com.pasich.encly.presentation.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

actual fun edgeToEdgeDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

@Composable
actual fun DialogSystemBarsLikeApp() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    SideEffect {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        val appWindow = activity?.window ?: return@SideEffect
        val app = WindowCompat.getInsetsController(appWindow, appWindow.decorView)
        WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView).apply {
            isAppearanceLightStatusBars = app.isAppearanceLightStatusBars
            isAppearanceLightNavigationBars = app.isAppearanceLightNavigationBars
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
