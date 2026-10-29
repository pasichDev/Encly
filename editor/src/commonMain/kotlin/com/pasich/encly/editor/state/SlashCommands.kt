package com.pasich.encly.editor.state

import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockFactory
import com.pasich.encly.editor.model.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The slash command picker: "/" typed at the start of an empty paragraph offers the block kinds
// the editor already has. Everything here is plain state; the popup and its labels are UI.

/**
 * A block kind the slash picker offers. [keywords] are extra words (English, for every
 * language) the query may start with, next to the words of the localized label. [headingLevel]
 * fills the "Heading %d" label.
 */
public enum class SlashCommand(
    internal val type: BlockType,
    internal val label: EditorString,
    internal val headingLevel: Int? = null,
    internal val keywords: List<String> = emptyList(),
) {
    TEXT(BlockType.TEXT, EditorString.TEXT, keywords = listOf("paragraph", "plain")),
    H1(BlockType.H1, EditorString.BLOCK_HEADING_LEVEL, headingLevel = 1, keywords = listOf("h1", "title")),
    H2(BlockType.H2, EditorString.BLOCK_HEADING_LEVEL, headingLevel = 2, keywords = listOf("h2")),
    H3(BlockType.H3, EditorString.BLOCK_HEADING_LEVEL, headingLevel = 3, keywords = listOf("h3")),
    H4(BlockType.H4, EditorString.BLOCK_HEADING_LEVEL, headingLevel = 4, keywords = listOf("h4")),
    CHECKLIST(BlockType.LIST_CHECK, EditorString.BLOCK_CHECKLIST, keywords = listOf("todo", "task", "checkbox")),
    BULLETS(BlockType.LIST_BULLET, EditorString.BLOCK_BULLETED_LIST, keywords = listOf("bullets", "ul")),
    NUMBERED(BlockType.LIST_NUMBER, EditorString.BLOCK_NUMBERED_LIST, keywords = listOf("ordered", "ol")),
    QUOTE(BlockType.QUOTE, EditorString.QUOTE, keywords = listOf("citation")),
    LINK(BlockType.LINK, EditorString.BLOCK_LINK, keywords = listOf("url", "bookmark")),
    DIVIDER(BlockType.SEPARATOR, EditorString.BLOCK_SEPARATOR, keywords = listOf("divider", "rule", "line", "hr")),
}

/**
 * The commands whose label ([labels], by command; a missing one counts as empty) or keywords
 * have a word that starts with [query], ignoring case, in the order of [SlashCommand]. An empty
 * query matches every command.
 */
internal fun slashMatches(query: String, labels: Map<SlashCommand, String>): List<SlashCommand> {
    if (query.isEmpty()) return SlashCommand.entries
    return SlashCommand.entries.filter { command ->
        val words = labels[command].orEmpty().split(' ') + command.keywords
        words.any { it.startsWith(query, ignoreCase = true) }
    }
}

/**
 * The picker as the UI shows it, on paragraph [blockId] whose text is "/" + [query]. It stays
 * hidden until the "/" is [committed] text (not under IME composition).
 */
public data class SlashMenu(
    val blockId: String,
    val query: String,
    val matches: List<SlashCommand>,
    val highlighted: Int,
    val committed: Boolean,
) {
    /** The command Enter would pick. */
    val highlightedCommand: SlashCommand? get() = matches.getOrNull(highlighted)

    /** Whether the user can see and use the menu. */
    val isShown: Boolean get() = committed && matches.isNotEmpty()
}

/**
 * Which paragraph has the slash picker open, and what has been typed after the "/". Only ever
 * in memory. A space, a query nothing matches, or any text that no longer starts with "/"
 * closes it; the literal text stays as typed.
 */
public class SlashPicker {
    private val _menu = MutableStateFlow<SlashMenu?>(null)

    /** The open slash menu; null while it is closed. */
    public val menu: StateFlow<SlashMenu?> = _menu.asStateFlow()

    private var labels: Map<SlashCommand, String> = emptyMap()

    /** The localized label of every command, for matching the query; set by the UI. */
    internal fun setLabels(labels: Map<SlashCommand, String>) {
        this.labels = labels
        _menu.value?.let { current -> _menu.value = current.withQuery(current.query) }
    }

    /** "/" was typed into the empty paragraph [blockId]. */
    public fun open(blockId: String) {
        _menu.value = SlashMenu(blockId, "", slashMatches("", labels), highlighted = 0, committed = false)
    }

    /** The "/" is committed text, so the menu may show. */
    public fun commit() {
        _menu.value = _menu.value?.copy(committed = true)
    }

    /** Paragraph [blockId] now reads [text]: the query follows it, or the menu closes. */
    public fun onText(blockId: String, text: String) {
        val current = _menu.value?.takeIf { it.blockId == blockId } ?: return
        val query = text.removePrefix("/")
        val stillSlash = text.startsWith("/") && query.none { it.isWhitespace() }
        val next = if (stillSlash) current.withQuery(query) else null
        _menu.value = next?.takeIf { it.matches.isNotEmpty() }
    }

    /** Moves the highlight by [delta] places, wrapping round. */
    public fun moveHighlight(delta: Int) {
        val current = _menu.value?.takeIf { it.matches.isNotEmpty() } ?: return
        val size = current.matches.size
        _menu.value = current.copy(highlighted = ((current.highlighted + delta) % size + size) % size)
    }

    /** Closes the slash menu. */
    public fun dismiss() {
        _menu.value = null
    }

    private fun SlashMenu.withQuery(query: String): SlashMenu =
        copy(query = query, matches = slashMatches(query, labels), highlighted = 0)
}

/**
 * Turns the empty-but-for-"/query" paragraph [blockId] into [command]'s block, as one undo step
 * (undo brings the paragraph back with the "/query" as typed). "Text" only clears the query;
 * a divider is followed by a paragraph to go on in. Returns false when [blockId] is not a
 * paragraph.
 */
public fun BlockEditorState.applySlash(blockId: String, command: SlashCommand): Boolean {
    val index = indexOf(blockId)
    val block = blocks.getOrNull(index) as? Block.TextBlock ?: return false
    when (command.type) {
        BlockType.TEXT -> {
            batch { setValue(block.id, block.text, "") }
            selection.focusAt(index, caret = 0)
        }

        BlockType.SEPARATOR -> replaceWithBlockAndParagraph(index, Block.SeparatorBlock())

        else -> {
            val replacement = BlockFactory.createBlock(command.type)
            batch { replaceBlock(index, replacement) }
            selection.focusAt(index, caret = 0, itemId = replacement.firstItemId())
        }
    }
    return true
}
