package com.pasich.encly.data.importer

import com.pasich.encly.data.zip.RawInflater
import com.pasich.encly.data.zip.decodeCp437
import com.pasich.encly.data.zip.newCrc32
import com.pasich.encly.data.zip.newRawInflater
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.Source

/**
 * A sequential ZIP reader, because the platform's ZIP stream cannot read what streaming
 * writers produce: ZIP64 archives with a data descriptor (Notesnook's export, among others) fail
 * there with "invalid entry size". This reads local headers and entry data only, never the
 * central directory, so it needs no seeking and works on any stream.
 *
 * Supported: stored entries with known sizes and deflated entries, with or without a data
 * descriptor (32- or 64-bit sizes, signature or not, told apart by the sizes the inflater actually
 * produced). Encrypted entries and other methods are an [IOException]. Each entry's CRC-32 is
 * checked. Names are UTF-8 when the archive says so (or when they are valid UTF-8 anyway) and
 * CP437 otherwise, so different names stay different; paths are validated by the caller, never here.
 */
// One small format (header, ZIP64 extra, descriptor layouts, buffer); every corrupt archive is an IOException.
@Suppress("TooManyFunctions", "ThrowsCount")
internal class ZipStreamReader(private val input: Source) {
    class Entry(val name: String) {
        val isDirectory: Boolean get() = name.endsWith("/")
    }

    private class State(val flags: Int, val method: Int, val expectedCrc: Long, var compressed: Long, var size: Long) {
        val crc = newCrc32()
        val inflater = if (method == DEFLATED) newRawInflater() else null
        var fed = 0
        var storedLeft = size
        var done = false
    }

    private val buf = ByteArray(BUFFER)
    private val chunk = ByteArray(BUFFER)
    private var pos = 0
    private var lim = 0
    private var state: State? = null

    /**
     * The next entry, or null at the central directory, an end record or a clean end of the
     * stream; unread data of the last one is dropped. Any other bytes between entries are an
     * [IOException], so a damaged archive never looks like a finished one.
     */
    fun next(): Entry? {
        state?.let { drain(it) }
        state = null
        val signature = readIntOrNull()
        return when (signature) {
            null -> null
            LOCAL_HEADER -> open(readExact(LOCAL_FIXED))
            in END_SIGNATURES -> null
            else -> throw IOException("bad zip signature")
        }
    }

    /** The uncompressed data of the current entry. */
    fun data(): RawSource = object : RawSource {
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            if (byteCount == 0L) return 0
            val length = minOf(byteCount, chunk.size.toLong()).toInt()
            val n = state?.let { readData(it, chunk, 0, length) } ?: -1
            if (n > 0) sink.write(chunk, 0, n)
            return n.toLong()
        }

