@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.core.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.IOException
import platform.Foundation.NSFileManager
import platform.posix.F_FULLFSYNC
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.fstat
import platform.posix.fsync
import platform.posix.open
import platform.posix.read
import platform.posix.rename
import platform.posix.stat
import platform.posix.strerror
import platform.posix.unlink
import platform.posix.write

/**
 * The iOS side of [VaultFileIo]: the vault file on POSIX calls, the only place that can fsync it and replace it
 * atomically (kotlinx-io has neither). [replace] writes `<path>.tmp`, flushes it to the disk with `F_FULLFSYNC`
 * (a plain `fsync` on Apple platforms only reaches the drive's cache; it is the fallback where the file system
 * refuses the stronger call), then `rename(2)`s it over the vault file, which is atomic on APFS. Every failure
 * is a [IOException].
 */
public class IosVaultFileIo(private val path: String) : VaultFileIo {
    override fun exists(): Boolean = platform.posix.access(path, platform.posix.F_OK) == 0

    override fun read(): ByteArray {
        val fd = open(path, O_RDONLY)
        if (fd < 0) fail("open $path")
        try {
            val size = memScoped {
                val info = alloc<stat>()
                if (fstat(fd, info.ptr) != 0) fail("stat $path")
                info.st_size.toInt()
            }
            val bytes = ByteArray(size)
            var done = 0
            while (done < size) {
                val count = bytes.usePinned { read(fd, it.addressOf(done), (size - done).convert()) }.toInt()
                if (count < 0) fail("read $path")
                if (count == 0) throw IOException("$path is shorter than its size")
                done += count
            }
            return bytes
        } finally {
            close(fd)
        }
    }

    override fun deleteTemp() {
        unlink(tempPath())
    }

    override fun replace(bytes: ByteArray, beforeRename: (() -> Unit)?) {
        createParentDirectory()
        val temp = tempPath()
        try {
            writeAndSync(temp, bytes)
            beforeRename?.invoke()
            if (rename(temp, path) != 0) fail("rename $temp")
        } catch (e: IOException) {
            unlink(temp)
            throw e
        }
    }

    override fun delete(): Boolean {
        unlink(path)
        return !exists()
    }

    private fun writeAndSync(file: String, bytes: ByteArray) {
        val fd = open(file, O_WRONLY or O_CREAT or O_TRUNC, FILE_MODE)
        if (fd < 0) fail("open $file")
        try {
            var done = 0
            while (done < bytes.size) {
                val count = bytes.usePinned { write(fd, it.addressOf(done), (bytes.size - done).convert()) }.toInt()
                if (count < 0) fail("write $file")
                done += count
            }
            if (fcntl(fd, F_FULLFSYNC) != 0 && fsync(fd) != 0) fail("sync $file")
        } finally {
            close(fd)
        }
    }

    private fun createParentDirectory() {
        val parent = path.substringBeforeLast('/', "")
        if (parent.isEmpty()) return
        val created = NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
        if (!created) throw IOException("cannot create $parent")
    }

    private fun tempPath() = path + TEMP_SUFFIX

    private fun fail(what: String): Nothing = throw IOException("$what: ${strerror(errno)?.toKString()}")

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
        const val FILE_MODE = 384 // 0600
    }
}
