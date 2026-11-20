package com.pasich.encly.data.importer

import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.PlatformDocuments
import dev.zacsweers.metro.Inject

/**
 * The platform side of an import: the user's picked files or folder as an [ImportFileSource]. Only
 * [PlatformDocuments.openRead] and the folder listing are used (no file paths, seeking or reliance on a
 * provider's size), and a folder is walked up to the depth and entry limits. The user's files are only
 * ever read.
 */
@Inject
class ImportDocuments(private val documents: PlatformDocuments) {

    fun files(docs: List<DocumentRef>): ImportFileSource = ImportFileSource {
        docs.asSequence().map { doc ->
            SourceFile(displayName(doc) ?: FALLBACK_NAME) { documents.openRead(doc) }
        }
    }

    fun tree(root: DocumentRef, limits: ImportLimits = ImportLimits()): ImportFileSource =
        TreeWalker(documents.openTree(root, limits.maxEntries), limits).source()

    /** A provider that will not tell the name (or refuses access) leaves the file unnamed. */
    private fun displayName(doc: DocumentRef): String? = try {
        documents.displayName(doc)
    } catch (_: RuntimeException) {
        null
    }

    private companion object {
        const val FALLBACK_NAME = "file"
    }
}
