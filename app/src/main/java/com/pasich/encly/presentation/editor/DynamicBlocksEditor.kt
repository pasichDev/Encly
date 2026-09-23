package com.pasich.encly.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.domain.enums.BottomSheetsOpenType
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.blocks.HBlock
import com.pasich.encly.presentation.editor.blocks.LinkBlock
import com.pasich.encly.presentation.editor.blocks.ListBlock
import com.pasich.encly.presentation.editor.blocks.QuoteBlock
import com.pasich.encly.presentation.editor.blocks.SeparatorBlock
import com.pasich.encly.presentation.editor.blocks.TextBlock
import com.pasich.encly.presentation.editor.focus.BlockFocusRegistry
import com.pasich.encly.presentation.editor.focus.RegisterFocusRequester
import com.pasich.encly.presentation.editor.focus.rememberBlockFocusRegistry
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DynamicBlocksEditor(
    bottomSheetsOpen: (BottomSheetsOpenType, Block, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = hiltViewModel(),
    isLocked: Boolean = false,
    enableScroll: Boolean = true,
) {
    val scrollState = rememberScrollState()
    val focusRegistry = rememberBlockFocusRegistry(viewModel.blocks)
    val editingLinkIds by viewModel.editingLinkIds.collectAsState()

    // The ViewModel says which block to focus; the registry, which holds the fields'
    // FocusRequesters, does it once that block is composed. A newer request replaces an older one.
    LaunchedEffect(viewModel, focusRegistry) {
        viewModel.focusRequests.collectLatest(focusRegistry::focusWhenComposed)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp).let { base ->
            if (enableScroll) base.verticalScroll(scrollState) else base
        },
    ) {
        viewModel.blocks.forEach { block ->
            // Keyed by the block, not its position: remembered field state, the focus target
            // and the cursor callback stay with this block when others are inserted, removed
            // or moved around it.
            key(block.id) {
                val blockActions = remember(block, viewModel, focusRegistry) {
                    BlockActionsImpl(block, viewModel, focusRegistry)
                }
                val callbacks = remember(block, viewModel, bottomSheetsOpen) {
                    EditorBlockCallbacks(
                        onFocus = { viewModel.onBlockFocused(block) },
                        onOpenSheet = { type ->
                            viewModel.onBlockInteraction(block)
                            bottomSheetsOpen(type, block, viewModel.indexOfBlock(block))
                        },
                    )
                }
                EditorBlock(
                    block = block,
                    blockActions = blockActions,
                    index = viewModel.indexOfBlock(block),
                    callbacks = callbacks,
                    focusRegistry = focusRegistry,
                    isLocked = isLocked,
                    isEditingLink = block.id in editingLinkIds,
                )
            }
        }

        Spacer(
            Modifier
                .height(200.dp)
                .fillMaxWidth()
                .clickable(
                    enabled = !isLocked,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    viewModel.addBlockToEnd()
                },
        )
    }
}

/** What a block reports back to the editor: it got focus, or asked for one of its sheets. */
private class EditorBlockCallbacks(val onFocus: () -> Unit, val onOpenSheet: (BottomSheetsOpenType) -> Unit)

@Composable
private fun EditorBlock(
    block: Block,
    blockActions: BlockActions,
    index: Int,
    callbacks: EditorBlockCallbacks,
    focusRegistry: BlockFocusRegistry,
    isLocked: Boolean = false,
    isEditingLink: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    RegisterFocusRequester(block.id, focusRegistry, focusRequester)

    fun openSheet(type: BottomSheetsOpenType) = callbacks.onOpenSheet(type)

    Box(
        modifier = Modifier.onFocusChanged { focusState ->
            // A list reports its items itself (see ListBlock).
            if (focusState.isFocused && block !is Block.ListBlock) callbacks.onFocus()
        },
    ) {
        val fieldModifier = Modifier.focusRequester(focusRequester)
        when (block) {
            is Block.TextBlock -> TextBlock(block, blockActions, modifier = fieldModifier, isLocked = isLocked)

            is Block.QuoteBlock -> QuoteBlock(block, blockActions, focusRequester, isLocked = isLocked)

            is Block.HBlock -> HBlock(block, blockActions, modifier = fieldModifier, isLocked = isLocked)

            is Block.ListBlock -> ListBlock(block, blockActions, index = index, isLocked = isLocked)

            is Block.LinkBlock -> LinkBlock(
                block,
                blockActions = blockActions,
                onClick = { openSheet(BottomSheetsOpenType.ACTION_LINK) },
                modifier = fieldModifier,
                isLocked = isLocked,
                isEditing = isEditingLink,
            )

            is Block.SeparatorBlock -> SeparatorBlock(
                onClick = { openSheet(BottomSheetsOpenType.ACTION_OTHER) },
                modifier = fieldModifier,
                isLocked = isLocked,
            )
        }
    }
}
