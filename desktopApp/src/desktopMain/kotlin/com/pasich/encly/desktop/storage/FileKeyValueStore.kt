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
 * once and writes on [writer]; [commit] writes before it returns. A file that is absent starts as empty. A file
 * that exists but cannot be read or parsed fails closed: nothing is changed or written, so the file is never
 * replaced by an empty state, and every read tries the file again. Meanwhile reads give the defaults.
 */
class FileKeyValueStore(
    private val file: Path,
    private val writer: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "encly-flags-writer").apply { isDaemon = true }
    },
) : KeyValueStore {
    private val stateLock = Any()
    private val writeLock = Any()

    // Guarded by [stateLock]. Null while the file exists but could not be read.
    private var values: MutableMap<String, String>? = load()?.toMutableMap()

    override fun getString(key: String, default: String?): String? = read { it[key] } ?: default

    override fun getBoolean(key: String, default: Boolean): Boolean =
        read { it[key] }?.toBooleanStrictOrNull() ?: default

    override fun getLong(key: String, default: Long): Long = read { it[key] }?.toLongOrNull() ?: default

    override fun contains(key: String): Boolean = read { it.containsKey(key) } ?: false

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        if (read { MapEditor(it).block() } != null) writer.execute { persist() }
    }

    override fun commit(block: KeyValueStore.Editor.() -> Unit): Boolean =
        read { MapEditor(it).block() } != null && persist()

    /**
     * [action] on the values, or null while the file is unreadable (it is read again first, and taken in once it
     * can be read).
     */
    private fun <T> read(action: (MutableMap<String, String>) -> T): T? = synchronized(stateLock) {
        val current = values ?: load()?.toMutableMap()?.also { values = it } ?: return null
        action(current)
    }

    /** Writes the current values; false if the file could not be replaced (the values stay in memory). */
    private fun persist(): Boolean = synchronized(writeLock) {
        val snapshot = synchronized(stateLock) { values?.toMap() } ?: return false
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

    /** The file's content; empty when there is no file, null when there is one that cannot be read or parsed. */
    private fun load(): Map<String, String>? {
        // Only a file known to be absent starts empty; "cannot tell" goes on to the read and fails closed.
        if (Files.notExists(file)) return emptyMap()
        return try {
            val properties = Properties()
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use(properties::load)
            properties.stringPropertyNames().associateWith { properties.getProperty(it) }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            // A malformed \u escape: the file is damaged.
            null
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
