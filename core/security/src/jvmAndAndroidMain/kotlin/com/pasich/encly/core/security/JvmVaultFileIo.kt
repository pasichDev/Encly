package com.pasich.encly.core.security

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/*
 * The Android/JVM side of VaultFileIo.kt: the vault file on java.io and java.nio, the only place
 * that can fsync it and replace it atomically. A Kotlin Multiplatform build would turn this into
 * an actual of the same interface.
 */

/** A [VaultStore] on the file [file]. */
public fun VaultStore(file: File): VaultStore = VaultStore(JvmVaultFileIo(file))

public class JvmVaultFileIo(private val file: File) : VaultFileIo {
    override fun exists(): Boolean = file.exists()

    override fun read(): ByteArray = file.readBytes()

    override fun deleteTemp() {
        tempFile().delete()
    }

    override fun replace(bytes: ByteArray, beforeRename: (() -> Unit)?) {
        file.parentFile?.mkdirs()
        val temp = tempFile()
        try {
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            beforeRename?.invoke()
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                if (!temp.renameTo(file)) throw IOException("rename failed")
            }
            syncParentDirectory()
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }

    /**
     * Makes the rename durable: until the directory is synced it can be lost to a power cut, and an older vault state
     * (a charged PIN failure counter rolled back) would come back. Best effort: where a directory cannot be opened
     * or synced (Windows), the rename is as durable as the platform makes it.
     */
    private fun syncParentDirectory() {
        val parent = file.absoluteFile.parentFile ?: return
        try {
            FileChannel.open(parent.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
            // Directories cannot be opened here.
        } catch (_: UnsupportedOperationException) {
            // Same.
        }
    }

    override fun delete(): Boolean = !file.exists() || file.delete()

    private fun tempFile() = File(file.path + TEMP_SUFFIX)

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
    }
}
