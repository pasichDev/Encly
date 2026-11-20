package com.pasich.encly.presentation.screen.editnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pasich.encly.platform.LocalNoteDocumentRenderer
import com.pasich.encly.platform.NoteDocumentRenderer
import com.pasich.encly.presentation.viewmodel.DocumentFormat
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.MarkdownExportTarget
import com.pasich.encly.presentation.viewmodel.MarkdownExportViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * "Export as PDF" and "Print" of the note in this editor, for hosts that have a [NoteDocumentRenderer] (the
 * desktop). They run the plaintext export's flow (warning, PIN, then the save dialog or the printer's), on the
 * note as it is stored: the editor saves first, exactly as the Markdown export does.
 */
internal class EditorDocumentExport(
    private val exits: EditorExits,
    private val viewModel: EditNoteViewModel,
    private val flow: MarkdownExportViewModel,
    private val renderer: NoteDocumentRenderer?,
) {
    /** Whether this host can make PDFs and printouts at all. */
    val available: Boolean get() = renderer != null

    /** Starts [format]; false when there is nothing to export (no renderer, a blank or an unreadable note). */
    fun start(format: DocumentFormat): Boolean {
        val draw = renderer
        if (draw == null || viewModel.isBlankDraft || viewModel.contentLoadFailed.value) return false
        exits.exportMarkdown { id -> flow.start(MarkdownExportTarget.NoteAsDocument(id, format, draw)) }
        return true
    }
}

@Composable
internal fun rememberEditorDocumentExport(exits: EditorExits): EditorDocumentExport {
    val viewModel: EditNoteViewModel = metroViewModel()
    val flow: MarkdownExportViewModel = metroViewModel()
    val renderer = LocalNoteDocumentRenderer.current
    return remember(exits, viewModel, flow, renderer) { EditorDocumentExport(exits, viewModel, flow, renderer) }
}
