package com.pasich.encly.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.focus.BlockFocusRegistry
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.theme.editorBlockFrame
import com.pasich.encly.editor.theme.frameInset
import com.pasich.encly.editor.ui.blocks.HBlock
import com.pasich.encly.editor.ui.blocks.LinkBlock
import com.pasich.encly.editor.ui.blocks.ListBlock
import com.pasich.encly.editor.ui.blocks.QuoteBlock
import com.pasich.encly.editor.ui.blocks.SeparatorBlock
import com.pasich.encly.editor.ui.blocks.TextBlock
import com.pasich.encly.editor.ui.components.ContextEntry
import com.pasich.encly.editor.ui.components.EditorContextMenu
import com.pasich.encly.editor.ui.sheets.BlockSheetKind
import kotlinx.coroutines.flow.MutableStateFlow

/** What the editor's blocks need from the screen that shows them. */
public class EditorBlocksHost(
    internal val controller: EditorController,
    internal val focusRegistry: BlockFocusRegistry,
    internal val highlights: FindHighlights,
    internal val onOpenSheet: (BlockSheetKind, Block) -> Unit,
)

/**
 * The note's blocks as lazy items, one per block keyed by its id, so a long note composes and
 * lays out only what is on screen, and remembered field state, the focus target and the cursor
 * callback stay with their block when others are inserted, removed or moved around it. The
 * editor's [blocks] list is read here, so structural changes update the items. Below the last
 * block a tap area focuses (or adds) a trailing paragraph; [afterBlocks], if given, is shown
 * between the last block and that area.
 */
