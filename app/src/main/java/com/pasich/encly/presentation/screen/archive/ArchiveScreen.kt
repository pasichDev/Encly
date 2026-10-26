package com.pasich.encly.presentation.screen.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.presentation.components.tiles.NoteItem
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.NoteSkeleton
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.NoteActionsSheet
import com.pasich.encly.presentation.screen.NoteSheetState
import com.pasich.encly.presentation.viewmodel.ArchiveState
import com.pasich.encly.presentation.viewmodel.ArchiveViewModel
import com.pasich.encly.presentation.viewmodel.enclyViewModel
import com.pasich.encly.ui.theme.EnclyTheme

private const val SKELETON_CARDS = 3

/**
 * The archive: notes taken out of the main list and search, newest edit first. A tap opens the
 * note; a long-press opens the same actions sheet as the main list, with "Unarchive".
 */
@Composable
fun ArchiveScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: ArchiveViewModel = enclyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet = remember { NoteSheetState() }
    fun edit(noteId: Long) = navController.navigate("${NavRoutes.EditNoteRoute.name}/$noteId")

    NoteActionsSheet(
        sheet = sheet,
        observeNote = viewModel::observeNote,
        onEvent = viewModel::onEvent,
        onEdit = { edit(it.id) },
        onDuplicate = { navController.navigate("${NavRoutes.EditNoteRoute.name}/-1?copySource=${it.id}") },
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(
                title = stringResource(R.string.main_drawer_archive),
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        ArchivedNotes(
            state = state,
            onOpen = ::edit,
            onActions = sheet::open,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

/** The skeleton while the archive loads, the read error, "Archive is empty", or the note cards. */
@Composable
private fun ArchivedNotes(
    state: ArchiveState,
    onOpen: (Long) -> Unit,
    onActions: (NoteListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = EnclyTheme.spacing
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = spacing.xs, bottom = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
    ) {
        val load = state.notesLoad
        when {
            load is LoadState.Loading -> items(SKELETON_CARDS) {
                NoteSkeleton(modifier = Modifier.padding(horizontal = spacing.listGutter))
            }

            // A failed read is never shown as an empty archive.
            load is LoadState.Failed -> item {
                EnclyEmptyState(
                    icon = EnclyIcons.Archive,
                    title = load.error.title.asString(),
                    body = load.error.message.asString(),
                    error = true,
                )
            }

            state.notes.isEmpty() -> item {
                EnclyEmptyState(
                    icon = EnclyIcons.Archive,
                    title = stringResource(R.string.archive_empty_title),
                    body = stringResource(R.string.archive_empty_desc),
                )
            }

            else -> items(items = state.notes, key = { it.note.id }) { item ->
                NoteItem(
                    item = item,
                    modifier = Modifier
                        .padding(horizontal = spacing.listGutter)
                        .animateItem(),
                    onItemClick = { onOpen(it.id) },
                    onItemLongClick = { onActions(item) },
                )
            }
        }
    }
}
