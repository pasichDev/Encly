package com.pasich.encly.presentation.components.editNote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.designsystem.EnclyEditorToolbar
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.presentation.designsystem.EnclyToolbarRule
import com.pasich.encly.presentation.designsystem.ToolStyle
import com.pasich.encly.presentation.editor.BlockToolButton
import com.pasich.encly.presentation.editor.mainBlockTools
import com.pasich.encly.presentation.editor.moreBlockTools
import com.pasich.encly.presentation.editor.state.toolType
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.launch

/**
 * The editor's formatting toolbar, pinned to the bottom above the keyboard (design spec §3.3):
 * the filled "Add block", then the block tools, the one of the block the user works on marked
 * active. "Add block" swaps in a paragraph and every heading level, undo and redo, and moving
 * or deleting the block. In simple editing only undo and redo are offered.
 */
@Composable
fun NoteBottomBar(
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = hiltViewModel(),
    simpleEdit: Boolean = false,
) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    // Re-evaluated when the block the user works on or the blocks (their order) change.
    val interactedBlockId by viewModel.interactedBlockId.collectAsState()
    val activeTool by remember(viewModel) {
        derivedStateOf { viewModel.blocks.firstOrNull { it.id == interactedBlockId }?.toolType() }
    }
    val canMoveUp by remember(viewModel) {
        derivedStateOf { interactedBlockId.let { viewModel.canMoveBlock(up = true) } }
    }
    val canMoveDown by remember(viewModel) {
        derivedStateOf { interactedBlockId.let { viewModel.canMoveBlock(up = false) } }
    }
    val canDelete by remember(viewModel) {
        derivedStateOf { interactedBlockId.let { viewModel.canRemoveInteractedBlock() } }
    }
    val blockFocused = viewModel.focusedBlockId.collectAsState().value != null
    val history = HistoryTools(
        onUndo = if (canUndo) viewModel::undo else null,
        onRedo = if (canRedo) viewModel::redo else null,
    )

    EnclyEditorToolbar(modifier = modifier.imePadding()) {
        when {
            simpleEdit -> ToolRow(spread = false) {
                HistoryButtons(history)
                Spacer(Modifier.weight(1f))
                KeyboardToggle(blockFocused = blockFocused, onShow = viewModel::focusWorkingBlock)
            }

            else -> AnimatedContent(
                targetState = showMore,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "toolbar",
            ) { more ->
                if (more) {
                    MoreTools(
                        history = history,
                        blockTools = BlockMoveTools(
                            onMoveUp = if (canMoveUp) ({ viewModel.moveBlock(up = true) }) else null,
                            onMoveDown = if (canMoveDown) ({ viewModel.moveBlock(up = false) }) else null,
                            onDelete = if (canDelete) viewModel::removeInteractedBlock else null,
                        ),
                        onApply = { viewModel.applyTool(it, exact = true) },
                        onClose = { showMore = false },
                    )
                } else {
                    MainTools(
                        activeTool = activeTool,
                        onApply = { viewModel.applyTool(it) },
                        onMore = { showMore = true },
                        keyboard = KeyboardToggleState(blockFocused, viewModel::focusWorkingBlock),
                    )
                }
            }
        }
    }
}

/** Undo and redo; a null action shows its button disabled. */
private class HistoryTools(val onUndo: (() -> Unit)?, val onRedo: (() -> Unit)?)

/** Moving and deleting the block the user works on; a null action shows its button disabled. */
private class BlockMoveTools(val onMoveUp: (() -> Unit)?, val onMoveDown: (() -> Unit)?, val onDelete: (() -> Unit)?)

/** "Add block", then the block tools, the one of the block the user works on marked active. */
@Composable
private fun MainTools(
    activeTool: BlockType?,
    onApply: (BlockType) -> Unit,
    onMore: () -> Unit,
    keyboard: KeyboardToggleState,
) {
    ToolRow(spread = true) {
        EnclyToolButton(
            icon = EnclyIcons.PlusBold,
            contentDescription = stringResource(R.string.block_add),
            onClick = onMore,
            style = ToolStyle.FILLED,
        )
        mainBlockTools.forEach { tool ->
            BlockToolButton(tool = tool, active = tool.type == activeTool, onClick = { onApply(tool.type) })
        }
        EnclyToolbarRule()
        KeyboardToggle(blockFocused = keyboard.blockFocused, onShow = keyboard.onShow)
    }
}

/** What the keyboard toggle needs: whether a block's field has focus, and how to focus one. */
private class KeyboardToggleState(val blockFocused: Boolean, val onShow: () -> Unit)

