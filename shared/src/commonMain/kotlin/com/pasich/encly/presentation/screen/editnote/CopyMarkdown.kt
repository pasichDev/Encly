package com.pasich.encly.presentation.screen.editnote

import com.pasich.encly.data.export.MarkdownNotes
import com.pasich.encly.platform.SensitiveClipboard
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

/**
 * Copies the note as the editor shows it now (unsaved edits included) as Markdown, through
 * [SensitiveClipboard]: marked sensitive and cleared after a minute. The note is already open, so no
 * re-authentication is asked; this is the text a selection would copy.
 *
 * [label] is the clipboard label and [confirmation] the text shown after the copy (both in the in-app
 * language). Returns the confirmation to show, or null when there is nothing to say: nothing was written
 * (a blank note, an unreadable one) or the system already confirms the copy (Android 13+).
 */
fun copyNoteAsMarkdown(
    clipboard: SensitiveClipboard,
    viewModel: EditNoteViewModel,
    label: String,
    confirmation: String,
): String? {
    if (viewModel.isBlankDraft || viewModel.contentLoadFailed.value) return null
    val markdown = MarkdownNotes.renderBody(viewModel.state.value.note.title, viewModel.blocks)
    val copied = clipboard.copy(label, markdown)
    return confirmation.takeIf { copied && !clipboard.systemConfirmsCopy }
}
