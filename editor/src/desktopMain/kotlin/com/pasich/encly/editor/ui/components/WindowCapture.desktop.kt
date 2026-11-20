package com.pasich.encly.editor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.PopupProperties

/** No window-level capture protection exists here: the plain properties. */
@Composable
public actual fun protectedPopupProperties(focusable: Boolean): PopupProperties = PopupProperties(focusable = focusable)
