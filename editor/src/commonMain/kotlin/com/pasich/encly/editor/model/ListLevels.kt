package com.pasich.encly.editor.model

// The rules of nested list items. A list is flat in storage and in the editor's state: a row's
// [ItemListBlock.level] says how deep it sits, and its children are the rows after it with a
// deeper level. These functions work on rows in that order and never change their text.

/** The deepest level a row can have: three levels in all (0, 1 and 2). */
public const val MAX_LIST_LEVEL: Int = 2

private val LIST_KINDS = setOf(BlockType.LIST_CHECK, BlockType.LIST_BULLET, BlockType.LIST_NUMBER)

/** Whether [this] is a kind of list (a checklist, a bulleted or a numbered list). */
public fun BlockType.isListKind(): Boolean = this in LIST_KINDS

/** The kind this row shows inside a list of [listType]: its own at a nested level, else the list's. */
public fun ItemListBlock.kindIn(listType: BlockType): BlockType = if (level > 0) kind ?: listType else listType

/**
 * The rows with valid levels: the first row at 0, every other at most one deeper than the row
 * before it and at most [MAX_LIST_LEVEL], and a [ItemListBlock.kind] only where it matters (a nested row
 * whose kind differs from [listType]). The same list when nothing needs fixing. Cutting a list,
 * removing a row or reading a damaged note can leave levels that are not valid; this is the one place
 * they are repaired.
 */
public fun List<ItemListBlock>.normalizedLevels(listType: BlockType): List<ItemListBlock> {
    var previous = -1
    var changed = false
    val fixed = map { item ->
        val level = item.level.coerceIn(0, minOf(MAX_LIST_LEVEL, previous + 1))
        val kind = item.kind.takeIf { level > 0 && it != listType && it?.isListKind() == true }
        previous = level
        if (level == item.level && kind == item.kind) {
            item
        } else {
            changed = true
            item.copy(level = level, kind = kind)
        }
    }
    return if (changed) fixed else this
}

/** One past the last row of the subtree of the row at [index]: the rows after it that are deeper. */
public fun List<ItemListBlock>.subtreeEnd(index: Int): Int {
    var end = index + 1
    while (end < size && this[end].level > this[index].level) end++
    return end
}

/**
 * The positions of the rows that share a parent with the row at [index] (itself included), in order. Each
 * starts a subtree of the same level, which moves as a whole.
 */
public fun List<ItemListBlock>.siblingStarts(index: Int): List<Int> {
    val level = this[index].level
    var from = index
    while (from > 0 && this[from - 1].level >= level) from--
    var to = index + 1
    while (to < size && this[to].level >= level) to++
    return (from until to).filter { this[it].level == level }
}

/**
 * Each row's number in its numbered run, 0 for a row that is not numbered. A run is the rows of one level
 * and one parent that are numbered one after another: it starts again at 1 under every new parent, and after a
 * row of another kind at its level.
 */
public fun List<ItemListBlock>.numbers(listType: BlockType): List<Int> {
    val counter = IntArray(MAX_LIST_LEVEL + 1)
    val runKind = arrayOfNulls<BlockType>(MAX_LIST_LEVEL + 1)
    return map { item ->
        val level = item.level.coerceIn(0, MAX_LIST_LEVEL)
        val kind = item.kindIn(listType)
        for (deeper in level + 1..MAX_LIST_LEVEL) {
            counter[deeper] = 0
            runKind[deeper] = null
        }
        counter[level] = if (runKind[level] == kind) counter[level] + 1 else 1
        runKind[level] = kind
        if (kind == BlockType.LIST_NUMBER) counter[level] else 0
    }
}

/** [this] row placed where [other] is: at its level and of its kind. */
internal fun ItemListBlock.alongside(other: ItemListBlock): ItemListBlock = copy(level = other.level, kind = other.kind)
