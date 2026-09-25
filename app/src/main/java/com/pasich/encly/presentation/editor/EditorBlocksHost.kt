package com.pasich.encly.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.pasich.encly.R
import com.pasich.encly.domain.enums.BottomSheetsOpenType
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.designsystem.editorBlockFrame
import com.pasich.encly.presentation.editor.blocks.HBlock
import com.pasich.encly.presentation.editor.blocks.LinkBlock
import com.pasich.encly.presentation.editor.blocks.ListBlock
import com.pasich.encly.presentation.editor.blocks.QuoteBlock
import com.pasich.encly.presentation.editor.blocks.SeparatorBlock
import com.pasich.encly.presentation.editor.blocks.TextBlock
import com.pasich.encly.presentation.editor.focus.BlockFocusRegistry
import com.pasich.encly.presentation.editor.focus.RegisterFocusRequester
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme

/** What the editor's blocks need from the screen that shows them. */
class EditorBlocksHost(
    val viewModel: EditNoteViewModel,
    val focusRegistry: BlockFocusRegistry,
    val onOpenSheet: (BottomSheetsOpenType, Block) -> Unit,
)

/**
 * The note's blocks as lazy items, one per block keyed by its id, so a long note composes and
 * lays out only what is on screen, and remembered field state, the focus target and the cursor
 * callback stay with their block when others are inserted, removed or moved around it. The
 * editor's [blocks] list is read here, so structural changes update the items. Below the last
 * block a tap area focuses (or adds) a trailing paragraph.
 */
fun LazyListScope.editorBlocks(
    host: EditorBlocksHost,
    blocks: List<Block>,
    isLocked: Boolean,
    editingLinkIds: Set<String>,
) {
    itemsIndexed(
        items = blocks,
        key = { _, block -> block.id },
        contentType = { _, block -> block::class.simpleName },
    ) { index, block ->
        val blockActions = remember(block, host) { BlockActionsImpl(block, host.viewModel, host.focusRegistry) }
        val callbacks = remember(block, host) {
            EditorBlockCallbacks(
                onFocus = { host.viewModel.onBlockFocused(block) },
                onFocusLost = { host.viewModel.onBlockFocusLost(block) },
                onOpenSheet = { type ->
                    host.viewModel.onBlockInteraction(block)
                    host.onOpenSheet(type, block)
                },
                onMove = { up -> host.viewModel.moveBlockOf(block, up) },
                onDelete = { host.viewModel.removeBlock(block, BlockRemoveAction.REMOVE) },
            )
        }
        EditorBlock(
            block = block,
            blockActions = blockActions,
            callbacks = callbacks,
            focusRegistry = host.focusRegistry,
            isLocked = isLocked,
            isEditingLink = block.id in editingLinkIds,
            isOnlyBlock = blocks.size == 1,
            position = BlockPosition(first = index == 0, last = index == blocks.lastIndex),
        )
    }

    item(key = TAP_AREA_KEY) {
        Spacer(
            Modifier
                .height(EnclyTheme.spacing.editorTapArea)
                .fillMaxWidth()
                .clickable(
                    enabled = !isLocked,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    host.viewModel.addBlockToEnd()
                },
        )
    }
}

private const val TAP_AREA_KEY = "editor-tap-area"

/** What a block reports back to the editor: it got or lost focus, or asked for one of its sheets. */
private class EditorBlockCallbacks(
    val onFocus: () -> Unit,
    val onFocusLost: () -> Unit,
    val onOpenSheet: (BottomSheetsOpenType) -> Unit,
    val onMove: (up: Boolean) -> Unit,
    val onDelete: () -> Unit,
)

/** Where a block is in the note: TalkBack offers to move it only where it can go. */
private data class BlockPosition(val first: Boolean, val last: Boolean)

