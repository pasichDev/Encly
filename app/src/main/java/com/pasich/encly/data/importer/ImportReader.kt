package com.pasich.encly.data.importer

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream

/** One file the user picked, or one found while walking a picked folder. [open] is null if unreadable. */
class SourceFile(val path: String, val open: () -> InputStream?)

/** Where the files of a selection come from; the Storage Access Framework implementation is [SafImportFiles]. */
fun interface ImportFileSource {
    fun files(): Sequence<SourceFile>
}

/** Entries read from a selection, plus what was left out on the way. */
class ReadResult(val entries: List<ImportEntry>, val skips: ReadSkips)

/**
 * Turns a selection into in-memory [ImportEntry]s, bounded by [ImportLimits].
 *
 * Only streams are used (no paths, no seeking, no declared sizes): ZIP through
 * [ZipStreamReader], TAR through [TarReader], plain files as they are. Everything counts the
 * bytes actually read, so a ZIP bomb stops at the limit with [ImportError.TOO_LARGE]. Unsafe
 * paths, links and devices, archives inside archives and oversized files are skipped and counted;
 * nothing is ever written to disk. ZIP symbolic links cannot be told apart in a sequential read,
 * but they are harmless here: their target path is just a short text entry.
 */
class ImportReader(private val limits: ImportLimits = ImportLimits()) {

    fun read(source: ImportFileSource, checkCancelled: () -> Unit = {}): ReadResult =
        Session(limits, checkCancelled).run(source)

    private class Session(private val limits: ImportLimits, private val checkCancelled: () -> Unit) {
        private val entries = ArrayList<ImportEntry>()
        private var seen = 0
        private var inputBytes = 0L
        private var totalBytes = 0L
        private var unsafe = 0
        private var nested = 0
        private var tooLarge = 0
        private var unreadable = 0

        fun run(source: ImportFileSource): ReadResult {
            for (file in source.files()) {
                checkCancelled()
                val stream = try {
                    file.open()
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
                if (stream == null) {
                    unreadable++
                    continue
                }
                try {
                    stream.use { process(file.path, Counting(it)) }
                } catch (_: IOException) {
                    unreadable++
                } catch (_: IllegalArgumentException) {
                    // A ZIP entry name that is not valid text.
                    unreadable++
                }
            }
            return ReadResult(entries, ReadSkips(unsafe, nested, tooLarge, unreadable))
        }

        private fun process(path: String, input: InputStream) {
            val head = readHead(input)
            val whole = SequenceInputStream(ByteArrayInputStream(head), input)
            when {
                isZip(head) -> readZip(whole)
                TarReader.looksLikeTar(head) -> readTar(whole)
                else -> accept(path, whole)
            }
        }

        /** Up to [TarReader.BLOCK] bytes of [input]: enough to tell a ZIP or TAR from a plain file. */
        private fun readHead(input: InputStream): ByteArray {
            val head = ByteArray(TarReader.BLOCK)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
            return head.copyOf(read)
        }

        private fun readZip(input: InputStream) {
            val zip = ZipStreamReader(input)
            while (true) {
                checkCancelled()
                val entry = zip.next() ?: return
                if (entry.isDirectory) skipDirectory(zip.data()) else accept(entry.name, zip.data())
            }
        }

        private fun readTar(input: InputStream) {
            val tar = TarReader(input)
            while (true) {
                checkCancelled()
                val header = tar.next() ?: return
                when (header.type) {
                    TarReader.DIRECTORY -> Unit

                    TarReader.REGULAR, TarReader.REGULAR_OLD -> accept(header.path, tar.data())

                    // Links, devices, FIFOs: never followed.
                    else -> unsafe++
                }
            }
        }

        /** Reads [input] (always to its end, so the counters see everything) and keeps it if it is usable. */
        private fun accept(rawPath: String, input: InputStream) {
            if (++seen > limits.maxEntries) throw ImportException(ImportError.TOO_LARGE)
            val path = safePath(rawPath, limits.maxPathDepth)
            val cap = if (path != null && isContainerName(path)) limits.maxContainerBytes else limits.maxEntryBytes
            val bytes = readBounded(input, cap)
            when {
                path == null -> unsafe++
                bytes == null -> tooLarge++
                looksLikeArchive(bytes) -> nested++
                else -> entries += ImportEntry(path, bytes)
            }
        }

        /**
         * A ZIP directory entry: it counts as an entry and whatever data it carries is inflated
         * against the total, so many directories with data cannot inflate past the limits.
         */
        private fun skipDirectory(input: InputStream) {
            if (++seen > limits.maxEntries) throw ImportException(ImportError.TOO_LARGE)
            // A directory carries no data; one that does is not a file to import.
            if (readBounded(input, 0) == null) unsafe++
        }

        /** The bytes of [input], or null if it is longer than [cap]; the rest is still read and counted. */
        private fun readBounded(input: InputStream, cap: Int): ByteArray? {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            var over = false
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                totalBytes += n
                if (totalBytes > limits.maxTotalBytes) throw ImportException(ImportError.TOO_LARGE)
                if (!over) {
                    if (out.size() + n > cap) over = true else out.write(buffer, 0, n)
                }
                checkCancelled()
            }
            return if (over) null else out.toByteArray()
        }

        private inner class Counting(input: InputStream) : FilterInputStream(input) {
            override fun read(): Int {
                val b = super.read()
                if (b >= 0) count(1)
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) count(n.toLong())
                return n
            }

            override fun markSupported(): Boolean = false

            private fun count(n: Long) {
                inputBytes += n
                if (inputBytes > limits.maxInputBytes) throw ImportException(ImportError.TOO_LARGE)
            }
        }
    }

    companion object {
        private const val BUFFER = 16 * 1024
        private const val BACKUP_FILE_PREFIX = "Standard Notes Backup and Import File"

        /** Files that legitimately hold many notes in one: JSON exports. */
        internal fun isContainerName(path: String): Boolean {
            val name = path.substringAfterLast('/')
            return name.endsWith(".json", ignoreCase = true) || name.startsWith(BACKUP_FILE_PREFIX)
        }

        /**
         * [raw] as a relative path with `.` and empty segments removed, or null if it is unsafe:
         * absolute, a drive letter, `..`, a backslash or NUL, or deeper than [maxDepth].
         */
        fun safePath(raw: String, maxDepth: Int = ImportLimits.MAX_PATH_DEPTH): String? {
            val segments = raw.split('/').filter { it.isNotEmpty() && it != "." }
            val unsafe = raw.any { it == '\u0000' || it == '\\' } || raw.startsWith("/") ||
                DRIVE.containsMatchIn(raw) || segments.isEmpty() || segments.size > maxDepth || ".." in segments
            return if (unsafe) null else segments.joinToString("/")
        }

        private val DRIVE = Regex("^[A-Za-z]:")

        private fun isZip(head: ByteArray): Boolean = head.size >= ZIP_MAGIC.size &&
            ZIP_MAGIC.indices.all { head[it] == ZIP_MAGIC[it] }

        /** ZIP, TAR and gzip content: archives inside archives are not expanded. */
        private fun looksLikeArchive(bytes: ByteArray): Boolean = isZip(bytes) ||
            (bytes.size >= 2 && bytes[0] == GZIP_0 && bytes[1] == GZIP_1) ||
            TarReader.looksLikeTar(bytes)

        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        private const val GZIP_0: Byte = 0x1f
        private const val GZIP_1: Byte = 0x8b.toByte()
    }
}
