package com.pasich.encly.presentation.screen.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Note
import com.pasich.encly.presentation.components.tiles.NoteItem
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.NoteSkeleton
import com.pasich.encly.presentation.viewmodel.TrashListEvent
import com.pasich.encly.presentation.viewmodel.TrashViewModel
import com.pasich.encly.ui.theme.EnclyTheme

private const val SKELETON_CARDS = 3

/**
 * The trashed notes: a skeleton while they load, "Trash is empty" or the read error, else the
 * dimmed cards under a hint that a long-press selects. Tapping a card opens it read-only, or
 * toggles it while a selection is open.
 */
@Composable
fun TrashNotesList(
    onItemClick: (Int, Note) -> Unit,
    modifier: Modifier = Modifier,
    trashViewModel: TrashViewModel = hiltViewModel(),
) {
    val listScrollState = rememberLazyListState()
    val state by trashViewModel.state.collectAsStateWithLifecycle()
    val stableNotes = remember(state.notes) {
        state.notes.mapIndexed { index, note -> StableTrashNoteItem(note, index) }
    }
    val spacing = EnclyTheme.spacing

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listScrollState,
        contentPadding = PaddingValues(top = spacing.xs, bottom = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        val load = state.notesLoad
        when {
            load is LoadState.Loading -> items(SKELETON_CARDS) {
                NoteSkeleton(modifier = Modifier.padding(horizontal = spacing.listGutter))
            }

            // A failed read is never shown as an empty trash.
            load is LoadState.Failed -> item {
                EnclyEmptyState(
                    icon = EnclyIcons.Trash,
                    title = load.error.title.asString(),
                    body = load.error.message.asString(),
                    error = true,
                )
            }

            stableNotes.isEmpty() -> item {
                EnclyEmptyState(
                    icon = EnclyIcons.Trash,
                    title = stringResource(R.string.empty_trash_title),
                    body = stringResource(R.string.empty_trash_desc),
                )
            }

            else -> trashCards(stableNotes, state.canCheck, onItemClick) { index ->
                trashViewModel.onEvent(TrashListEvent.ToggleCheckItem(index))
            }
        }
    }
}

/** The hint and the dimmed cards; [onToggle] selects or unselects the card at an index. */
private fun LazyListScope.trashCards(
    notes: List<StableTrashNoteItem>,
    selecting: Boolean,
    onOpen: (Int, Note) -> Unit,
    onToggle: (Int) -> Unit,
) {
    item(key = "hint") {
        Text(
            text = stringResource(R.string.trash_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.listGutter),
        )
    }
    items(items = notes, key = { it.id }) { stableItem ->
        NoteItem(
            itemNote = stableItem.note,
            dimmed = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EnclyTheme.spacing.listGutter)
                .animateItem(),
            onItemClick = {
                if (selecting) onToggle(stableItem.index) else onOpen(stableItem.index, stableItem.note)
            },
            onItemLongClick = { if (!selecting) onToggle(stableItem.index) },
        )
    }
}

@Stable
private data class StableTrashNoteItem(val note: Note, val index: Int, val id: Long = note.id)
