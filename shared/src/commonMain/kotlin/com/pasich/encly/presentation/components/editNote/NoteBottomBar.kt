package com.pasich.encly.presentation.components.editNote

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
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
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.block_duplicate
import com.pasich.encly.core.resources.block_more_tools
import com.pasich.encly.core.resources.block_move_down
import com.pasich.encly.core.resources.block_move_up
import com.pasich.encly.core.resources.checklist_lock
import com.pasich.encly.core.resources.checklist_unlock
import com.pasich.encly.core.resources.close
import com.pasich.encly.core.resources.delete_block
import com.pasich.encly.core.resources.keyboard_hide
import com.pasich.encly.core.resources.keyboard_show
import com.pasich.encly.core.resources.redo
import com.pasich.encly.core.resources.undo
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.state.toolType
import com.pasich.encly.platform.LocalAppWindow
import com.pasich.encly.presentation.designsystem.EnclyEditorToolbar
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.presentation.designsystem.EnclyToolbarRule
import com.pasich.encly.presentation.designsystem.ToolStyle
import com.pasich.encly.presentation.effects.rememberImeVisible
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The editor's formatting toolbar, pinned to the bottom above the keyboard (design spec §3.3):
 * the filled "Add block", then the block tools, the one of the block the user works on marked
 * active, and undo and redo. The chevron at the start swaps in a paragraph, every heading level, a link
 * and a separator, and moving, duplicating or deleting the block. In simple editing only undo and redo
 * are offered.
 */
@Composable
fun NoteBottomBar(
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = metroViewModel(),
    simpleEdit: Boolean = false,
) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    // Re-evaluated when the block the user works on or the blocks (their order) change.
    val interactedBlockId by viewModel.interactedBlockId.collectAsState()
    val activeTool by remember(viewModel) {
        derivedStateOf { viewModel.blocks.firstOrNull { it.id == interactedBlockId }?.toolType() }
    }
    val checklistLock = rememberChecklistLock(viewModel, interactedBlockId)
    val blockFocused = viewModel.focusedBlockId.collectAsState().value != null
    val anchor = rememberKeyboardAnchor()
    // A change that replaces the focused field runs inside the anchor, so the keyboard stays up.
    val keepingKeyboard: (() -> Unit) -> Unit = { change ->
        anchor.around(viewModel.focusedBlockId.value, { id -> viewModel.blocks.any { it.id == id } }, change)
    }
    val history = rememberHistoryTools(viewModel, keepingKeyboard)
    val blockTools = rememberBlockMoveTools(viewModel, interactedBlockId, checklistLock, keepingKeyboard)
    EnclyEditorToolbar(modifier = modifier.imePadding()) {
        KeyboardAnchorField(anchor)
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
                        blockTools = blockTools,
                        onApply = toolApplier(checklistLock) { type ->
                            keepingKeyboard { viewModel.applyTool(type, exact = true) }
                        },
                        onClose = { showMore = false },
                    )
                } else {
                    MainTools(
                        activeTool = activeTool,
                        history = history,
                        onApply = toolApplier(checklistLock) { type -> keepingKeyboard { viewModel.applyTool(type) } },
                        onMore = { showMore = true },
                        keyboard = KeyboardToggleState(blockFocused, viewModel::focusWorkingBlock),
                    )
                }
            }
        }
    }
}

/** The block-tools toggle, the block tools (the active one marked), then undo and redo. */
@Composable
private fun MainTools(
    activeTool: BlockType?,
    history: HistoryTools,
    onApply: ((BlockType) -> Unit)?,
    onMore: () -> Unit,
    keyboard: KeyboardToggleState,
) {
    ToolRow(spread = true) {
        EnclyToolButton(
            icon = EnclyIcons.ChevronUp,
            contentDescription = stringResource(Res.string.block_more_tools),
            onClick = onMore,
            style = ToolStyle.FILLED,
        )
        // The filled toggle never touches a highlighted tool next to it, even when the row is tight.
        Spacer(Modifier.width(EnclyTheme.spacing.xs))
        mainBlockTools.forEach { tool ->
            BlockToolButton(
                tool = tool,
                active = tool.type == activeTool,
                onClick = onApply?.let { apply -> { apply(tool.type) } },
            )
        }
        EnclyToolbarRule()
        HistoryButtons(history)
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
    val imeVisible = rememberImeVisible()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val window = LocalAppWindow.current
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
        contentDescription = stringResource(if (shown) Res.string.keyboard_hide else Res.string.keyboard_show),
        onClick = {
            if (shown) {
                keyboard?.hide()
                window.hideKeyboard()
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
                    window.showKeyboard()
                }
            }
        },
    )
}

/** Frames to wait for the focused field before asking the keyboard to show. */
private const val SHOW_AFTER_FRAMES = 2

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
        contentDescription = stringResource(Res.string.undo),
        onClick = history.onUndo,
    )
    EnclyToolButton(
        icon = EnclyIcons.Redo,
        contentDescription = stringResource(Res.string.redo),
        onClick = history.onRedo,
    )
}

/** Behind the toggle: close, paragraph, heading levels, link and separator, move, duplicate and delete. */
@Composable
private fun MoreTools(blockTools: BlockMoveTools, onApply: ((BlockType) -> Unit)?, onClose: () -> Unit) {
    ToolRow(spread = false) {
        EnclyToolButton(
            icon = EnclyIcons.Close,
            contentDescription = stringResource(Res.string.close),
            onClick = onClose,
        )
        moreBlockTools.forEach { tool ->
            BlockToolButton(
                tool = tool,
                active = false,
                onClick = onApply?.let { apply ->
                    {
                        apply(tool.type)
                        onClose()
                    }
                },
            )
        }
        EnclyToolbarRule()
        EnclyToolButton(
            icon = EnclyIcons.ArrowUp,
            contentDescription = stringResource(Res.string.block_move_up),
            onClick = blockTools.onMoveUp,
        )
        EnclyToolButton(
            icon = EnclyIcons.ArrowDown,
            contentDescription = stringResource(Res.string.block_move_down),
            onClick = blockTools.onMoveDown,
        )
        EnclyToolButton(
            icon = EnclyIcons.Duplicate,
            contentDescription = stringResource(Res.string.block_duplicate),
            onClick = blockTools.onDuplicate,
        )
        blockTools.lock?.let { lock ->
            EnclyToolButton(
                icon = if (lock.locked) EnclyIcons.LockOpen else EnclyIcons.Lock,
                contentDescription = stringResource(
                    if (lock.locked) Res.string.checklist_unlock else Res.string.checklist_lock,
                ),
                onClick = lock.onToggle,
            )
        }
        EnclyToolButton(
            icon = EnclyIcons.Trash,
            contentDescription = stringResource(Res.string.delete_block),
            onClick = blockTools.onDelete,
        )
    }
}
