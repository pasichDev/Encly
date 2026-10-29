package com.pasich.encly.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.asSource
import kotlinx.io.readTo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes backup files at a Storage Access Framework [Uri] the user picked. Only the
 * already encrypted container passes through here: no plaintext and no temp file is ever
 * written, and no storage permission is needed.
 */
@Singleton
class BackupDocuments @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun read(uri: Uri): ByteArray = io {
        context.contentResolver.openInputStream(uri).orFail().use { readCapped(it.asSource()) }
    }

    /** The document's display name, for showing which file was picked; null when unknown. */
    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    /**
     * Writes [file] to a document the picker just created, then reads it back to check it.
     *
     * An existing, non-empty document is refused with [BackupError.TARGET_EXISTS] instead of
     * being overwritten: truncating it first would destroy the previous backup whenever the new
     * write then fails (storage full, provider error, revoked grant). On any failure the
     * incomplete new document is deleted where the provider allows it.
     */
    fun write(uri: Uri, file: ByteArray) = io {
        val resolver = context.contentResolver
        if (hasContent(uri)) throw BackupException(BackupError.TARGET_EXISTS)
        var complete = false
        try {
            // "wt": the document is empty, but some providers need an explicit truncate mode.
            resolver.openOutputStream(uri, "wt").orFail().use { it.write(file) }
            val written = resolver.openInputStream(uri).orFail().use { readCapped(it.asSource()) }
            if (!written.contentEquals(file)) throw BackupException(BackupError.IO)
            complete = true
        } finally {
            if (!complete) discard(uri)
        }
    }

    private fun hasContent(uri: Uri): Boolean =
        context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } ?: false

    /** Best effort: a provider may not support deleting, and the write error is what matters. */
    private fun discard(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    private fun <T : AutoCloseable> T?.orFail(): T = this ?: throw BackupException(BackupError.IO)

    /**
     * Maps every provider failure to [BackupError.IO]. Third-party DocumentsProviders also throw
     * RuntimeExceptions across Binder (IllegalArgumentException "Unknown URI", unsupported mode,
     * IllegalStateException...). Their messages are dropped: they can quote the document path.
     */
    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (_: IOException) {
        throw BackupException(BackupError.IO)
    } catch (_: SecurityException) {
        // The picked document is no longer accessible (e.g. its provider revoked the grant).
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
