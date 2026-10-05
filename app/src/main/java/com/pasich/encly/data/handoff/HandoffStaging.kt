package com.pasich.encly.data.handoff

import java.io.File
import java.io.IOException
import java.io.InputStream

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
class HandoffStaging(private val dir: File, private val limits: HandoffLimits = HandoffLimits()) {

    /**
     * Copies what [open] returns, reads and maps it. Throws [HandoffException]; an [IOException]
     * or [SecurityException] from [open] or the copy means the URI could not be read.
     */
    fun load(open: () -> InputStream?): HandoffImport {
        val zip = createStagingFile(dir)
        try {
            val input = open() ?: throw HandoffException(HandoffError.INVALID_PAYLOAD)
            input.use { source -> zip.outputStream().use { MyNotesHandoffReader.copyLimited(source, it, limits) } }
            return MyNotesHandoffMapper.map(MyNotesHandoffReader.read(zip, limits))
        } finally {
            release(dir, zip)
        }
    }

    companion object {
        const val DIR_NAME = "mynotes-handoff"
        private const val ZIP_PREFIX = "handoff-"
        private const val ZIP_SUFFIX = ".zip"

        /** Paths of the files loads in this process are using; also the lock of the directory. */
        private val inUse = mutableSetOf<String>()

        /**
         * Deletes everything in [dir] that no load in this process is using (what killed
         * processes left behind), and [dir] itself once it is empty.
         */
        fun sweep(dir: File) {
            synchronized(inUse) {
                dir.listFiles()?.filterNot { it.path in inUse }?.forEach { it.deleteRecursively() }
                if (inUse.isEmpty()) dir.delete()
            }
        }

        private fun createStagingFile(dir: File): File = synchronized(inUse) {
            if (!dir.mkdirs() && !dir.isDirectory) throw IOException("no staging directory")
            File.createTempFile(ZIP_PREFIX, ZIP_SUFFIX, dir).also { inUse += it.path }
        }

        private fun release(dir: File, zip: File) {
            synchronized(inUse) {
                zip.delete()
                inUse -= zip.path
                // Only when no other load is using it (delete fails on a directory that is not empty).
                if (inUse.isEmpty()) dir.delete()
            }
        }
    }
}

/** Deletes what hand-offs of killed processes left in the app's cache (see [HandoffStaging.sweep]). */
fun interface HandoffStagingSweeper {
    fun sweep()
}
