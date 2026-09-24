package com.pasich.encly.core.security

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * The vault's key slots and PIN lockout state, in one app-private file.
 *
 * Every secret stored here is already an AES-256-GCM envelope (PIN, recovery, backup-key and
 * biometric slots), so the file needs no second encryption layer and no Keystore key to be
 * read: unlike EncryptedSharedPreferences, a broken Keystore/Tink keyset can not make it
 * unreadable. What it does need is to never be half-written and to notice damage:
 *
 * - every change rewrites the whole file to a temp file, fsyncs it and renames it over the
 *   old one (an atomic replace on the same filesystem), so a crash leaves either the old or
 *   the new state, never a mix;
 * - the file ends with a SHA-256 of its content. A file that fails the check is reported by
 *   [isCorrupt] and reads as empty (fail closed: no slot, nothing unlocks), writes are
 *   refused, and only [clear] (the damaged-vault wipe) starts over.
 *
 * The digest detects accidental damage only; tampering is caught by each envelope's GCM tag.
 * Values are copied in and out, and the caller wipes what it gets.
 */
@Suppress("TooManyFunctions") // Typed getters/setters of one small key-value file.
class VaultStore(private val file: File) {
    private val lock = Any()

    // Guarded by [lock]. Null until the first read.
    private var cache: Map<String, ByteArray>? = null
    private var corrupt = false

    /** True when the file exists but could not be read back intact. */
    fun isCorrupt(): Boolean = synchronized(lock) {
        load()
        corrupt
    }

    fun contains(key: String): Boolean = synchronized(lock) { load().containsKey(key) }

    fun getBytes(key: String): ByteArray? = synchronized(lock) { load()[key]?.copyOf() }

    fun getInt(key: String, default: Int): Int = synchronized(lock) {
        load()[key]?.takeIf { it.size == Int.SIZE_BYTES }?.let { ByteBuffer.wrap(it).int } ?: default
    }

    fun getLong(key: String, default: Long): Long = synchronized(lock) {
        load()[key]?.takeIf { it.size == Long.SIZE_BYTES }?.let { ByteBuffer.wrap(it).long } ?: default
    }

    fun getBoolean(key: String, default: Boolean): Boolean = synchronized(lock) {
        load()[key]?.takeIf { it.size == BOOLEAN_BYTES }?.let { it[0] == TRUE } ?: default
    }

    /**
     * Applies every change made in [block] in one atomic, durable write. Returns false (and
     * changes nothing) when the file is damaged or cannot be written.
     */
    fun edit(block: Editor.() -> Unit): Boolean = synchronized(lock) {
        val current = load()
        if (corrupt) return false
        val next = LinkedHashMap(current)
        Editor(next).block()
        val written = try {
            write(next)
            true
        } catch (_: IOException) {
            false
        }
        if (written) {
            cache = next
            // Values dropped or replaced by this edit are no longer referenced.
            current.forEach { (key, value) -> if (next[key] !== value) SensitiveDataCleaner.clear(value) }
        } else {
            next.forEach { (key, value) -> if (current[key] !== value) SensitiveDataCleaner.clear(value) }
        }
        written
    }

    /** Deletes the file (damaged or not) and forgets everything. */
    fun clear(): Boolean = synchronized(lock) {
        cache?.values?.forEach(SensitiveDataCleaner::clear)
        cache = emptyMap()
        corrupt = false
        tempFile().delete()
        !file.exists() || file.delete()
    }

    class Editor internal constructor(private val values: MutableMap<String, ByteArray>) {
        /** Stores a copy of [value]; the caller keeps (and wipes) its own. */
        fun putBytes(key: String, value: ByteArray) {
            values[key] = value.copyOf()
        }

        fun putInt(key: String, value: Int) {
            values[key] = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
        }

        fun putLong(key: String, value: Long) {
            values[key] = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()
        }

        fun putBoolean(key: String, value: Boolean) {
            values[key] = byteArrayOf(if (value) TRUE else FALSE)
        }

        fun remove(key: String) {
            values.remove(key)
        }

        /** Removes every key starting with [prefix]. */
        fun removePrefix(prefix: String) {
            values.keys.removeAll { it.startsWith(prefix) }
        }
    }

    private fun load(): Map<String, ByteArray> {
        cache?.let { return it }
        // A temp file left by a write that never reached its rename is never the state.
        tempFile().delete()
        val loaded = if (!file.exists()) {
            emptyMap()
        } else {
            try {
                parse(file.readBytes())
            } catch (_: IOException) {
                null
            }
        }
        corrupt = loaded == null
        return (loaded ?: emptyMap()).also { cache = it }
    }

    private fun write(values: Map<String, ByteArray>) {
        val bytes = serialize(values)
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
        } finally {
            SensitiveDataCleaner.clear(bytes)
        }
    }

    private fun tempFile() = File(file.path + TEMP_SUFFIX)

    /** Test hook: runs after the temp file is durable and before it replaces the real one. */
    internal var beforeRename: (() -> Unit)? = null

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
        const val DIGEST_LENGTH = 32
        const val MAX_ENTRIES = 256
        const val MAX_VALUE_LENGTH = 64 * 1024
        const val BOOLEAN_BYTES = 1
        const val TRUE: Byte = 1
        const val FALSE: Byte = 0
        val MAGIC = "ENCLYVS3".toByteArray(Charsets.US_ASCII)

        fun serialize(values: Map<String, ByteArray>): ByteArray {
            val buffer = ByteArrayOutputStream()
            DataOutputStream(buffer).use { out ->
                out.write(MAGIC)
                out.writeInt(values.size)
                values.forEach { (key, value) ->
                    out.writeUTF(key)
                    out.writeInt(value.size)
                    out.write(value)
                }
            }
            val body = buffer.toByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(body)
            return try {
                body + digest
            } finally {
                SensitiveDataCleaner.clear(body)
            }
        }

        /** The entries of [bytes], or null when it is not an intact store file. */
        @Suppress("ReturnCount") // Each structural check fails closed.
        fun parse(bytes: ByteArray): Map<String, ByteArray>? {
            try {
                if (bytes.size < MAGIC.size + Int.SIZE_BYTES + DIGEST_LENGTH) return null
                val bodyLength = bytes.size - DIGEST_LENGTH
                val expected = MessageDigest.getInstance("SHA-256").apply { update(bytes, 0, bodyLength) }.digest()
                if (!MessageDigest.isEqual(expected, bytes.copyOfRange(bodyLength, bytes.size))) return null
                val input = DataInputStream(bytes.inputStream(0, bodyLength))
                val magic = ByteArray(MAGIC.size).also(input::readFully)
                if (!magic.contentEquals(MAGIC)) return null
                val count = input.readInt()
                if (count !in 0..MAX_ENTRIES) return null
                val values = LinkedHashMap<String, ByteArray>(count)
                repeat(count) {
                    val key = input.readUTF()
                    val length = input.readInt()
                    if (length !in 0..MAX_VALUE_LENGTH) return null
                    values[key] = ByteArray(length).also(input::readFully)
                }
                return if (input.available() == 0) values else null
            } catch (_: IOException) {
                return null
            } finally {
                SensitiveDataCleaner.clear(bytes)
            }
        }
    }
}
