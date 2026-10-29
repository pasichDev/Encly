package com.pasich.encly.presentation.components.editNote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import kotlinx.coroutines.flow.MutableStateFlow

// What the editor toolbar's buttons do, as state the toolbar only has to show.

@Composable
internal fun rememberHistoryTools(viewModel: EditNoteViewModel, keepingKeyboard: (() -> Unit) -> Unit): HistoryTools {
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    return HistoryTools(
        onUndo = if (canUndo) ({ keepingKeyboard { viewModel.undo() } }) else null,
        onRedo = if (canRedo) ({ keepingKeyboard { viewModel.redo() } }) else null,
    )
}

/** [apply] for the block tools, or null (they are disabled) while the checklist the user works on is locked. */
internal fun toolApplier(lock: ChecklistLock?, apply: (BlockType) -> Unit): ((BlockType) -> Unit)? =
    apply.takeUnless { lock?.locked == true }

/** Moving, duplicating, deleting and locking the block the user works on, each only where it applies. */
@Composable
internal fun rememberBlockMoveTools(
    viewModel: EditNoteViewModel,
    interactedBlockId: String?,
    lock: ChecklistLock?,
    keepingKeyboard: (() -> Unit) -> Unit,
): BlockMoveTools {
    val current by rememberUpdatedState(interactedBlockId)
    val canMoveUp by remember(viewModel) { derivedStateOf { current.let { viewModel.canMoveBlock(up = true) } } }
    val canMoveDown by remember(viewModel) { derivedStateOf { current.let { viewModel.canMoveBlock(up = false) } } }
    val canDelete by remember(viewModel) { derivedStateOf { current.let { viewModel.canRemoveInteractedBlock() } } }
    return BlockMoveTools(
        onMoveUp = if (canMoveUp) ({ viewModel.moveBlock(up = true) }) else null,
        onMoveDown = if (canMoveDown) ({ viewModel.moveBlock(up = false) }) else null,
        onDuplicate = viewModel::duplicateInteractedBlock,
        onDelete = if (canDelete) ({ keepingKeyboard(viewModel::removeInteractedBlock) }) else null,
        lock = lock,
    )
}

/** The lock of the checklist the user works on; null while they work on anything else. */
@Composable
internal fun rememberChecklistLock(viewModel: EditNoteViewModel, interactedBlockId: String?): ChecklistLock? {
    val currentId by rememberUpdatedState(interactedBlockId)
    val checklist by remember(viewModel) {
        derivedStateOf {
            (viewModel.blocks.firstOrNull { it.id == currentId } as? Block.ListBlock)
                ?.takeIf { it.blockType == BlockType.LIST_CHECK }
        }
    }
    val lockState = remember(checklist) { checklist?.locked ?: MutableStateFlow(false) }
    val locked by lockState.collectAsState()
    return checklist?.let { list -> ChecklistLock(locked) { viewModel.toggleChecklistLock(list) } }
}

/** Undo and redo; a null action shows its button disabled. */
internal class HistoryTools(val onUndo: (() -> Unit)?, val onRedo: (() -> Unit)?)

/** Moving, duplicating and deleting the block the user works on; a null action shows its button disabled. */
internal class BlockMoveTools(
    val onMoveUp: (() -> Unit)?,
    val onMoveDown: (() -> Unit)?,
    val onDuplicate: (() -> Unit)?,
    val onDelete: (() -> Unit)?,
    val lock: ChecklistLock?,
)

/** Locking the checklist the user works on: whether it is [locked], and how to flip that. */
internal class ChecklistLock(val locked: Boolean, val onToggle: () -> Unit)