public fun LazyListScope.editorBlocks(
    host: EditorBlocksHost,
    blocks: List<Block>,
    isLocked: Boolean,
    editingLinkIds: Set<String>,
    afterBlocks: (@Composable () -> Unit)? = null,
) {
    itemsIndexed(
        items = blocks,
        key = { _, block -> block.id },
        contentType = { _, block -> block::class.simpleName },
    ) { index, block ->
        val blockActions =
            remember(block, host) { BlockActionsImpl(block, host.controller, host.focusRegistry, host.highlights) }
        val callbacks = remember(block, host) {
            EditorBlockCallbacks(
                onFocus = { host.controller.onBlockFocused(block) },
                onInteract = { host.controller.onBlockInteraction(block) },
                onToggleLock = { host.controller.toggleChecklistLock(block) },
                onFocusLost = { host.controller.onBlockFocusLost(block) },
                onOpenSheet = { type ->
                    host.controller.onBlockInteraction(block)
                    host.onOpenSheet(type, block)
                },
                onMove = { up -> host.controller.moveBlockOf(block, up) },
                onDuplicate = { host.controller.duplicateBlock(block) },
                onDelete = { host.controller.removeBlock(block, BlockRemoveAction.REMOVE) },
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

    // Below the blocks, so the blocks keep their positions in the list.
    if (afterBlocks != null) item(key = AFTER_BLOCKS_KEY) { afterBlocks() }

    item(key = TAP_AREA_KEY) {
        val keyboardController = LocalSoftwareKeyboardController.current
        Spacer(
            Modifier
                .height(LocalEditorTheme.current.spacing.tapArea)
                .fillMaxWidth()
                .clickable(
                    enabled = !isLocked,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    host.controller.addBlockToEnd()
                    // The last field may still hold focus with the keyboard hidden: focusing it again shows nothing.
                    keyboardController?.show()
                },
        )
    }
}

private const val TAP_AREA_KEY = "editor-tap-area"
private const val AFTER_BLOCKS_KEY = "editor-after-blocks"

/** What a block reports back to the editor: it got or lost focus, or asked for one of its sheets. */
@Suppress("LongParameterList") // One callback per thing a block reports or is asked to do.
private class EditorBlockCallbacks(
    val onFocus: () -> Unit,
    val onInteract: () -> Unit,
    val onToggleLock: () -> Unit,
    val onFocusLost: () -> Unit,
    val onOpenSheet: (BlockSheetKind) -> Unit,
    val onMove: (up: Boolean) -> Unit,
    val onDuplicate: () -> Unit,
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
    position: BlockPosition,
    isLocked: Boolean = false,
    isEditingLink: Boolean = false,
    isOnlyBlock: Boolean = false,
) {
    val theme = LocalEditorTheme.current
    val focusRequester = remember { FocusRequester() }
    // A list registers its own target: it focuses its first or last item.
    if (block !is Block.ListBlock) RegisterFocusRequester(block.id, focusRegistry, focusRequester)
    // Whether the block is being edited: its placeholder shows then.
    var hasFocus by remember { mutableStateOf(false) }

    val checklist = (block as? Block.ListBlock)?.takeIf { it.blockType == BlockType.LIST_CHECK }
    val lockedFlow = remember(checklist) { checklist?.locked ?: MutableStateFlow(false) }
    val checklistLocked by lockedFlow.collectAsState()
    val commands = blockCommands(callbacks, position, isOnlyBlock, checklist?.let { checklistLocked })
    // The block's actions for TalkBack, for the keyboard (Ctrl / Cmd+Shift+Up and Down move it) and for a
    // right click on a desktop.
    EditorContextMenu(
        entries = { commands.map { ContextEntry(it.label, it.icon, it.destructive, it.run) } },
        enabled = !isLocked,
        // A separator and a link card have no text to select: the menu takes their right click. A text
        // field keeps its own (cut, copy, paste).
        claimPress = block.hasNoText(),
    ) {
        Box(
            modifier = Modifier
                .onPreviewKeyEvent { event -> moveBlockByKey(event, callbacks, position, isLocked) }
                // A touch anywhere in a list makes it the block the toolbar acts on, even where its text
                // cannot take focus (a locked checklist), so its lock can be undone from the toolbar.
                .then(if (block is Block.ListBlock) Modifier.markInteractionOn(callbacks.onInteract) else Modifier)
                // Blocks carry an 8 dp frame of their own: they sit that much inside the gutter.
                .padding(horizontal = theme.spacing.m)
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
                .editorBlockFrame(
                    theme.lineSpacing.frameInset.takeIf {
                        block is Block.TextBlock || block is Block.ListBlock || block is Block.QuoteBlock
                    },
                ),
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
                modifier = if (isLocked) Modifier else blockAccessibilityActions(commands),
            )
        }
    }
}

/** A separator and a link card have no text field of their own to keep a right click for. */
private fun Block.hasNoText() = this is Block.SeparatorBlock || this is Block.LinkBlock

/** Moves the focused block when [event] is the move chord and the block can go that way; true when it did. */
private fun moveBlockByKey(
    event: KeyEvent,
    callbacks: EditorBlockCallbacks,
    position: BlockPosition,
    isLocked: Boolean,
): Boolean {
    val up = blockMoveDirection(event)?.takeIf { !isLocked } ?: return false
    val canMove = if (up) !position.first else !position.last
    if (canMove) callbacks.onMove(up)
    return canMove
}

/** One thing to do with a block: what TalkBack offers and the context menu lists. */
private class BlockCommand(val label: String, val icon: ImageVector, val destructive: Boolean, val run: () -> Unit)

/**
 * The block's commands: move it up or down (only where it can go), duplicate it, delete it (not the only
 * block) and, for a checklist, lock or unlock it. Without them a TalkBack user could only reach these
 * through the toolbar's "Add block" row.
 */
@Composable
private fun blockCommands(
    callbacks: EditorBlockCallbacks,
    position: BlockPosition,
    isOnlyBlock: Boolean,
    checklistLocked: Boolean?,
): List<BlockCommand> {
    val strings = LocalEditorStrings.current
    val icons = LocalEditorTheme.current.icons
    return buildList {
        if (!position.first) {
            add(BlockCommand(strings.text(EditorString.BLOCK_MOVE_UP), icons.arrowUp, false) { callbacks.onMove(true) })
        }
        if (!position.last) {
            add(
                BlockCommand(strings.text(EditorString.BLOCK_MOVE_DOWN), icons.arrowDown, false) {
                    callbacks.onMove(false)
                },
            )
        }
        add(
            BlockCommand(strings.text(EditorString.BLOCK_DUPLICATE), icons.duplicate, false) {
                callbacks.onDuplicate()
            },
        )
        if (!isOnlyBlock) {
            add(BlockCommand(strings.text(EditorString.BLOCK_DELETE), icons.trash, true) { callbacks.onDelete() })
        }
        if (checklistLocked != null) {
            val lock = if (checklistLocked) EditorString.CHECKLIST_UNLOCK else EditorString.CHECKLIST_LOCK
            add(BlockCommand(strings.text(lock), icons.checklist, false) { callbacks.onToggleLock() })
        }
    }
}

/** TalkBack's actions on a block's field: its [commands]. */
private fun blockAccessibilityActions(commands: List<BlockCommand>): Modifier = Modifier.semantics {
    customActions = commands.map { command -> CustomAccessibilityAction(command.label) { true.also { command.run() } } }
}

/** How a block shows: read-only, its link being edited, its placeholder. */
private class BlockContentState(val isLocked: Boolean, val isEditingLink: Boolean, val showPlaceholder: Boolean)

@Composable
private fun BlockContent(
    block: Block,
    blockActions: BlockActions,
    focusRequester: FocusRequester,
    state: BlockContentState,
    onOpenSheet: (BlockSheetKind) -> Unit,
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
            onClick = { onOpenSheet(BlockSheetKind.LINK) },
            fieldModifier = fieldModifier,
            isLocked = isLocked,
            isEditing = state.isEditingLink,
        )

        // The only kind left (an else keeps Compose from adding a branch that can never run).
        else -> SeparatorBlock(
            onClick = { onOpenSheet(BlockSheetKind.OTHER) },
            modifier = fieldModifier,
            isLocked = isLocked,
        )
    }
}

/** Calls [onTouch] on every touch that lands in the block, whether or not a child handles it. */
private fun Modifier.markInteractionOn(onTouch: () -> Unit): Modifier = pointerInput(onTouch) {
    awaitPointerEventScope {
        while (true) {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onTouch()
        }
    }
}
