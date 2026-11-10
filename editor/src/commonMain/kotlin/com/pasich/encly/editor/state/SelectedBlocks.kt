package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The blocks the user has picked as whole blocks (block selection mode), by block id. Transient
 * editor state: it is never stored with the note, and it is forgotten when the note is loaded, an
 * edit is undone or redone, or a field takes focus. Text selected inside one block is not part of
 * it; that stays the field's own.
 *
 * [blocks] is the editor's live list. A picked block that has left it (deleted, undone away) drops
 * out on the next [prune], and the mode ends when none is left.
 */
public class SelectedBlocks(private val blocks: List<Block>) {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())

    /** The ids of the picked blocks; empty while block selection mode is off. */
    public val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    // A range grows from the anchor towards the head with Shift+arrows.
    private var anchorId: String? = null
    private var headId: String? = null

    /** Whether block selection mode is on: at least one block is picked. */
    public val isActive: Boolean get() = _ids.value.isNotEmpty()

    /** How many blocks are picked. */
    public val count: Int get() = _ids.value.size

    /** The picked blocks, in the order of the note. */
    public fun picked(): List<Block> = _ids.value.let { ids -> blocks.filter { it.id in ids } }

    /** Starts block selection mode with [blockId] (a long press on its handle); the other picks are dropped. */
    public fun select(blockId: String) {
        if (blocks.none { it.id == blockId }) return
        anchorId = blockId
        headId = blockId
        _ids.value = setOf(blockId)
    }

    /** Picks [blockId], or lets it go when it is picked already; the mode ends with the last pick. */
    public fun toggle(blockId: String) {
        if (blocks.none { it.id == blockId }) return
        val ids = _ids.value
        if (blockId in ids) {
            _ids.value = ids - blockId
            if (_ids.value.isEmpty()) forgetRange() else repairRange()
        } else {
            if (ids.isEmpty()) anchorId = blockId
            headId = blockId
            _ids.value = ids + blockId
        }
    }

    /** Picks every block of the note. */
    public fun selectAll() {
        anchorId = blocks.first().id
        headId = blocks.last().id
        _ids.value = blocks.mapTo(mutableSetOf()) { it.id }
    }

    /**
     * Shift+Up ([up]) or Shift+Down: moves the end of the range one block, picking the block it
     * reaches, or letting go of the one it leaves when it moves back towards the anchor. Does
     * nothing outside block selection mode or past the first or last block.
     */
    public fun extend(up: Boolean) {
        if (!isActive) return
        val anchor = blocks.indexOfFirst { it.id == anchorId }
        val head = blocks.indexOfFirst { it.id == headId }
        val next = if (up) head - 1 else head + 1
        if (next !in blocks.indices) return
        val shrinking = if (up) head > anchor else head < anchor
        headId = blocks[next].id
        _ids.value = if (shrinking) _ids.value - blocks[head].id else _ids.value + blocks[next].id
    }

    /** Leaves block selection mode. */
    public fun clear() {
        _ids.value = emptySet()
        forgetRange()
    }

    /** Lets go of picked blocks that are not in the note any more. */
    internal fun prune() {
        if (!isActive) return
        val present = blocks.mapTo(mutableSetOf()) { it.id }
        val kept = _ids.value.filterTo(mutableSetOf()) { it in present }
        if (kept.size == _ids.value.size) return
        _ids.value = kept
        if (kept.isEmpty()) forgetRange() else repairRange()
    }

    // The range's ends must be picked blocks: after a pick is let go or lost they move to the nearest picked ones.
    private fun repairRange() {
        val picked = picked()
        if (picked.none { it.id == anchorId }) anchorId = picked.first().id
        if (picked.none { it.id == headId }) headId = picked.last().id
    }

    private fun forgetRange() {
        anchorId = null
        headId = null
    }
}
