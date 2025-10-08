package com.pasich.encly.presentation.components.editNote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.presentation.viewmodel.SaveStatusNote
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.utils.formatNoteDate
import java.util.Date

val sizeIcon = 18.dp
val sizeWidthSpacesItems = 15.dp
val sizeWidthSpacesRow = 5.dp

@Composable
fun NoteSubTitle(
    modifier: Modifier = Modifier,
    editTagListViewModel: TagListViewModel = hiltViewModel(),
    statusSaveNote: SaveStatusNote,
    note: Note,
    tagsViewListen: ((Boolean) -> Unit)? = null,
    changeTag: ((Long) -> Unit)? = null,
    isDuplicate: Boolean = false
) {

    val state by editTagListViewModel.state.collectAsState()
    var isTagListVisible by remember { mutableStateOf(false) }
    val targetTag = state.listTags.find { it.id == note.tagId }

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(targetState = !isTagListVisible) { visible ->
            if (visible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (statusSaveNote != SaveStatusNote.LOADING) Row(
                        modifier = modifier.clickable {
                            if (state.listTags.isEmpty()) return@clickable
                            isTagListVisible = true
                            tagsViewListen?.let { it(true) }
                        }) {
                        Box(
                            Modifier.animateContentSize(
                                keyframes { durationMillis = 200 })
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tag),
                                contentDescription = "Saved",
                                modifier = Modifier.size(sizeIcon),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(sizeWidthSpacesRow))
                        Text(
                            text = targetTag?.nameTag ?: stringResource(R.string.no_tag),
                            style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    else Spacer(modifier = Modifier.width(16.dp))
                    Spacer(modifier = Modifier.width(sizeWidthSpacesItems))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (statusSaveNote) {
                            SaveStatusNote.OLD -> Row {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "Saved",
                                    modifier = Modifier.size(sizeIcon),
                                    tint = MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(modifier = Modifier.width(sizeWidthSpacesRow))
                                Text(
                                    text = formatNoteDate(
                                        if (note.date == 0L) Date() else Date(
                                            note.date
                                        )
                                    ),
                                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.outlineVariant)
                                )
                            }


                            SaveStatusNote.SAVING -> Row {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(sizeIcon),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(sizeWidthSpacesRow))
                                Text(
                                    text = "Збережння",
                                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.outlineVariant)
                                )
                            }

                            SaveStatusNote.SAVED -> Row {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Saved",
                                    modifier = Modifier.size(sizeIcon),
                                    tint = MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(modifier = Modifier.width(sizeWidthSpacesRow))
                                Text(
                                    text = "Збережено",
                                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.outlineVariant)
                                )
                            }

                            SaveStatusNote.LOADING -> Row {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(sizeIcon),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(sizeWidthSpacesRow))
                                Text(
                                    text = "Завантаження",
                                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                    }

                    if (isDuplicate) Row {
                        Spacer(modifier = Modifier.width(sizeWidthSpacesItems))
                        Icon(
                            painter = painterResource(R.drawable.ic_duplicate),
                            contentDescription = "Duplicate",
                            modifier = Modifier.size(sizeIcon),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.width(sizeWidthSpacesRow))
                        Text(
                            text = stringResource(R.string.duplicate),
                            style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            } else {

                val reorderedTags = remember(state.listTags, targetTag) {
                    if (targetTag == null) {
                        state.listTags
                    } else {
                        buildList {
                            state.listTags.find { it.id == targetTag.id }?.let { add(it) }
                            addAll(state.listTags.filter { it.id != targetTag.id })
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        TagAssistChip(
                            text = stringResource(R.string.no_tag),
                            isSelected = targetTag == null,
                            onClick = {
                                changeTag?.invoke(0)
                                isTagListVisible = false
                                tagsViewListen?.let { it(false) }
                            },
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp
                            )
                        )
                    }
                    items(reorderedTags) {
                        TagAssistChip(
                            text = it.nameTag,
                            isSelected = (targetTag?.id ?: 0) == it.id,
                            onClick = {
                                changeTag?.invoke(it.id)
                                isTagListVisible = false
                                tagsViewListen?.let { it(false) }
                            },
                            modifier = Modifier.padding(
                                start = 0.dp, end = 16.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TagAssistChip(
    text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        },
        leadingIcon = {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    modifier = Modifier.size(18.dp),
                    contentDescription = "Selected"
                )
            }
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = BorderStroke(0.dp, Color.Transparent),
        shape = MaterialTheme.shapes.medium.copy(CornerSize(12.dp))
    )
}
