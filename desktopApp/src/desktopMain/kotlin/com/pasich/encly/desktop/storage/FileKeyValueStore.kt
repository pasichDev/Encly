package com.pasich.encly.desktop.storage

import com.pasich.encly.core.platform.KeyValueStore
import java.io.IOException
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * [KeyValueStore] in one small properties file inside the app's data directory. It replaces
 * `java.util.prefs`, which writes to the registry on Windows and a plist in `~/Library/Preferences` on macOS,
 * outside the private directory and out of Encly's control.
 *
 * Every change rewrites the whole file to a temp file in the same directory, fsyncs it and renames it over the
 * old one (an atomic replace), so a crash leaves the old content or the new, never a mix. [edit] returns at
 * once and writes on [writer]; [commit] writes before it returns. A file that cannot be read starts as empty
 * (the keys are non-secret flags).
 */
class FileKeyValueStore(
    private val file: Path,
    private val writer: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "encly-flags-writer").apply { isDaemon = true }
    },
) : KeyValueStore {
    private val stateLock = Any()
    private val writeLock = Any()
    private val values: MutableMap<String, String> = load().toMutableMap()

    override fun getString(key: String, default: String?): String? = synchronized(stateLock) { values[key] } ?: default

    override fun getBoolean(key: String, default: Boolean): Boolean =
        synchronized(stateLock) { values[key] }?.toBooleanStrictOrNull() ?: default

    override fun getLong(key: String, default: Long): Long =
        synchronized(stateLock) { values[key] }?.toLongOrNull() ?: default

    override fun contains(key: String): Boolean = synchronized(stateLock) { values.containsKey(key) }

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        apply(block)
        writer.execute { persist() }
    }

    override fun commit(block: KeyValueStore.Editor.() -> Unit): Boolean {
        apply(block)
        return persist()
    }

    private fun apply(block: KeyValueStore.Editor.() -> Unit) {
        synchronized(stateLock) { MapEditor(values).block() }
    }

    /** Writes the current values; false if the file could not be replaced (the values stay in memory). */
    private fun persist(): Boolean = synchronized(writeLock) {
        val snapshot = synchronized(stateLock) { values.toMap() }
        try {
            write(snapshot)
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun write(snapshot: Map<String, String>) {
        val dir = file.toAbsolutePath().parent
        Files.createDirectories(dir)
        // createTempFile makes the file owner-only (0600) where POSIX permissions exist.
        val temp = Files.createTempFile(dir, file.fileName.toString(), ".tmp")
        try {
            val properties = Properties().apply { snapshot.forEach { (key, value) -> setProperty(key, value) } }
            val text = StringWriter().also { properties.store(it, null) }.toString()
            FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8))
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            moveOver(temp)
        } catch (e: IOException) {
            Files.deleteIfExists(temp)
            throw e
        }
    }

    private fun moveOver(temp: Path) {
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun load(): Map<String, String> {
        if (!Files.isRegularFile(file)) return emptyMap()
        return try {
            val properties = Properties()
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use(properties::load)
            properties.stringPropertyNames().associateWith { properties.getProperty(it) }
        } catch (_: IOException) {
            emptyMap()
        } catch (_: IllegalArgumentException) {
            // A malformed \u escape: the file is damaged.
            emptyMap()
        }
    }

    private class MapEditor(private val map: MutableMap<String, String>) : KeyValueStore.Editor {
        override fun putString(key: String, value: String) {
            map[key] = value
        }

        override fun putBoolean(key: String, value: Boolean) {
            map[key] = value.toString()
        }

        override fun putLong(key: String, value: Long) {
            map[key] = value.toString()
        }

        override fun remove(key: String) {
            map.remove(key)
        }

        override fun clear() {
            map.clear()
        }
    }
}
