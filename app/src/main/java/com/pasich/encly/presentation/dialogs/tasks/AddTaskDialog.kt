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
import androidx.compose.ui.unit.dp
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.PriorityIndicator
import com.pasich.encly.presentation.components.tasks.PriorityIndicatorSize
import kotlinx.coroutines.delay

private const val TASK_TITLE_MAX_LENGTH = 100
private const val TASK_DESCRIPTION_MAX_LENGTH = 150
private const val INITIAL_FOCUS_DELAY_MS = 300L

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

    if (showPriorityDialog) {
        PrioritySelectionDialog(
            onDismissRequest = { showPriorityDialog = false },
            onPrioritySelected = {
                selectedPriority = it
                showPriorityDialog = false
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        TaskEditorContent(
            title = title,
            description = description,
            reminderDate = reminderDate,
            priority = selectedPriority,
            isEditMode = editTask != null,
            titleFocusRequester = titleFocusRequester,
            onTitleChange = { title = it.take(TASK_TITLE_MAX_LENGTH) },
            onDescriptionChange = { description = it.take(TASK_DESCRIPTION_MAX_LENGTH) },
            onReminderDateChange = { reminderDate = it },
            onPriorityClick = { showPriorityDialog = true },
            onSubmit = {
                submitTask(
                    editTask = editTask,
                    title = title,
                    description = description,
                    reminderDate = reminderDate,
                    priority = selectedPriority,
                    onAddTask = onAddTask,
                    onEditTask = onEditTask
                )
            }
        )
    }

    LaunchedEffect(Unit) {
        delay(INITIAL_FOCUS_DELAY_MS)
        titleFocusRequester.requestFocus()
    }
}

private fun submitTask(
    editTask: Task?,
    title: String,
    description: String,
    reminderDate: Long?,
    priority: Int,
    onAddTask: (String, String?, Long?, Int) -> Unit,
    onEditTask: ((Long, String, String?, Long?, Int) -> Unit)?
) {
    if (title.isBlank()) return
    val normalizedDescription = description.ifBlank { null }
    if (editTask != null && onEditTask != null) {
        onEditTask(editTask.id, title, normalizedDescription, reminderDate, priority)
    } else {
        onAddTask(title, normalizedDescription, reminderDate, priority)
    }
}

@Composable
private fun TaskEditorContent(
    title: String,
    description: String,
    reminderDate: Long?,
    priority: Int,
    isEditMode: Boolean,
    titleFocusRequester: FocusRequester,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onReminderDateChange: (Long?) -> Unit,
    onPriorityClick: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(15.dp))
        TaskTextFields(
            title = title,
            description = description,
            isEditMode = isEditMode,
            titleFocusRequester = titleFocusRequester,
            onTitleChange = onTitleChange,
            onDescriptionChange = onDescriptionChange
        )
        TaskEditorFooter(
            reminderDate = reminderDate,
            priority = priority,
            isEditMode = isEditMode,
            onReminderDateChange = onReminderDateChange,
            onPriorityClick = onPriorityClick,
            onSubmit = onSubmit
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TaskTextFields(
    title: String,
    description: String,
    isEditMode: Boolean,
    titleFocusRequester: FocusRequester,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    TaskTextField(
        value = title,
        onValueChange = onTitleChange,
        placeholder = if (isEditMode) "Редагувати завдання" else "Нове завдання",
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(titleFocusRequester)
    )
    TaskTextField(
        value = description,
        onValueChange = onDescriptionChange,
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
                style = if (bodyStyle) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
            )
        },
        modifier = modifier.padding(0.dp),
        singleLine = true,
        textStyle = if (bodyStyle) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
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
    reminderDate: Long?,
    priority: Int,
    isEditMode: Boolean,
    onReminderDateChange: (Long?) -> Unit,
    onPriorityClick: () -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateTimeSelector(
                selectedDateTime = reminderDate,
                onDateTimeSelected = onReminderDateChange,
                isEnabled = true
            )
            Spacer(Modifier.width(15.dp))
            PriorityIndicator(
                priority = priority,
                size = PriorityIndicatorSize.Large,
                modifier = Modifier.clickable(onClick = onPriorityClick)
            )
        }
        TextButton(onClick = onSubmit) {
            Text(if (isEditMode) "Зберегти" else "Додати")
        }
    }
}
