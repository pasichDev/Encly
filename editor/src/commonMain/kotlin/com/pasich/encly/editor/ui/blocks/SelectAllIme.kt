package com.pasich.encly.editor.ui.blocks

import androidx.compose.runtime.Composable

/*
 * The keyboard's own "Select all" (an Android IME asks the field to run the Select all menu action) only
 * ever selects the text of one field. Where the field's text is already all selected, it is taken as a
 * request for the whole note instead: `onSelectAll` runs and the keyboard's action is swallowed.
 *
 * Only Android has such a keyboard; the other platforms show the content as it is.
 */

/**
 * Shows [content] with the keyboard's Select all intercepted as above. [onSelectAll] returns whether it took
 * the request (block selection is on); false lets the field select its own text.
 */
@Composable
internal expect fun BlockSelectAllIme(onSelectAll: () -> Boolean, content: @Composable () -> Unit)
