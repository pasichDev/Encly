package com.pasich.encly.presentation.screen.trash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyOverflowMenu
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.OverflowAction
import com.pasich.encly.presentation.dialogs.ConfirmDialog
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.TrashListEvent
import com.pasich.encly.presentation.viewmodel.TrashViewModel

enum class DialogTrashAction {
    DELETE_PERMANENTLY,
    CLEAN_ALL,
    DISABLE,
}

@Composable
fun TrashScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    trashViewModel: TrashViewModel = hiltViewModel(),
) {
    val state by trashViewModel.state.collectAsStateWithLifecycle()
    var isDialogVisible by remember { mutableStateOf(DialogTrashAction.DISABLE) }

    // Back (gesture or bar) first leaves the selection, then the screen.
    BackHandler(enabled = state.canCheck) { trashViewModel.onEvent(TrashListEvent.ClearSelection) }

    TrashConfirmDialog(
        action = isDialogVisible,
        checkedCount = state.checkedCount,
        onConfirm = { action ->
            when (action) {
                DialogTrashAction.CLEAN_ALL -> trashViewModel.onEvent(TrashListEvent.CleanAll())
                DialogTrashAction.DELETE_PERMANENTLY -> trashViewModel.onEvent(TrashListEvent.CleanNotes())
                DialogTrashAction.DISABLE -> {}
            }
            isDialogVisible = DialogTrashAction.DISABLE
        },
        onDismiss = { isDialogVisible = DialogTrashAction.DISABLE },
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TrashTopBar(
                checkedCount = state.checkedCount.takeIf { state.canCheck },
                hasNotes = state.notes.isNotEmpty(),
                actions = TrashBarActions(
                    onBack = { navController.popBackStack() },
                    onClearSelection = { trashViewModel.onEvent(TrashListEvent.ClearSelection) },
                    onRestore = { trashViewModel.onEvent(TrashListEvent.RestoreNotes()) },
                    onDeleteSelected = { isDialogVisible = DialogTrashAction.DELETE_PERMANENTLY },
                    onEmptyTrash = { isDialogVisible = DialogTrashAction.CLEAN_ALL },
                ),
            )
        },
    ) { padding ->
        // TrashNotesList resolves the same back-stack-scoped TrashViewModel itself.
        TrashNotesList(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onItemClick = { _, note ->
                navController.navigate("${NavRoutes.EditNoteRoute.name}/${note.id}?isReadTrashOnly=true")
            },
        )
    }
}

private class TrashBarActions(
    val onBack: () -> Unit,
    val onClearSelection: () -> Unit,
    val onRestore: () -> Unit,
    val onDeleteSelected: () -> Unit,
    val onEmptyTrash: () -> Unit,
)

/**
 * "Trash" with an overflow "Empty trash"; while notes are selected ([checkedCount] set), "N
 * selected" with close, restore and delete forever.
 */
@Composable
private fun TrashTopBar(checkedCount: Int?, hasNotes: Boolean, actions: TrashBarActions) {
    val selecting = checkedCount != null
    EnclyTopBar(
        title = if (checkedCount != null) {
            pluralStringResource(R.plurals.trash_selected, checkedCount, checkedCount)
        } else {
            stringResource(R.string.main_drawer_trash)
        },
        onBack = actions.onBack.takeUnless { selecting },
        onClose = actions.onClearSelection.takeIf { selecting },
        actions = {
            AnimatedVisibility(selecting) {
                IconButton(onClick = actions.onRestore) {
                    Icon(EnclyIcons.Restore, contentDescription = stringResource(R.string.restore))
                }
            }
            AnimatedVisibility(selecting) {
                IconButton(onClick = actions.onDeleteSelected) {
                    Icon(EnclyIcons.Trash, contentDescription = stringResource(R.string.delete_forever))
                }
            }
            if (!selecting && hasNotes) {
                EnclyOverflowMenu(
                    items = listOf(
                        OverflowAction(
                            text = stringResource(R.string.empty_trash_action),
                            onClick = actions.onEmptyTrash,
                            destructive = true,
                        ),
                    ),
                )
            }
        },
    )
}

/** Confirms emptying the trash, or deleting the selected notes for good. */
@Composable
private fun TrashConfirmDialog(
    action: DialogTrashAction,
    checkedCount: Int,
    onConfirm: (DialogTrashAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val cleanAll = action == DialogTrashAction.CLEAN_ALL
    ConfirmDialog(
        isVisible = action != DialogTrashAction.DISABLE,
        titleText = if (cleanAll) {
            stringResource(R.string.clean_trash_title)
        } else {
            pluralStringResource(R.plurals.delete_notes_confirm, checkedCount, checkedCount)
        },
        messageText = stringResource(if (cleanAll) R.string.clean_trash_message else R.string.clean_permanent_message),
        onConfirm = { onConfirm(action) },
        onDismiss = onDismiss,
        destructive = true,
    )
}
