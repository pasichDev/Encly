package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colour painted behind the transparent status bar. The app root paints the theme
 * background there; a screen whose top edge is another colour (the editor's tinted page) sets
 * its own for as long as it is shown, so the bar and the page meet without a seam.
 */
@Stable
class StatusBarTint {
    var color: Color? by mutableStateOf(null)
}

val LocalStatusBarTint = staticCompositionLocalOf { StatusBarTint() }

/** Paints the status bar area [color] while the calling screen is in the composition. */
@Composable
fun TintStatusBar(color: Color) {
    val tint = LocalStatusBarTint.current
    SideEffect { tint.color = color }
    DisposableEffect(tint) { onDispose { tint.color = null } }
}
