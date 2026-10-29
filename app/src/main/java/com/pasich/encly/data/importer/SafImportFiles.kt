package com.pasich.encly.data.importer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.pasich.encly.core.di.ApplicationContext
import dev.zacsweers.metro.Inject
import kotlinx.io.asSource

/**
 * The Storage Access Framework side of an import: the user's picked files or folder as an
 * [ImportFileSource]. Only [ContentResolver.openInputStream] is used (no file paths, seeking or
 * reliance on a provider's size), and a folder is walked with [DocumentsContract] up to the
 * depth and entry limits. The user's files are only ever read.
 */
@Inject
class SafImportFiles(@param:ApplicationContext private val context: Context) {

    fun files(uris: List<Uri>): ImportFileSource = ImportFileSource {
        uris.asSequence().map { uri -> SourceFile(displayName(uri) ?: FALLBACK_NAME) { open(uri) } }
    }

    fun tree(tree: Uri, limits: ImportLimits = ImportLimits()): ImportFileSource =
        TreeWalker(SafTree(tree, limits), limits).source()

    /**
     * The platform stream as a kotlinx-io source: the import logic never sees an [java.io.InputStream]
     * or a [SecurityException]. A provider that refuses access is an unreadable file, like a missing one.
     */
    private fun open(uri: Uri) = try {
        context.contentResolver.openInputStream(uri)?.asSource()
    } catch (_: SecurityException) {
        null
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** A picked folder through [DocumentsContract]; document ids are the provider's. */
    private inner class SafTree(private val tree: Uri, private val limits: ImportLimits) : DocumentTree {
        override val rootId: String = DocumentsContract.getTreeDocumentId(tree)

        /** Collected at once: a provider cursor must not stay open across a lazily consumed sequence. */
        override fun children(parentId: String): List<TreeNode> {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val found = ArrayList<TreeNode>()
            try {
                context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && found.size < limits.maxEntries) {
                        val directory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                        found += TreeNode(cursor.getString(0), cursor.getString(1).orEmpty(), directory)
                    }
                }
            } catch (_: SecurityException) {
                // A folder the provider will not list is skipped; the rest of the walk goes on.
            } catch (_: IllegalArgumentException) {
                // Same for a malformed document id.
            }
            return found
        }

        override fun open(id: String) = open(DocumentsContract.buildDocumentUriUsingTree(tree, id))
    }

    private companion object {
        const val FALLBACK_NAME = "file"
    }
}
