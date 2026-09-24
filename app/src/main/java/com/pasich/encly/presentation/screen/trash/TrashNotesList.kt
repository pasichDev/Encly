package com.pasich.encly.presentation.screen.trash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.pasich.encly.R
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.data.model.Note
import com.pasich.encly.presentation.components.tiles.NoteItem
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.viewmodel.TrashListEvent
import com.pasich.encly.presentation.viewmodel.TrashViewModel
import com.pasich.encly.ui.theme.EnclyTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashNotesList(
    onItemClick: (Int, Note) -> Unit,
    modifier: Modifier = Modifier,
    trashViewModel: TrashViewModel = hiltViewModel(),
) {
    val listScrollState = rememberLazyListState()
    val trashListState by trashViewModel.state.collectAsStateWithLifecycle()

    // Stable keys for better performance
    val stableNotes = remember(trashListState.notes) {
        trashListState.notes.mapIndexed { index, note ->
            StableTrashNoteItem(note, index)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column {
            val failure = (trashListState.notesLoad as? LoadState.Failed)?.error
            AnimatedVisibility(
                visible = failure != null || trashListState.notesLoad.valueOrNull()?.isEmpty() == true,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300)),
            ) {
                // A failed read is never shown as an empty trash.
                EnclyEmptyState(
                    icon = Lucide.Trash2,
                    title = failure?.title?.asString(),
                    body = failure?.message?.asString() ?: stringResource(R.string.empty_trash_desc),
                    error = failure != null,
                )
            }

            AnimatedVisibility(
                visible = trashListState.notes.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300)),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listScrollState,
                    contentPadding = PaddingValues(top = EnclyTheme.spacing.xs, bottom = EnclyTheme.spacing.l),
                    verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.cardGap),
                ) {
                    items(
                        items = stableNotes,
                        key = { it.id },
                    ) { stableItem ->
                        NoteItem(
                            itemNote = stableItem.note,
                            dimmed = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = EnclyTheme.spacing.listGutter)
                                .animateItem(),
                            onItemClick = {
                                if (trashListState.canCheck) {
                                    trashViewModel.onEvent(TrashListEvent.ToggleCheckItem(stableItem.index))
                                    return@NoteItem
                                }
                                onItemClick(stableItem.index, stableItem.note)
                            },
                            onItemLongClick = {
                                if (trashListState.canCheck) {
                                    return@NoteItem
                                }
                                trashViewModel.onEvent(TrashListEvent.ToggleCheckItem(stableItem.index))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Stable
private data class StableTrashNoteItem(val note: Note, val index: Int, val id: Long = note.id)
