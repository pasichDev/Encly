package com.pasich.encly.presentation.editor.state

import com.pasich.encly.dynamicBlocks.Block
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Find in this note: a read-only search of the open note's text. The query and the matches live
// only in memory, in the editor ViewModel; they are not saved with the screen's state and are
// dropped when the vault locks.

/**
 * One match in the text of paragraph, heading or quote [blockId] (or of its list item [itemId]),
 * at the half-open range [start], [end] of that field's text.
 */
data class FindMatch(val blockId: String, val itemId: String?, val start: Int, val end: Int) {
    /** The field the match is in, as [FindState.byField] keys it. */
    val field: String get() = fieldKey(blockId, itemId)
}

/** Key of a block's text field, or of its list item's. */
fun fieldKey(blockId: String, itemId: String?): String = if (itemId == null) blockId else "$blockId/$itemId"

/** The find bar: whether it is open, what it searches for, and where it found it ([current]: -1 for none). */
data class FindState(
    val isOpen: Boolean = false,
    val query: String = "",
    val matches: List<FindMatch> = emptyList(),
    val current: Int = -1,
) {
    /** The matches of each field, for drawing. */
    val byField: Map<String, List<FindMatch>> by lazy(LazyThreadSafetyMode.NONE) { matches.groupBy { it.field } }

    val currentMatch: FindMatch? get() = matches.getOrNull(current)
}

/**
 * Every place [query] occurs in the text of [blocks], in reading order, ignoring case. Only
 * the blocks' own text is searched (paragraphs, headings, quotes and list items); the blocks are
 * never touched. A blank query finds nothing.
 */
fun findMatches(blocks: List<Block>, query: String): List<FindMatch> {
    if (query.isBlank()) return emptyList()
    val found = mutableListOf<FindMatch>()
    for (block in blocks) {
        when (block) {
            is Block.TextBlock -> found.addMatches(block.id, null, block.text.value, query)
            is Block.HBlock -> found.addMatches(block.id, null, block.text.value, query)
            is Block.QuoteBlock -> found.addMatches(block.id, null, block.text.value, query)
            is Block.ListBlock -> block.items.value.forEach { found.addMatches(block.id, it.id, it.value, query) }
            is Block.LinkBlock, is Block.SeparatorBlock -> Unit
        }
    }
    return found
}

private fun MutableList<FindMatch>.addMatches(blockId: String, itemId: String?, text: String, query: String) {
    var from = text.indexOf(query, ignoreCase = true)
    while (from >= 0) {
        add(FindMatch(blockId, itemId, from, from + query.length))
        from = text.indexOf(query, from + query.length, ignoreCase = true)
    }
}

/** The find bar's state and its movement through the matches. */
class NoteFind {
    private val _state = MutableStateFlow(FindState())
    val state: StateFlow<FindState> = _state.asStateFlow()

    val isOpen: Boolean get() = _state.value.isOpen

    fun open() {
        _state.update { it.copy(isOpen = true) }
    }

    /** Closes the bar and forgets the query and every match. */
    fun close() {
        _state.value = FindState()
    }

    /** Searches [blocks] for [query]; the first match becomes the current one. */
    fun search(query: String, blocks: List<Block>) {
        val matches = findMatches(blocks, query)
        _state.update { it.copy(query = query, matches = matches, current = if (matches.isEmpty()) -1 else 0) }
    }

    /**
     * The text changed: searches [blocks] again for the same query. The current match stays at
     * its place in the order, or the nearest one left.
     */
    fun refresh(blocks: List<Block>) {
        _state.update { now ->
            if (!now.isOpen || now.query.isBlank()) return@update now
            val matches = findMatches(blocks, now.query)
            now.copy(
                matches = matches,
                current = if (matches.isEmpty()) -1 else now.current.coerceIn(0, matches.lastIndex),
            )
        }
    }

    /** The next match, wrapping round after the last. */
    fun next() = step(1)

    /** The match before, wrapping round before the first. */
    fun previous() = step(-1)

    private fun step(delta: Int) {
        _state.update { now ->
            val size = now.matches.size
            if (size == 0) now else now.copy(current = ((now.current + delta) % size + size) % size)
        }
    }
}
