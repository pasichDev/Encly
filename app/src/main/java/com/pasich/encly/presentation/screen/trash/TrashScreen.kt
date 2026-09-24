package com.pasich.encly.presentation.screen.trash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
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
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyTopBar
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
    var isDialogVisible by remember { mutableStateOf<DialogTrashAction>(DialogTrashAction.DISABLE) }

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
                canCheck = state.canCheck,
                checkedCount = state.checkedCount,
                hasNotes = state.notes.isNotEmpty(),
                actions = TrashBarActions(
                    onBack = { navController.popBackStack() },
                    onRestore = { trashViewModel.onEvent(TrashListEvent.RestoreNotes()) },
                    onDelete = {
                        isDialogVisible = if (state.canCheck) {
                            DialogTrashAction.DELETE_PERMANENTLY
                        } else {
                            DialogTrashAction.CLEAN_ALL
                        }
                    },
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // TrashNotesList resolves the same back-stack-scoped TrashViewModel itself.
            TrashNotesList(onItemClick = { _, note ->
                navController.navigate("${NavRoutes.EditNoteRoute.name}/${note.id}?isReadTrashOnly=true")
            })
        }
    }
}

private class TrashBarActions(val onBack: () -> Unit, val onRestore: () -> Unit, val onDelete: () -> Unit)

/** "Trash", or the number of selected notes; restore (with a selection) and delete or empty. */
@Composable
private fun TrashTopBar(canCheck: Boolean, checkedCount: Int, hasNotes: Boolean, actions: TrashBarActions) {
    EnclyTopBar(
        title = if (canCheck) {
            stringResource(id = R.string.checked_count, checkedCount)
        } else {
            stringResource(R.string.main_drawer_trash)
        },
        onBack = actions.onBack,
        actions = {
            AnimatedVisibility(canCheck) {
                IconButton(onClick = actions.onRestore) {
                    Icon(Lucide.ArchiveRestore, contentDescription = stringResource(R.string.restore))
                }
            }
            IconButton(enabled = hasNotes, onClick = actions.onDelete) {
                Icon(
                    Lucide.Trash2,
                    contentDescription = stringResource(
                        if (canCheck) R.string.delete_from_trash else R.string.clean_trash_title,
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
