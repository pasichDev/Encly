package com.pasich.encly.data.export

import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.PlatformDocuments
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.buffered

/**
 * Writes the plaintext (Markdown) export to a document the user just created in the system save
 * dialog. The content is streamed straight into that document: no temp file, no cache or files
 * directory, no share intent, and no storage permission.
 */
@SingleIn(AppScope::class)
@Inject
class MarkdownDocuments(private val documents: PlatformDocuments) {

    /**
     * Streams what [block] writes into the document at [doc]. A document that already holds data
     * is refused with [BackupError.TARGET_EXISTS] instead of being overwritten. On any failure the
     * incomplete document is deleted where the provider allows it. Provider failures become
     * [BackupError.IO] without their message, which can quote the document's path.
     */
    fun write(doc: DocumentRef, block: (Sink) -> Unit) = io {
        if (hasContent(doc)) throw BackupException(BackupError.TARGET_EXISTS)
        var complete = false
        try {
            val sink = documents.openWrite(doc) ?: throw BackupException(BackupError.IO)
            sink.buffered().use(block)
            complete = true
        } finally {
            if (!complete) discard(doc)
        }
    }

    /**
     * Best effort: removes the empty document the picker created when nothing could be written to
     * it. A document that holds data is never deleted: a provider may have handed back an existing
     * file of the user's, and an unreadable one is left alone too.
     */
    fun discard(doc: DocumentRef) {
        runCatching { if (!hasContent(doc)) documents.delete(doc) }
    }

    private fun hasContent(doc: DocumentRef): Boolean =
        documents.openRead(doc)?.use { it.readAtMostTo(Buffer(), 1) >= 0 } ?: false

    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: BackupException) {
        throw e
    } catch (_: IOException) {
        throw BackupException(BackupError.IO)
    } catch (_: RuntimeException) {
        // Third-party providers throw RuntimeExceptions across Binder (SecurityException when the grant
        // was revoked among them).
        throw BackupException(BackupError.IO)
    }
}
