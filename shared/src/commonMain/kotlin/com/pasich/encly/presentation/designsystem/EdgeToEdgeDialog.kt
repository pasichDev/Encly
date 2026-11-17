package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/**
 * The properties of a full-screen [androidx.compose.ui.window.Dialog] that draws under the system bars, the way the
 * app's own window does (Android: `decorFitsSystemWindows = false`; elsewhere a dialog has no bars to fit). Without
 * it the dialog window stops at the bars and leaves dimmed strips over them. The content paints its own background
 * and pads itself by the window insets.
 */
expect fun edgeToEdgeDialogProperties(): DialogProperties

/**
 * Gives the window of the dialog this is called in the same light or dark system bar icons as the app's window
 * (Android only), so they stay readable over the dialog's own background now that it draws under the bars. Call
 * it at the top of the content of a dialog shown with [edgeToEdgeDialogProperties].
 */
@Composable
expect fun DialogSystemBarsLikeApp()
