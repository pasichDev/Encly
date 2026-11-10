@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("MagicNumber", "TooManyFunctions") // The offsets and flags of the ZIP format; one seam, many parts.

package com.pasich.encly.data.zip

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.files.Path
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe
import platform.posix.O_RDONLY
import platform.posix.SEEK_END
import platform.posix.close
import platform.posix.lseek
import platform.posix.memcpy
import platform.posix.memset
import platform.posix.open
import platform.posix.pread
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NEED_DICT
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import kotlin.time.Instant

/*
 * The iOS side of ZipPlatform.kt. zlib comes with the system (`platform.zlib`) and does the deflating and
 * inflating; the container format (local headers, central directory, ZIP64 sizes of single entries) is read and
 * written here, because iOS has no ZIP API. Not supported, and refused with an IOException instead of read wrongly:
 * encrypted entries, compression methods other than stored and deflate, archives that need the ZIP64 end record
 * (more than 65,534 entries or a directory past 4 GiB), and several-disk archives.
 */

private const val RAW_WINDOW_BITS = 15
private const val CHUNK = 8 * 1024
private const val BAD_DATA = "bad zip data"

private const val SIG_LOCAL = 0x04034b50
private const val SIG_CENTRAL = 0x02014b50
private const val SIG_END = 0x06054b50
private const val END_RECORD_SIZE = 22
private const val MAX_COMMENT = 0xFFFF
private const val CENTRAL_HEADER_SIZE = 46
private const val LOCAL_HEADER_SIZE = 30
private const val MAX_CENTRAL_BYTES = 256L * 1024 * 1024
private const val METHOD_STORED = 0
private const val METHOD_DEFLATED = 8
private const val FLAG_ENCRYPTED = 0x0001
private const val FLAG_UTF8 = 0x0800
private const val MASK_32 = 0xFFFFFFFFL
private const val MAX_32 = 0xFFFFFFFFL
private const val MAX_16 = 0xFFFF
private const val ZIP64_EXTRA = 0x0001
private const val VERSION_NEEDED = 20
private const val DOS_EPOCH_YEAR = 1980
private const val DOS_MAX_YEAR = 2107

// -- CRC-32 ------------------------------------------------------------------------------------------------------

private val CRC_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
    c
}

internal actual fun newCrc32(): Crc32 = object : Crc32 {
    private var crc = -1

    override val value: Long get() = crc.inv().toLong() and MASK_32

    override fun update(bytes: ByteArray, offset: Int, length: Int) {
        var c = crc
        for (i in offset until offset + length) c = CRC_TABLE[(c xor bytes[i].toInt()) and 0xFF] xor (c ushr 8)
        crc = c
    }
}

// -- Inflate -----------------------------------------------------------------------------------------------------

internal actual fun newRawInflater(): RawInflater = ZlibInflater()

private class ZlibInflater : RawInflater {
    private val stream = nativeHeap.alloc<z_stream>()
    private var input: CPointer<ByteVar>? = null
    private var done = false
    private var dictionaryNeeded = false
    private var ended = false

    init {
        memset(stream.ptr, 0, sizeOf<z_stream>().convert())
        val status = inflateInit2_(stream.ptr, -RAW_WINDOW_BITS, ZLIB_VERSION, sizeOf<z_stream>().toInt())
        if (status != Z_OK) {
            nativeHeap.free(stream.rawPtr)
            ended = true
            throw IOException(BAD_DATA)
        }
    }

    override val remaining: Int get() = if (ended) 0 else stream.avail_in.toInt()
    override val bytesRead: Long get() = if (ended) 0 else stream.total_in.toLong()
    override val bytesWritten: Long get() = if (ended) 0 else stream.total_out.toLong()

    override fun needsInput(): Boolean = !ended && !done && stream.avail_in == 0u

    override fun needsDictionary(): Boolean = dictionaryNeeded

    override fun finished(): Boolean = done

    override fun setInput(bytes: ByteArray, offset: Int, length: Int) {
        check(!ended) { "inflater ended" }
        input?.let { nativeHeap.free(it.rawValue) }
        input = null
        if (length > 0) {
            val copy = nativeHeap.allocArray<ByteVar>(length)
            bytes.usePinned { memcpy(copy, it.addressOf(offset), length.convert()) }
            input = copy
        }
        stream.next_in = input?.reinterpret<UByteVar>()
        stream.avail_in = length.convert()
    }

