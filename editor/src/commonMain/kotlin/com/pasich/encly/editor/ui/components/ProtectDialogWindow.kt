package com.pasich.encly.editor.ui.components

import androidx.compose.runtime.Composable

/**
 * Window-level privacy for the window a dialog or a modal sheet is shown in (Android only; a no-op
 * elsewhere): no autofill service sees its fields, accessibility tools that are not accessibility
 * services get no data, and other apps' overlays are hidden. A dialog has a window of its own, so
 * what the activity sets on its window does not reach it. Call it at the top of its content.
 */
@Composable
public expect fun ProtectDialogWindow()
