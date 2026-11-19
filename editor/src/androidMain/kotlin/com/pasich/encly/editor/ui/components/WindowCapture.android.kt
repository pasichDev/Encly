package com.pasich.encly.editor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.SecureFlagPolicy

/**
 * A popup's window is protected for as long as the host's windows are: while they may be captured it takes
 * what the main window has, as any popup does. The policy is state, so a popup that is open when the host
 * protects its windows again is protected with them.
 */
@Composable
public actual fun protectedPopupProperties(focusable: Boolean): PopupProperties {
    val capture = LocalWindowCapture.current
    var capturable by remember(capture) { mutableStateOf(capture?.capturable ?: true) }
    DisposableEffect(capture) {
        val stop = capture?.observe { capturable = it }
        onDispose { stop?.invoke() }
    }
    return PopupProperties(
        focusable = focusable,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        securePolicy = if (capturable) SecureFlagPolicy.Inherit else SecureFlagPolicy.SecureOn,
        excludeFromSystemGesture = true,
        clippingEnabled = true,
    )
}
