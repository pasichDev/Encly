package com.pasich.encly.presentation.screen

import androidx.annotation.StringRes
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.model.SwipeSettings
import com.pasich.encly.presentation.components.swipe.SwipeContext
import com.pasich.encly.presentation.components.swipe.SwipeableNote
import com.pasich.encly.presentation.components.swipe.rememberSwipeContext
import com.pasich.encly.presentation.components.tiles.NoteItem
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.NoteSkeleton
import com.pasich.encly.presentation.designsystem.SectionOverline
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
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    swipe: SwipeSettings = SwipeSettings.Off,
    onSecondActionClick: (Note) -> Unit = {},
    header: @Composable () -> Unit = {},
    noteListViewModel: NoteListViewModel = metroViewModel(),
) {
    val state by noteListViewModel.state.collectAsStateWithLifecycle()
    val sheet = remember { NoteSheetState() }

    NoteActionsSheet(
        sheet = sheet,
        observeNote = noteListViewModel::observeNote,
        onEvent = noteListViewModel::onEvent,
        onEdit = onItemClick,
        onDuplicate = onSecondActionClick,
    )

    // While a search is typed the list shows its matches, highlighted, instead of the tag's notes.
    val searching = search.query.isNotBlank()
    val shown = if (searching) search.results.notes else state.notes
    val content = NotesContent(
        // Search results come in match order, without the pinned/other sections.
        entries = remember(shown, searching) { noteEntries(shown, sections = !searching) },
        highlight = if (searching) search.results.query else "",
        onItemClick = onItemClick,
        onItemLongClick = sheet::open,
        swipe = rememberSwipeContext(swipe, noteListViewModel::perform),
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
internal class NoteSheetState {
    var active by mutableStateOf<NoteListItem?>(null)
    var visible by mutableStateOf(false)

    fun open(item: NoteListItem) {
        active = item
        visible = true
    }
}

/** A note's actions: edit, pin, archive, duplicate, delete, its tag and description. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteActionsSheet(
    sheet: NoteSheetState,
    observeNote: (Long) -> Flow<NoteWithTag?>,
    onEvent: (NoteListEvent) -> Unit,
    onEdit: (Note) -> Unit,
    onDuplicate: (Note) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The sheet shows the note as stored now, not as the list held it when the sheet opened: a tag
    // changed here moves the note out of this list, and the next action must still act on the right row.
    val activeId = sheet.active?.note?.id
    val live by remember(activeId) { activeId?.let(observeNote) ?: emptyFlow() }.collectAsState(initial = null)

    fun close() {
        sheet.visible = false
        scope.launch {
            bottomSheetState.hide()
            delay(SHEET_CLEAR_DELAY_MS)
            sheet.active = null
        }
    }

    val item = live ?: sheet.active?.noteWithTag ?: return
    NoteCardBottomSheet(
        isVisible = sheet.visible,
        item = item,
        sheetState = bottomSheetState,
        onAction = { action ->
            when (action) {
                NoteAction.Edit -> onEdit(item.note)
                NoteAction.Duplicate -> onDuplicate(item.note)
                else -> action.toEvent(item.note)?.let(onEvent)
            }
            // Changing the tag, the colour or the description keeps the sheet open.
            if (action.closesSheet()) close()
        },
        onDismiss = ::close,
    )
}

private fun NoteAction.closesSheet(): Boolean =
    this !is NoteAction.ChangeTag && this !is NoteAction.ChangeColor && this !is NoteAction.ChangeDescription

/** The list event a sheet [NoteAction] on [note] stands for; null for the ones that open another screen. */
private fun NoteAction.toEvent(note: Note): NoteListEvent? = when (this) {
    NoteAction.Edit, NoteAction.Duplicate -> null
    NoteAction.Delete -> NoteToTrash(note)
    is NoteAction.Pin -> NoteListEvent.PinNote(note, pinned)
    is NoteAction.Archive -> NoteListEvent.ArchiveNote(note, archived)
    is NoteAction.ChangeColor -> NoteListEvent.ChangeColor(note, color)
    is NoteAction.ChangeTag -> ChangeTag(note = note, idTag = tagId)
    is NoteAction.ChangeDescription -> NoteListEvent.ChangeDescription(note = note, description = description)
}

/** What sits above the cards: the caller's header, then the loading, empty or search status. */
private class NotesTop(val header: @Composable () -> Unit, val status: @Composable () -> Unit)

/** The cards to show and what a tap or long-press on one does. */
private class NotesContent(
    val entries: List<NoteEntry>,
    val highlight: String,
    val onItemClick: (Note) -> Unit,
    val onItemLongClick: (NoteListItem) -> Unit,
    val swipe: SwipeContext,
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
        items(items = content.entries, key = { it.key }) { entry ->
            when (entry) {
                is NoteSection -> SectionOverline(
                    text = stringResource(entry.title),
                    modifier = Modifier
                        .padding(horizontal = spacing.listGutter)
                        .animateItem(),
                )

                is NoteRow -> SwipeableNote(
                    note = entry.item.note,
                    swipe = content.swipe,
                    modifier = Modifier
                        .padding(horizontal = spacing.listGutter)
                        .animateItem(),
                ) { cardModifier ->
                    NoteItem(
                        item = entry.item,
                        highlight = content.highlight,
                        modifier = cardModifier,
                        onItemClick = content.onItemClick,
                        onItemLongClick = { content.onItemLongClick(entry.item) },
                    )
                }
            }
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
        items(
            items = content.entries,
            key = { it.key },
            span = { entry ->
                if (entry is NoteSection) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
            },
        ) { entry ->
            when (entry) {
                is NoteSection -> SectionOverline(text = stringResource(entry.title), modifier = Modifier.animateItem())

                is NoteRow -> SwipeableNote(
                    note = entry.item.note,
                    swipe = content.swipe,
                    modifier = Modifier.animateItem(),
                ) { cardModifier ->
                    NoteItem(
                        item = entry.item,
                        excerptLines = GRID_EXCERPT_LINES,
                        modifier = cardModifier,
                        onItemClick = content.onItemClick,
                        onItemLongClick = { content.onItemLongClick(entry.item) },
                    )
                }
            }
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

/** A row of the notes list: a section heading, or a note. [key] is stable across reorders. */
internal sealed interface NoteEntry {
    val key: Any
}

/** The "Pinned" / "Others" heading above a group of notes. */
internal data class NoteSection(@StringRes val title: Int) : NoteEntry {
    override val key: Any get() = "section-$title"
}

@Stable
internal data class NoteRow(val item: NoteListItem) : NoteEntry {
    override val key: Any get() = item.note.id
}

/**
 * [notes] (already ordered: pinned first, then by the chosen sort, archived last) as list rows. With
 * [sections], pinned notes sit under "Pinned", the rest under "Others" and, in a hidden tag's view, its
 * archived notes under "Archive"; with neither pinned nor archived notes there are no headings at all.
 */
internal fun noteEntries(notes: List<NoteListItem>, sections: Boolean): List<NoteEntry> {
    val rows = notes.map(::NoteRow)
    // An archived note is never pinned; should a stored one be both, it is listed once, as archived.
    val pinned = rows.filter { it.item.note.isPinned && !it.item.note.isArchived }
    val archived = rows.filter { it.item.note.isArchived }
    val others = rows - pinned.toSet() - archived.toSet()
    // Plain notes alone need no heading; next to pinned or archived ones they are "Others".
    if (!sections || (pinned.isEmpty() && archived.isEmpty())) return rows
    return buildList {
        if (pinned.isNotEmpty()) {
            add(NoteSection(R.string.notes_section_pinned))
            addAll(pinned)
        }
        if (others.isNotEmpty()) {
            add(NoteSection(R.string.notes_section_other))
            addAll(others)
        }
        if (archived.isNotEmpty()) {
            add(NoteSection(R.string.main_drawer_archive))
            addAll(archived)
        }
    }
}

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
