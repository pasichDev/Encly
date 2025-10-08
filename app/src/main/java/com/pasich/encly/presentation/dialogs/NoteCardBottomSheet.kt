package com.pasich.encly.presentation.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.presentation.components.custombox.ModalBoxItem
import com.pasich.encly.presentation.components.custombox.RoundPosition
import com.pasich.encly.presentation.components.editNote.NoteSubTitle
import com.pasich.encly.presentation.viewmodel.SaveStatusNote
import com.pasich.encly.ui.theme.bodyNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


sealed class NoteAction {
    object Edit : NoteAction()
    object Share : NoteAction()
    object Duplicate : NoteAction()
    object Delete : NoteAction()
    data class ChangeTag(val tagId: Long) : NoteAction()
    data class ChangeDescription(val description: String) : NoteAction()
}

@Composable
fun NoteActionHeader(
    item: NoteWithTag,
    changeTag: (Long) -> Unit,
) {
    var isVisibleTitle by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp)
    ) {

        Row {
            AnimatedVisibility(visible = isVisibleTitle) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = "Note Icon",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            AnimatedVisibility(visible = isVisibleTitle) {
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start
            ) {
                AnimatedVisibility(visible = isVisibleTitle) {
                    Text(
                        text = item.note.title.ifEmpty {
                            stringResource(
                                R.string.untitled
                            )
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                AnimatedVisibility(visible = isVisibleTitle) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                NoteSubTitle(
                    tagsViewListen = { isVisibleTitle = !it },
                    statusSaveNote = SaveStatusNote.OLD,
                    note = item.note,
                    changeTag = changeTag
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))


    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCardBottomSheet(
    isVisible: Boolean,
    item: NoteWithTag,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onAction: (NoteAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var noteDescription by remember { mutableStateOf(item.note.description) }
    val descriptionFR = remember { FocusRequester() }
    var isTextFieldEnabled by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column {
                NoteActionHeader(item, changeTag = { onAction(NoteAction.ChangeTag(it)) })
                TextField(
                    value = noteDescription,
                    enabled = isTextFieldEnabled,
                    onValueChange = { newValue -> noteDescription = newValue },
                    textStyle = bodyNote.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    placeholder = {
                        Text(
                            text = "Опис...",
                            style = bodyNote.copy(
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    },
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .padding(horizontal = 16.dp)
                        .focusRequester(descriptionFR)
                        .onFocusChanged { focusState ->
                            if (isTextFieldEnabled != focusState.isFocused) {
                                isTextFieldEnabled = focusState.isFocused
                            }
                        },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_thought),
                            contentDescription = "Description",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }, trailingIcon = {
                        if (!isTextFieldEnabled)
                            Icon(
                                Icons.Default.Edit,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        coroutineScope.launch {
                                            isTextFieldEnabled = true
                                            delay(300)
                                            descriptionFR.requestFocus()
                                        }
                                    }
                            )
                        else
                            Icon(
                                Icons.Default.Check,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        onAction(NoteAction.ChangeDescription(noteDescription))
                                        isTextFieldEnabled = false
                                    }
                            )
                    }
                )

                AnimatedVisibility(!isTextFieldEnabled) {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        item {
                            ModalBoxItem(
                                title = stringResource(id = R.string.edit),
                                icon = painterResource(R.drawable.ic_edit_modal),
                                roundPosition = RoundPosition.First,
                                action = { onAction(NoteAction.Edit) })
                        }


                        item {
                            ModalBoxItem(
                                title = stringResource(id = R.string.share),
                                icon = painterResource(R.drawable.ic_share),
                                roundPosition = RoundPosition.Medium,
                                action = { onAction(NoteAction.Share) })
                        }

                        item {
                            ModalBoxItem(
                                title = stringResource(id = R.string.duplicate),
                                icon = painterResource(R.drawable.ic_duplicate),
                                roundPosition = RoundPosition.Medium,
                                action = { onAction(NoteAction.Duplicate) })
                        }


                        item {
                            ModalBoxItem(
                                title = stringResource(id = R.string.delete),
                                icon = painterResource(R.drawable.ic_delete),
                                roundPosition = RoundPosition.Last,
                                confirmationRequest = MaterialTheme.colorScheme.error,
                                action = { onAction(NoteAction.Delete) })
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

            }
        }
    }
}
