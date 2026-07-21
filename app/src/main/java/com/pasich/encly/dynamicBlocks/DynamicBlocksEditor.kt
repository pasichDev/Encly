package com.pasich.encly.dynamicBlocks

import com.pasich.encly.core.AppLogger
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.domain.enums.BottomSheetsOpenType
import com.pasich.encly.dynamicBlocks.blocks.HBlock
import com.pasich.encly.dynamicBlocks.blocks.LinkBlock
import com.pasich.encly.dynamicBlocks.blocks.ListBlock
import com.pasich.encly.dynamicBlocks.blocks.QuoteBlock
import com.pasich.encly.dynamicBlocks.blocks.SeparatorBlock
import com.pasich.encly.dynamicBlocks.blocks.TextBlock
import com.pasich.encly.dynamicBlocks.focus.RegisterFocusRequester
import com.pasich.encly.dynamicBlocks.focus.centralizedFocusManagement
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DynamicBlocksEditor(
    viewModel: EditNoteViewModel = hiltViewModel(),
    bottomSheetsOpen: (BottomSheetsOpenType, Block, Int) -> Unit,
    isLocked: Boolean = false,
    enableScroll: Boolean = true,
    useNewFocusSystem: Boolean = false,
) {
    val scrollState = rememberScrollState()

    // Pass the focus manager to the ViewModel
    viewModel.focusManager.let { vmFocusManager ->
        // Synchronize with the ViewModel
    }

    val blocks = viewModel.blocks
    val currentFocusIndex by viewModel.currentFocusIndex.collectAsState()
    val isEditMode by viewModel.isBlockEditMode.collectAsState()

    AppLogger.d(
        "DynamicBlocksEditor",
        "DynamicBlocksEditor composed: blocks.size=${blocks.size}, isLocked=$isLocked, useNewFocusSystem=$useNewFocusSystem",
    )
    blocks.forEachIndexed { i, b ->
        AppLogger.d("DynamicBlocksEditor", "Block $i: ${b::class.simpleName}")
    }

    // Track focus changes and request focus accordingly
    LaunchedEffect(currentFocusIndex) {
        if (currentFocusIndex != -1 && currentFocusIndex < blocks.size) {
            snapshotFlow {
                viewModel.focusManager.isFocusReady(currentFocusIndex)
            }.collect { isReady ->
                if (isReady && !viewModel.focusManager.shouldIgnoreFocus) {
                    viewModel.focusManager.setFocus(currentFocusIndex)
                    AppLogger.d("DynamicBlocksEditor", "New focus: block at index $currentFocusIndex")
                }
            }
        }
    }

    val isLockBlockEdit = isEditMode || isLocked

    /**
     * In the note editor, if focus is on a text block, offer a list of actions (opening the edit dialog)
     */

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).let { modifier ->
                if (enableScroll) {
                    modifier.verticalScroll(scrollState)
                } else {
                    modifier
                }
            },
    ) {
        blocks.forEachIndexed { index, block ->
            AppLogger.d("DynamicBlocksEditor", "Rendering block $index: ${block::class.simpleName}")

            val focusRequester = remember { FocusRequester() }
            // Register the FocusRequester in the centralized manager (only for the legacy system)
            if (!useNewFocusSystem) {
                RegisterFocusRequester(index, viewModel.focusManager, focusRequester)
            }
            Box(
                modifier = if (useNewFocusSystem) {
                    Modifier.centralizedFocusManagement(
                            index = index,
                            onFocusChanged = { idx, focusState ->
                                if (focusState.isFocused && block !is Block.ListBlock) {
                                    viewModel.setLastInteractionIndex(idx)
                                    viewModel.updateCurrentFocusIndex(idx)
                                }
                            },
                            enableAutoScroll = true,
                            scrollDelay = 150L, // Delay for the keyboard
                        )
                } else {
                    Modifier.onFocusChanged { focusState ->
                            if (focusState.isFocused && block !is Block.ListBlock) {
                                // Notify that the block received focus, but do not set focus programmatically
                                // to avoid recursion
                                viewModel.setLastInteractionIndex(index)
                                // Update only the focus index state without calling requestFocus
                                viewModel.updateCurrentFocusIndex(index)
                            }
                        }
                },
            ) {
                when (block) {
                    is Block.TextBlock -> TextBlock(
                        block = block,
                        blockActions = BlockActionsImpl(block, index, viewModel),
                        modifier = Modifier.focusRequester(focusRequester),
                        isLocked = isLockBlockEdit,
                        index = index,
                    )

                    is Block.QuoteBlock -> QuoteBlock(
                        block,
                        blockActions = BlockActionsImpl(block, index, viewModel),
                        Modifier.focusRequester(focusRequester),
                        isLocked = isLockBlockEdit,
                        index = index,
                    )

                    is Block.LinkBlock -> LinkBlock(
                        block,
                        blockActions = BlockActionsImpl(block, index, viewModel),
                        onClick = {
                            viewModel.setLastInteractionIndex(index)
                            bottomSheetsOpen(
                                BottomSheetsOpenType.ACTION_LINK,
                                block,
                                index,
                            )
                        },
                        Modifier.focusRequester(focusRequester),
                        isLocked = isLockBlockEdit,
                    )

                    is Block.HBlock -> HBlock(
                        block,
                        blockActions = BlockActionsImpl(block, index, viewModel),
                        Modifier.focusRequester(focusRequester),
                        isLocked = isEditMode || isLocked,
                        index = index,
                    )

                    is Block.SeparatorBlock -> SeparatorBlock(
                        Modifier.focusRequester(focusRequester),
                        onClick = {
                            viewModel.setLastInteractionIndex(index)
                            bottomSheetsOpen(
                                BottomSheetsOpenType.ACTION_OTHER,
                                block,
                                index,
                            )
                        },
                    )


                    is Block.ListBlock -> ListBlock(
                        block,
                        blockActions = BlockActionsImpl(block, index, viewModel),
                        isLocked = isEditMode || isLocked,
                        index = index,
                    )
                }
            }
        }

        Spacer(
            Modifier
                .height(200.dp)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    viewModel.addBlockToEnd()
                },
        )
    }
}
