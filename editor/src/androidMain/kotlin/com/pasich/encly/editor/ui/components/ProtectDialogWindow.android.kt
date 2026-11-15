package com.pasich.encly.editor.ui.components

import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Window-level privacy: no autofill service sees any field (notes are not form data), the content is
 * marked sensitive for accessibility services that are not accessibility tools (TalkBack still reads
 * it), and other apps' overlays are hidden while Encly is in front. Activities and dialog windows share it.
 */
public fun Window.applyWindowPrivacy(): Unit = applyWindowPrivacy(decorView, this)

private fun applyWindowPrivacy(decor: View, window: Window?) {
    decor.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        decor.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
    }
    if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // The host app declares HIDE_OVERLAY_WINDOWS; one that does not (a test host) goes without it.
        try {
            window.setHideOverlayWindows(true)
        } catch (_: SecurityException) {
            // Hiding overlays is a hardening on top of the other flags, never a requirement.
        }
    }
}

/** Applies [applyWindowPrivacy] to the dialog window this content is shown in. */
@Composable
public actual fun ProtectDialogWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) applyWindowPrivacy(window.decorView, window) else applyWindowPrivacy(view.rootView, null)
        onDispose { }
    }
}
