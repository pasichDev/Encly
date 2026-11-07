package com.pasich.encly.presentation.screen.editnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.markdown_copied
import com.pasich.encly.core.resources.markdown_copy
import com.pasich.encly.core.resources.selection_copied
import com.pasich.encly.data.export.MarkdownBlocks
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.state.plainText
import com.pasich.encly.editor.ui.SelectedBlocksActions
import com.pasich.encly.editor.ui.SelectedBlocksBar
import com.pasich.encly.editor.ui.TextRangeBar
import com.pasich.encly.platform.LocalSensitiveClipboard
import com.pasich.encly.platform.SensitiveClipboard
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/** The [blocks] as the text a copy puts on the clipboard: Markdown, or plain text. */
internal fun selectedBlocksText(blocks: List<Block>, markdown: Boolean): String =
    if (markdown) MarkdownBlocks.render(blocks) else blocks.plainText()

/**
 * Copies the picked blocks as [markdown] or plain text through [SensitiveClipboard]: marked sensitive and cleared
 * after a minute. Block selection mode ends when the text reached the clipboard. Returns the confirmation to show
 * ([confirmation]), or null when there is nothing to say: nothing was copied or the system already confirms it.
 */
internal fun copyPickedBlocks(
    clipboard: SensitiveClipboard,
    viewModel: EditNoteViewModel,
    markdown: Boolean,
    label: String,
    confirmation: String,
): String? {
    val text = selectedBlocksText(viewModel.selectedBlocks.picked(), markdown)
    val copied = text.isNotEmpty() && clipboard.copy(label, text)
    if (copied) viewModel.selectedBlocks.clear()
    return confirmation.takeIf { copied && !clipboard.systemConfirmsCopy }
}

/**
 * Cuts the picked blocks: Markdown (so pasting them back into a note rebuilds the blocks) goes to the clipboard, and
 * only when it got there are the blocks removed, as one undo step. Does nothing in a note that cannot be changed.
 * Returns the confirmation to show, or null.
 */
internal fun cutPickedBlocks(
    clipboard: SensitiveClipboard,
    viewModel: EditNoteViewModel,
    label: String,
    confirmation: String,
): String? {
    if (!viewModel.canEditBlocks) return null
    val text = selectedBlocksText(viewModel.selectedBlocks.picked(), markdown = true)
    val copied = text.isNotEmpty() && clipboard.copy(label, text)
    if (copied) viewModel.deleteSelectedBlocks()
    return confirmation.takeIf { copied && !clipboard.systemConfirmsCopy }
}

/**
 * The bar over the selected blocks: copy (plain or Markdown), cut and delete. A locked or trashed note can be
 * copied from, not changed. [onMessage] shows the confirmation of a copy.
 */
@Composable
internal fun EditorSelectionBar(onMessage: (String) -> Unit, viewModel: EditNoteViewModel = metroViewModel()) {
    val clipboard = LocalSensitiveClipboard.current
    val label = stringResource(Res.string.markdown_copy)
    val copied = stringResource(Res.string.selection_copied)
    val copiedMarkdown = stringResource(Res.string.markdown_copied)
    val locked by viewModel.lockEditor.collectAsState()
    val actions = remember(viewModel, clipboard, label, copied, copiedMarkdown, locked) {
        SelectedBlocksActions(
            canEdit = viewModel.canEditBlocks,
            onCopy = { copyPickedBlocks(clipboard, viewModel, false, label, copied)?.let(onMessage) },
            onCopyMarkdown = { copyPickedBlocks(clipboard, viewModel, true, label, copiedMarkdown)?.let(onMessage) },
            onCut = { cutPickedBlocks(clipboard, viewModel, label, copied)?.let(onMessage) },
            onDelete = { viewModel.deleteSelectedBlocks() },
        )
    }
    SelectedBlocksBar(viewModel.selectedBlocks, actions)
}

/**
 * The bar over a text selection that runs across blocks: copy (plain or Markdown), cut and delete, through the same
 * clipboard and rules as the keys. A locked or trashed note can be copied from, not changed.
 */
@Composable
internal fun EditorTextRangeBar(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = metroViewModel(),
) {
    val clipboard = LocalSensitiveClipboard.current
    val label = stringResource(Res.string.markdown_copy)
    val copied = stringResource(Res.string.selection_copied)
    val copiedMarkdown = stringResource(Res.string.markdown_copied)
    val locked by viewModel.lockEditor.collectAsState()
    val actions = remember(viewModel, clipboard, label, copied, copiedMarkdown, locked) {
        fun copy(markdown: Boolean, message: String) {
            val text = selectedBlocksText(viewModel.textRangeBlocks(), markdown)
            if (text.isNotEmpty() && clipboard.copy(label, text)) {
                viewModel.textRange.clear()
                message.takeIf { !clipboard.systemConfirmsCopy }?.let(onMessage)
            }
        }
        SelectedBlocksActions(
            canEdit = viewModel.canEditBlocks,
            onCopy = { copy(markdown = false, message = copied) },
            onCopyMarkdown = { copy(markdown = true, message = copiedMarkdown) },
            onCut = {
                val text = selectedBlocksText(viewModel.textRangeBlocks(), markdown = true)
                if (text.isNotEmpty() && clipboard.copy(label, text)) viewModel.replaceTextRange("")
            },
            onDelete = { viewModel.replaceTextRange("") },
        )
    }
    TextRangeBar(viewModel.textRange, actions, modifier)
}

/** Back leaves block selection mode, or a text selection across blocks, before it does anything else. */
@Composable
internal fun LeaveBlockSelectionOnBack(viewModel: EditNoteViewModel = metroViewModel()) {
    val picking by viewModel.selectedBlocks.ids.collectAsState()
    val textRange by viewModel.textRange.ends.collectAsState()
    BackHandler(enabled = picking.isNotEmpty() || (textRange != null && viewModel.textRange.isCrossField)) {
        viewModel.selectedBlocks.clear()
        viewModel.textRange.clear()
    }
}
