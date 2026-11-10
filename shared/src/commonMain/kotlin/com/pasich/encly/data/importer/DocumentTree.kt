package com.pasich.encly.data.importer

import kotlinx.io.RawSource

/** One entry of a [DocumentTree]; [id] is what the tree needs to list or open it. */
class TreeNode(val id: String, val name: String, val isDirectory: Boolean)

/**
 * A folder the user picked, as the Storage Access Framework shows it ([SafImportFiles]) or, in
 * tests, as a plain directory. Listing and opening are all the import needs.
 */
interface DocumentTree {
    val rootId: String

    fun children(parentId: String): List<TreeNode>

    fun open(id: String): RawSource?
}

/**
 * Walks a [DocumentTree] lazily into [SourceFile]s with paths relative to its root and never
 * listing more than [ImportLimits.maxEntries] entries in all, so a cyclic or enormous tree ends
 * instead of looping. Deeper than [ImportLimits.maxPathDepth] nothing is opened or listed: the entries
 * there are handed on as unsafe, so the reader counts them instead of silently losing them.
 * Media is never opened either; the reader recognises it by name.
 */
class TreeWalker(private val tree: DocumentTree, private val limits: ImportLimits = ImportLimits()) {
    fun source(): ImportFileSource = ImportFileSource {
        entriesLeft = limits.maxEntries
        files(tree.rootId, prefix = "", depth = 1)
    }

    private var entriesLeft = 0

    private fun files(parentId: String, prefix: String, depth: Int): Sequence<SourceFile> = sequence {
        for (node in tree.children(parentId).take(limits.maxEntries)) {
            if (entriesLeft-- <= 0) break
            val path = if (prefix.isEmpty()) node.name else "$prefix/${node.name}"
            when {
                depth > limits.maxPathDepth -> yield(SourceFile(path, unsafe = true) { null })
                node.isDirectory -> yieldAll(files(node.id, path, depth + 1))
                else -> yield(SourceFile(path) { tree.open(node.id) })
            }
        }
    }
}
