package com.pasich.encly.presentation.screen.editnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pasich.encly.presentation.navigation.EditorCommands
import com.pasich.encly.presentation.navigation.EditorCommandsBinding
import com.pasich.encly.presentation.viewmodel.DocumentFormat
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * What the keyboard shortcuts and the host's menu do in this editor, registered while it is on screen: Find opens
 * the find bar, Delete asks, then moves the note to the trash (not a note already in the trash, nor a blank new
 * one, which has nothing to lose), Esc closes the find bar, and Print and Export as PDF start the document export.
 */
@Composable
internal fun EditorShortcutCommands(
    overlays: EditorOverlaysState,
    documents: EditorDocumentExport,
    viewModel: EditNoteViewModel = metroViewModel(),
) {
    val commands = remember(viewModel, overlays, documents) {
        EditorCommands(
            find = { true.also { viewModel.openFind() } },
            moveToTrash = {
                val allowed = !viewModel.isReadTrashOnly && !viewModel.isBlankDraft
                allowed.also { if (it) overlays.isTrashDialogVisible = true }
            },
            dismiss = { viewModel.find.value.isOpen.also { open -> if (open) viewModel.closeFind() } },
            print = { documents.start(DocumentFormat.PRINT) },
            exportPdf = { documents.start(DocumentFormat.PDF) },
        )
    }
    EditorCommandsBinding(commands)
}
