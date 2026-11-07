package com.pasich.encly.editor.model

// Changing the levels and the places of list items: indenting, moving among siblings, finding where a drag landed, and
// making a sub-list of another kind. Each takes rows with valid levels (see normalizedLevels) and returns new rows.

/**
 * The rows with the subtree of the row at [index] indented one level ([deeper]) or outdented. Null when it
 * cannot: the first row and a row already one deeper than the row before it cannot go deeper, nothing
 * goes past [MAX_LIST_LEVEL], and a row at level 0 cannot come out. [this] must have valid levels.
 */
public fun List<ItemListBlock>.shifted(index: Int, deeper: Boolean, listType: BlockType): List<ItemListBlock>? {
    val item = getOrNull(index)
    val end = if (item == null) index else subtreeEnd(index)
    val allowed = item != null && if (deeper) {
        index > 0 && item.level <= this[index - 1].level && (index until end).none { this[it].level >= MAX_LIST_LEVEL }
    } else {
        item.level > 0
    }
    val delta = if (deeper) 1 else -1
    return if (!allowed) {
        null
    } else {
        mapIndexed { at, row -> if (at in index until end) row.copy(level = row.level + delta) else row }
            .normalizedLevels(listType)
    }
}

/**
 * The rows with the subtree of the row at [index] moved [offset] places among its siblings (up is
 * negative), and the row's new position. Null when there is no sibling that far. [this] must have valid levels.
 */
public fun List<ItemListBlock>.movedBy(index: Int, offset: Int): Pair<List<ItemListBlock>, Int>? {
    if (index !in indices || offset == 0) return null
    val siblings = siblingStarts(index)
    val at = siblings.indexOf(index)
    val target = at + offset
    return if (target !in siblings.indices) {
        null
    } else {
        val end = subtreeEnd(index)
        val moved = subList(index, end)
        val rest = take(index) + drop(end)
        val insertAt = if (target < at) siblings[target] else subtreeEnd(siblings[target]) - moved.size
        (rest.take(insertAt) + moved + rest.drop(insertAt)) to insertAt
    }
}

/** How many siblings from the row at [index] the position [y] is: negative above it, positive below. */
public fun List<ItemListBlock>.siblingOffsetAt(
    index: Int,
    rowTops: List<Float>,
    rowBottoms: List<Float>,
    y: Float,
): Int {
    if (rowTops.size < size || rowBottoms.size < size) return 0
    val siblings = siblingStarts(index)
    // The sibling whose rows (with its children) the position is in, else the nearest end.
    val target = siblings.indices.firstOrNull { s ->
        y in rowTops[siblings[s]]..rowBottoms[subtreeEnd(siblings[s]) - 1]
    } ?: if (y < rowTops[siblings.first()]) 0 else siblings.lastIndex
    return target - siblings.indexOf(index)
}

/** The rows with the kind of the row at [index] and its siblings set to [kind]; null when that changes nothing. */
public fun List<ItemListBlock>.withSiblingsOfKind(
    index: Int,
    kind: BlockType,
    listType: BlockType,
): List<ItemListBlock>? {
    val item = getOrNull(index)
    if (item == null || item.level == 0 || item.kindIn(listType) == kind) return null
    val siblings = siblingStarts(index).toSet()
    return mapIndexed { at, row -> if (at in siblings) row.copy(kind = kind) else row }.normalizedLevels(listType)
}
