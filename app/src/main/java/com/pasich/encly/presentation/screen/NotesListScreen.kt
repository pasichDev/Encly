package com.pasich.encly.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.presentation.components.tiles.NoteItem
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.NoteSkeleton
import com.pasich.encly.presentation.designsystem.bleed
import com.pasich.encly.presentation.dialogs.NoteAction
import com.pasich.encly.presentation.dialogs.NoteCardBottomSheet
import com.pasich.encly.presentation.viewmodel.NoteListEvent
import com.pasich.encly.presentation.viewmodel.NoteListEvent.ChangeTag
import com.pasich.encly.presentation.viewmodel.NoteListEvent.NoteToTrash
import com.pasich.encly.presentation.viewmodel.NoteListState
import com.pasich.encly.presentation.viewmodel.NoteListViewModel
import com.pasich.encly.presentation.viewmodel.NoteSearchViewModel
import com.pasich.encly.presentation.viewmodel.NotesEmptyState
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SHEET_CLEAR_DELAY_MS = 200L
private const val FADE_MS = 300
private const val SKELETON_CARDS = 4

/** A grid card shows more of its note than a list card. */
private const val GRID_EXCERPT_LINES = 4

/** What the search field of the notes screen holds, and the matches for it. */
class NotesSearch(val query: String, val results: NoteSearchViewModel.SearchResults)

@Composable
fun NotesList(
    isGrid: Boolean,
    listScrollState: LazyListState,
    gridScrollState: LazyStaggeredGridState,
    onItemClick: (Note) -> Unit,
    modifier: Modifier = Modifier,
    search: NotesSearch = NotesSearch("", NoteSearchViewModel.SearchResults()),
    onSecondActionClick: (Note) -> Unit = {},
    header: @Composable () -> Unit = {},
    noteListViewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by noteListViewModel.state.collectAsStateWithLifecycle()
    val sheet = remember { NoteSheetState() }

    NoteActionsSheet(
        sheet = sheet,
        notes = state.notes,
        onEvent = noteListViewModel::onEvent,
        onEdit = onItemClick,
        onDuplicate = onSecondActionClick,
    )

    // While a search is typed the list shows its matches, highlighted, instead of the tag's notes.
    val searching = search.query.isNotBlank()
    val shown = if (searching) search.results.notes else state.notes
    val content = NotesContent(
        notes = remember(shown) { shown.map { StableNoteItem(it) } },
        highlight = if (searching) search.results.query else "",
        onItemClick = onItemClick,
        onItemLongClick = sheet::open,
    )
    val status: @Composable () -> Unit = {
        NotesStatus(
            state = state,
            searching = searching,
            search = search,
            onShowAll = { noteListViewModel.onEvent(NoteListEvent.SelectTag(Tag(id = 0, nameTag = "All"))) },
        )
    }

    if (isGrid && !searching) {
        NotesGrid(content, gridScrollState, NotesTop(header, status), modifier)
    } else {
        NotesColumn(content, listScrollState, NotesTop(header, status), modifier)
    }
}

/** The long-pressed note whose actions sheet is open. */
@Stable
private class NoteSheetState {
    var active by mutableStateOf<NoteListItem?>(null)
    var visible by mutableStateOf(false)

    fun open(item: NoteListItem) {
        active = item
        visible = true
    }
}

/** A note's actions: edit, duplicate, delete, its tag and description. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteActionsSheet(
    sheet: NoteSheetState,
    notes: List<NoteListItem>,
    onEvent: (NoteListEvent) -> Unit,
    onEdit: (Note) -> Unit,
    onDuplicate: (Note) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(notes, sheet.active?.note?.id) {
        sheet.active?.let { current ->
            val updated = notes.find { it.note.id == current.note.id }
            if (updated != null && updated != current) sheet.active = updated
        }
    }

    fun close() {
        sheet.visible = false
        scope.launch {
            bottomSheetState.hide()
            delay(SHEET_CLEAR_DELAY_MS)
            sheet.active = null
        }
    }

    val item = sheet.active ?: return
    NoteCardBottomSheet(
        isVisible = sheet.visible,
        item = item.noteWithTag,
        sheetState = bottomSheetState,
        onAction = { action ->
            when (action) {
                NoteAction.Edit -> onEdit(item.note)

                NoteAction.Duplicate -> onDuplicate(item.note)

                NoteAction.Delete -> onEvent(NoteToTrash(item.note))

                is NoteAction.ChangeTag -> onEvent(ChangeTag(note = item.note, idTag = action.tagId))

                is NoteAction.ChangeDescription -> onEvent(
                    NoteListEvent.ChangeDescription(note = item.note, description = action.description),
                )
            }
            // Changing the tag or the description keeps the sheet open.
            if (action !is NoteAction.ChangeTag && action !is NoteAction.ChangeDescription) close()
        },
        onDismiss = ::close,
    )
}

/** What sits above the cards: the caller's header, then the loading, empty or search status. */
private class NotesTop(val header: @Composable () -> Unit, val status: @Composable () -> Unit)

