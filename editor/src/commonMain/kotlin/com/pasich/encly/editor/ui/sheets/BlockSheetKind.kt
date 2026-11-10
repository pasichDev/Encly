package com.pasich.encly.editor.ui.sheets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorClipboard
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.LocalLinkOpener
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.ui.EditorController
import kotlinx.coroutines.launch

/** The sheets a block can open: a saved link's, or the one of every other block (a separator's). */
public enum class BlockSheetKind { LINK, OTHER }

/**
 * The sheet of one block, found by the block's id on every composition, so it can never act on a
 * position that undo, redo or a delete already changed. Nothing shows while [kind] or [blockId] is
 * null or names no block. [canEdit] is false in a read-only editor: only what does not change the
 * note is offered. Copying a link goes to the provided `EditorClipboard` as sensitive text, opening
 * it to the provided `LinkOpener`. [onClose] is called once the sheet has slid away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun EditorBlockSheet(
    kind: BlockSheetKind?,
    blockId: String?,
    canEdit: Boolean,
    controller: EditorController,
    onClose: () -> Unit,
) {
    val strings = LocalEditorStrings.current
    val clipboard = LocalEditorClipboard.current
    val linkOpener = LocalLinkOpener.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val block = blockId?.let { id -> controller.blocks.firstOrNull { it.id == id } }
    if (block == null || kind == null) return

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
    }

    fun onAction(action: ActionBlockDialog) {
        val link = (block as? Block.LinkBlock)?.block?.value?.url.orEmpty()
        when (action) {
            ActionBlockDialog.Duplicate -> controller.duplicateBlock(block)
            ActionBlockDialog.Delete -> controller.removeBlock(block, BlockRemoveAction.REMOVE)
            is ActionBlockDialog.Move -> controller.moveBlock(action.move == 1)
            ActionBlockDialog.OpenLink -> linkOpener.open(link)
            ActionBlockDialog.CopyLink -> clipboard.copy(strings.text(EditorString.BLOCK_LINK), link, sensitive = true)
            ActionBlockDialog.EditLink -> (block as? Block.LinkBlock)?.let(controller::editLink)
        }
        if (action !is ActionBlockDialog.Move) dismiss()
    }

    val settings = SettingsBlockDialog(
        block = block,
        isBottomSheetVisible = true,
        blockMove = controller.interactedBlockPosition(),
        canEdit = canEdit,
    )
    if (kind == BlockSheetKind.LINK) {
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
