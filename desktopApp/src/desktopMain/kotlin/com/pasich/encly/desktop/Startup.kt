package com.pasich.encly.desktop

import com.pasich.encly.desktop.platform.DroppedKind
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Only one Encly may run per data directory: two processes on the same encrypted database and the same key
 * slots file would corrupt each other's state. The lock is an OS file lock, so it is released when the process
 * ends, however it ends. A second start does not open a second window; it exits (it does not hand its files to
 * the first, which would need a local socket; the file can be dropped on the open window instead).
 */
class SingleInstanceLock private constructor(private val channel: FileChannel, private val lock: FileLock) :
    AutoCloseable {
    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        private const val LOCK_FILE = ".instance.lock"

        /** The lock for [dataDirectory], or null if another Encly of this user holds it. */
        @Suppress("ReturnCount") // Each failure to get the lock is a different way to say no.
        fun tryAcquire(dataDirectory: Path): SingleInstanceLock? {
            val channel = try {
                FileChannel.open(dataDirectory.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            } catch (_: IOException) {
                return null
            }
            val lock = try {
                channel.tryLock()
            } catch (_: IOException) {
                null
            } catch (_: java.nio.channels.OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return SingleInstanceLock(channel, lock)
        }
    }
}

/** The files named on the command line: what the OS passes for a double click on a backup (Windows, Linux). */
object LaunchFiles {
    fun fromArguments(args: Array<String>): List<Path> = args.mapNotNull { argument ->
        try {
            Paths.get(argument).takeIf { Files.exists(it) && DroppedKind.of(it) != null }
        } catch (_: InvalidPathException) {
            null
        }
    }
}
