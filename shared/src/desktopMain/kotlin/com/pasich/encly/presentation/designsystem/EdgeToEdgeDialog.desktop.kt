package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

// No system bars are drawn over a dialog window here.
actual fun edgeToEdgeDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false)

@Composable
actual fun DialogSystemBarsLikeApp() = Unit
