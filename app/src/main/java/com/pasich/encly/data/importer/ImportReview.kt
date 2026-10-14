package com.pasich.encly.data.importer

import com.pasich.encly.core.backup.BackupNote
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * One note of the review list. Held in memory only, for the open vault; it is dropped on cancel,
 * on finish and when the vault locks, and is never saved in instance state.
 */
data class ReviewNote(
    val uid: String,
    val title: String,
    /** About one line of the first text, no markup. */
    val snippet: String,
    val hasChecklist: Boolean,
    val tag: String?,
    val trashed: Boolean,
    val date: Long,
    /** A note with this uid is already in the vault, so importing it would add nothing. */
    val existing: Boolean = false,
)

/** Review-list helpers: the row of a note, its read-only text and the payload of a selection. */
object ImportReview {
    private const val SNIPPET_CHARS = 80
    private const val BODY_CHARS = 4000
    private val WHITESPACE = Regex("""\s+""")

    internal fun row(uid: String, note: ImportedNote, tag: String?, date: Long) = ReviewNote(
        uid = uid,
        title = note.title.trim(),
        snippet = snippet(note.blocks),
        hasChecklist = note.blocks.any { it is Block.ListBlock && it.blockType == BlockType.LIST_CHECK },
        tag = tag,
        trashed = note.trashed,
        date = date,
    )

    private fun snippet(blocks: List<Block>): String {
        val first = blocks.firstNotNullOfOrNull { text(it).takeIf(String::isNotBlank) }.orEmpty()
        return first.trim().replace(WHITESPACE, " ").take(SNIPPET_CHARS)
    }

    private fun text(block: Block): String = when (block) {
        is Block.TextBlock -> block.text.value
        is Block.HBlock -> block.text.value
        is Block.QuoteBlock -> block.text.value
        is Block.ListBlock -> block.items.value.firstOrNull()?.value.orEmpty()
        else -> ""
    }

    /** The note as plain text for the inline read-only preview, cut at a few thousand characters. */
    fun body(note: BackupNote): String {
        val blocks = BlockConverter.jsonToBlocksOrNull(note.value).orEmpty()
        val text = blocks.joinToString("\n\n") { render(it) }.trim()
        return if (text.length > BODY_CHARS) text.take(BODY_CHARS) + "…" else text
    }

    private fun render(block: Block): String = when (block) {
        is Block.TextBlock -> block.text.value

        is Block.HBlock -> block.text.value

        is Block.QuoteBlock -> block.text.value.lines().joinToString("\n") { "> $it" }

        is Block.SeparatorBlock -> "———"

        is Block.LinkBlock -> block.block.value.url

        is Block.ListBlock -> block.items.value.withIndex().joinToString("\n") { (index, item) ->
            when (block.blockType) {
                BlockType.LIST_CHECK -> (if (item.isCheck) "☑ " else "☐ ") + item.value
                BlockType.LIST_NUMBER -> "${index + 1}. ${item.value}"
                else -> "• ${item.value}"
            }
        }
    }

    /**
     * [payload] reduced to the notes in [selected] and the tags they use, renumbered and validated
     * again: what the one MERGE transaction then writes.
     */
    fun selection(payload: BackupPayload, selected: Set<String>): BackupPayload {
        val notes = payload.notes.filter { it.uid in selected }
        val used = notes.mapNotNullTo(HashSet()) { it.tagUid }
        val tags = payload.tags.filter { it.uid in used }.mapIndexed { index, tag -> tag.copy(position = index) }
        return payload.copy(tags = tags, notes = notes).also(BackupPayloadCodec::validate)
    }
}
