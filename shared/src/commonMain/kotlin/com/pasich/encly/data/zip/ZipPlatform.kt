package com.pasich.encly.data.zip

import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.files.Path
import kotlin.time.Clock

/*
 * What the ZIP code needs from the platform. kotlinx-io has no ZIP, no CRC-32 and no inflater, and
 * the format's default name encoding (CP437) is a charset, so these small interfaces are the whole
 * seam: everything that parses, limits or maps archives is platform-free and uses only them.
 * The JVM implementation (Android and desktop) is JvmZip.kt, the only file that touches java.util.zip.
 */

/** A running CRC-32. */
internal interface Crc32 {
    val value: Long

    fun update(bytes: ByteArray, offset: Int, length: Int)
}

/**
 * A raw (headerless) deflate decompressor: the input is handed over in pieces and [inflate] is
 * called until [finished]. Corrupt data is a [kotlinx.io.IOException] with the message "bad zip data".
 */
internal interface RawInflater {
    /** Input bytes handed over but not yet used. */
    val remaining: Int

    /** Compressed bytes consumed so far. */
    val bytesRead: Long

    /** Uncompressed bytes produced so far. */
    val bytesWritten: Long

    fun needsInput(): Boolean

    fun needsDictionary(): Boolean

    fun finished(): Boolean

    fun setInput(bytes: ByteArray, offset: Int, length: Int)

    /** Up to [length] uncompressed bytes into [bytes]; 0 when it needs input or is finished. */
    fun inflate(bytes: ByteArray, offset: Int, length: Int): Int

    /** Frees the decompressor; it is not used afterwards. */
    fun end()
}

/** One entry of a [ZipEntrySource]. Everything but [open] comes from the archive's central directory. */
internal interface ZipEntryHandle {
    val name: String
    val isDirectory: Boolean

    /** The size the archive claims, or -1 if unknown. A hint only: it can lie, so read through a limit. */
    val declaredSize: Long

    /** The uncompressed data; the caller closes it. */
    fun open(): RawSource
}

/** An archive with a central directory, read with random access. Closing it closes the file. */
internal interface ZipEntrySource : AutoCloseable {
    val entryCount: Int

    /** The entry called exactly [name] (the platform may also match a directory of that name), or null. */
    fun find(name: String): ZipEntryHandle?
}

/** Writes a ZIP to a sink: deflated entries, then [finish]. The sink is flushed but not closed. */
internal interface ZipEntrySink {
    /** An entry named [name] holding [bytes], last modified at [time] (epoch millis), or "now" if null. */
    fun put(name: String, time: Long?, bytes: ByteArray)

    /** Writes the central directory; nothing is added afterwards. */
    fun finish()
}

/** A running CRC-32. */
internal expect fun newCrc32(): Crc32

/** A raw inflater. */
internal expect fun newRawInflater(): RawInflater

/** [bytes] as CP437, the ZIP format's default name encoding (ISO-8859-1 where the platform has no CP437). */
internal expect fun decodeCp437(bytes: ByteArray): String

/** The ZIP at [path], opened for random access; throws an [kotlinx.io.IOException] if it is not one. */
internal expect fun openZipFile(path: Path): ZipEntrySource

/** A ZIP writer over [out]; an entry without a time gets [now] (epoch millis). */
internal expect fun zipWriter(out: Sink, now: () -> Long = { Clock.System.now().toEpochMilliseconds() }): ZipEntrySink
