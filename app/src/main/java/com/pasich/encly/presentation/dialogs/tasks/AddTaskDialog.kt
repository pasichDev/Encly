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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    isGrantedNotification: Boolean = false,
    onAddTask: (title: String, description: String?, reminderDate: Long?, priority: Int) -> Unit,
    editTask: Task? = null,
    onEditTask: ((taskId: Long, title: String, description: String?, reminderDate: Long?, priority: Int) -> Unit)? = null
) {
    var title by remember { mutableStateOf(editTask?.title ?: "") }
    var description by remember { mutableStateOf(editTask?.description ?: "") }
    var reminderDate by remember { mutableStateOf(editTask?.reminderDate) }
    var selectedPriority by remember { mutableIntStateOf(editTask?.priority ?: 0) }

    val isEditMode = editTask != null


    val titleFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }

    var triggerInitialTitleFocus by remember { mutableStateOf(true) }
    var triggerDescriptionFocus by remember { mutableStateOf(false) }


    var showDialogPriority by remember { mutableStateOf(false) }

    if (showDialogPriority) {
        PrioritySelectionDialog(
            onDismissRequest = { showDialogPriority = false },
            onPrioritySelected = {
                selectedPriority = it
                showDialogPriority = false
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {

            Spacer(Modifier.height(15.dp))
            TextField(
                value = title,
                onValueChange = {
                    if (it.length <= 100) {
                        title = it
                    }
                },
                placeholder = { Text(if (isEditMode) "Редагувати завдання" else "Нове завдання") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester)
                    .padding(0.dp),
                singleLine = true,
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

            TextField(
                value = description,
                onValueChange = {
                    if (it.length <= 150) {
                        description = it
                    }
                },
                placeholder = { Text("Опис", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp)
                    .focusRequester(descriptionFocusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
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



            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {

                    DateTimeSelector(
                        selectedDateTime = reminderDate,
                        onDateTimeSelected = { reminderDate = it },
                        isEnabled = isGrantedNotification
                    )

                    Spacer(Modifier.width(15.dp))

                    PriorityIndicator(
                        priority = selectedPriority,
                        size = PriorityIndicatorSize.Large,
                        modifier = Modifier.clickable {
                            showDialogPriority = true
                        }
                    )


                }
                TextButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            if (isEditMode && onEditTask != null) {
                                onEditTask(
                                    editTask.id,
                                    title,
                                    description.ifBlank { null },
                                    reminderDate,
                                    selectedPriority
                                )
                            } else {
                                onAddTask(
                                    title,
                                    description.ifBlank { null },
                                    reminderDate,
                                    selectedPriority
                                )
                            }
                        }
                    }) {
                    Text(if (isEditMode) "Зберегти" else "Додати")
                }
            }


            Spacer(Modifier.height(20.dp))

        }
    }
    LaunchedEffect(triggerInitialTitleFocus) {
        if (triggerInitialTitleFocus) {
            delay(300L)
            titleFocusRequester.requestFocus()
            triggerInitialTitleFocus = false
        }
    }

    LaunchedEffect(triggerDescriptionFocus) {
        if (triggerDescriptionFocus) {
            delay(200L)
            descriptionFocusRequester.requestFocus()
            triggerDescriptionFocus = false
        }
    }
}
