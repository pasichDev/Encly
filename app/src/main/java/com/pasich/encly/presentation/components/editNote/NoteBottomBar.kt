package com.pasich.encly.presentation.components.editNote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Redo2
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Undo2
import com.composables.icons.lucide.X
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyEditorToolbar
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.presentation.designsystem.EnclyToolbarRule
import com.pasich.encly.presentation.designsystem.ToolStyle
import com.pasich.encly.presentation.editor.DynamicButtons
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme

enum class NoteBottomBarFragment {
    BLOCKS,
    MAIN,
}

/**
 * The editor's formatting toolbar above the keyboard (design spec §3.3): undo and redo, then the
 * filled "Add block" and the block tools (move, delete); "Add block" swaps in the block types.
 */
@Composable
fun NoteBottomBar(
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = hiltViewModel(),
    simpleEdit: Boolean = false,
) {
    var noteBottomBarFragment by rememberSaveable {
        mutableStateOf(
            NoteBottomBarFragment.MAIN,
        )
    }
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    // Re-evaluated when the block the user works on or the blocks (their order) change.
    val interactedBlockId by viewModel.interactedBlockId.collectAsState()
    val blocks = viewModel.blocks.toList()
    val canMoveUp = remember(interactedBlockId, blocks) { viewModel.canMoveBlock(up = true) }
    val canMoveDown = remember(interactedBlockId, blocks) { viewModel.canMoveBlock(up = false) }
    val canDelete = remember(interactedBlockId, blocks) { viewModel.canRemoveInteractedBlock() }

    val toMain = {
        noteBottomBarFragment = NoteBottomBarFragment.MAIN
    }
    val main = noteBottomBarFragment == NoteBottomBarFragment.MAIN

    EnclyEditorToolbar(modifier = modifier.imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            AnimatedVisibility(visible = main) {
                Row(horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs)) {
                    EnclyToolButton(
                        icon = Lucide.Undo2,
                        contentDescription = stringResource(R.string.undo),
                        onClick = if (canUndo) viewModel::undo else null,
                    )
                    EnclyToolButton(
                        icon = Lucide.Redo2,
                        contentDescription = stringResource(R.string.redo),
                        onClick = if (canRedo) viewModel::redo else null,
                    )
                }
            }

            AnimatedVisibility(visible = !main) {
                EnclyToolButton(icon = Lucide.X, contentDescription = stringResource(R.string.close), onClick = toMain)
            }

            if (!simpleEdit) EnclyToolbarRule()

            AnimatedVisibility(visible = main && !simpleEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs)) {
                    EnclyToolButton(
                        icon = Lucide.Plus,
                        contentDescription = stringResource(R.string.block_add),
                        onClick = { noteBottomBarFragment = NoteBottomBarFragment.BLOCKS },
                        style = ToolStyle.FILLED,
                    )
                    EnclyToolButton(
                        icon = Lucide.ArrowUp,
                        contentDescription = stringResource(R.string.block_move_up),
                        onClick = if (canMoveUp) ({ viewModel.moveBlock(up = true) }) else null,
                    )
                    EnclyToolButton(
                        icon = Lucide.ArrowDown,
                        contentDescription = stringResource(R.string.block_move_down),
                        onClick = if (canMoveDown) ({ viewModel.moveBlock(up = false) }) else null,
                    )
                    EnclyToolButton(
                        icon = Lucide.Trash2,
                        contentDescription = stringResource(R.string.delete_block),
                        onClick = if (canDelete) viewModel::removeInteractedBlock else null,
                    )
                }
            }

            AnimatedVisibility(visible = !main && !simpleEdit) {
                DynamicButtons(
                    onAddBlock = { type ->
                        viewModel.addBlock(type)
                        toMain()
                    },
                )
            }
        }
    }
}
