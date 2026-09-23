package com.pasich.encly.presentation.editor.blocks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pasich.encly.presentation.editor.BlockActions

/**
 * The text field state of a text-bearing block: follows [text] when it changes from outside
 * (undo/redo) and lets the editor put the cursor at the end. Must be called inside the
 * block's `key(block.id)`, so the state belongs to one block for its whole life.
 */
@Composable
internal fun rememberBlockTextFieldValue(text: String, blockActions: BlockActions): MutableState<TextFieldValue> {
    val fieldValue = remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }

    LaunchedEffect(text) {
        if (fieldValue.value.text != text) {
            fieldValue.value = TextFieldValue(text = text, selection = TextRange(text.length))
        }
    }

    val actions by rememberUpdatedState(blockActions)
    DisposableEffect(Unit) {
        val cursorToEnd = {
            fieldValue.value = fieldValue.value.copy(selection = TextRange(fieldValue.value.text.length))
        }
        val unregister = actions.registerCursorToEnd(cursorToEnd)
        onDispose { unregister() }
    }
    return fieldValue
}