/**
 * Hides the keyboard while it is up, and brings it back into the block the user was writing in
 * ([onShow] focuses it, the cursor where it was) while it is down.
 *
 * Built to work on every keyboard and skin:
 * - Whether the keyboard is up comes from the window insets, but a floating or split keyboard
 *   (Gboard floating, Samsung's) reports none. So a focused block ([blockFocused]) counts as the
 *   keyboard being up too, unless the insets just showed it being put away with the block still
 *   focused (Back on a docked keyboard).
 * - Hiding clears focus as well as asking the keyboard to hide: an editor without a focused field
 *   has no input connection, which closes any keyboard, however it handles hide requests.
 * - Showing focuses the working block first (a keyboard cannot open for nothing), then asks
 *   both Compose's controller and the window's insets controller, since some OEM keyboards and
 *   skins ignore one of the two.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyboardToggle(blockFocused: Boolean, onShow: () -> Unit) {
    val imeVisible = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val insets = remember(view) { view.context.findWindow()?.let { WindowCompat.getInsetsController(it, view) } }
    // True once the insets saw the keyboard go away while a block kept focus: it is down now,
    // even though the block is focused. A new focus or a show starts over.
    var dismissedWhileFocused by remember { mutableStateOf(false) }
    var wasImeVisible by remember { mutableStateOf(imeVisible) }
    LaunchedEffect(imeVisible, blockFocused) {
        when {
            !blockFocused || imeVisible -> dismissedWhileFocused = false
            wasImeVisible -> dismissedWhileFocused = true
        }
        wasImeVisible = imeVisible
    }
    val shown = imeVisible || (blockFocused && !dismissedWhileFocused)
    EnclyToolButton(
        icon = if (shown) EnclyIcons.KeyboardHide else EnclyIcons.Keyboard,
        contentDescription = stringResource(if (shown) R.string.keyboard_hide else R.string.keyboard_show),
        onClick = {
            if (shown) {
                keyboard?.hide()
                insets?.hide(WindowInsetsCompat.Type.ime())
                focusManager.clearFocus()
            } else {
                dismissedWhileFocused = false
                // A field that still has focus reopens its input connection when focused again.
                if (blockFocused) focusManager.clearFocus()
                onShow()
                scope.launch {
                    // Once the field has taken focus (the next frames), in case the keyboard did
                    // not come up on its own.
                    repeat(SHOW_AFTER_FRAMES) { withFrameNanos { } }
                    keyboard?.show()
                    insets?.show(WindowInsetsCompat.Type.ime())
                }
            }
        },
    )
}

/** Frames to wait for the focused field before asking the keyboard to show. */
private const val SHOW_AFTER_FRAMES = 2

private tailrec fun Context.findWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> null
}

/**
 * One row of tools across the toolbar's width: [spread] evenly (`SpaceBetween`) when they fit,
 * scrolling sideways on a screen too narrow for them.
 */
@Composable
private fun ToolRow(spread: Boolean, content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (spread) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.spacedBy(EnclyTheme.spacing.xxs)
            },
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = maxWidth),
            content = content,
        )
    }
}

@Composable
private fun HistoryButtons(history: HistoryTools) {
    EnclyToolButton(
        icon = EnclyIcons.Undo,
        contentDescription = stringResource(R.string.undo),
        onClick = history.onUndo,
    )
    EnclyToolButton(
        icon = EnclyIcons.Redo,
        contentDescription = stringResource(R.string.redo),
        onClick = history.onRedo,
    )
}

/** Behind "Add block": close, paragraph and heading levels, undo and redo, move and delete. */
@Composable
private fun MoreTools(
    history: HistoryTools,
    blockTools: BlockMoveTools,
    onApply: (BlockType) -> Unit,
    onClose: () -> Unit,
) {
    ToolRow(spread = false) {
        EnclyToolButton(icon = EnclyIcons.Close, contentDescription = stringResource(R.string.close), onClick = onClose)
        moreBlockTools.forEach { tool ->
            BlockToolButton(
                tool = tool,
                active = false,
                onClick = {
                    onApply(tool.type)
                    onClose()
                },
            )
        }
        EnclyToolbarRule()
        HistoryButtons(history)
        EnclyToolbarRule()
        EnclyToolButton(
            icon = EnclyIcons.ArrowUp,
            contentDescription = stringResource(R.string.block_move_up),
            onClick = blockTools.onMoveUp,
        )
        EnclyToolButton(
            icon = EnclyIcons.ArrowDown,
            contentDescription = stringResource(R.string.block_move_down),
            onClick = blockTools.onMoveDown,
        )
        EnclyToolButton(
            icon = EnclyIcons.Trash,
            contentDescription = stringResource(R.string.delete_block),
            onClick = blockTools.onDelete,
        )
    }
}
