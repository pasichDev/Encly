package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.PlatformDocuments
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readTo

/**
 * Reads and writes backup files at a [DocumentRef] the user picked (the system's document dialog). Only the
 * already encrypted container passes through here: no plaintext and no temp file is ever
 * written, and no storage permission is needed.
 */
@SingleIn(AppScope::class)
@Inject
class BackupDocuments(private val documents: PlatformDocuments) {
    fun read(doc: DocumentRef): ByteArray = io {
        documents.openRead(doc).orFail().use { readCapped(it) }
    }

    /** The document's display name, for showing which file was picked; null when unknown. */
    fun displayName(doc: DocumentRef): String? = runCatching { documents.displayName(doc) }.getOrNull()

    /**
     * Writes [file] to a document the picker just created, then reads it back to check it.
     *
     * An existing, non-empty document is refused with [BackupError.TARGET_EXISTS] instead of
     * being overwritten: truncating it first would destroy the previous backup whenever the new
     * write then fails (storage full, provider error, revoked grant). On any failure the
     * incomplete new document is deleted where the provider allows it.
     */
    fun write(doc: DocumentRef, file: ByteArray) = io {
        if (hasContent(doc)) throw BackupException(BackupError.TARGET_EXISTS)
        var complete = false
        try {
            documents.openWrite(doc).orFail().buffered().use { it.write(file) }
            val written = documents.openRead(doc).orFail().use { readCapped(it) }
            if (!written.contentEquals(file)) throw BackupException(BackupError.IO)
            complete = true
        } finally {
            if (!complete) discard(doc)
        }
    }

    private fun hasContent(doc: DocumentRef): Boolean =
        documents.openRead(doc)?.use { it.readAtMostTo(Buffer(), 1) >= 0 } ?: false

    /** Best effort: a provider may not support deleting, and the write error is what matters. */
    private fun discard(doc: DocumentRef) {
        runCatching { documents.delete(doc) }
    }

    private fun <T : AutoCloseable> T?.orFail(): T = this ?: throw BackupException(BackupError.IO)

    /**
     * Maps every provider failure to [BackupError.IO]. Third-party DocumentsProviders also throw
     * RuntimeExceptions across Binder (IllegalArgumentException "Unknown URI", unsupported mode,
     * IllegalStateException, SecurityException when the grant was revoked...). Their messages are
     * dropped: they can quote the document path.
     */
    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (_: IOException) {
        throw BackupException(BackupError.IO)
    } catch (_: RuntimeException) {
        throw BackupException(BackupError.IO)
    }

    internal companion object {
        private const val BUFFER_BYTES = 64 * 1024

        /**
         * Reads one backup file of at most [limit] bytes. The buffer is sized from the header's
         * ciphertext length, so the file is held in memory exactly once. Anything that is not a
         * backup, declares more than [limit] bytes, or runs past its declared end is rejected
         * without reading the rest; a file shorter than declared is returned for
         * [BackupFormat.parse] to report as truncated. [input] is read unbuffered, so nothing
         * past what was needed is taken from the provider.
         */
        fun readCapped(input: RawSource, limit: Int = BackupFormat.MAX_FILE_BYTES): ByteArray {
            val head = ByteArray(BackupFormat.HEADER_LENGTH)
            val headRead = fill(input, head, 0)
            val file = if (headRead < head.size) {
                head.copyOf(headRead)
            } else {
                val header = BackupFormat.parseHeader(head, limit.toLong())
                head.copyOf(BackupFormat.HEADER_LENGTH + header.ciphertextLength)
            }
            val filled = fill(input, file, headRead)
            return when {
                filled < file.size -> file.copyOf(filled)
                input.readAtMostTo(Buffer(), 1) >= 0 -> throw BackupException(BackupError.CORRUPTED)
                else -> file
            }
        }

        /** Reads into [target] from [from] until it is full or the stream ends; returns the end. */
        private fun fill(input: RawSource, target: ByteArray, from: Int): Int {
            val chunk = Buffer()
            var position = from
            while (position < target.size) {
                val read = input.readAtMostTo(chunk, minOf(BUFFER_BYTES, target.size - position).toLong()).toInt()
                if (read < 0) break
                chunk.readTo(target, position, position + read)
                position += read
            }
            return position
        }
    }
}