    override fun inflate(bytes: ByteArray, offset: Int, length: Int): Int {
        check(!ended) { "inflater ended" }
        if (length == 0 || done) return 0
        val produced = bytes.usePinned { pinned ->
            stream.next_out = pinned.addressOf(offset).reinterpret<UByteVar>()
            stream.avail_out = length.convert()
            when (inflate(stream.ptr, Z_NO_FLUSH)) {
                Z_STREAM_END -> done = true

                Z_NEED_DICT -> dictionaryNeeded = true

                // Z_BUF_ERROR: no progress was possible (more input is needed); the caller sees 0.
                Z_OK, platform.zlib.Z_BUF_ERROR -> Unit

                else -> throw IOException(BAD_DATA)
            }
            length - stream.avail_out.toInt()
        }
        return produced
    }

    override fun end() {
        if (ended) return
        ended = true
        inflateEnd(stream.ptr)
        input?.let { nativeHeap.free(it.rawValue) }
        input = null
        nativeHeap.free(stream.rawPtr)
    }
}

// -- File names --------------------------------------------------------------------------------------------------

private const val CP437_HIGH =
    "ÇüéâäàåçêëèïîìÄÅ" +
        "ÉæÆôöòûùÿÖÜ¢£¥₧ƒ" +
        "áíóúñÑªº¿⌐¬½¼¡«»" +
        "░▒▓│┤╡╢╖╕╣║╗╝╜╛┐" +
        "└┴┬├─┼╞╟╚╔╩╦╠═╬╧" +
        "╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
        "αßΓπΣσµτΦΘΩδ∞φε∩" +
        "≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ "

internal actual fun decodeCp437(bytes: ByteArray): String = buildString(bytes.size) {
    for (byte in bytes) {
        val code = byte.toInt() and 0xFF
        append(if (code < 0x80) code.toChar() else CP437_HIGH[code - 0x80])
    }
}

// -- Reading -----------------------------------------------------------------------------------------------------

/** A file read at absolute offsets (`pread`), closed once. */
private class RandomAccessFile(path: String) : AutoCloseable {
    private val fd: Int = open(path, O_RDONLY)
    private var closed = false
    val size: Long

    init {
        if (fd < 0) throw IOException("cannot open the archive")
        size = lseek(fd, 0, SEEK_END)
        if (size < 0) {
            close(fd)
            throw IOException("cannot read the archive")
        }
    }

    /** Fills [target] from [offset]; throws if the file ends first. */
    fun readFully(offset: Long, target: ByteArray, start: Int = 0, count: Int = target.size - start) {
        var done = 0
        while (done < count) {
            val read = readAtMost(offset + done, target, start + done, count - done)
            if (read <= 0) throw IOException("the archive is cut short")
            done += read
        }
    }

    /** Up to [count] bytes from [offset] into [target]; 0 at the end of the file. */
    fun readAtMost(offset: Long, target: ByteArray, start: Int, count: Int): Int {
        check(!closed) { "archive closed" }
        if (count == 0) return 0
        val read = target.usePinned { pread(fd, it.addressOf(start), count.convert(), offset) }.toInt()
        if (read < 0) throw IOException("cannot read the archive")
        return read
    }

    override fun close() {
        if (!closed) {
            closed = true
            close(fd)
        }
    }
}

private fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.u32(at: Int): Long = (u16(at).toLong()) or (u16(at + 2).toLong() shl 16)

private fun ByteArray.u64(at: Int): Long = u32(at) or (u32(at + 4) shl 32)

private class CentralEntry(
    val name: String,
    val method: Int,
    val flags: Int,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localOffset: Long,
)

internal actual fun openZipFile(path: Path): ZipEntrySource {
    val file = RandomAccessFile(path.toString())
    var opened = false
    try {
        return IosZipSource(file, readCentralDirectory(file)).also { opened = true }
    } finally {
        if (!opened) file.close()
    }
}

