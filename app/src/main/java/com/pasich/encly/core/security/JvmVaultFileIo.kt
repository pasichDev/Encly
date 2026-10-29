package com.pasich.encly.core.security

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/*
 * The Android/JVM side of VaultFileIo.kt: the vault file on java.io and java.nio, the only place
 * that can fsync it and replace it atomically. A Kotlin Multiplatform build would turn this into
 * an actual of the same interface.
 */

/** A [VaultStore] on the file [file]. */
fun VaultStore(file: File): VaultStore = VaultStore(JvmVaultFileIo(file))

class JvmVaultFileIo(private val file: File) : VaultFileIo {
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
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }

    override fun delete(): Boolean = !file.exists() || file.delete()

    private fun tempFile() = File(file.path + TEMP_SUFFIX)

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
    }
}
