package com.pasich.encly.presentation.screen.trash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.clean_permanent_message
import com.pasich.encly.core.resources.clean_trash_message
import com.pasich.encly.core.resources.clean_trash_title
import com.pasich.encly.core.resources.delete_forever
import com.pasich.encly.core.resources.delete_notes_confirm
import com.pasich.encly.core.resources.empty_trash_action
import com.pasich.encly.core.resources.main_drawer_trash
import com.pasich.encly.core.resources.restore
import com.pasich.encly.core.resources.trash_selected
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyOverflowMenu
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.IconWithTooltip
import com.pasich.encly.presentation.designsystem.OverflowAction
import com.pasich.encly.presentation.dialogs.ConfirmDialog
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.TrashListEvent
import com.pasich.encly.presentation.viewmodel.TrashViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
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
            pluralStringResource(Res.plurals.trash_selected, checkedCount, checkedCount)
        } else {
            stringResource(Res.string.main_drawer_trash)
        },
        onBack = actions.onBack.takeUnless { selecting },
        onClose = actions.onClearSelection.takeIf { selecting },
        actions = {
            AnimatedVisibility(selecting) {
                IconWithTooltip(EnclyIcons.Restore, stringResource(Res.string.restore), actions.onRestore)
            }
            AnimatedVisibility(selecting) {
                IconWithTooltip(EnclyIcons.Trash, stringResource(Res.string.delete_forever), actions.onDeleteSelected)
            }
            if (!selecting && hasNotes) {
                EnclyOverflowMenu(
                    items = listOf(
                        OverflowAction(
                            text = stringResource(Res.string.empty_trash_action),
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
            stringResource(Res.string.clean_trash_title)
        } else {
            pluralStringResource(Res.plurals.delete_notes_confirm, checkedCount, checkedCount)
        },
        messageText = stringResource(
            if (cleanAll) Res.string.clean_trash_message else Res.string.clean_permanent_message,
        ),
        onConfirm = { onConfirm(action) },
        onDismiss = onDismiss,
        destructive = true,
    )
}
