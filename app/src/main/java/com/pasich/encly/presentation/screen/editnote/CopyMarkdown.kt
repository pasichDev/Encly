package com.pasich.encly.presentation.screen.editnote

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.pasich.encly.R
import com.pasich.encly.data.export.MarkdownNotes
import com.pasich.encly.presentation.components.SensitiveClip
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

/**
 * Copies the note as the editor shows it now (unsaved edits included) as Markdown, through
 * [SensitiveClip]: marked sensitive and cleared after a minute. The note is already open, so no
 * re-authentication is asked; this is the text a selection would copy.
 *
 * Returns the confirmation to show, or null when there is nothing to say: nothing was written
 * (a blank note, an unreadable one) or the system already confirms the copy (Android 13+).
 */
fun copyNoteAsMarkdown(context: Context, viewModel: EditNoteViewModel): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (clipboard == null || viewModel.isBlankDraft || viewModel.contentLoadFailed.value) return null
    val markdown = MarkdownNotes.renderBody(viewModel.state.value.note.title, viewModel.blocks)
    SensitiveClip.copy(clipboard, context.getString(R.string.markdown_copy), markdown)
    return if (Build.VERSION.SDK_INT <
        Build.VERSION_CODES.TIRAMISU
    ) {
        context.getString(R.string.markdown_copied)
    } else {
        null
    }
}
