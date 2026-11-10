package com.pasich.encly.editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.TextualBlock
import com.pasich.encly.editor.model.normalizedLevels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A place in the note's text: block [blockId], a logical character [offset] into its text. In a list, [itemId] names
 * the item; without it the point is the list's first item when it starts a selection, its last when it ends one.
 */
public data class TextPoint(val blockId: String, val offset: Int, val itemId: String? = null)

/** The two ends of a text selection that may run across blocks: where it began and where it is now. */
public data class TextRangeEnds(val anchor: TextPoint, val head: TextPoint)

/**
 * Where Shift plus a movement key takes the end of a text selection. Previous and next are logical (towards the start
 * and towards the end of the stored text), so a right-to-left paragraph just swaps which arrow is which.
 */
public enum class HeadMove {
    PREVIOUS,
    NEXT,
    UP,
    DOWN,
    FIELD_START,
    FIELD_END,
    PAGE_UP,
    PAGE_DOWN,
    NOTE_START,
    NOTE_END,
}

/** How many fields Page Up and Page Down move the end of a selection. */
private const val PAGE_FIELDS = 5

/**
 * A text selection across blocks: from [TextRangeEnds.anchor] to [TextRangeEnds.head], in either direction. The
 * first and last fields (a paragraph, heading, quote or list item) are selected from and to their offsets, the
 * fields between them whole. Offsets are logical (positions in the stored text, never screen sides), so a block's
 * text direction does not matter. A link or a separator at an end, or between, is selected whole. Transient: never
 * stored with the note, forgotten when the note is loaded, an edit is undone or redone, or a field takes focus.
 */
@Suppress("TooManyFunctions") // One call for each way a selection is made, moved and read.
public class TextRangeSelection(private val blocks: List<Block>) {
    private val _ends = MutableStateFlow<TextRangeEnds?>(null)

    // The same value as snapshot state, so what draws the selection redraws when it changes.
    private var current by mutableStateOf<TextRangeEnds?>(null)

    /** The ends of the selection; null while there is none. */
    public val ends: StateFlow<TextRangeEnds?> = _ends.asStateFlow()

    /** Whether a selection exists. */
    public val isActive: Boolean get() = current != null

    private fun set(value: TextRangeEnds?) {
        current = value
        _ends.value = value
    }

    /** Selects from [anchor] to [head]; ignored when either block is not in the note. */
    public fun select(anchor: TextPoint, head: TextPoint) {
        if (indexOf(anchor.blockId) >= 0 && indexOf(head.blockId) >= 0) set(TextRangeEnds(anchor, head))
    }

    /** Moves the head to [head], keeping the anchor (Shift+click, Shift+arrows, dragging a handle). */
    public fun extendTo(head: TextPoint) {
        current?.let { select(it.anchor, head) }
    }

    /** Selects the whole note as text: from the start of its first block to the end of its last. */
    public fun selectAll() {
        select(TextPoint(blocks.first().id, 0), TextPoint(blocks.last().id, Int.MAX_VALUE))
    }

    /**
     * The start and the end of the selection in the order of the note, as points with offsets kept inside the text
     * and, in a list, the item; null without a selection or when an end is in a block that holds no text.
     */
    public fun orderedPoints(): Pair<TextPoint, TextPoint>? {
        val r = resolved() ?: return null
        val from = pointAt(r.first, r.firstItem, r.firstOffset)
        val to = pointAt(r.last, r.lastItem, r.lastOffset)
        val ends = listOfNotNull(from, to)
        return if (ends.size == 2) ends[0] to ends[1] else null
    }

    private fun pointAt(block: Int, item: Int, offset: Int): TextPoint? = when (val b = blocks[block]) {
        is TextualBlock -> TextPoint(b.id, offset)
        is Block.ListBlock -> TextPoint(b.id, offset, b.items.value.getOrNull(item)?.id)
        else -> null
    }

    /**
     * Whether the selection runs from one field into another. A cell of a table is a field here too, though a range
     * only ever takes a table whole (see [resolved]): what is typed over a selection from a cell into a paragraph
     * replaces the range, it does not go into the cell.
     */
    public val isCrossField: Boolean
        get() = range(trimTables = false)?.let { it.first != it.last || it.firstItem != it.lastItem } == true

