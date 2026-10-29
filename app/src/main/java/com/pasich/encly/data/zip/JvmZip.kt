package com.pasich.encly.data.zip

import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.asOutputStream
import kotlinx.io.asSource
import java.io.File
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/*
 * The Android/JVM side of ZipPlatform.kt: the one file that uses java.util.zip. A Kotlin
 * Multiplatform build would turn these top-level functions into expect/actual declarations.
 */

internal fun newCrc32(): Crc32 = object : Crc32 {
    private val crc = CRC32()

    override val value: Long get() = crc.value

    override fun update(bytes: ByteArray, offset: Int, length: Int) = crc.update(bytes, offset, length)
}

internal fun newRawInflater(): RawInflater = object : RawInflater {
    private val inflater = Inflater(true)

    override val remaining: Int get() = inflater.remaining
    override val bytesRead: Long get() = inflater.bytesRead
    override val bytesWritten: Long get() = inflater.bytesWritten

    override fun needsInput() = inflater.needsInput()

    override fun needsDictionary() = inflater.needsDictionary()

    override fun finished() = inflater.finished()

    override fun setInput(bytes: ByteArray, offset: Int, length: Int) = inflater.setInput(bytes, offset, length)

    override fun inflate(bytes: ByteArray, offset: Int, length: Int): Int = try {
        inflater.inflate(bytes, offset, length)
    } catch (_: DataFormatException) {
        throw IOException("bad zip data")
    }

    override fun end() = inflater.end()
}

private val cp437: Charset = try {
    Charset.forName("IBM437")
} catch (_: IllegalArgumentException) {
    Charsets.ISO_8859_1
}

/** [bytes] as CP437, the ZIP format's default name encoding (ISO-8859-1 where the JVM has no CP437). */
internal fun decodeCp437(bytes: ByteArray): String = String(bytes, cp437)

/** The ZIP at [file], opened with [ZipFile]; throws an [IOException] if it is not one. */
internal fun openZipFile(file: File): ZipEntrySource = object : ZipEntrySource {
    private val archive = ZipFile(file)

    override val entryCount: Int get() = archive.size()

    override fun find(name: String): ZipEntryHandle? = archive.getEntry(name)?.let { entry ->
        object : ZipEntryHandle {
            override val name: String get() = entry.name
            override val isDirectory: Boolean get() = entry.isDirectory
            override val declaredSize: Long get() = entry.size

            override fun open(): RawSource = archive.getInputStream(entry).asSource()
        }
    }

    override fun close() = archive.close()
}

/** A [ZipOutputStream] over [out]; an entry without a time gets [now] (epoch millis). */
internal fun zipWriter(out: Sink, now: () -> Long = System::currentTimeMillis): ZipEntrySink = object : ZipEntrySink {
    private val zip = ZipOutputStream(out.asOutputStream())

    override fun put(name: String, time: Long?, bytes: ByteArray) {
        val entry = ZipEntry(name)
        entry.time = time ?: now()
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    override fun finish() {
        zip.finish()
        out.flush()
    }
}
