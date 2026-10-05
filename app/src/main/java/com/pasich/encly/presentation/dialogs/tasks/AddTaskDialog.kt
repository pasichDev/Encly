package com.pasich.encly.presentation.dialogs.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pasich.encly.R
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.PriorityValues
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyChip
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.viewmodel.SubtaskDraft
import com.pasich.encly.presentation.viewmodel.TaskDraft
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.delay

private const val TASK_TITLE_MAX_LENGTH = 100
private const val TASK_DESCRIPTION_MAX_LENGTH = 150
private const val INITIAL_FOCUS_DELAY_MS = 300L

/** [subtasks] is the checklist to save, null while an edited task's one has not loaded. */
private data class TaskEditorState(
    val title: String,
    val description: String,
    val priority: Int,
    val editTaskId: Long?,
    val subtasks: List<SubtaskDraft>?,
) {
    val isEditMode: Boolean get() = editTaskId != null
}

private data class TaskEditorActions(
    val onTitleChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onPrioritySelect: (Int) -> Unit,
    val onSubmit: () -> Unit,
    /** Null when there is nothing to delete (a new task). */
    val onDelete: (() -> Unit)? = null,
)

/** Saves the sheet: title, description, priority and the checklist (null: leave it as stored). */
private fun interface TaskSubmit {
    fun submit(title: String, description: String?, priority: Int, subtasks: List<SubtaskDraft>?)
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Compose sheet API: independent state + callbacks from TasksScreen.
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    onAddTask: (title: String, description: String?, priority: Int, subtasks: List<SubtaskDraft>) -> Unit,
    editTask: Task? = null,
    onEditTask: (
        (
            taskId: Long,
            title: String,
            description: String?,
            priority: Int,
            subtasks: List<SubtaskDraft>?,
        ) -> Unit
    )? = null,
    onBackgroundSave: (TaskDraft) -> Unit = {},
    onDeleteTask: ((Task) -> Unit)? = null,
    editSubtasks: List<SubtaskDraft>? = emptyList(),
    subtasks: SubtaskListState? = null,
) {
    var title by remember { mutableStateOf(editTask?.title.orEmpty()) }
    var description by remember { mutableStateOf(editTask?.description.orEmpty()) }
    var selectedPriority by remember { mutableIntStateOf(editTask?.priority ?: 0) }
    // The caller's checklist (TasksViewModel's, which outlives a rotation), or one of its own.
    val ownChecklist = rememberSubtaskListState(editSubtasks)
    val checklist = subtasks ?: ownChecklist
    val titleFocusRequester = remember { FocusRequester() }

    val state = TaskEditorState(
        title = title,
        description = description,
        priority = selectedPriority,
        editTaskId = editTask?.id,
        subtasks = checklist.toSave(),
    )
    val submit = TaskSubmit { taskTitle, taskDescription, priority, checklist ->
        val taskId = editTask?.id
        if (taskId != null && onEditTask != null) {
            onEditTask(taskId, taskTitle, taskDescription, priority, checklist)
        } else {
            onAddTask(taskTitle, taskDescription, priority, checklist.orEmpty())
        }
    }
    val actions = TaskEditorActions(
        onTitleChange = { title = it.take(TASK_TITLE_MAX_LENGTH) },
        onDescriptionChange = { description = it.take(TASK_DESCRIPTION_MAX_LENGTH) },
        onPrioritySelect = { selectedPriority = it },
        onSubmit = { submitTask(state, submit) },
        onDelete = editTask?.let { task -> onDeleteTask?.let { delete -> { delete(task) } } },
    )

    // Backgrounding re-locks the vault and drops this sheet; flush the draft first, like
    // EditNote does. ON_PAUSE fires well before ProcessLifecycleOwner's delayed ON_STOP.
    val currentDraft by rememberUpdatedState(
        TaskDraft(title, description, selectedPriority, state.subtasks),
    )
    val currentOnBackgroundSave by rememberUpdatedState(onBackgroundSave)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) currentOnBackgroundSave(currentDraft)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    EnclyBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TaskEditorContent(state, titleFocusRequester, actions, checklist)
    }

    LaunchedEffect(Unit) {
        delay(INITIAL_FOCUS_DELAY_MS)
        titleFocusRequester.requestFocus()
    }
}

private fun submitTask(state: TaskEditorState, submit: TaskSubmit) {
    if (state.title.isBlank()) return
    submit.submit(state.title, state.description.ifBlank { null }, state.priority, state.subtasks)
}

@Composable
private fun TaskEditorContent(
    state: TaskEditorState,
    titleFocusRequester: FocusRequester,
    actions: TaskEditorActions,
    checklist: SubtaskListState,
) {
    val descriptionFocus = remember { FocusRequester() }
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.fieldGap),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        EnclyTextField(
            value = state.title,
            onValueChange = actions.onTitleChange,
            label = stringResource(
                if (state.isEditMode) R.string.task_edit_placeholder else R.string.task_new_placeholder,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { descriptionFocus.requestFocus() }),
            fieldModifier = Modifier.focusRequester(titleFocusRequester),
        )
        EnclyTextField(
            value = state.description,
            onValueChange = actions.onDescriptionChange,
            label = stringResource(R.string.task_description_placeholder),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
            fieldModifier = Modifier.focusRequester(descriptionFocus),
        )
        // Right under the description, so it stays in view above the keyboard.
        SubtaskEditor(checklist)
        PriorityChips(selected = state.priority, onSelect = actions.onPrioritySelect)
        TaskEditorFooter(state, actions)
    }
}

/** The priority as three chips, highest first. */
@Composable
private fun PriorityChips(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
        SectionOverline(stringResource(R.string.priority_select_title))
        Row(horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
            PriorityValues.priorities.forEach { priority ->
                EnclyChip(
                    label = stringResource(priority.label),
                    selected = priority.id == selected,
                    onClick = { onSelect(priority.id) },
                )
            }
        }
    }
}

@Composable
private fun TaskEditorFooter(state: TaskEditorState, actions: TaskEditorActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.onDelete?.let { onDelete ->
            EnclyTextButton(text = stringResource(R.string.task_delete), onClick = onDelete, destructive = true)
        }
        EnclyButton(
            text = stringResource(if (state.isEditMode) R.string.save else R.string.add),
            onClick = actions.onSubmit,
            enabled = state.title.isNotBlank(),
            modifier = Modifier.weight(1f),
        )
    }
}
