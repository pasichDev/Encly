package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.Composable

@Composable
internal actual fun ProvideTextContextMenu(allowCopyAndCut: Boolean, content: @Composable () -> Unit) = content()

internal actual val providesTextContextMenu: Boolean = false
