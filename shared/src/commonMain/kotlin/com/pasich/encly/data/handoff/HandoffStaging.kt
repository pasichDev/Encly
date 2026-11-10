package com.pasich.encly.data.handoff

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.data.zip.openZipFile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The one place a hand-off touches the disk: My Notes' ZIP is copied into a file of its own in
 * [dir] (the app-private cache) because the URI grant ends with the activity and the reader needs
 * random access, then parsed and mapped, then deleted in a `finally`. The plaintext lives on disk
 * only for the parse. What a killed process left behind is deleted by [sweep], at the next start
 * of the app and when a wipe-PIN erase finishes; a file a load in this process is still reading
 * is never touched, so two hand-offs at once do not delete each other's.
 *
 * Deleting is all that can be done: on flash storage overwriting a file does not reliably
 * erase the old blocks, so it is not attempted.
 */
class HandoffStaging(private val dir: Path, private val limits: HandoffLimits = HandoffLimits()) {

    /**
     * Copies what [open] returns, reads and maps it. Throws [HandoffException]; an [IOException]
     * from [open] or the copy (the platform adapter also reports a refused grant as one) means
     * the URI could not be read.
     */
    fun load(open: () -> RawSource?): HandoffImport {
        val zip = createStagingFile(dir)
        try {
            val input = open() ?: throw HandoffException(HandoffError.INVALID_PAYLOAD)
            input.use { source ->
                SystemFileSystem.sink(zip).buffered().use {
                    MyNotesHandoffReader.copyLimited(source, it, limits)
                }
            }
            return MyNotesHandoffMapper.map(read(zip, limits))
        } finally {
            release(dir, zip)
        }
    }

    companion object {
        const val DIR_NAME = "mynotes-handoff"
        private const val ZIP_PREFIX = "handoff-"
        private const val ZIP_SUFFIX = ".zip"
        private const val NAME_RANDOM_BYTES = 8
        private const val BYTE_MASK = 0xFF
        private const val HEX_RADIX = 16

        /** The hand-off in the ZIP file [zip], which is opened with the platform's ZIP support. */
        internal fun read(zip: Path, limits: HandoffLimits = HandoffLimits()): MyNotesHandoff =
            MyNotesHandoffReader.read(SystemFileSystem.metadataOrNull(zip)?.size ?: 0L, limits) { openZipFile(zip) }

        /** Paths of the files loads in this process are using; also the lock of the directory. */
        private val inUse = mutableSetOf<String>()

        private val lock = SynchronizedObject()

        /**
         * Deletes everything in [dir] that no load in this process is using (what killed
         * processes left behind), and [dir] itself once it is empty.
         */
        fun sweep(dir: Path) {
            synchronized(lock) {
                if (SystemFileSystem.exists(dir)) {
                    try {
                        SystemFileSystem.list(dir).filterNot { it.toString() in inUse }.forEach(::deleteRecursively)
                    } catch (_: IOException) {
                        // Not a readable directory: nothing to sweep.
                    }
                }
                if (inUse.isEmpty()) deleteIfEmpty(dir)
            }
        }

        private fun createStagingFile(dir: Path): Path = synchronized(lock) {
            try {
                SystemFileSystem.createDirectories(dir)
            } catch (_: IOException) {
                throw IOException("no staging directory")
            }
            var zip: Path
            do {
                zip = Path(dir, ZIP_PREFIX + hex(Crypto.randomBytes(NAME_RANDOM_BYTES)) + ZIP_SUFFIX)
            } while (SystemFileSystem.exists(zip))
            SystemFileSystem.sink(zip).close()
            zip.also { inUse += it.toString() }
        }

        private fun release(dir: Path, zip: Path) {
            synchronized(lock) {
                deleteRecursively(zip)
                inUse -= zip.toString()
                // Only when no other load is using it (a directory that is not empty stays).
                if (inUse.isEmpty()) deleteIfEmpty(dir)
            }
        }

        /** Deletes [path] and, for a directory, what is in it; what cannot be deleted is left. */
        private fun deleteRecursively(path: Path) {
            try {
                if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
                    SystemFileSystem.list(path).forEach(::deleteRecursively)
                }
                SystemFileSystem.delete(path, mustExist = false)
            } catch (_: IOException) {
                // Left for the next sweep.
            }
        }

        /** Deletes the directory [dir] unless something is in it (or it is gone already). */
        private fun deleteIfEmpty(dir: Path) {
            try {
                SystemFileSystem.delete(dir, mustExist = false)
            } catch (_: IOException) {
                // Not empty.
            }
        }

        private fun hex(bytes: ByteArray): String =
            bytes.joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }
    }
}
