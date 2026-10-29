package com.pasich.encly.presentation.screen.editnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pasich.encly.presentation.navigation.EditorCommands
import com.pasich.encly.presentation.navigation.EditorCommandsBinding
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * What the keyboard shortcuts do in this editor, registered while it is on screen: Find opens the find bar,
 * Delete asks, then moves the note to the trash (not a note already in the trash, nor a blank new one, which
 * has nothing to lose), and Esc closes the find bar.
 */
@Composable
internal fun EditorShortcutCommands(overlays: EditorOverlaysState, viewModel: EditNoteViewModel = metroViewModel()) {
    val commands = remember(viewModel, overlays) {
        EditorCommands(
            find = { true.also { viewModel.openFind() } },
            moveToTrash = {
                val allowed = !viewModel.isReadTrashOnly && !viewModel.isBlankDraft
                allowed.also { if (it) overlays.isTrashDialogVisible = true }
            },
            dismiss = { viewModel.find.value.isOpen.also { open -> if (open) viewModel.closeFind() } },
        )
    }
    EditorCommandsBinding(commands)
}
