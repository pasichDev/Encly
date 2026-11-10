package com.pasich.encly.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.pasich.encly.core.di.ApplicationContext
import com.pasich.encly.data.importer.DocumentTree
import com.pasich.encly.data.importer.TreeNode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.asSink
import kotlinx.io.asSource

/**
 * [PlatformDocuments] over the Storage Access Framework: the documents the user picked through the system
 * dialogs, reached by their content URI. Only [android.content.ContentResolver] streams are used (no file
 * paths, seeking or reliance on a provider's size), and a folder is walked with [DocumentsContract]. A
 * refused grant (a [SecurityException]) is reported as an [IOException] without its message.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
class AndroidDocuments(@param:ApplicationContext private val context: Context) : PlatformDocuments {

    override fun openRead(doc: DocumentRef): RawSource? =
        refusedAsIo { context.contentResolver.openInputStream(doc.uri())?.asSource() }

    // "wt": the document is empty, but some providers need an explicit truncate mode.
    override fun openWrite(doc: DocumentRef): RawSink? =
        refusedAsIo { context.contentResolver.openOutputStream(doc.uri(), "wt")?.asSink() }

    override fun displayName(doc: DocumentRef): String? = try {
        context.contentResolver.query(doc.uri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    override fun delete(doc: DocumentRef) {
        DocumentsContract.deleteDocument(context.contentResolver, doc.uri())
    }

    override fun openTree(root: DocumentRef, maxEntries: Int): DocumentTree = SafTree(root.uri(), maxEntries)

    private fun DocumentRef.uri(): Uri = value.toUri()

    private inline fun <T> refusedAsIo(block: () -> T): T = try {
        block()
    } catch (_: SecurityException) {
        throw IOException("access to the document was refused")
    }

    /** A picked folder through [DocumentsContract]; document ids are the provider's. */
    private inner class SafTree(private val tree: Uri, private val maxEntries: Int) : DocumentTree {
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
                    while (cursor.moveToNext() && found.size < maxEntries) {
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

        /** A file the provider refuses to open is an unreadable one, like a missing one. */
        override fun open(id: String): RawSource? = try {
            context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.asSource()
        } catch (_: SecurityException) {
            null
        }
    }
}
