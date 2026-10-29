package com.pasich.encly.data.importer

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray

/**
 * One file the user picked, or one found while walking a picked folder. [open] is null if
 * unreadable. An [unsafe] file (a folder too deep to walk) is counted as an unsafe path and never opened.
 */
class SourceFile(val path: String, val unsafe: Boolean = false, val open: () -> RawSource?)

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
        private val scratch = Buffer()
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
                Counting(stream).buffered().use { process(file.path, it) }
            } catch (_: IOException) {
                unreadable++
            } catch (_: IllegalArgumentException) {
                // A ZIP entry name that is not valid text.
                unreadable++
            }
        }

        private fun process(path: String, input: Source) {
            val head = readHead(input)
            when {
                isZip(head) -> readZip(input)

                TarReader.looksLikeTar(head) -> readTar(input)

                else -> {
                    countEntry()
                    accept(path, input)
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
        private fun readHead(input: Source): ByteArray {
            val head = ByteArray(TarReader.BLOCK)
            // Peeked, not consumed: the archive readers below start at the first byte.
            val peek = input.peek()
            var read = 0
            while (read < head.size) {
                val n = peek.readAtMostTo(head, read, head.size)
                if (n < 0) break
                read += n
            }
            return head.copyOf(read)
        }

        private fun readZip(input: Source) {
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

        private fun readTar(input: Source) {
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
        private fun accept(rawPath: String, input: RawSource) {
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
        private fun skipDirectory(input: RawSource) {
            // A directory carries no data; one that does is not a file to import.
            if (readBounded(input, 0) == null) unsafe++
        }

        /** Reads [input] to its end and throws it away, counting what it inflates against the skipped-data cap. */
        private fun drain(input: RawSource) {
            while (true) {
                val n = input.readAtMostTo(scratch, BUFFER.toLong())
                if (n < 0) return
                scratch.clear()
                skippedBytes += n
                if (skippedBytes > limits.maxSkippedBytes) throw ImportException(ImportError.TOO_LARGE)
                checkCancelled()
            }
        }

        /** The bytes of [input], or null if it is longer than [cap]; the rest is still read and counted. */
        private fun readBounded(input: RawSource, cap: Int): ByteArray? {
            val out = Buffer()
            val chunk = Buffer()
            var over = false
            while (true) {
                val n = input.readAtMostTo(chunk, BUFFER.toLong())
                if (n < 0) break
                totalBytes += n
                if (totalBytes > limits.maxTotalBytes) throw ImportException(ImportError.TOO_LARGE)
                if (!over) {
                    if (out.size + n > cap) over = true else chunk.transferTo(out)
                }
                chunk.clear()
                checkCancelled()
            }
            return if (over) null else out.readByteArray()
        }

        /** Counts the bytes taken from the file itself against [ImportLimits.maxInputBytes]. */
        private inner class Counting(private val upstream: RawSource) : RawSource {
            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                val n = upstream.readAtMostTo(sink, byteCount)
                if (n > 0) {
                    inputBytes += n
                    if (inputBytes > limits.maxInputBytes) throw ImportException(ImportError.TOO_LARGE)
                }
                return n
            }

            override fun close() = upstream.close()
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
