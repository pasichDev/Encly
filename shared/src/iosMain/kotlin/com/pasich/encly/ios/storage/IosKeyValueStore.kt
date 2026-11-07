package com.pasich.encly.ios.storage

import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.core.security.IosVaultFileIo
import com.pasich.encly.core.security.VaultFileIo
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * [KeyValueStore] in one small JSON file inside the app's data directory (not `NSUserDefaults`, which lives
 * outside it, is backed up, and is out of Encly's control).
 *
 * Every change rewrites the whole file through [io] (a temp file, `F_FULLFSYNC`, an atomic rename), so a crash
 * leaves the old content or the new, never a mix. [edit] returns at once and writes in order on a background
 * thread; [commit] writes before it returns. A file that is absent starts as empty. A file that exists but cannot
 * be read (an I/O error, for example while protected data is unavailable) fails closed: nothing is changed or
 * written, so the file is never replaced by an empty state, and every read tries the file again. Meanwhile reads
 * give the defaults. A file that was read but does not parse is damaged for good, and the vault does not depend on
 * these flags: it is moved aside (`<name>.damaged`, for a look) and the store starts empty. Only when it cannot be
 * moved does it stay closed, as for an unreadable one.
 */
internal class IosKeyValueStore(
    private val io: VaultFileIo,
    private val moveAside: () -> Boolean,
    private val writer: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
) : KeyValueStore {
    constructor(path: String) : this(IosVaultFileIo(path), { moveToDamaged(path) })

    private val stateLock = SynchronizedObject()
    private val writeLock = SynchronizedObject()

    // Guarded by [stateLock]. Null while the file exists but could not be read.
    private var values: MutableMap<String, String>? = load()?.toMutableMap()

    override fun getString(key: String, default: String?): String? = read { it[key] } ?: default

    override fun getBoolean(key: String, default: Boolean): Boolean =
        read { it[key] }?.toBooleanStrictOrNull() ?: default

    override fun getLong(key: String, default: Long): Long = read { it[key] }?.toLongOrNull() ?: default

    override fun contains(key: String): Boolean = read { it.containsKey(key) } ?: false

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        if (read { MapEditor(it).block() } != null) writer.launch { persist() }
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

    /** Waits for the writes queued by [edit] (tests). */
    internal fun flush() = runBlocking { writer.launch { }.join() }

    /** Writes the current values; false if the file could not be replaced (the values stay in memory). */
    private fun persist(): Boolean = synchronized(writeLock) {
        val snapshot = synchronized(stateLock) { values?.toMap() } ?: return false
        try {
            io.replace(Json.encodeToString(SERIALIZER, snapshot).encodeToByteArray(), null)
            true
        } catch (_: IOException) {
            false
        }
    }

    /** The file's content; empty when there is no file, null when there is one that cannot be read or parsed. */
    private fun load(): Map<String, String>? {
        if (!io.exists()) return emptyMap()
        return try {
            Json.decodeFromString(SERIALIZER, io.read().decodeToString())
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            // Damaged JSON (a SerializationException is one too): nothing to wait for, and the flags are not worth
            // a stuck store.
            if (moveAside()) emptyMap() else null
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

    private companion object {
        const val ASIDE_SUFFIX = ".damaged"

        fun moveToDamaged(path: String): Boolean = try {
            SystemFileSystem.atomicMove(Path(path), Path(path + ASIDE_SUFFIX))
            true
        } catch (_: IOException) {
            false
        }

        val SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}