    /**
     * Shift plus [move] in a field whose own selection runs from [anchor] to [caret] (the same point when it is
     * only a cursor), [onEdgeLine] being whether the cursor is on the field's first line (Up) or last (Down). Takes
     * the key, true, when it leaves the field or the selection already runs across fields: the end of the selection
     * moves. Otherwise false, and the field moves its own selection.
     */
    public fun extend(move: HeadMove, anchor: TextPoint, caret: TextPoint, onEdgeLine: Boolean): Boolean {
        val across = isCrossField
        val leaves = across || leavesField(move, caret, onEdgeLine)
        if (leaves && !across) select(anchor, caret)
        if (leaves) moveHead(move)
        return leaves
    }

    /** Moves the end of the selection by [move]; does nothing without a selection. */
    public fun moveHead(move: HeadMove) {
        val ends = current ?: return
        val head = ends.head
        val fields = textFields()
        val at = fieldIndex(fields, head)
        if (at < 0) return
        val offset = head.offset.coerceIn(0, fields[at].text.length)
        extendTo(target(move, fields, at, offset))
    }

    private fun target(move: HeadMove, fields: List<TextField>, at: Int, offset: Int): TextPoint = when (move) {
        HeadMove.PREVIOUS -> previous(fields, at, offset)
        HeadMove.NEXT -> next(fields, at, offset)
        HeadMove.UP -> pointIn(fields, at - 1, if (at > 0) offset else 0)
        HeadMove.DOWN -> pointIn(fields, at + 1, if (at < fields.lastIndex) offset else Int.MAX_VALUE)
        HeadMove.FIELD_START -> pointIn(fields, at, 0)
        HeadMove.FIELD_END -> pointIn(fields, at, Int.MAX_VALUE)
        HeadMove.PAGE_UP -> pointIn(fields, at - PAGE_FIELDS, offset)
        HeadMove.PAGE_DOWN -> pointIn(fields, at + PAGE_FIELDS, offset)
        HeadMove.NOTE_START -> pointIn(fields, 0, 0)
        HeadMove.NOTE_END -> pointIn(fields, fields.lastIndex, Int.MAX_VALUE)
    }

    private fun previous(fields: List<TextField>, at: Int, offset: Int): TextPoint = when {
        offset > 0 -> pointIn(fields, at, stepped(fields[at].text, offset, -1))
        at > 0 -> pointIn(fields, at - 1, Int.MAX_VALUE)
        else -> pointIn(fields, at, 0)
    }

    private fun next(fields: List<TextField>, at: Int, offset: Int): TextPoint = when {
        offset < fields[at].text.length -> pointIn(fields, at, stepped(fields[at].text, offset, 1))
        at < fields.lastIndex -> pointIn(fields, at + 1, 0)
        else -> pointIn(fields, at, Int.MAX_VALUE)
    }

    private fun leavesField(move: HeadMove, caret: TextPoint, onEdgeLine: Boolean): Boolean {
        val fields = textFields()
        val at = fieldIndex(fields, caret)
        val length = fields.getOrNull(at)?.let { it.text.length } ?: 0
        return when (move) {
            HeadMove.PREVIOUS -> at > 0 && caret.offset == 0
            HeadMove.NEXT -> at in 0 until fields.lastIndex && caret.offset >= length
            HeadMove.UP -> at > 0 && onEdgeLine
            HeadMove.DOWN -> at in 0 until fields.lastIndex && onEdgeLine
            HeadMove.PAGE_UP, HeadMove.PAGE_DOWN, HeadMove.NOTE_START, HeadMove.NOTE_END -> at >= 0
            HeadMove.FIELD_START, HeadMove.FIELD_END -> false
        }
    }

    private class TextField(val blockId: String, val itemId: String?, val text: String)

    private fun textFields(): List<TextField> = blocks.flatMap { block ->
        when (block) {
            is TextualBlock -> listOf(TextField(block.id, null, block.text.value))
            is Block.ListBlock -> block.items.value.map { TextField(block.id, it.id, it.value) }
            else -> emptyList()
        }
    }

    // The field of [point]; an unnamed point in a list is in its last item.
    private fun fieldIndex(fields: List<TextField>, point: TextPoint): Int {
        val named = fields.indexOfFirst { it.blockId == point.blockId && it.itemId == point.itemId }
        return if (named >= 0) named else fields.indexOfLast { it.blockId == point.blockId }
    }

    private fun pointIn(fields: List<TextField>, at: Int, offset: Int): TextPoint {
        val field = fields[at.coerceIn(0, fields.lastIndex)]
        return TextPoint(field.blockId, offset.coerceIn(0, field.text.length), field.itemId)
    }

    /** Offset [offset] moved one character in [direction], a surrogate pair counting as one. */
    private fun stepped(text: String, offset: Int, direction: Int): Int {
        val next = offset + direction
        return if (direction < 0) next.roundedDown(text) else next.roundedUp(text)
    }

