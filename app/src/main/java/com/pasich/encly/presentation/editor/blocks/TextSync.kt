package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import com.pasich.encly.presentation.editor.BlockActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which text a field and the stored model last agreed on. An edit differs from it and goes to
 * the model; a model change that differs from it came from elsewhere (undo, redo, a load) and
 * replaces the field's text. A late echo of the field's own edit never does.
 */
internal class TextSync(var known: String) {
    /** The field shows [text]: whether it is an edit to hand to the model. */
    fun isEdit(text: String): Boolean = (text != known).also { if (it) known = text }

    /** The model holds [stored]: whether the field must be set to it. */
    fun isExternal(stored: String): Boolean = (stored != known).also { if (it) known = stored }
}

/**
 * The state of an editor text field, kept in step with the text the editor stores. The field
 * owns what the user types: every edit goes to [onEdit] (the ViewModel records it for undo and
 * autosave). [modelChanges] emits when the stored text may have changed and [model] reads it
 * right then (null: gone); a change that did not come from this field replaces the field's text.
 *
 * Call it inside the field's `key(...)`, so the state belongs to one field for its whole life.
 */
@Composable
internal fun rememberSyncedTextFieldState(
    model: () -> String?,
    modelChanges: Flow<*>,
    onEdit: (String) -> Unit,
): TextFieldState {
    val initial = remember { model().orEmpty() }
    val state = remember { TextFieldState(initial, TextRange(initial.length)) }
    val sync = remember { TextSync(initial) }
    val currentModel by rememberUpdatedState(model)
    val currentOnEdit by rememberUpdatedState(onEdit)

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (sync.isEdit(text)) currentOnEdit(text)
        }
    }
    LaunchedEffect(modelChanges) {
        modelChanges.collect {
            val stored = currentModel() ?: return@collect
            if (sync.isExternal(stored) && state.text.toString() != stored) state.setTextAndPlaceCursorAtEnd(stored)
        }
    }
    return state
}

/** [rememberSyncedTextFieldState] of a text-bearing block, which can also put its cursor at the end. */
@Composable
internal fun rememberBlockTextFieldState(text: StateFlow<String>, blockActions: BlockActions): TextFieldState {
    val actions by rememberUpdatedState(blockActions)
    val state = rememberSyncedTextFieldState(
        model = { text.value },
        modelChanges = text,
        onEdit = { actions.onTextChanged(it) },
    )
    DisposableEffect(state) {
        val unregister = actions.registerCursorToEnd { state.edit { placeCursorAtEnd() } }
        onDispose { unregister() }
    }
    return state
}
