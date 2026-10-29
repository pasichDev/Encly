package com.pasich.encly.data.export

import com.pasich.encly.data.model.Note
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.hasContent

/** What kind of list a [DocumentBlock.Items] is. */
enum class ListKind { BULLET, NUMBERED, CHECKLIST }

/** One row of a list; [checked] only means something in a checklist. */
data class DocumentItem(val text: String, val checked: Boolean = false)

/**
 * A block of a [NoteDocument]: what a page of the note shows, with no editor state in it. A renderer (the PDF
 * writer, the printer) draws these and nothing else, so it needs no knowledge of the editor.
 */
sealed interface DocumentBlock {
    /** A heading; [level] is 1 (largest) to 4. */
    data class Heading(val level: Int, val text: String) : DocumentBlock

    data class Paragraph(val text: String) : DocumentBlock

    data class Quote(val text: String) : DocumentBlock

    /** A link, shown as its text: [title] (when it has one) and the address. Never an active link. */
    data class Link(val title: String, val url: String) : DocumentBlock

    data object Separator : DocumentBlock

    data class Items(val kind: ListKind, val items: List<DocumentItem>) : DocumentBlock
}

/**
 * One note as a document for paper or PDF: [title], the [tag] it carries and the [blocks] that are not blank.
 * It holds plaintext and is built from the stored note only inside an authenticated export (see
 * `MarkdownExportViewModel`); nothing keeps it afterwards.
 */
class NoteDocument(val title: String, val tag: String?, val blocks: List<DocumentBlock>) {
    companion object {
        private const val H1_TO_H4 = 4

        /**
         * The document of [note]. [tagName] is its tag, if any; a note whose stored content cannot be read gets
         * [unreadable] as its only paragraph, and a note without a title [untitled].
         */
        fun of(note: Note, tagName: String?, untitled: String, unreadable: String): NoteDocument {
            val parsed = BlockConverter.jsonToBlocksOrNull(note.value)
            val blocks = parsed?.let(::fromBlocks) ?: listOf(DocumentBlock.Paragraph(unreadable))
            return NoteDocument(
                title = note.title.trim().ifEmpty { untitled },
                tag = tagName?.trim()?.takeIf { it.isNotEmpty() },
                blocks = blocks,
            )
        }

        /** The blocks of the editor as document blocks; blocks without content are left out. */
        fun fromBlocks(blocks: List<Block>): List<DocumentBlock> = blocks.filter { it.hasContent() }.map { block ->
            when (block) {
                is Block.TextBlock -> DocumentBlock.Paragraph(block.text.value)

                is Block.HBlock -> DocumentBlock.Heading(headingLevel(block.blockType), block.text.value)

                is Block.QuoteBlock -> DocumentBlock.Quote(block.text.value)

                is Block.LinkBlock -> block.block.value.let { DocumentBlock.Link(it.title.trim(), it.url.trim()) }

                is Block.SeparatorBlock -> DocumentBlock.Separator

                is Block.ListBlock -> DocumentBlock.Items(
                    kind = when (block.blockType) {
                        BlockType.LIST_NUMBER -> ListKind.NUMBERED
                        BlockType.LIST_CHECK -> ListKind.CHECKLIST
                        else -> ListKind.BULLET
                    },
                    items = block.items.value.filter { it.value.isNotBlank() }
                        .map { DocumentItem(it.value, it.isCheck) },
                )
            }
        }

        private fun headingLevel(type: BlockType): Int = (type.ordinal - BlockType.H1.ordinal + 1).coerceIn(1, H1_TO_H4)
    }
}