    /** Forgets the selection. */
    public fun clear() {
        set(null)
    }

    /** How many blocks the selection touches (for "N blocks selected"); 0 without one. */
    public fun blockCount(): Int = resolved()?.let { it.last - it.first + 1 } ?: 0

    /**
     * The part of the field of block [blockId] (list item [itemId]) of text length [length] that the selection
     * covers, as a start and an end offset; null when it covers none of it.
     */
    public fun coverOf(blockId: String, itemId: String?, length: Int): Pair<Int, Int>? {
        val r = resolved() ?: return null
        val at = indexOf(blockId)
        val block = blocks.getOrNull(at)
        val item = if (block is Block.ListBlock) block.items.value.indexOfFirst { it.id == itemId } else -1
        val field = Field(at, item)
        val covered = field >= Field(r.first, r.firstItem) && field <= Field(r.last, r.lastItem)
        return if (covered) {
            val from = if (field == Field(r.first, r.firstItem)) r.firstOffset else 0
            val to = if (field == Field(r.last, r.lastItem)) r.lastOffset.coerceAtMost(length) else length
            from.coerceAtMost(to) to to
        } else {
            null
        }
    }

    /**
     * The selection in the order of the note with its offsets kept inside the text; null without one. A table at an
     * end of a selection that runs into another block is part of it only when the selection covers the whole table
     * (from the start of its first cell, or to the end of its last): a drag that ends in the middle of a table leaves
     * the table alone and selects only what lies outside it.
     */
    internal fun resolved(): ResolvedRange? = range(trimTables = true)

    private fun range(trimTables: Boolean): ResolvedRange? {
        val ends = current?.takeIf { indexOf(it.anchor.blockId) >= 0 && indexOf(it.head.blockId) >= 0 }
            ?: return null
        val ordered = ends.ordered().let { if (trimTables) withoutPartialTables(it) else it }
        return ordered?.let { placed(it, ends.head) }
    }

    // A table at an end of a selection across blocks, covered only in part, is left out: the selection then starts at
    // the block after it or ends at the block before it. Null when nothing is left.
    private fun withoutPartialTables(ordered: Pair<TextPoint, TextPoint>): Pair<TextPoint, TextPoint>? {
        val (start, end) = ordered
        val from = indexOf(start.blockId)
        val to = indexOf(end.blockId)
        if (from == to) return ordered
        val first = if (isPartialTable(from, start, atStart = true)) from + 1 else from
        val last = if (isPartialTable(to, end, atStart = false)) to - 1 else to
        return if (first > last) {
            null
        } else {
            (if (first == from) start else TextPoint(blocks[first].id, 0)) to
                (if (last == to) end else TextPoint(blocks[last].id, Int.MAX_VALUE))
        }
    }

    private fun isPartialTable(index: Int, point: TextPoint, atStart: Boolean): Boolean {
        val block = blocks[index] as? Block.TableBlock ?: return false
        val cells = block.table.value.rows.flatMap { it.cells }
        val cell = if (atStart) cells.first() else cells.last()
        val inEdgeCell = point.itemId == null || point.itemId == cell.id
        val atEdge = if (atStart) point.offset <= 0 else point.offset >= cell.value.length
        return !(inEdgeCell && atEdge)
    }

    /** [this] as (start, end) in the order of the note. */
    private fun TextRangeEnds.ordered(): Pair<TextPoint, TextPoint> {
        val a = fieldOf(anchor, isStart = true)
        val h = fieldOf(head, isStart = true)
        val forward = a < h || (a == h && anchor.offset <= head.offset)
        return if (forward) anchor to head else head to anchor
    }

    /** Where [point] is: its block and, in a list, its item (the first or, unless [isStart], the last when unnamed). */
    private fun fieldOf(point: TextPoint, isStart: Boolean): Field {
        val at = indexOf(point.blockId)
        val list = blocks[at] as? Block.ListBlock
        val named = list?.let { l -> l.items.value.indexOfFirst { it.id == point.itemId } } ?: -1
        val item = when {
            list == null -> -1
            named >= 0 -> named
            isStart -> 0
            else -> list.items.value.lastIndex
        }
        return Field(at, item)
    }

