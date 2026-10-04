package com.pasich.encly.presentation.dialogs.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.CheckboxSize
import com.pasich.encly.presentation.designsystem.EnclyCheckbox
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.viewmodel.SubtaskDraft
import com.pasich.encly.presentation.viewmodel.SubtaskDrafts
import com.pasich.encly.ui.theme.EnclyTheme
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableScope

internal const val SUBTASK_TITLE_MAX_LENGTH = 100

/**
 * The editor sheet's checklist: its rows ([subtasks] is null until an edited task's ones have
 * loaded) and the text of the "add" field.
 */
@Stable
internal class SubtaskListState(initial: List<SubtaskDraft>?) {
    var subtasks by mutableStateOf(initial)
        private set
    var newTitle by mutableStateOf("")
        private set

    /** New rows get negative keys; stored rows keep their (positive) ids. */
    private var nextKey = -1L

    /** Takes an edited task's rows once they arrive, after the sheet opened. */
    fun load(loaded: List<SubtaskDraft>?) {
        if (subtasks == null && loaded != null) subtasks = loaded
    }

    /** The checklist to save: a title still in the "add" field counts as a new sub-task. */
    fun toSave(): List<SubtaskDraft>? = subtasks?.let { rows ->
        if (newTitle.isBlank()) rows else rows + SubtaskDraft(key = nextKey, title = newTitle)
    }

    fun setTitle(key: Long, title: String) = update(key) { it.copy(title = title.take(SUBTASK_TITLE_MAX_LENGTH)) }

    fun setChecked(key: Long, checked: Boolean) = update(key) { it.copy(isCompleted = checked) }

    fun remove(key: Long) {
        subtasks = subtasks?.filterNot { it.key == key }
    }

    fun move(from: Int, to: Int) {
        subtasks = subtasks?.let { SubtaskDrafts.move(it, from, to) }
    }

    fun changeNewTitle(title: String) {
        newTitle = title.take(SUBTASK_TITLE_MAX_LENGTH)
    }

    fun add() {
        if (newTitle.isBlank()) return
        subtasks = subtasks?.plus(SubtaskDraft(key = nextKey--, title = newTitle))
        newTitle = ""
    }

    private fun update(key: Long, change: (SubtaskDraft) -> SubtaskDraft) {
        subtasks = subtasks?.map { if (it.key == key) change(it) else it }
    }
}

/** A [SubtaskListState] that picks up [initial] when it changes from null (loaded). */
@Composable
internal fun rememberSubtaskListState(initial: List<SubtaskDraft>?): SubtaskListState {
    val state = remember { SubtaskListState(initial) }
    LaunchedEffect(initial) { state.load(initial) }
    return state
}

/**
 * The task's sub-tasks in the editor sheet: one row each (checkbox, title, remove, drag handle
 * with "Move up"/"Move down" accessibility actions), and a field that adds a new one.
 * One level only: a sub-task has no sub-tasks. Nothing shows until the rows have loaded.
 */
@Composable
internal fun SubtaskEditor(state: SubtaskListState) {
    val subtasks = state.subtasks ?: return
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs)) {
        SectionOverline(stringResource(R.string.subtasks_title))
        ReorderableColumn(
            list = subtasks,
            onSettle = state::move,
            onMove = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        ) { index, subtask, _ ->
            key(subtask.key) {
                SubtaskRow(
                    subtask = subtask,
                    state = state,
                    moveUp = { state.move(index, index - 1) }.takeIf { index > 0 },
                    moveDown = { state.move(index, index + 1) }.takeIf { index < subtasks.lastIndex },
                )
            }
        }
        NewSubtaskRow(title = state.newTitle, onTitleChange = state::changeNewTitle, onAdd = state::add)
    }
}

@Composable
private fun ReorderableScope.SubtaskRow(
    subtask: SubtaskDraft,
    state: SubtaskListState,
    moveUp: (() -> Unit)?,
    moveDown: (() -> Unit)?,
) {
    val moveUpLabel = stringResource(R.string.tag_move_up)
    val moveDownLabel = stringResource(R.string.tag_move_down)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOfNotNull(
                    moveUp?.let { up ->
                        CustomAccessibilityAction(moveUpLabel) {
                            up()
                            true
                        }
                    },
                    moveDown?.let { down ->
                        CustomAccessibilityAction(moveDownLabel) {
                            down()
                            true
                        }
                    },
                )
            },
    ) {
        EnclyCheckbox(
            checked = subtask.isCompleted,
            onCheckedChange = { state.setChecked(subtask.key, it) },
            size = CheckboxSize.SMALL,
        )
        SubtaskTextField(
            value = subtask.title,
            onValueChange = { state.setTitle(subtask.key, it) },
            placeholder = stringResource(R.string.subtask_placeholder),
            done = subtask.isCompleted,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { state.remove(subtask.key) }) {
            Icon(
                EnclyIcons.Close,
                contentDescription = stringResource(R.string.subtask_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        }
        DragHandle(Modifier.draggableHandle())
    }
}

/** The grip a row is dragged by; [modifier] carries the reorderable drag gesture. */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.tag_drag_handle)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(EnclyTheme.spacing.minTouchTarget)
            .semantics { contentDescription = description },
    ) {
        Icon(
            EnclyIcons.Grip,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
        )
    }
}

/** "Add sub-task": type a title and press the keyboard's action or the plus to add it. */
@Composable
private fun NewSubtaskRow(title: String, onTitleChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onAdd, enabled = title.isNotBlank()) {
            Icon(
                EnclyIcons.Plus,
                contentDescription = stringResource(R.string.subtask_add),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        }
        SubtaskTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = stringResource(R.string.subtask_add),
            onImeAction = onAdd,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SubtaskTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    done: Boolean = false,
    onImeAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium.copy(
        color = if (done) colors.onSurfaceVariant else colors.onSurface,
        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(colors.primary),
        // Same keyboard as the task's own fields.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        // The "add" field keeps the keyboard open, so several sub-tasks go in one after another.
        keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() ?: defaultKeyboardAction(ImeAction.Done) }),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = style.copy(color = colors.onSurfaceVariant))
                }
                innerTextField()
            }
        },
    )
}