        override fun close() = Unit
    }

    private fun open(header: ByteArray): Entry {
        val flags = u16(header, FLAGS)
        val method = u16(header, METHOD)
        if (flags and ENCRYPTED != 0 ||
            (method != STORED && method != DEFLATED)
        ) {
            throw IOException("unsupported zip entry")
        }
        val name = decodeName(readExact(u16(header, NAME_LENGTH)))
        val extra = readExact(u16(header, EXTRA_LENGTH))
        var compressed = u32(header, COMPRESSED)
        var size = u32(header, SIZE)
        val zip64 = zip64Sizes(extra)
        if (zip64 != null && size == MAX_32) size = zip64.first
        if (zip64 != null && compressed == MAX_32) compressed = zip64.second
        if (method == STORED && flags and DESCRIPTOR != 0) throw IOException("stored zip entry of unknown size")
        state = State(flags, method, u32(header, CRC), compressed, size)
        return Entry(name)
    }

    /**
     * A name as UTF-8 if the bytes are valid UTF-8 (what the UTF-8 flag, bit 11, promises, and what
     * many writers produce without it), else as CP437, the format's default: a name that breaks
     * the promise still decodes to distinct text instead of U+FFFD.
     */
    private fun decodeName(bytes: ByteArray): String {
        val utf8 = try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            null
        }
        return utf8 ?: decodeCp437(bytes)
    }

    /** The (size, compressed size) of the ZIP64 extra field, if present. */
    private fun zip64Sizes(extra: ByteArray): Pair<Long, Long>? {
        var at = 0
        var found: Pair<Long, Long>? = null
        while (at + EXTRA_HEADER <= extra.size && found == null) {
            val id = u16(extra, at)
            val length = u16(extra, at + 2)
            if (id == ZIP64_ID && length >= ZIP64_SIZES && at + EXTRA_HEADER + ZIP64_SIZES <= extra.size) {
                found = u64(extra, at + EXTRA_HEADER) to u64(extra, at + EXTRA_HEADER + LONG)
            }
            at += EXTRA_HEADER + length
        }
        return found
    }

    private fun readData(entry: State, b: ByteArray, off: Int, len: Int): Int {
        var result = -1
        if (!entry.done && len > 0) {
            result =
                if (entry.inflater ==
                    null
                ) {
                    readStored(entry, b, off, len)
                } else {
                    inflate(entry, entry.inflater, b, off, len)
                }
            if (result > 0) entry.crc.update(b, off, result)
        }
        return result
    }

    private fun readStored(entry: State, b: ByteArray, off: Int, len: Int): Int {
        if (entry.storedLeft <= 0) return finish(entry)
        if (!fill()) throw IOException("truncated zip")
        val n = minOf(len.toLong(), entry.storedLeft, (lim - pos).toLong()).toInt()
        buf.copyInto(b, off, pos, pos + n)
        pos += n
        entry.storedLeft -= n
        return n
    }

    private fun inflate(entry: State, inflater: RawInflater, b: ByteArray, off: Int, len: Int): Int {
        while (true) {
            if (inflater.needsInput()) {
                sync(entry, inflater)
                if (!fill()) throw IOException("truncated zip")
                inflater.setInput(buf, pos, lim - pos)
                entry.fed = lim - pos
            }
            val n = inflater.inflate(b, off, len)
            when {
                n > 0 -> return n

                inflater.finished() -> {
                    sync(entry, inflater)
                    entry.compressed = inflater.bytesRead
                    entry.size = inflater.bytesWritten
                    return finish(entry)
                }

                inflater.needsDictionary() -> throw IOException("bad zip data")
            }
        }
    }

    /** Moves the read position past the input the inflater has used. */
    private fun sync(entry: State, inflater: RawInflater) {
        pos += entry.fed - inflater.remaining
        entry.fed = 0
    }

    /** The entry's data is over: read its descriptor if it has one and check the CRC. */
    private fun finish(entry: State): Int {
        entry.done = true
        entry.inflater?.end()
        val crc = if (entry.flags and DESCRIPTOR != 0) descriptor(entry) else entry.expectedCrc
        if (crc != entry.crc.value) throw IOException("zip checksum mismatch")
        return -1
    }

    /** The CRC from a data descriptor, in whichever of its four layouts matches the sizes just read. */
    private fun descriptor(entry: State): Long {
        val bytes = peek(DESCRIPTOR_MAX)
        val signed = bytes.size >= INT && le(bytes, 0, INT) == DESCRIPTOR_SIGNATURE
        val start = if (signed) INT else 0
        val wide = bytes.size >= start + INT + 2 * LONG &&
            le(bytes, start + INT, LONG) == entry.compressed && le(bytes, start + INT + LONG, LONG) == entry.size
        val narrow = bytes.size >= start + INT + 2 * INT &&
            le(bytes, start + INT, INT) == entry.compressed && le(bytes, start + 2 * INT, INT) == entry.size
        val length = start + INT + if (wide) 2 * LONG else 2 * INT
        if (!wide && !narrow) throw IOException("bad zip data descriptor")
        val crc = le(bytes, start, INT)
        pos += length
        return crc
    }

    /** Reads and drops what the caller left of an entry, at most [MAX_DRAIN] bytes: a directory with data is a bomb. */
    private fun drain(entry: State) {
        val sink = ByteArray(BUFFER)
        var dropped = 0L
        while (!entry.done) {
            dropped += readData(entry, sink, 0, sink.size).coerceAtLeast(0)
            if (dropped > MAX_DRAIN) throw IOException("zip entry skipped with too much data")
        }
    }

    // Buffer ------------------------------------------------------------------------------

    private fun fill(): Boolean {
        if (pos >= lim) {
            lim = input.readAtMostTo(buf, 0, buf.size).coerceAtLeast(0)
            pos = 0
        }
        return pos < lim
    }

    /** Up to [count] upcoming bytes, not consumed (fewer at the end of the stream). */
    private fun peek(count: Int): ByteArray {
        if (lim - pos < count) {
            buf.copyInto(buf, 0, pos, lim)
            lim -= pos
            pos = 0
            while (lim < count) {
                val n = input.readAtMostTo(buf, lim, buf.size)
                if (n < 0) break
                lim += n
            }
        }
        return buf.copyOfRange(pos, minOf(lim, pos + count))
    }

    private fun readExact(count: Int): ByteArray {
        val out = ByteArray(count)
        var done = 0
        while (done < count) {
            if (!fill()) throw IOException("truncated zip")
            val n = minOf(count - done, lim - pos)
            buf.copyInto(out, done, pos, pos + n)
            pos += n
            done += n
        }
        return out
    }

    private fun readIntOrNull(): Long? = if (fill()) le(readExact(INT), 0, INT) else null

    private fun u16(b: ByteArray, at: Int) = le(b, at, SHORT).toInt()

    private fun u32(b: ByteArray, at: Int) = le(b, at, INT)

    private fun u64(b: ByteArray, at: Int) = le(b, at, LONG)

    private fun le(b: ByteArray, at: Int, bytes: Int): Long {
        var value = 0L
        for (i in bytes - 1 downTo 0) value = (value shl BITS) or (b[at + i].toLong() and BYTE_MASK.toLong())
        return value
    }

    private companion object {
        const val BUFFER = 16 * 1024
        const val MAX_DRAIN = 1024 * 1024
        const val LOCAL_HEADER = 0x04034b50L
        const val DESCRIPTOR_SIGNATURE = 0x08074b50L

        // Central directory header, end of central directory (and its ZIP64 forms), archive extra data.
        val END_SIGNATURES = setOf(0x02014b50L, 0x06054b50L, 0x06064b50L, 0x07064b50L, 0x08064b50L)
        const val MAX_32 = 0xffffffffL
        const val STORED = 0
        const val DEFLATED = 8
        const val ENCRYPTED = 0x1
        const val DESCRIPTOR = 0x8
        const val ZIP64_ID = 1
        const val ZIP64_SIZES = 16
        const val EXTRA_HEADER = 4
        const val DESCRIPTOR_MAX = 24
        const val SHORT = 2
        const val INT = 4
        const val LONG = 8
        const val BITS = 8
        const val BYTE_MASK = 0xff

        // Local header (after the signature): offsets in the 26 fixed bytes.
        const val LOCAL_FIXED = 26
        const val FLAGS = 2
        const val METHOD = 4
        const val CRC = 10
        const val COMPRESSED = 14
        const val SIZE = 18
        const val NAME_LENGTH = 22
        const val EXTRA_LENGTH = 24
    }
}