    private fun placed(ordered: Pair<TextPoint, TextPoint>, head: TextPoint): ResolvedRange {
        val (start, end) = ordered
        val from = fieldOf(start, isStart = true)
        val to = fieldOf(end, isStart = false)
        val firstText = textAt(from)
        val lastText = textAt(to)
        return ResolvedRange(
            first = from.block,
            firstItem = from.item,
            firstOffset = firstText?.let { start.offset.coerceIn(0, it.length).roundedDown(it) } ?: 0,
            last = to.block,
            lastItem = to.item,
            lastOffset = lastText?.let { end.offset.coerceIn(0, it.length).roundedUp(it) } ?: 0,
            head = head,
        )
    }

    private fun textAt(field: Field): String? = when (val block = blocks[field.block]) {
        is TextualBlock -> block.text.value
        is Block.ListBlock -> block.items.value.getOrNull(field.item)?.value
        else -> null
    }

    private fun indexOf(blockId: String): Int = blocks.indexOfFirst { it.id == blockId }
}

/** A field of the note by position: block [block] and, in a list, item [item] (-1 for any other block). */
private data class Field(val block: Int, val item: Int) : Comparable<Field> {
    override fun compareTo(other: Field): Int = compareValuesBy(this, other, Field::block, Field::item)
}

/**
 * A [TextRangeSelection] placed in the note: blocks [first] to [last] (in a list, items [firstItem] and [lastItem],
 * else -1), offsets into those two fields (0 if not text), and the [head] as it was given.
 */
@Suppress("LongParameterList") // Both ends of the selection, each as block, item and offset.
internal class ResolvedRange(
    val first: Int,
    val firstItem: Int,
    val firstOffset: Int,
    val last: Int,
    val lastItem: Int,
    val lastOffset: Int,
    val head: TextPoint,
)

/** [this] offset moved back out of the middle of a surrogate pair, so an emoji is never cut in two. */
private fun Int.roundedDown(text: String): Int = if (splitsPair(text)) this - 1 else this

/** [this] offset moved forward out of the middle of a surrogate pair: the emoji at the end is selected whole. */
private fun Int.roundedUp(text: String): Int = if (splitsPair(text)) this + 1 else this

private fun Int.splitsPair(text: String): Boolean =
    this in 1 until text.length && text[this - 1].isHighSurrogate() && text[this].isLowSurrogate()

/**
 * The selected part of the note as blocks of their own, nothing shared with the note: the first and last fields cut
 * to their offsets (in a list, the items outside the selection left out), everything between whole. Empty without a
 * selection. Hand them to a plain text or Markdown renderer to copy.
 */
public fun BlockEditorState.textRangeBlocks(): List<Block> {
    val r = textRange.resolved() ?: return emptyList()
    return (r.first..r.last).map { index ->
        val copy = blocks[index].duplicate()
        val from = if (index == r.first) r.firstOffset else 0
        val to = if (index == r.last) r.lastOffset else Int.MAX_VALUE
        when (copy) {
            is TextualBlock -> copy.text.value = copy.text.value.let { it.substring(from, to.coerceAtMost(it.length)) }

            // The copied items start at the top, whatever level the first one had.
            is Block.ListBlock -> copy.items.value = copy.items.value.slice(
                firstItem = if (index == r.first) r.firstItem else 0,
                firstOffset = from,
                lastItem = if (index == r.last) r.lastItem else copy.items.value.lastIndex,
                lastOffset = to,
            ).normalizedLevels(copy.blockType)

            else -> Unit
        }
        copy
    }
}

/** Items [firstItem] to [lastItem] of [this], the first cut from [firstOffset] and the last cut at [lastOffset]. */
private fun List<ItemListBlock>.slice(firstItem: Int, firstOffset: Int, lastItem: Int, lastOffset: Int) =
    subList(firstItem, lastItem + 1).mapIndexed { at, item ->
        val start = if (at == 0) firstOffset else 0
        val end = if (firstItem + at == lastItem) lastOffset.coerceAtMost(item.value.length) else item.value.length
        item.copy(value = item.value.substring(start.coerceAtMost(end), end))
    }

/** The selected part of the note as plain text (see [plainText]). */
public fun BlockEditorState.textRangePlainText(): String = textRangeBlocks().plainText()

/**
 * Replaces the selected text with [replacement] ("" deletes it) as one undo step; false without a selection. What
 * stays of the first field and of the last join into one around [replacement], and the blocks between are removed.
 * The first block's type wins (Notion's rule); where the first block is not text, the last one's does, and where
 * neither is, a paragraph holds the result. A list that the selection starts in keeps its earlier items and takes
 * the items after the selection's end; one it ends in, with a paragraph before it, stays as the items after the end.
 * The cursor goes after [replacement]; undo puts it back at the selection's head, redo after [replacement] again.
 * A selection that reaches a locked checklist, at an end or between, changes nothing and gives false, as in a locked
 * editor: the lock keeps the list's text out of reach, so it has to be unlocked first (see [canReplaceTextRange]).
 */
