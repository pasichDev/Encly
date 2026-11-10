package com.pasich.encly.presentation.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.MouseInjectionScope

/** A press and release of the middle mouse button on the node. */
@OptIn(ExperimentalTestApi::class)
fun MouseInjectionScope.middleClick() {
    moveTo(center)
    press(MouseButton.Tertiary)
    release(MouseButton.Tertiary)
}
