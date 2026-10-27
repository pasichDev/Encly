package com.pasich.encly.data.export

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * The words the exported files contain besides the user's own text, in the in-app language
 * (resolved by the caller: this layer holds no Android resources).
 *
 * @property untitled a note without a title: its heading and file name
 * @property unreadable the body of a note whose stored content cannot be read
 * @property tasks the heading of the tasks file
 * @property priorities the label of each task priority (0 low, 1 medium, 2 high)
 */
class MarkdownLabels(val untitled: String, val unreadable: String, val tasks: String, val priorities: Map<Int, String>)

/**
 * One note as a Markdown file: a front matter block, the title as a heading, then the blocks.
 *
 * The front matter is the safe subset Encly's own Markdown import reads back (`title`, `tags`,
 * `created`, `updated`); `description`, `pinned`, `archived` and `color` ride along for other
 * tools. Every text value is a JSON string, which is also a valid YAML double-quoted scalar, so
 * a title with a colon, a quote or a line break cannot break out of its line.
 */
object MarkdownNotes {
    /** [tagName] is the note's tag, if any. */
    fun render(note: Note, tagName: String?, labels: MarkdownLabels): String {
        val title = note.title.trim()
        val blocks = BlockConverter.jsonToBlocksOrNull(note.value)
        val body = blocks?.let(MarkdownBlocks::render) ?: MarkdownText.escapeInline(labels.unreadable)
        return buildString {
            append("---\n")
            append("title: ").append(quoted(title.ifEmpty { labels.untitled })).append('\n')
            if (!tagName.isNullOrBlank()) append("tags: [").append(quoted(tagName.trim())).append("]\n")
            if (note.dateCreate > 0L) append("created: ").append(iso(note.dateCreate)).append('\n')
            if (note.date > 0L) append("updated: ").append(iso(note.date)).append('\n')
            if (note.description.isNotBlank()) {
                append(
                    "description: ",
                ).append(quoted(note.description.trim())).append('\n')
            }
            if (note.isPinned) append("pinned: true\n")
            if (note.isArchived) append("archived: true\n")
            if (note.color != Note.NO_COLOR) append("color: ").append(quoted(note.color)).append('\n')
            append("---\n\n")
            append(titledBody(title, body))
        }
    }

    /**
     * A note as it stands in the editor, without the front matter: [title] as a heading (none when
     * blank), then [blocks]. This is what "Copy as Markdown" puts on the clipboard, and it is the
     * body [render] writes after its front matter.
     */
    fun renderBody(title: String, blocks: List<Block>): String = titledBody(title.trim(), MarkdownBlocks.render(blocks))

    /** The heading and [body] as paragraphs of one text, ending in a line break unless both are empty. */
    private fun titledBody(title: String, body: String): String {
        val heading = if (title.isEmpty()) "" else MarkdownBlocks.render(listOf(headingOf(title)))
        val text = listOf(heading, body).filter { it.isNotEmpty() }.joinToString("\n\n")
        return if (text.isEmpty()) text else text + '\n'
    }

    private fun headingOf(title: String) = Block.HBlock(text = MutableStateFlow(title), blockType = BlockType.H1)

    private fun quoted(value: String): String = JsonPrimitive(
        value.filterNot {
            it.isISOControl() && it != '\n'
        },
    ).toString()

    private fun iso(millis: Long): String = Instant.ofEpochMilli(millis).toString()
}
