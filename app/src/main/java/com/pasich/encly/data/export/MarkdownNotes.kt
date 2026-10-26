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
            val heading = if (title.isEmpty()) "" else MarkdownBlocks.render(listOf(headingOf(title)))
            append(listOf(heading, body).filter { it.isNotEmpty() }.joinToString("\n\n"))
            if (heading.isNotEmpty() || body.isNotEmpty()) append('\n')
        }
    }

    private fun headingOf(title: String) = Block.HBlock(text = MutableStateFlow(title), blockType = BlockType.H1)

    private fun quoted(value: String): String = JsonPrimitive(
        value.filterNot {
            it.isISOControl() && it != '\n'
        },
    ).toString()

    private fun iso(millis: Long): String = Instant.ofEpochMilli(millis).toString()
}