@Suppress("ThrowsCount") // Each check of the end record fails in its own way.
private fun readCentralDirectory(file: RandomAccessFile): Map<String, CentralEntry> {
    val tailSize = minOf(file.size, (END_RECORD_SIZE + MAX_COMMENT).toLong()).toInt()
    if (tailSize < END_RECORD_SIZE) throw IOException("not a zip file")
    val tail = ByteArray(tailSize)
    file.readFully(file.size - tailSize, tail)
    var end = tailSize - END_RECORD_SIZE
    while (end >= 0 && tail.u32(end) != SIG_END.toLong()) end--
    if (end < 0) throw IOException("not a zip file")
    val disk = tail.u16(end + 4)
    val entries = tail.u16(end + 10)
    val size = tail.u32(end + 12)
    val offset = tail.u32(end + 16)
    if (disk != 0 || tail.u16(end + 6) != 0) throw IOException("zip archives of several disks are not supported")
    if (entries == MAX_16 || size == MAX_32 || offset == MAX_32) throw IOException("zip64 archives are not supported")
    if (size > MAX_CENTRAL_BYTES || offset + size > file.size) throw IOException("bad zip data")
    val directory = ByteArray(size.toInt())
    file.readFully(offset, directory)
    val result = LinkedHashMap<String, CentralEntry>(entries)
    var at = 0
    repeat(entries) {
        if (at + CENTRAL_HEADER_SIZE > directory.size || directory.u32(at) != SIG_CENTRAL.toLong()) {
            throw IOException(BAD_DATA)
        }
        val entry = parseCentralEntry(directory, at)
        result.putIfAbsentFirst(entry)
        at += CENTRAL_HEADER_SIZE + directory.u16(at + 28) + directory.u16(at + 30) + directory.u16(at + 32)
    }
    return result
}

private fun MutableMap<String, CentralEntry>.putIfAbsentFirst(entry: CentralEntry) {
    if (!containsKey(entry.name)) this[entry.name] = entry
}

@Suppress("CyclomaticComplexMethod") // The fields of one central directory record, then its ZIP64 extra.
private fun parseCentralEntry(directory: ByteArray, at: Int): CentralEntry {
    val flags = directory.u16(at + 8)
    val method = directory.u16(at + 10)
    var compressed = directory.u32(at + 20)
    var uncompressed = directory.u32(at + 24)
    val nameLength = directory.u16(at + 28)
    val extraLength = directory.u16(at + 30)
    var localOffset = directory.u32(at + 42)
    val nameStart = at + CENTRAL_HEADER_SIZE
    val extraStart = nameStart + nameLength
    if (extraStart + extraLength > directory.size) throw IOException(BAD_DATA)
    val nameBytes = directory.copyOfRange(nameStart, extraStart)
    val name = if (flags and FLAG_UTF8 != 0) nameBytes.decodeToString() else decodeCp437(nameBytes)
    // The ZIP64 extra holds, in this order, only the values the record set to 0xFFFFFFFF.
    var extra = extraStart
    while (extra + 4 <= extraStart + extraLength) {
        val id = directory.u16(extra)
        val length = directory.u16(extra + 2)
        if (id == ZIP64_EXTRA) {
            var field = extra + 4
            if (uncompressed == MAX_32) uncompressed = directory.u64(field).also { field += 8 }
            if (compressed == MAX_32) compressed = directory.u64(field).also { field += 8 }
            if (localOffset == MAX_32) localOffset = directory.u64(field)
        }
        extra += 4 + length
    }
    return CentralEntry(name, method, flags, compressed, uncompressed, localOffset)
}

private class IosZipSource(private val file: RandomAccessFile, private val entries: Map<String, CentralEntry>) :
    ZipEntrySource {
    override val entryCount: Int get() = entries.size

    override fun find(name: String): ZipEntryHandle? =
        (entries[name] ?: entries["$name/"])?.let { entry -> IosZipEntry(file, entry) }

    override fun close() = file.close()
}

private class IosZipEntry(private val file: RandomAccessFile, private val entry: CentralEntry) : ZipEntryHandle {
    override val name: String get() = entry.name
    override val isDirectory: Boolean get() = entry.name.endsWith("/")
    override val declaredSize: Long get() = entry.uncompressedSize

