package com.pasich.encly.presentation.screen.trash

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.clean_permanent_message
import com.pasich.encly.core.resources.clean_trash_title
import com.pasich.encly.core.resources.delete_forever
import com.pasich.encly.core.resources.delete_notes_confirm
import com.pasich.encly.core.resources.main_drawer_trash
import com.pasich.encly.core.resources.restore
import com.pasich.encly.core.resources.trash_delete_failed
import com.pasich.encly.core.resources.trash_empty
import com.pasich.encly.core.resources.trash_empty_confirm
import com.pasich.encly.core.resources.trash_keep
import com.pasich.encly.core.resources.trash_restore_failed
import com.pasich.encly.core.resources.trash_selected
import com.pasich.encly.domain.usecase.note.TRASH_RETENTION_DAYS
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyChoiceDialog
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.IconWithTooltip
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.openRoute
import com.pasich.encly.presentation.navigation.popBack
import com.pasich.encly.presentation.viewmodel.TrashListEvent
import com.pasich.encly.presentation.viewmodel.TrashOperationFailure
import com.pasich.encly.presentation.viewmodel.TrashViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class DialogTrashAction {
    DELETE_PERMANENTLY,
    CLEAN_ALL,
    DISABLE,
}

@Composable
fun TrashScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    trashViewModel: TrashViewModel = metroViewModel(),
) {
    val state by trashViewModel.state.collectAsStateWithLifecycle()
    var isDialogVisible by remember { mutableStateOf(DialogTrashAction.DISABLE) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(trashViewModel) {
        trashViewModel.operationFailures.collect { failure ->
            snackbarHostState.showSnackbar(getString(failure.message()))
        }
    }

    // Back (gesture or bar) first leaves the selection, then the screen.
    BackHandler(enabled = state.canCheck) { trashViewModel.onEvent(TrashListEvent.ClearSelection) }

    TrashConfirmDialog(
        action = isDialogVisible,
        counts = TrashCounts(all = state.notes.size, checked = state.checkedCount),
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
        snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        topBar = {
            TrashTopBar(
                checkedCount = state.checkedCount.takeIf { state.canCheck },
                hasNotes = state.notes.isNotEmpty(),
                actions = TrashBarActions(
                    onBack = { navController.popBack() },
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
                navController.openRoute("${NavRoutes.EditNoteRoute.name}/${note.id}?isReadTrashOnly=true")
            },
        )
    }
}

/** What the user is told when a trash operation fails. */
private fun TrashOperationFailure.message(): StringResource = when (this) {
    TrashOperationFailure.RESTORE -> Res.string.trash_restore_failed
    TrashOperationFailure.DELETE -> Res.string.trash_delete_failed
}

private class TrashBarActions(
    val onBack: () -> Unit,
    val onClearSelection: () -> Unit,
    val onRestore: () -> Unit,
    val onDeleteSelected: () -> Unit,
    val onEmptyTrash: () -> Unit,
)

/**
 * "Trash" with the text action "Empty"; while notes are selected ([checkedCount] set), "N
 * selected" with close, restore and delete forever.
 */
@Composable
private fun TrashTopBar(checkedCount: Int?, hasNotes: Boolean, actions: TrashBarActions) {
    val selecting = checkedCount != null
    EnclyTopBar(
        title = if (checkedCount != null) {
            pluralStringResource(Res.plurals.trash_selected, checkedCount, checkedCount)
        } else {
            stringResource(Res.string.main_drawer_trash)
        },
        onBack = actions.onBack.takeUnless { selecting },
        onClose = actions.onClearSelection.takeIf { selecting },
        actions = {
            // Swapped in one frame, like the title and the overflow menu beside them.
            if (selecting) {
                IconWithTooltip(EnclyIcons.Restore, stringResource(Res.string.restore), actions.onRestore)
                IconWithTooltip(EnclyIcons.Trash, stringResource(Res.string.delete_forever), actions.onDeleteSelected)
            }
            if (!selecting && hasNotes) {
                EnclyTextButton(
                    text = stringResource(Res.string.trash_empty),
                    onClick = actions.onEmptyTrash,
                    destructive = true,
                )
            }
        },
    )
}

/** How many notes the trash holds, and how many of them are selected. */
private class TrashCounts(val all: Int, val checked: Int)

/**
 * Confirms emptying the trash, or deleting the selected notes for good: how many notes go, then the red
 * answer beside "Keep".
 */
@Composable
private fun TrashConfirmDialog(
    action: DialogTrashAction,
    counts: TrashCounts,
    onConfirm: (DialogTrashAction) -> Unit,
    onDismiss: () -> Unit,
) {
    if (action == DialogTrashAction.DISABLE) return
    val cleanAll = action == DialogTrashAction.CLEAN_ALL
    EnclyChoiceDialog(
        title = if (cleanAll) {
            stringResource(Res.string.clean_trash_title)
        } else {
            pluralStringResource(Res.plurals.delete_notes_confirm, counts.checked, counts.checked)
        },
        text = if (cleanAll) {
            pluralStringResource(Res.plurals.trash_empty_confirm, counts.all, counts.all, TRASH_RETENTION_DAYS)
        } else {
            stringResource(Res.string.clean_permanent_message)
        },
        destructive = DialogAction(
            text = stringResource(if (cleanAll) Res.string.trash_empty else Res.string.delete_forever),
            onClick = { onConfirm(action) },
        ),
        keep = DialogAction(stringResource(Res.string.trash_keep), onDismiss),
    )
}
