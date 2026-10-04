package com.pasich.encly.data.handoff

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * The one place a hand-off touches the disk: My Notes' ZIP is copied into [dir] (the app-private
 * cache) because the URI grant ends with the activity and the reader needs
 * random access, then parsed and mapped, then deleted in a `finally`. [clear] also removes
 * what a killed process left behind. The plaintext lives on disk only for the parse.
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
        clear()
        try {
            if (!dir.mkdirs() && !dir.isDirectory) throw IOException("no staging directory")
            val zip = File(dir, ZIP_NAME)
            val input = open() ?: throw HandoffException(HandoffError.INVALID_PAYLOAD)
            input.use { source -> zip.outputStream().use { MyNotesHandoffReader.copyLimited(source, it, limits) } }
            return MyNotesHandoffMapper.map(MyNotesHandoffReader.read(zip, limits))
        } finally {
            clear()
        }
    }

    fun clear() {
        dir.deleteRecursively()
    }

    companion object {
        const val DIR_NAME = "mynotes-handoff"
        private const val ZIP_NAME = "handoff.zip"
    }
}
