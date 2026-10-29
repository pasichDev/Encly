package com.pasich.encly.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.buffered

/**
 * Writes the plaintext (Markdown) export to a Storage Access Framework document the user just
 * created in the system save dialog. The content is streamed straight into that document: no
 * temp file, no cache or files directory, no share intent, and no storage permission.
 */
@SingleIn(AppScope::class)
@Inject
class MarkdownDocuments(@param:ApplicationContext private val context: Context) {

    /**
     * Streams what [block] writes into the document at [uri]. A document that already holds data
     * is refused with [BackupError.TARGET_EXISTS] instead of being overwritten. On any failure the
     * incomplete document is deleted where the provider allows it. Provider failures become
     * [BackupError.IO] without their message, which can quote the document's path.
     */
    fun write(uri: Uri, block: (Sink) -> Unit) = io {
        if (hasContent(uri)) throw BackupException(BackupError.TARGET_EXISTS)
        var complete = false
        try {
            // "wt": the document is empty, but some providers need an explicit truncate mode.
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw BackupException(BackupError.IO)
            stream.asSink().buffered().use(block)
            complete = true
        } finally {
            if (!complete) discard(uri)
        }
    }

    /**
     * Best effort: removes the empty document the picker created when nothing could be written to
     * it. A document that holds data is never deleted: a provider may have handed back an existing
     * file of the user's, and an unreadable one is left alone too.
     */
    fun discard(uri: Uri) {
        runCatching { if (!hasContent(uri)) DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    private fun hasContent(uri: Uri): Boolean =
        context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } ?: false

    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: BackupException) {
        throw e
    } catch (_: IOException) {
        throw BackupException(BackupError.IO)
    } catch (_: SecurityException) {
        // The picked document is no longer accessible (its provider revoked the grant).
        throw BackupException(BackupError.IO)
    } catch (_: RuntimeException) {
        // Third-party providers throw RuntimeExceptions across Binder.
        throw BackupException(BackupError.IO)
    }
}
