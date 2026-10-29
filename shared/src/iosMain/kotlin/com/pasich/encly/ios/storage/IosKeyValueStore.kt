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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * [KeyValueStore] in one small JSON file inside the app's data directory (not `NSUserDefaults`, which lives
 * outside it, is backed up, and is out of Encly's control).
 *
 * Every change rewrites the whole file through [io] (a temp file, `F_FULLFSYNC`, an atomic rename), so a crash
 * leaves the old content or the new, never a mix. [edit] returns at once and writes in order on a background
 * thread; [commit] writes before it returns. A file that cannot be read starts as empty (the keys are non-secret
 * flags).
 */
internal class IosKeyValueStore(
    private val io: VaultFileIo,
    private val writer: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
) : KeyValueStore {
    constructor(path: String) : this(IosVaultFileIo(path))

    private val stateLock = SynchronizedObject()
    private val writeLock = SynchronizedObject()
    private val values: MutableMap<String, String> = load().toMutableMap()

    override fun getString(key: String, default: String?): String? = synchronized(stateLock) { values[key] } ?: default

    override fun getBoolean(key: String, default: Boolean): Boolean =
        synchronized(stateLock) { values[key] }?.toBooleanStrictOrNull() ?: default

    override fun getLong(key: String, default: Long): Long =
        synchronized(stateLock) { values[key] }?.toLongOrNull() ?: default

    override fun contains(key: String): Boolean = synchronized(stateLock) { values.containsKey(key) }

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        apply(block)
        writer.launch { persist() }
    }

    override fun commit(block: KeyValueStore.Editor.() -> Unit): Boolean {
        apply(block)
        return persist()
    }

    /** Waits for the writes queued by [edit] (tests). */
    internal fun flush() = runBlocking { writer.launch { }.join() }

    private fun apply(block: KeyValueStore.Editor.() -> Unit) {
        synchronized(stateLock) { MapEditor(values).block() }
    }

    /** Writes the current values; false if the file could not be replaced (the values stay in memory). */
    private fun persist(): Boolean = synchronized(writeLock) {
        val snapshot = synchronized(stateLock) { values.toMap() }
        try {
            io.replace(Json.encodeToString(SERIALIZER, snapshot).encodeToByteArray(), null)
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun load(): Map<String, String> {
        if (!io.exists()) return emptyMap()
        return try {
            Json.decodeFromString(SERIALIZER, io.read().decodeToString())
        } catch (_: IOException) {
            emptyMap()
        } catch (_: IllegalArgumentException) {
            // Damaged JSON.
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

    private companion object {
        val SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}
