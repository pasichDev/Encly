package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.Composable

/**
 * Sets what a right click in a text field under [content] offers, on a desktop: Cut, Copy, Paste and Select all, in
 * the language of the app, or with [allowCopyAndCut] false only Paste and Select all (a field whose text must never
 * leave it). Only what the field can do at that moment is listed. Where the host has no pointer menu (a phone), it
 * does nothing and shows [content] as it is.
 */
@Composable
internal expect fun ProvideTextContextMenu(allowCopyAndCut: Boolean, content: @Composable () -> Unit)

/**
 * Whether [ProvideTextContextMenu] decides the right-click menu of a text field here. Where it does not (a phone),
 * the host's own selection menu applies, and a field that must offer no copy has to refuse the right button itself.
 */
internal expect val providesTextContextMenu: Boolean
