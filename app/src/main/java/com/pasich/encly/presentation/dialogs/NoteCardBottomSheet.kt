package com.pasich.encly.presentation.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquareText
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.pasich.encly.R
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.presentation.components.editNote.NoteSubTitle
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DESCRIPTION_FOCUS_DELAY_MS = 300L
private const val DESCRIPTION_MAX_LINES = 5

sealed class NoteAction {
    object Edit : NoteAction()
    object Duplicate : NoteAction()
    object Delete : NoteAction()
    data class ChangeTag(val tagId: Long) : NoteAction()
    data class ChangeDescription(val description: String) : NoteAction()
}

@Composable
private fun NoteActionHeader(item: NoteWithTag, changeTag: (Long) -> Unit) {
    var isVisibleTitle by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = isVisibleTitle) {
            Text(
                text = item.note.title.ifEmpty { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NoteSubTitle(
            tagsViewListen = { isVisibleTitle = !it },
            statusSaveNote = SaveStatusNote.OLD,
            note = item.note,
            changeTag = changeTag,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCardBottomSheet(
    isVisible: Boolean,
    item: NoteWithTag,
    onAction: (NoteAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    var isEditingDescription by remember { mutableStateOf(false) }

    if (isVisible) {
        EnclyBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
            NoteActionHeader(item, changeTag = { onAction(NoteAction.ChangeTag(it)) })
            DescriptionField(
                initial = item.note.description,
                editing = isEditingDescription,
                onEditingChange = { isEditingDescription = it },
                onSave = { onAction(NoteAction.ChangeDescription(it)) },
            )
            AnimatedVisibility(!isEditingDescription) {
                Column {
                    EnclySheetRow(
                        title = stringResource(id = R.string.edit),
                        icon = Lucide.Pencil,
                        onClick = { onAction(NoteAction.Edit) },
                    )
                    EnclySheetRow(
                        title = stringResource(id = R.string.duplicate),
                        icon = Lucide.CopyPlus,
                        onClick = { onAction(NoteAction.Duplicate) },
                    )
                    EnclySheetRow(
                        title = stringResource(id = R.string.delete),
                        icon = Lucide.Trash2,
                        destructive = true,
                        confirmFirst = true,
                        onClick = { onAction(NoteAction.Delete) },
                    )
                }
            }
        }
    }
}

/**
 * The note's description: read-only until the pencil is tapped, then saved with the check. [editing]
 * follows the field's focus.
 */
@Composable
private fun DescriptionField(
    initial: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSave: (String) -> Unit,
) {
    var noteDescription by remember { mutableStateOf(initial) }
    val descriptionFR = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    TextField(
        value = noteDescription,
        enabled = editing,
        onValueChange = { newValue -> noteDescription = newValue },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        placeholder = {
            Text(
                text = stringResource(R.string.note_description_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        maxLines = DESCRIPTION_MAX_LINES,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(descriptionFR)
            .onFocusChanged { focusState ->
                if (editing !=
                    focusState.isFocused
                ) {
                    onEditingChange(focusState.isFocused)
                }
            },
        shape = MaterialTheme.shapes.small,
        colors = descriptionFieldColors(),
        leadingIcon = { Icon(Lucide.MessageSquareText, contentDescription = null) },
        trailingIcon = {
            DescriptionAction(
                editing = editing,
                onEdit = {
                    coroutineScope.launch {
                        onEditingChange(true)
                        delay(DESCRIPTION_FOCUS_DELAY_MS)
                        descriptionFR.requestFocus()
                    }
                },
                onSave = {
                    onSave(noteDescription)
                    onEditingChange(false)
                },
            )
        },
    )
}

/** The pencil that starts editing the description, or the check that saves it. */
@Composable
private fun DescriptionAction(editing: Boolean, onEdit: () -> Unit, onSave: () -> Unit) {
    IconButton(onClick = if (editing) onSave else onEdit) {
        Icon(
            if (editing) Lucide.Check else Lucide.Pencil,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = stringResource(if (editing) R.string.save else R.string.edit),
        )
    }
}

/** A tonal field without an indicator line, its icon in `primary`. */
@Composable
private fun descriptionFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.primary,
)
