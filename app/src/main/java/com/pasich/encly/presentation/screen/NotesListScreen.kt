package com.pasich.encly.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.presentation.components.EmptyStateWidget
import com.pasich.encly.presentation.components.tiles.NoteItem
import com.pasich.encly.presentation.dialogs.NoteAction
import com.pasich.encly.presentation.dialogs.NoteCardBottomSheet
import com.pasich.encly.presentation.viewmodel.NoteListEvent
import com.pasich.encly.presentation.viewmodel.NoteListEvent.ChangeTag
import com.pasich.encly.presentation.viewmodel.NoteListEvent.NoteToTrash
import com.pasich.encly.presentation.viewmodel.NoteListViewModel
import com.pasich.encly.utils.NotesTextFormatter
import com.pasich.encly.utils.shareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesList(
    isGrid: Boolean,
    listScrollState: LazyListState,
    gridScrollState: LazyStaggeredGridState,
    onItemClick: (Note) -> Unit,
    onSecondActionClick: (Note) -> Unit = {},
    noteListViewModel: NoteListViewModel = hiltViewModel()
) {
    val state by noteListViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var activeNote by remember { mutableStateOf<NoteWithTag?>(null) }
    var isBottomSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.notes, activeNote?.note?.id) {
        activeNote?.let { current ->
            val updated = state.notes.find { it.note.id == current.note.id }
            if (updated != null && updated != current) {
                activeNote = updated
            }
        }
    }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    activeNote?.let { item ->
        NoteCardBottomSheet(
            isVisible = isBottomSheetVisible,
            item = item,
            sheetState = bottomSheetState,
            onAction = {
                when (it) {
                    NoteAction.Edit -> onItemClick(item.note)

                    NoteAction.Share -> {
                        shareText(
                            context,
                            "${item.note.title}\n\n${NotesTextFormatter.jsonToPlainText(item.note.value)}"
                        )
                    }

                    NoteAction.Duplicate -> {
                        onSecondActionClick(item.note)
                    }

                    NoteAction.Delete -> {
                        noteListViewModel.onEvent(NoteToTrash(item.note))
                    }

                    is NoteAction.ChangeTag -> {
                        noteListViewModel.onEvent(ChangeTag(note = item.note, idTag = it.tagId))
                        return@NoteCardBottomSheet
                    }

                    is NoteAction.ChangeDescription -> {
                        noteListViewModel.onEvent(
                            NoteListEvent.ChangeDescription(
                                note = item.note,
                                description = it.description
                            )
                        )
                        return@NoteCardBottomSheet
                    }
                }
                isBottomSheetVisible = false
                scope.launch {
                    bottomSheetState.hide()
                    delay(200)
                    activeNote = null
                }
            },
            onDismiss = {
                isBottomSheetVisible = false
                scope.launch {
                    bottomSheetState.hide()
                    delay(200)
                    activeNote = null

                }
            })
    }

    // Stable keys for better performance
    val stableNotes = remember(state.notes) {
        state.notes.map { StableNoteItem(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            AnimatedVisibility(
                visible = state.notes.isEmpty() && !state.baseState.isLoading,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                EmptyStateWidget(
                    iconRes = R.drawable.ic_notes_empty,
                    title = stringResource(R.string.empty_notes),
                    description = stringResource(R.string.empty_notes_desc)
                )

            }

            AnimatedVisibility(
                visible = state.notes.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Crossfade(targetState = isGrid) { targetIsGrid ->
                    if (targetIsGrid) {

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            state = gridScrollState,
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = 80.dp // Space for FAB
                            ),
                            verticalItemSpacing = 8.dp,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = stableNotes,
                                key = { it.id }
                            ) { stableItem ->
                                NoteItem(
                                    item = stableItem.item,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .animateItem(),
                                    onItemClick = onItemClick,
                                    onItemLongClick = {
                                        activeNote = stableItem.item
                                        isBottomSheetVisible = true
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listScrollState,
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = stableNotes,
                                key = { it.id }
                            ) { stableItem ->
                                NoteItem(
                                    item = stableItem.item,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .animateItem(),
                                    onItemClick = onItemClick,
                                    onItemLongClick = {
                                        activeNote = stableItem.item
                                        isBottomSheetVisible = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Stable
private data class StableNoteItem(
    val item: NoteWithTag,
    val id: Long = item.note.id
)
