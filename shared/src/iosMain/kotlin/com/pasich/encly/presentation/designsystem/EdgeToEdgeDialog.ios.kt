package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

// The platform dialog already covers the screen under the system bars here.
actual fun edgeToEdgeDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false)

@Composable
actual fun DialogSystemBarsLikeApp() = Unit
