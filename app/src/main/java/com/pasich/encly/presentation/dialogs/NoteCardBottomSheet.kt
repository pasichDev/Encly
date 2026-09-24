package com.pasich.encly.presentation.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquareText
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.pasich.encly.R
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyChip
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.utils.formatNoteDate
import java.util.Date

sealed class NoteAction {
    object Edit : NoteAction()
    object Duplicate : NoteAction()
    object Delete : NoteAction()
    data class ChangeTag(val tagId: Long) : NoteAction()
    data class ChangeDescription(val description: String) : NoteAction()
}

/**
 * Long-press actions for a note. Every block sits on the sheet's gutter, so the title, the tag chips,
 * the description and the rows share one left edge.
 */
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
            NoteActionHeader(item)
            TagChips(selectedTagId = item.note.tagId ?: 0L, onSelect = { onAction(NoteAction.ChangeTag(it)) })
            DescriptionEditor(
                initial = item.note.description,
                editing = isEditingDescription,
                onEditingChange = { isEditingDescription = it },
                onSave = { onAction(NoteAction.ChangeDescription(it)) },
            )
            AnimatedVisibility(!isEditingDescription) {
                Column {
                    EnclyGroupDivider()
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

/** Title and last edit: the sheet's heading. */
@Composable
private fun NoteActionHeader(item: NoteWithTag) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = item.note.title.ifEmpty { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatNoteDate(if (item.note.date == 0L) Date() else Date(item.note.date)),
            style = EnclyTheme.typography.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "No tag" and every tag as chips; the selected one is the note's tag. */
@Composable
private fun TagChips(
    selectedTagId: Long,
    onSelect: (Long) -> Unit,
    tagListViewModel: TagListViewModel = hiltViewModel(),
) {
    val state by tagListViewModel.state.collectAsState()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            EnclyChip(
                label = stringResource(R.string.no_tag),
                selected = selectedTagId == 0L,
                onClick = { onSelect(0L) },
            )
        }
        items(state.listTags, key = { it.id }) { tag ->
            EnclyChip(label = tag.nameTag, selected = selectedTagId == tag.id, onClick = { onSelect(tag.id) })
        }
    }
}

/** The description as a row; tapping it turns it into a field with Cancel and Save. */
@Composable
private fun DescriptionEditor(
    initial: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSave: (String) -> Unit,
) {
    if (!editing) {
        EnclySheetRow(
            title = initial.ifBlank { stringResource(R.string.note_description_placeholder) },
            icon = Lucide.MessageSquareText,
            onClick = { onEditingChange(true) },
        )
        return
    }
    var text by remember(initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        EnclyTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(R.string.note_description_placeholder),
            fieldModifier = Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = false,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            EnclyTextButton(text = stringResource(R.string.cancel), onClick = { onEditingChange(false) }, muted = true)
            EnclyButton(
                text = stringResource(R.string.save),
                onClick = {
                    onSave(text.trim())
                    onEditingChange(false)
                },
            )
        }
    }
}