public fun BlockEditorState.replaceTextRange(replacement: String): Boolean {
    val r = textRange.resolved()
    if (r == null || touchesLockedList(r)) return false
    val plan = RangePlan(this, r, replacement)
    batch { plan.apply(this) }
    textRange.clear()
    val target = blocks.indexOfFirst { it.id == plan.keeper.id }
    attachCaretToLastStep(
        CaretTarget(r.head.blockId, r.head.itemId, r.head.offset),
        CaretTarget(plan.keeper.id, plan.keeperItem, plan.caret),
    )
    selection.focusAt(target, caret = plan.caret, itemId = plan.keeperItem)
    return true
}

/** Whether [replaceTextRange] can change the selection: there is one, and it reaches no locked checklist. */
public fun BlockEditorState.canReplaceTextRange(): Boolean =
    textRange.resolved()?.let { !touchesLockedList(it) } == true

private fun BlockEditorState.touchesLockedList(r: ResolvedRange): Boolean =
    (r.first..r.last).any { index -> blocks[index].let { it is Block.ListBlock && it.locked.value } }

/** What replacing a selection does: the block that holds the result, its new content and the blocks that go. */
private class RangePlan(state: BlockEditorState, private val r: ResolvedRange, replacement: String) {
    private val blocks = state.blocks
    private val first = blocks[r.first]
    private val last = blocks[r.last]
    private val prefix = fieldText(first, r.firstItem)?.substring(0, r.firstOffset).orEmpty()
    private val suffix = fieldText(last, r.lastItem)?.substring(r.lastOffset).orEmpty()
    private val joined = prefix + replacement + suffix
    private val startsInText = first is TextualBlock || first is Block.ListBlock
    private val endsInText = last is TextualBlock || last is Block.ListBlock
    private val isNew = !startsInText && !endsInText

    /** The block that holds the result. */
    val keeper: Block = when {
        startsInText -> first
        endsInText -> last
        else -> Block.TextBlock(MutableStateFlow(joined), EditorString.PRESS_TO_EDIT)
    }
    private val keeperIndex = if (!startsInText && endsInText) r.last else r.first
    private val keeperItemIndex = if (keeper === first) r.firstItem else r.lastItem
    private val keptList = keeper as? Block.ListBlock

    // Items of the last list after the selection's end; they stay, in the first list or as a list of their own.
    private val tail: List<ItemListBlock> =
        (last as? Block.ListBlock)?.let { it.items.value.drop(r.lastItem + 1) }.orEmpty()

    /** The list item the cursor goes into; null when the result is not in a list. */
    val keeperItem: String? = keptList?.let { it.items.value[keeperItemIndex].id }

    /** Where the cursor goes in the result, after the replacement. */
    val caret: Int = prefix.length + replacement.length

    fun apply(batch: BlockOperations.Batch) {
        // A paragraph that the selection starts in leaves the rest of a list it ends in as a list of its own.
        val keepsLast = tail.isNotEmpty() && keeper !== last && keptList == null
        when {
            keptList != null && keeper === first ->
                batch.setValue(
                    keptList.id,
                    keptList.items,
                    (keptList.items.value.take(r.firstItem) + item(keptList) + tail)
                        .normalizedLevels(keptList.blockType),
                )

            keptList != null -> batch.setValue(
                keptList.id,
                keptList.items,
                (listOf(item(keptList)) + tail).normalizedLevels(keptList.blockType),
            )

            isNew -> batch.replaceBlock(r.first, keeper)

            else -> batch.setValue(keeper.id, (keeper as TextualBlock).text, joined)
        }
        if (keepsLast) {
            val remaining = last as Block.ListBlock
            batch.setValue(remaining.id, remaining.items, tail.normalizedLevels(remaining.blockType))
        }
        // Highest first: a removal never shifts the position of one still to come.
        val gone = (r.first..r.last).filter { it != keeperIndex && !(keepsLast && it == r.last) }
        for (index in gone.asReversed()) batch.removeBlock(index)
    }

    private fun item(list: Block.ListBlock): ItemListBlock = list.items.value[keeperItemIndex].copy(value = joined)
}

private fun fieldText(block: Block, item: Int): String? = when (block) {
    is TextualBlock -> block.text.value
    is Block.ListBlock -> block.items.value.getOrNull(item)?.value
    else -> null
}
