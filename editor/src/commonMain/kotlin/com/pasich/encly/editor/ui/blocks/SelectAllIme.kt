package com.pasich.encly.editor.ui.blocks

import androidx.compose.runtime.Composable

/*
 * The keyboard's own "Select all" (an Android IME asks the field to run the Select all menu action) only
 * ever selects the text of one field. Where the field's text is already all selected, it is taken as a
 * request for the whole note instead: `onSelectAll` runs and the keyboard's action is swallowed.
 *
 * The same keyboard's Copy and Cut act on the field's own selection, which after that second Select all is still
 * the one paragraph: while the selection runs across blocks they go to `onCopy` and `onCut` instead.
 *
 * Only Android has such a keyboard; the other platforms show the content as it is.
 */

/**
 * Shows [content] with the keyboard's Select all, Copy and Cut intercepted as above. [onSelectAll] returns whether it
 * took the request (block selection is on); false lets the field select its own text. [onCopy] and [onCut] return
 * whether they took theirs (a selection across blocks exists); false lets the field copy or cut its own selection.
 */
@Composable
internal expect fun BlockSelectAllIme(
    onSelectAll: () -> Boolean,
    onCopy: () -> Boolean = { false },
    onCut: () -> Boolean = { false },
    content: @Composable () -> Unit,
)