/** The cards to show and what a tap or long-press on one does. */
private class NotesContent(
    val notes: List<StableNoteItem>,
    val highlight: String,
    val onItemClick: (Note) -> Unit,
    val onItemLongClick: (NoteListItem) -> Unit,
)

/** Room for the last card above the FAB. */
@Composable
private fun notesBottomPadding() = EnclyTheme.spacing.fabHeight + EnclyTheme.spacing.l + EnclyTheme.spacing.m

@Composable
private fun NotesColumn(content: NotesContent, state: LazyListState, top: NotesTop, modifier: Modifier = Modifier) {
    val spacing = EnclyTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(top = spacing.xs, bottom = notesBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.cardGap)) {
                top.header()
                top.status()
            }
        }
        items(items = content.notes, key = { it.id }) { stableItem ->
            NoteItem(
                item = stableItem.item,
                highlight = content.highlight,
                modifier = Modifier
                    .padding(horizontal = spacing.listGutter)
                    .animateItem(),
                onItemClick = content.onItemClick,
                onItemLongClick = { content.onItemLongClick(stableItem.item) },
            )
        }
    }
}

@Composable
private fun NotesGrid(
    content: NotesContent,
    state: LazyStaggeredGridState,
    top: NotesTop,
    modifier: Modifier = Modifier,
) {
    val spacing = EnclyTheme.spacing
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(
            start = spacing.listGutter,
            end = spacing.listGutter,
            top = spacing.xs,
            bottom = notesBottomPadding(),
        ),
        verticalItemSpacing = spacing.cardGap,
        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        // Full-width rows lay themselves out on the gutter, as in the list.
        item(span = StaggeredGridItemSpan.FullLine) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
                modifier = Modifier.bleed(spacing.listGutter),
            ) {
                top.header()
                top.status()
            }
        }
        items(items = content.notes, key = { it.id }) { stableItem ->
            NoteItem(
                item = stableItem.item,
                excerptLines = GRID_EXCERPT_LINES,
                modifier = Modifier.animateItem(),
                onItemClick = content.onItemClick,
                onItemLongClick = { content.onItemLongClick(stableItem.item) },
            )
        }
    }
}

/** The skeleton while notes load, the empty or failed state, or "nothing matches" for a search. */
@Composable
private fun NotesStatus(state: NoteListState, searching: Boolean, search: NotesSearch, onShowAll: () -> Unit) {
    val spacing = EnclyTheme.spacing
    val notesLoad = state.notesLoad
    when {
        searching -> {
            val resultsAreCurrent = search.results.query == search.query.trim()
            if (resultsAreCurrent && search.results.notes.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_nothing_matches, search.results.query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.listGutter, vertical = spacing.l),
                )
            }
        }

        notesLoad is LoadState.Loading -> Column(
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
            modifier = Modifier.padding(horizontal = spacing.listGutter),
        ) {
            repeat(SKELETON_CARDS) { NoteSkeleton() }
        }

        else -> NotesEmptyOrFailed(state, onShowAll)
    }
}

@Stable
private data class StableNoteItem(val item: NoteListItem, val id: Long = item.note.id)

/**
 * "No notes yet" once loading finished with nothing, "No notes tagged …" (with Show all) while a
 * tag chip filters, or the read error when loading failed. A failed read is never shown as
 * "No notes": the vault must not look emptied.
 */
@Composable
private fun NotesEmptyOrFailed(state: NoteListState, onShowAll: () -> Unit) {
    val failure = (state.notesLoad as? LoadState.Failed)?.error
    val empty = state.emptyState
    AnimatedVisibility(
        visible = failure != null || empty != null,
        enter = fadeIn(animationSpec = tween(durationMillis = FADE_MS)),
        exit = fadeOut(animationSpec = tween(durationMillis = FADE_MS)),
    ) {
        when {
            failure != null -> EnclyEmptyState(
                icon = EnclyIcons.Paper,
                title = failure.title.asString(),
                body = failure.message.asString(),
                error = true,
            )

            empty is NotesEmptyState.NoneTagged -> EnclyEmptyState(
                icon = EnclyIcons.Tag,
                title = stringResource(R.string.empty_notes_tagged, empty.tagName),
                body = stringResource(R.string.empty_notes_tagged_desc),
                actionLabel = stringResource(R.string.show_all_notes),
                onAction = onShowAll,
            )

            else -> EnclyEmptyState(
                icon = EnclyIcons.Paper,
                title = stringResource(R.string.empty_notes),
                body = stringResource(R.string.empty_notes_desc),
            )
        }
    }
}