    override fun open(): RawSource {
        if (entry.flags and FLAG_ENCRYPTED != 0) throw IOException("encrypted zip entries are not supported")
        if (entry.method != METHOD_STORED && entry.method != METHOD_DEFLATED) {
            throw IOException("unsupported zip compression method")
        }
        val header = ByteArray(LOCAL_HEADER_SIZE)
        file.readFully(entry.localOffset, header)
        if (header.u32(0) != SIG_LOCAL.toLong()) throw IOException(BAD_DATA)
        val dataStart = entry.localOffset + LOCAL_HEADER_SIZE + header.u16(26) + header.u16(28)
        if (dataStart + entry.compressedSize > file.size) throw IOException(BAD_DATA)
        return EntrySource(file, dataStart, entry.compressedSize, entry.method == METHOD_DEFLATED)
    }
}

/** The bytes of one entry: copied as they are (stored) or inflated, a piece per read. */
private class EntrySource(
    private val file: RandomAccessFile,
    private var position: Long,
    private var compressedLeft: Long,
    deflated: Boolean,
) : RawSource {
    private val inflater: RawInflater? = if (deflated) newRawInflater() else null
    private val compressed = ByteArray(CHUNK)
    private val plain = ByteArray(CHUNK)
    private var closed = false

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        check(!closed) { "source closed" }
        require(byteCount >= 0) { "byteCount < 0: $byteCount" }
        if (byteCount == 0L) return 0
        val max = minOf(byteCount, CHUNK.toLong()).toInt()
        return if (inflater == null) readStored(sink, max) else readInflated(inflater, sink, max)
    }

    private fun readStored(sink: Buffer, max: Int): Long {
        if (compressedLeft == 0L) return -1
        val count = minOf(max.toLong(), compressedLeft).toInt()
        val read = file.readAtMost(position, plain, 0, count)
        if (read <= 0) throw IOException(BAD_DATA)
        position += read
        compressedLeft -= read
        sink.write(plain, 0, read)
        return read.toLong()
    }

    @Suppress("ReturnCount", "ThrowsCount") // The states of a streaming inflate.
    private fun readInflated(inflater: RawInflater, sink: Buffer, max: Int): Long {
        while (true) {
            if (inflater.finished()) return -1
            if (inflater.needsInput()) {
                if (compressedLeft == 0L) throw IOException(BAD_DATA)
                val read = file.readAtMost(position, compressed, 0, minOf(CHUNK.toLong(), compressedLeft).toInt())
                if (read <= 0) throw IOException(BAD_DATA)
                position += read
                compressedLeft -= read
                inflater.setInput(compressed, 0, read)
            }
            val produced = inflater.inflate(plain, 0, max)
            if (inflater.needsDictionary()) throw IOException(BAD_DATA)
            if (produced > 0) {
                sink.write(plain, 0, produced)
                return produced.toLong()
            }
            if (inflater.finished()) return -1
            // No output and no progress to hope for: all input is used up but the stream did not end.
            if (!inflater.needsInput() && inflater.remaining == 0 && compressedLeft == 0L) throw IOException(BAD_DATA)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        inflater?.end()
    }
}

// -- Writing -----------------------------------------------------------------------------------------------------

internal actual fun zipWriter(out: Sink, now: () -> Long): ZipEntrySink = IosZipWriter(out, now)

private class IosZipWriter(private val out: Sink, private val now: () -> Long) : ZipEntrySink {
    @Suppress("LongParameterList") // One local header, kept for the central directory.
    private class Written(
        val name: ByteArray,
        val method: Int,
        val dosTime: Int,
        val dosDate: Int,
        val crc: Long,
        val compressedSize: Int,
        val size: Int,
        val offset: Long,
    )

    private val entries = ArrayList<Written>()
    private var position = 0L

    override fun put(name: String, time: Long?, bytes: ByteArray) {
        val nameBytes = name.encodeToByteArray()
        val crc = newCrc32().also { it.update(bytes, 0, bytes.size) }.value
        val deflated = if (bytes.isEmpty()) bytes else deflateRaw(bytes)
        val method = if (bytes.isEmpty()) METHOD_STORED else METHOD_DEFLATED
        val (dosDate, dosTime) = dosDateTime(time ?: now())
        val entry = Written(nameBytes, method, dosTime, dosDate, crc, deflated.size, bytes.size, position)
        out.writeIntLe(SIG_LOCAL)
        out.writeShortLe(VERSION_NEEDED.toShort())
        out.writeShortLe(FLAG_UTF8.toShort())
        out.writeShortLe(method.toShort())
        out.writeShortLe(dosTime.toShort())
        out.writeShortLe(dosDate.toShort())
        out.writeIntLe(crc.toInt())
        out.writeIntLe(deflated.size)
        out.writeIntLe(bytes.size)
        out.writeShortLe(nameBytes.size.toShort())
        out.writeShortLe(0)
        out.write(nameBytes)
        out.write(deflated)
        position += LOCAL_HEADER_SIZE + nameBytes.size + deflated.size
        if (position > MAX_32 || entries.size >= MAX_16 - 1) throw IOException("the archive is too large for a zip")
        entries += entry
    }

