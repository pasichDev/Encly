package com.pasich.encly.presentation.screen.editnote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.R
import com.pasich.encly.domain.enums.BottomSheetsOpenType
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.components.editNote.NoteTagSheet
import com.pasich.encly.presentation.designsystem.EnclyBackButton
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.dialogs.blocks.ActionBlockDialog
import com.pasich.encly.presentation.dialogs.blocks.ActionLinkBottomSheet
import com.pasich.encly.presentation.dialogs.blocks.ActionOtherBottomSheet
import com.pasich.encly.presentation.dialogs.blocks.SettingsBlockDialog
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.launch

/**
 * The sheet of one block (a link's, or a separator's), found by the block's id on every
 * composition, so it can never act on a position that undo, redo or a delete already changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorBlockSheets(
    sheet: BlockSheetState,
    canEdit: Boolean,
    viewModel: EditNoteViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val block = sheet.blockId?.let { id -> viewModel.blocks.firstOrNull { it.id == id } }
    if (block == null || sheet.type == BottomSheetsOpenType.NONE) return

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { sheet.close() }
    }

    fun onAction(action: ActionBlockDialog) {
        val link = (block as? Block.LinkBlock)?.block?.value?.url.orEmpty()
        when (action) {
            ActionBlockDialog.Delete -> viewModel.removeBlock(block, BlockRemoveAction.REMOVE)
            is ActionBlockDialog.Move -> viewModel.moveBlock(action.move == 1)
            ActionBlockDialog.OpenLink -> openNoteLink(context, link)
            ActionBlockDialog.CopyLink -> copyNoteLink(context, link)
            ActionBlockDialog.EditLink -> (block as? Block.LinkBlock)?.let(viewModel::editLink)
        }
        if (action !is ActionBlockDialog.Move) dismiss()
    }

    val settings = SettingsBlockDialog(
        block = block,
        isBottomSheetVisible = true,
        blockMove = viewModel.getMoveBlockState(),
        canEdit = canEdit,
    )
    if (sheet.type == BottomSheetsOpenType.ACTION_LINK) {
        ActionLinkBottomSheet(
            settings = settings,
            sheetState = sheetState,
            onDismiss = ::dismiss,
            onAction = ::onAction,
        )
    } else {
        ActionOtherBottomSheet(
            settings = settings,
            sheetState = sheetState,
            onDismiss = ::dismiss,
            onAction = ::onAction,
        )
    }
}

/** Chooses the note's tag; "Manage tags" leaves for the tags screen ([onManageTags]). */
@Composable
internal fun EditorTagSheet(
    onManageTags: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: EditNoteViewModel = hiltViewModel(),
    tagViewModel: TagListViewModel = hiltViewModel(),
) {
    val tagState by tagViewModel.state.collectAsState()
    val noteState by viewModel.state.collectAsState()
    NoteTagSheet(
        tags = tagState.listTags,
        selectedTagId = noteState.note.tagId,
        onSelect = { tagId ->
            viewModel.updateTagNote(tagId)
            onDismiss()
        },
        onManageTags = onManageTags,
        onDismiss = onDismiss,
    )
}

/** What the editor bar shows: trash actions, or tags, lock (unless the note is unreadable) and more. */
internal class EditorBarState(val isReadTrashOnly: Boolean, val lockEditor: Boolean, val canToggleLock: Boolean)

/**
 * The editor bar: back (saves and closes), then Edit tags, Lock editing and More actions. In
 * the trash: back, Restore and Delete.
 */
internal class EditorBarActions(
    val onBack: () -> Unit,
    val onRestore: () -> Unit,
    val onDelete: () -> Unit,
    val onTags: () -> Unit,
    val onMenu: () -> Unit,
    val onLockToggle: () -> Unit,
)

/** Pinned above the note; a hairline appears once the note scrolls under it. */
@Composable
internal fun EditorTopBar(
    state: EditorBarState,
    listState: LazyListState,
    actions: EditorBarActions,
    modifier: Modifier = Modifier,
) {
    val scrolled by remember(listState) { derivedStateOf { listState.canScrollBackward } }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, modifier = modifier) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EnclyTheme.spacing.topBarHeight)
                    .padding(horizontal = EnclyTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
            ) {
                EnclyBackButton(onClick = actions.onBack)
                Spacer(modifier = Modifier.weight(1f))
                if (state.isReadTrashOnly) {
                    EnclyTextButton(text = stringResource(R.string.restore), onClick = actions.onRestore)
                    EnclyTextButton(
                        text = stringResource(R.string.delete_from_trash),
                        onClick = actions.onDelete,
                        destructive = true,
                    )
                } else {
                    EditorBarButtons(state, actions)
                }
            }
            HorizontalDivider(
                thickness = EnclyTheme.spacing.hairline,
                color = if (scrolled) colors.outlineVariant else colors.surface,
            )
        }
    }
}

@Composable
private fun EditorBarButtons(state: EditorBarState, actions: EditorBarActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs)) {
        if (state.canToggleLock) {
            IconButton(onClick = actions.onTags, enabled = !state.lockEditor) {
                Icon(EnclyIcons.Tag, contentDescription = stringResource(R.string.edit_tags))
            }
            // A toggle, so TalkBack announces whether editing is locked.
            IconToggleButton(
                checked = state.lockEditor,
                onCheckedChange = { actions.onLockToggle() },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = colors.onSurface,
                    checkedContentColor = colors.primary,
                ),
            ) {
                Icon(
                    if (state.lockEditor) EnclyIcons.Lock else EnclyIcons.LockOpen,
                    contentDescription = stringResource(R.string.editor_lock),
                )
            }
        }
        IconButton(onClick = actions.onMenu) {
            Icon(EnclyIcons.More, contentDescription = stringResource(R.string.more_options), tint = colors.onSurface)
        }
    }
}
