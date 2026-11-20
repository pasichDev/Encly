package com.pasich.encly.data.importer

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.Source

/**
 * A minimal sequential reader for POSIX ustar / GNU tar (Joplin's JEX export). It never seeks and
 * never allocates by a size a header claims: data is handed out as a stream bounded by that
 * size, and whatever the caller leaves unread is read and discarded. Headers are checksummed;
 * anything malformed is an [IOException].
 *
 * Supported: regular files, directories, GNU long names (`L`) and pax `path` records (`x`),
 * both bounded. Other types are reported as they are so the caller can refuse them.
 */
// One small format (header fields, padding, long names); a malformed header exits early.
@Suppress("TooManyFunctions", "ReturnCount")
internal class TarReader(private val input: Source) {
    class Header(val path: String, val type: Char)

    private var remaining = 0L
    private var padding = 0
    private var finished = false

    /** The next header, or null at the end of the archive. Leftover data of the last entry is discarded. */
    fun next(): Header? {
        if (finished) return null
        discard(remaining + padding)
        remaining = 0
        padding = 0
        var longName: String? = null
        while (true) {
            val block = readBlock() ?: return end()
            if (block.all { it == ZERO }) return end()
            if (!checksumOk(block)) throw IOException("bad tar checksum")
            val type = block[TYPE_OFFSET].toInt().toChar()
            val size = octal(block, SIZE_OFFSET, SIZE_LENGTH)
            when (type) {
                GNU_LONG_NAME, PAX_HEADER -> {
                    val data = readSmall(size)
                    longName = if (type == PAX_HEADER) paxPath(data) ?: longName else cString(data, 0, data.size)
                }

                PAX_GLOBAL -> discard(size + paddingFor(size))

                else -> {
                    remaining = size
                    padding = paddingFor(size)
                    return Header(longName ?: name(block), type)
                }
            }
        }
    }

    /** The data of the current entry; reading past its size is end of stream. */
    fun data(): RawSource = object : RawSource {
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            if (remaining <= 0) return -1
            if (byteCount == 0L) return 0
            val n = input.readAtMostTo(sink, minOf(byteCount, remaining))
            if (n < 0) throw IOException("truncated tar")
            remaining -= n
            return n
        }

        override fun close() = Unit
    }

    private fun end(): Header? {
        finished = true
        return null
    }

    private fun readBlock(): ByteArray? {
        val block = ByteArray(BLOCK)
        var read = 0
        while (read < BLOCK) {
            val n = input.readAtMostTo(block, read, BLOCK)
            if (n < 0) {
                if (read == 0) return null
                throw IOException("truncated tar")
            }
            read += n
        }
        return block
    }

    private fun readSmall(size: Long): ByteArray {
        if (size < 0 || size > MAX_META) throw IOException("tar header too long")
        val data = ByteArray(size.toInt())
        var read = 0
        while (read < data.size) {
            val n = input.readAtMostTo(data, read, data.size)
            if (n < 0) throw IOException("truncated tar")
            read += n
        }
        discard(paddingFor(size).toLong())
        return data
    }

    private fun discard(count: Long) {
        var left = count
        val sink = ByteArray(BLOCK)
        while (left > 0) {
            val n = input.readAtMostTo(sink, 0, minOf(left, sink.size.toLong()).toInt())
            if (n < 0) throw IOException("truncated tar")
            left -= n
        }
    }

    private fun paddingFor(size: Long): Int = ((BLOCK - size % BLOCK) % BLOCK).toInt()

    private fun name(block: ByteArray): String {
        val base = cString(block, 0, NAME_LENGTH)
        val ustar = block.decodeToString(MAGIC_OFFSET, MAGIC_OFFSET + USTAR.length) == USTAR
        val prefix = if (ustar) cString(block, PREFIX_OFFSET, PREFIX_LENGTH) else ""
        return if (prefix.isEmpty()) base else "$prefix/$base"
    }

    private fun cString(bytes: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && bytes[end] != ZERO) end++
        return bytes.decodeToString(offset, end)
    }

    private fun octal(block: ByteArray, offset: Int, length: Int): Long {
        // GNU base-256 sizes (high bit set) mean an entry far beyond any limit.
        if (block[offset].toInt() and HIGH_BIT != 0) throw IOException("tar entry too large")
        var value = 0L
        var i = offset
        while (i < offset + length && block[i].toInt().toChar() == ' ') i++
        while (i < offset + length) {
            val c = block[i].toInt().toChar()
            if (c == ' ' || c == '\u0000') break
            if (c !in '0'..'7') throw IOException("bad tar number")
            value = value * OCTAL + (c - '0')
            i++
        }
        return value
    }

    internal fun checksumOk(block: ByteArray): Boolean {
        var sum = 0L
        for (i in block.indices) {
            sum += if (i in CHECKSUM_OFFSET until CHECKSUM_OFFSET + CHECKSUM_LENGTH) {
                ' '.code
            } else {
                block[i].toInt() and BYTE_MASK
            }
        }
        return sum == octal(block, CHECKSUM_OFFSET, CHECKSUM_LENGTH)
    }

    /** The `path` of a pax extended header: records of `<length> <key>=<value>\n`. */
    private fun paxPath(data: ByteArray): String? {
        var pos = 0
        while (pos < data.size) {
            val space = data.indexOf(' '.code.toByte(), pos)
            if (space < 0) return null
            val length = data.decodeToString(pos, space).toIntOrNull() ?: return null
            if (length <= space - pos + 1 || pos + length > data.size) return null
            val record = data.decodeToString(space + 1, pos + length - 1)
            if (record.startsWith("path=")) return record.removePrefix("path=")
            pos += length
        }
        return null
    }

    private fun ByteArray.indexOf(value: Byte, from: Int): Int {
        for (i in from until size) if (this[i] == value) return i
        return -1
    }

    companion object {
        const val BLOCK = 512
        const val USTAR = "ustar"
        const val GNU_LONG_NAME = 'L'
        const val PAX_HEADER = 'x'
        const val PAX_GLOBAL = 'g'
        const val REGULAR = '0'
        const val REGULAR_OLD = '\u0000'
        const val DIRECTORY = '5'

        private const val ZERO: Byte = 0
        private const val NAME_LENGTH = 100
        private const val SIZE_OFFSET = 124
        private const val SIZE_LENGTH = 12
        private const val CHECKSUM_OFFSET = 148
        private const val CHECKSUM_LENGTH = 8
        private const val TYPE_OFFSET = 156
        private const val MAGIC_OFFSET = 257
        private const val PREFIX_OFFSET = 345
        private const val PREFIX_LENGTH = 155
        private const val MAX_META = 64L * 1024
        private const val OCTAL = 8
        private const val HIGH_BIT = 0x80
        private const val BYTE_MASK = 0xff

        /** Whether [head] (the first bytes of a file) is a valid tar header: a magic plus a checksum. */
        fun looksLikeTar(head: ByteArray): Boolean {
            if (head.size < BLOCK) return false
            if (head.decodeToString(MAGIC_OFFSET, MAGIC_OFFSET + USTAR.length) != USTAR) return false
            return runCatching { TarReader(Buffer()).checksumOk(head.copyOf(BLOCK)) }.getOrDefault(false)
        }
    }
}