@Composable
private fun EditorBlock(
    block: Block,
    blockActions: BlockActions,
    callbacks: EditorBlockCallbacks,
    focusRegistry: BlockFocusRegistry,
    isLocked: Boolean = false,
    isEditingLink: Boolean = false,
    isOnlyBlock: Boolean = false,
    position: BlockPosition = BlockPosition(first = true, last = true),
) {
    val focusRequester = remember { FocusRequester() }
    // A list registers its own target: it focuses its first or last item.
    if (block !is Block.ListBlock) RegisterFocusRequester(block.id, focusRegistry, focusRequester)
    // Only for the outline of the block being edited; the focus callback below is unchanged.
    var hasFocus by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            // Blocks carry an 8 dp frame of their own: they sit that much inside the gutter.
            .padding(horizontal = EnclyTheme.spacing.m)
            .onFocusChanged { focusState ->
                hasFocus = focusState.hasFocus
                // A list has one field per item: any of them focused counts as the list focused,
                // so a new block goes after the list and not after the block focused before it.
                val focused = if (block is Block.ListBlock) focusState.hasFocus else focusState.isFocused
                when {
                    focused -> callbacks.onFocus()
                    !focusState.hasFocus -> callbacks.onFocusLost()
                }
            }
            .editorBlockFrame(active = hasFocus && !isLocked, color = MaterialTheme.colorScheme.primary),
    ) {
        BlockContent(
            block = block,
            blockActions = blockActions,
            focusRequester = focusRequester,
            state = BlockContentState(
                isLocked = isLocked,
                isEditingLink = isEditingLink,
                showPlaceholder = !isLocked && (hasFocus || isOnlyBlock),
            ),
            onOpenSheet = callbacks.onOpenSheet,
            modifier = if (isLocked) Modifier else blockAccessibilityActions(callbacks, position, isOnlyBlock),
        )
    }
}

/**
 * TalkBack's actions on a block's field: move it up or down, delete it. Without them a TalkBack
 * user could only reach these through the toolbar's "Add block" row.
 */
@Composable
private fun blockAccessibilityActions(
    callbacks: EditorBlockCallbacks,
    position: BlockPosition,
    isOnlyBlock: Boolean,
): Modifier {
    val moveUp = stringResource(R.string.block_move_up)
    val moveDown = stringResource(R.string.block_move_down)
    val delete = stringResource(R.string.delete_block)
    val actions = buildList {
        if (!position.first) add(CustomAccessibilityAction(moveUp) { true.also { callbacks.onMove(true) } })
        if (!position.last) add(CustomAccessibilityAction(moveDown) { true.also { callbacks.onMove(false) } })
        if (!isOnlyBlock) add(CustomAccessibilityAction(delete) { true.also { callbacks.onDelete() } })
    }
    return Modifier.semantics { customActions = actions }
}

/** How a block shows: read-only, its link being edited, its placeholder. */
private class BlockContentState(val isLocked: Boolean, val isEditingLink: Boolean, val showPlaceholder: Boolean)

@Composable
private fun BlockContent(
    block: Block,
    blockActions: BlockActions,
    focusRequester: FocusRequester,
    state: BlockContentState,
    onOpenSheet: (BottomSheetsOpenType) -> Unit,
    modifier: Modifier = Modifier,
) {
    // [modifier] reaches the block's field (its TalkBack actions); the focus target joins it.
    val fieldModifier = modifier.focusRequester(focusRequester)
    val isLocked = state.isLocked
    when (block) {
        is Block.TextBlock -> TextBlock(
            block,
            blockActions,
            modifier = fieldModifier,
            isLocked = isLocked,
            showPlaceholder = state.showPlaceholder,
        )

        is Block.QuoteBlock -> QuoteBlock(
            block,
            blockActions,
            focusRequester,
            fieldModifier = modifier,
            isLocked = isLocked,
        )

        is Block.HBlock -> HBlock(block, blockActions, modifier = fieldModifier, isLocked = isLocked)

        is Block.ListBlock -> ListBlock(block, blockActions, fieldModifier = modifier, isLocked = isLocked)

        is Block.LinkBlock -> LinkBlock(
            block,
            blockActions = blockActions,
            onClick = { onOpenSheet(BottomSheetsOpenType.ACTION_LINK) },
            fieldModifier = fieldModifier,
            isLocked = isLocked,
            isEditing = state.isEditingLink,
        )

        is Block.SeparatorBlock -> SeparatorBlock(
            onClick = { onOpenSheet(BottomSheetsOpenType.ACTION_OTHER) },
            modifier = fieldModifier,
            isLocked = isLocked,
        )
    }
}
