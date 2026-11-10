package com.pasich.encly.presentation.effects

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

@Composable
internal actual fun rememberImeVisible(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0

@Composable
internal actual fun windowWidth(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp()
}
