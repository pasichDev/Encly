package com.pasich.encly.presentation.components.editNote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.R
import com.pasich.encly.presentation.components.VerticalDivider
import com.pasich.encly.presentation.components.appbar.AppBarIconButton
import com.pasich.encly.presentation.editor.DynamicButtons
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

enum class NoteBottomBarFragment {
    BLOCKS,
    MAIN,
}

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

    Row(
        modifier = modifier
            .imePadding()
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 0.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.MAIN) {
                AppBarIconButton(
                    icon = R.drawable.ic_undo,
                    contentDescription = stringResource(R.string.undo),
                    onPress = if (canUndo) viewModel::undo else null,
                )
            }
            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.MAIN) {
                AppBarIconButton(
                    icon = R.drawable.ic_redo,
                    contentDescription = stringResource(R.string.redo),
                    onPress = if (canRedo) viewModel::redo else null,
                )
            }

            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.BLOCKS) {
                AppBarIconButton(
                    icon = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.close),
                    onPress = toMain,
                )
            }

            if (!simpleEdit) {
                VerticalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp,
                    height = 20.dp,
                )
            }

            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.MAIN && !simpleEdit) {
                Row {
                    AppBarIconButton(
                        icon = R.drawable.ic_add,
                        contentDescription = stringResource(R.string.block_add),
                        onPress = { noteBottomBarFragment = NoteBottomBarFragment.BLOCKS },
                    )
                    AppBarIconButton(
                        icon = R.drawable.ic_up,
                        contentDescription = stringResource(R.string.block_move_up),
                        onPress = if (canMoveUp) ({ viewModel.moveBlock(up = true) }) else null,
                        margin = 2.dp,
                    )
                    AppBarIconButton(
                        icon = R.drawable.ic_down,
                        contentDescription = stringResource(R.string.block_move_down),
                        onPress = if (canMoveDown) ({ viewModel.moveBlock(up = false) }) else null,
                        margin = 2.dp,
                    )
                    AppBarIconButton(
                        icon = R.drawable.ic_trash_all_clean,
                        contentDescription = stringResource(R.string.delete_block),
                        onPress = if (canDelete) viewModel::removeInteractedBlock else null,
                        margin = 2.dp,
                    )
                }
            }

            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.BLOCKS && !simpleEdit) {
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
