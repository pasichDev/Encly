package com.pasich.encly.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.pasich.encly.presentation.editor.focus.rememberBlockFocusRegistry
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme
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
        // Blocks carry an 8 dp frame of their own: the column sits that much inside the gutter.
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = modifier.fillMaxSize().padding(horizontal = EnclyTheme.spacing.m).let { base ->
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
                        onFocusLost = { viewModel.onBlockFocusLost(block) },
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
                .height(EnclyTheme.spacing.editorTapArea)
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

/** What a block reports back to the editor: it got or lost focus, or asked for one of its sheets. */
private class EditorBlockCallbacks(
    val onFocus: () -> Unit,
    val onFocusLost: () -> Unit,
    val onOpenSheet: (BottomSheetsOpenType) -> Unit,
)

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
    // Only for the outline of the block being edited; the focus callback below is unchanged.
    var hasFocus by remember { mutableStateOf(false) }

    fun openSheet(type: BottomSheetsOpenType) = callbacks.onOpenSheet(type)

    Box(
        modifier = Modifier
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
