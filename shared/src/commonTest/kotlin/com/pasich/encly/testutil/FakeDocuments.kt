package com.pasich.encly.testutil

import com.pasich.encly.data.importer.DocumentTree
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.PlatformDocuments
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.readByteArray

/**
 * The picked documents in memory. A document nobody wrote to is empty (as the one the save dialog just
 * created). The failures are set per test.
 */
internal class FakeDocuments : PlatformDocuments {
    val files = mutableMapOf<DocumentRef, ByteArray>()
    val names = mutableMapOf<DocumentRef, String>()
    val deleted = mutableListOf<DocumentRef>()
    var opensForWrite = 0
        private set

    /** Thrown by [openRead] and [displayName]. */
    var readFailure: Throwable? = null

    /** Thrown by [openWrite]. */
    var writeFailure: Throwable? = null

    /** [openRead] has no stream to give. */
    var noStream = false

    /** A provider that silently loses the last byte of what is written. */
    var losesLastByte = false

    /** The folders [openTree] hands out. */
    val trees = mutableMapOf<DocumentRef, DocumentTree>()

    override fun openRead(doc: DocumentRef): RawSource? {
        readFailure?.let { throw it }
        if (noStream) return null
        return Buffer().apply { write(files[doc] ?: ByteArray(0)) }
    }

    override fun openWrite(doc: DocumentRef): RawSink? {
        writeFailure?.let { throw it }
        opensForWrite++
        val written = Buffer()
        return object : RawSink {
            override fun write(source: Buffer, byteCount: Long) = written.write(source, byteCount)

            override fun flush() = Unit

            override fun close() {
                val bytes = written.readByteArray()
                files[doc] = if (losesLastByte) bytes.copyOf(bytes.size - 1) else bytes
            }
        }
    }

    override fun displayName(doc: DocumentRef): String? {
        readFailure?.let { throw it }
        return names[doc]
    }

    override fun delete(doc: DocumentRef) {
        deleted += doc
        files.remove(doc)
    }

    override fun openTree(root: DocumentRef, maxEntries: Int): DocumentTree = trees.getValue(root)
}
