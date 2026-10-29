package com.pasich.encly.presentation.effects

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/** Whether the on-screen keyboard is showing (its inset is not zero), as the platform reports it. */
@Composable
internal expect fun rememberImeVisible(): Boolean

/** The width of the window the UI is laid out in, as the platform's configuration reports it. */
@Composable
internal expect fun windowWidth(): Dp