    override fun finish() {
        val directoryStart = position
        for (entry in entries) {
            out.writeIntLe(SIG_CENTRAL)
            out.writeShortLe(VERSION_NEEDED.toShort())
            out.writeShortLe(VERSION_NEEDED.toShort())
            out.writeShortLe(FLAG_UTF8.toShort())
            out.writeShortLe(entry.method.toShort())
            out.writeShortLe(entry.dosTime.toShort())
            out.writeShortLe(entry.dosDate.toShort())
            out.writeIntLe(entry.crc.toInt())
            out.writeIntLe(entry.compressedSize)
            out.writeIntLe(entry.size)
            out.writeShortLe(entry.name.size.toShort())
            out.writeShortLe(0)
            out.writeShortLe(0)
            out.writeShortLe(0)
            out.writeShortLe(0)
            out.writeIntLe(0)
            out.writeIntLe(entry.offset.toInt())
            out.write(entry.name)
            position += CENTRAL_HEADER_SIZE + entry.name.size
        }
        val directorySize = position - directoryStart
        out.writeIntLe(SIG_END)
        out.writeShortLe(0)
        out.writeShortLe(0)
        out.writeShortLe(entries.size.toShort())
        out.writeShortLe(entries.size.toShort())
        out.writeIntLe(directorySize.toInt())
        out.writeIntLe(directoryStart.toInt())
        out.writeShortLe(0)
        out.flush()
    }
}

/** [bytes] as a raw deflate stream (no zlib header), in memory. */
private fun deflateRaw(bytes: ByteArray): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    memset(stream.ptr, 0, sizeOf<z_stream>().convert())
    val init = deflateInit2_(
        stream.ptr,
        Z_DEFAULT_COMPRESSION,
        Z_DEFLATED,
        -RAW_WINDOW_BITS,
        8,
        Z_DEFAULT_STRATEGY,
        ZLIB_VERSION,
        sizeOf<z_stream>().toInt(),
    )
    if (init != Z_OK) throw IOException("cannot compress")
    try {
        val bound = deflateBound(stream.ptr, bytes.size.convert()).toInt()
        val output = ByteArray(bound)
        bytes.usePinned { input ->
            output.usePinned { target ->
                stream.next_in = input.addressOf(0).reinterpret<UByteVar>()
                stream.avail_in = bytes.size.convert()
                stream.next_out = target.addressOf(0).reinterpret<UByteVar>()
                stream.avail_out = bound.convert()
                if (deflate(stream.ptr, Z_FINISH) != Z_STREAM_END) throw IOException("cannot compress")
            }
        }
        output.copyOf(stream.total_out.toInt())
    } finally {
        deflateEnd(stream.ptr)
    }
}

/** The DOS date and time of [epochMillis] in the local zone, as ZIP stores them; years outside 1980..2107 clamp. */
@Suppress("ReturnCount") // The two ends of the DOS range, then the date.
private fun dosDateTime(epochMillis: Long): Pair<Int, Int> {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    if (local.year < DOS_EPOCH_YEAR) return ((1 shl 5) or 1) to 0
    if (local.year > DOS_MAX_YEAR) {
        return (((DOS_MAX_YEAR - DOS_EPOCH_YEAR) shl 9) or (12 shl 5) or 31) to
            ((23 shl 11) or (59 shl 5) or 29)
    }
    val date = ((local.year - DOS_EPOCH_YEAR) shl 9) or ((local.month.ordinal + 1) shl 5) or local.day
    val time = (local.hour shl 11) or (local.minute shl 5) or (local.second / 2)
    return date to time
}
