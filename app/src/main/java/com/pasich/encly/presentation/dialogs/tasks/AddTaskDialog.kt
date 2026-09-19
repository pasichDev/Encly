package com.pasich.encly.presentation.dialogs.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.PriorityIndicator
import com.pasich.encly.presentation.components.tasks.PriorityIndicatorSize
import kotlinx.coroutines.delay

private const val TASK_TITLE_MAX_LENGTH = 100
private const val TASK_DESCRIPTION_MAX_LENGTH = 150
private const val INITIAL_FOCUS_DELAY_MS = 300L

private data class TaskEditorState(
    val title: String,
    val description: String,
    val reminderDate: Long?,
    val priority: Int,
    val editTaskId: Long?
) {
    val isEditMode: Boolean get() = editTaskId != null
}

private data class TaskEditorActions(
    val onTitleChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onReminderDateChange: (Long?) -> Unit,
    val onPriorityClick: () -> Unit,
    val onSubmit: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    onAddTask: (title: String, description: String?, reminderDate: Long?, priority: Int) -> Unit,
    editTask: Task? = null,
    onEditTask: ((
        taskId: Long,
        title: String,
        description: String?,
        reminderDate: Long?,
        priority: Int
    ) -> Unit)? = null
) {
    var title by remember { mutableStateOf(editTask?.title.orEmpty()) }
    var description by remember { mutableStateOf(editTask?.description.orEmpty()) }
    var reminderDate by remember { mutableStateOf(editTask?.reminderDate) }
    var selectedPriority by remember { mutableIntStateOf(editTask?.priority ?: 0) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }

    TaskPriorityDialog(
        visible = showPriorityDialog,
        onDismiss = { showPriorityDialog = false },
        onSelected = {
            selectedPriority = it
            showPriorityDialog = false
        }
    )

    val state = TaskEditorState(
        title = title,
        description = description,
        reminderDate = reminderDate,
        priority = selectedPriority,
        editTaskId = editTask?.id
    )
    val actions = TaskEditorActions(
        onTitleChange = { title = it.take(TASK_TITLE_MAX_LENGTH) },
        onDescriptionChange = { description = it.take(TASK_DESCRIPTION_MAX_LENGTH) },
        onReminderDateChange = { reminderDate = it },
        onPriorityClick = { showPriorityDialog = true },
        onSubmit = { submitTask(state, onAddTask, onEditTask) }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        TaskEditorContent(state, titleFocusRequester, actions)
    }

    LaunchedEffect(Unit) {
        delay(INITIAL_FOCUS_DELAY_MS)
        titleFocusRequester.requestFocus()
    }
}

@Composable
private fun TaskPriorityDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit
) {
    if (!visible) return
    PrioritySelectionDialog(
        onDismissRequest = onDismiss,
        onPrioritySelected = onSelected
    )
}

private fun submitTask(
    state: TaskEditorState,
    onAddTask: (String, String?, Long?, Int) -> Unit,
    onEditTask: ((Long, String, String?, Long?, Int) -> Unit)?
) {
    if (state.title.isBlank()) return
    val description = state.description.ifBlank { null }
    val taskId = state.editTaskId
    if (taskId != null && onEditTask != null) {
        onEditTask(taskId, state.title, description, state.reminderDate, state.priority)
    } else {
        onAddTask(state.title, description, state.reminderDate, state.priority)
    }
}

@Composable
private fun TaskEditorContent(
    state: TaskEditorState,
    titleFocusRequester: FocusRequester,
    actions: TaskEditorActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(15.dp))
        TaskTextFields(state, titleFocusRequester, actions)
        TaskEditorFooter(state, actions)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TaskTextFields(
    state: TaskEditorState,
    titleFocusRequester: FocusRequester,
    actions: TaskEditorActions
) {
    TaskTextField(
        value = state.title,
        onValueChange = actions.onTitleChange,
        placeholder = if (state.isEditMode) "Редагувати завдання" else "Нове завдання",
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(titleFocusRequester)
    )
    TaskTextField(
        value = state.description,
        onValueChange = actions.onDescriptionChange,
        placeholder = "Опис",
        modifier = Modifier.fillMaxWidth(),
        bodyStyle = true
    )
}

@Composable
private fun TaskTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    bodyStyle: Boolean = false
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                style = if (bodyStyle) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.bodyLarge
            )
        },
        modifier = modifier.padding(0.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.PasswordVisible),
        textStyle = if (bodyStyle) MaterialTheme.typography.bodyMedium
        else MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun TaskEditorFooter(
    state: TaskEditorState,
    actions: TaskEditorActions
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateTimeSelector(
                selectedDateTime = state.reminderDate,
                onDateTimeSelected = actions.onReminderDateChange,
                isEnabled = true
            )
            Spacer(Modifier.width(15.dp))
            PriorityIndicator(
                priority = state.priority,
                size = PriorityIndicatorSize.Large,
                modifier = Modifier.clickable(onClick = actions.onPriorityClick)
            )
        }
        TextButton(onClick = actions.onSubmit) {
            Text(if (state.isEditMode) "Зберегти" else "Додати")
        }
    }
}
