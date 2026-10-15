package com.pasich.encly.data.importer

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream

/**
 * One file the user picked, or one found while walking a picked folder. [open] is null if
 * unreadable. An [unsafe] file (a folder too deep to walk) is counted as an unsafe path and never opened.
 */
class SourceFile(val path: String, val unsafe: Boolean = false, val open: () -> InputStream?)

/** Where the files of a selection come from; the Storage Access Framework implementation is [SafImportFiles]. */
fun interface ImportFileSource {
    fun files(): Sequence<SourceFile>
}

/**
 * Entries read from a selection, plus what was left out on the way. [attachments] counts the
 * media files (images, audio, video, PDFs) that were passed over without being kept.
 */
class ReadResult(val entries: List<ImportEntry>, val skips: ReadSkips, val attachments: Int = 0)

/**
 * Turns a selection into in-memory [ImportEntry]s, bounded by [ImportLimits].
 *
 * Only streams are used (no paths, no seeking, no declared sizes): ZIP through
 * [ZipStreamReader], TAR through [TarReader], plain files as they are. Everything counts the
 * bytes actually read, so a ZIP bomb stops at the limit with [ImportError.TOO_LARGE]. Unsafe
 * paths, links and devices, archives inside archives and oversized files are skipped and counted;
 * nothing is ever written to disk. ZIP symbolic links cannot be told apart in a sequential read,
 * but they are harmless here: their target path is just a short text entry.
 *
 * Media and attachments (judged by name) are counted, never kept: a picked file or folder entry
 * is not even opened, a TAR member's data is discarded, and a ZIP member is inflated through a
 * small buffer because the next header cannot be found otherwise. What it inflates counts against
 * [ImportLimits.maxSkippedBytes], not [ImportLimits.maxTotalBytes], so a Takeout full of photos
 * fits and a bomb named `photo.jpg` still stops.
 */
class ImportReader(private val limits: ImportLimits = ImportLimits()) {

    fun read(source: ImportFileSource, checkCancelled: () -> Unit = {}): ReadResult =
        Session(limits, checkCancelled).run(source)

    // One read of a selection: the counters, the three archive formats and their skips live together.
    @Suppress("TooManyFunctions")
    private class Session(private val limits: ImportLimits, private val checkCancelled: () -> Unit) {
        private val entries = ArrayList<ImportEntry>()
        private var seen = 0
        private var attachments = 0
        private var skippedBytes = 0L
        private val scratch = ByteArray(BUFFER)
        private var inputBytes = 0L
        private var totalBytes = 0L
        private var unsafe = 0
        private var nested = 0
        private var tooLarge = 0
        private var unreadable = 0

        fun run(source: ImportFileSource): ReadResult {
            for (file in source.files()) {
                checkCancelled()
                if (!passedOver(file)) read(file)
            }
            return ReadResult(entries, ReadSkips(unsafe, nested, tooLarge, unreadable), attachments)
        }

        /** A file that is counted without being opened: one known to be unsafe, or media. */
        private fun passedOver(file: SourceFile): Boolean {
            val pass = file.unsafe || ImportKinds.isMedia(file.path)
            if (pass) {
                countEntry()
                if (file.unsafe) unsafe++ else attachment(file.path)
            }
            return pass
        }

        private fun read(file: SourceFile) {
            val stream = try {
                file.open()
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
            if (stream == null) {
                unreadable++
                return
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

        private fun process(path: String, input: InputStream) {
            val head = readHead(input)
            val whole = SequenceInputStream(ByteArrayInputStream(head), input)
            when {
                isZip(head) -> readZip(whole)

                TarReader.looksLikeTar(head) -> readTar(whole)

                else -> {
                    countEntry()
                    accept(path, whole)
                }
            }
        }

        private fun countEntry() {
            if (++seen > limits.maxEntries) throw ImportException(ImportError.TOO_LARGE)
        }

        /** A media file that is not kept: an attachment, unless it sits in a hidden folder or has an unsafe path. */
        private fun attachment(rawPath: String) {
            val path = safePath(rawPath, limits.maxPathDepth)
            when {
                path == null -> unsafe++
                !ImportKinds.isHidden(path) -> attachments++
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
                countEntry()
                when {
                    entry.isDirectory -> skipDirectory(zip.data())

                    ImportKinds.isMedia(entry.name) -> {
                        attachment(entry.name)
                        drain(zip.data())
                    }

                    else -> accept(entry.name, zip.data())
                }
            }
        }

        private fun readTar(input: InputStream) {
            val tar = TarReader(input)
            while (true) {
                checkCancelled()
                val header = tar.next() ?: return
                countEntry()
                when (header.type) {
                    TarReader.DIRECTORY -> Unit

                    // The reader discards the data of a member that is not read.
                    TarReader.REGULAR, TarReader.REGULAR_OLD -> {
                        val media = ImportKinds.isMedia(header.path)
                        if (media) attachment(header.path) else accept(header.path, tar.data())
                    }

                    // Links, devices, FIFOs: never followed.
                    else -> unsafe++
                }
            }
        }

        /** Reads [input] (always to its end, so the counters see everything) and keeps it if it is usable. */
        private fun accept(rawPath: String, input: InputStream) {
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
            // A directory carries no data; one that does is not a file to import.
            if (readBounded(input, 0) == null) unsafe++
        }

        /** Reads [input] to its end and throws it away, counting what it inflates against the skipped-data cap. */
        private fun drain(input: InputStream) {
            while (true) {
                val n = input.read(scratch)
                if (n < 0) return
                skippedBytes += n
                if (skippedBytes > limits.maxSkippedBytes) throw ImportException(ImportError.TOO_LARGE)
                checkCancelled()
            }
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
         * [raw] as a relative path with `.` and empty segments removed and `\` read as `/` (old
         * Windows tools write it), or null if it is unsafe: absolute, a drive (`C:` as the first
         * segment), `..` or NUL, or deeper than [maxDepth].
         */
        fun safePath(raw: String, maxDepth: Int = ImportLimits.MAX_PATH_DEPTH): String? {
            val normalized = raw.replace('\\', '/')
            val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }
            val unsafe = '\u0000' in raw || normalized.startsWith("/") || segments.isEmpty() ||
                segments.size > maxDepth || ".." in segments || DRIVE.matches(segments.first())
            return if (unsafe) null else segments.joinToString("/")
        }

        private val DRIVE = Regex("^[A-Za-z]:$")

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
