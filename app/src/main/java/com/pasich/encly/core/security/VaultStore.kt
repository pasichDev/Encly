package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto
import kotlinx.io.IOException

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
 *
 * The file format is the one `DataOutputStream` wrote (big-endian ints, modified-UTF-8 keys),
 * coded here by hand so the logic is platform-free; only the file itself (the fsync and the
 * atomic replace, which kotlinx-io cannot promise) is behind [VaultFileIo].
 */
@Suppress("TooManyFunctions") // Typed getters/setters of one small key-value file.
class VaultStore(private val io: VaultFileIo) {
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

    /** The keys present; for tests that compare what a store reveals by its shape. */
    internal fun keys(): Set<String> = synchronized(lock) { load().keys.toSet() }

    fun getBytes(key: String): ByteArray? = synchronized(lock) { load()[key]?.copyOf() }

    fun getInt(key: String, default: Int): Int = synchronized(lock) {
        load()[key]?.takeIf { it.size == Int.SIZE_BYTES }?.let { BigEndian.readInt(it) } ?: default
    }

    fun getLong(key: String, default: Long): Long = synchronized(lock) {
        load()[key]?.takeIf { it.size == Long.SIZE_BYTES }?.let { BigEndian.readLong(it) } ?: default
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
        io.deleteTemp()
        io.delete()
    }

    class Editor internal constructor(private val values: MutableMap<String, ByteArray>) {
        /** Stores a copy of [value]; the caller keeps (and wipes) its own. */
        fun putBytes(key: String, value: ByteArray) {
            values[key] = value.copyOf()
        }

        fun putInt(key: String, value: Int) {
            values[key] = BigEndian.bytesOf(value)
        }

        fun putLong(key: String, value: Long) {
            values[key] = BigEndian.bytesOf(value)
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
        io.deleteTemp()
        val loaded = if (!io.exists()) {
            emptyMap()
        } else {
            try {
                parse(io.read())
            } catch (_: IOException) {
                null
            }
        }
        corrupt = loaded == null
        return (loaded ?: emptyMap()).also { cache = it }
    }

    private fun write(values: Map<String, ByteArray>) {
        val bytes = serialize(values)
        try {
            io.replace(bytes, beforeRename)
        } finally {
            SensitiveDataCleaner.clear(bytes)
        }
    }

    /** Test hook: runs after the temp file is durable and before it replaces the real one. */
    internal var beforeRename: (() -> Unit)? = null

    private companion object {
        const val DIGEST_LENGTH = 32
        const val UTF_LENGTH_BYTES = 2
        const val BYTE_MASK = 0xFF
        const val MAX_ENTRIES = 256
        const val MAX_VALUE_LENGTH = 64 * 1024
        const val BOOLEAN_BYTES = 1
        const val TRUE: Byte = 1
        const val FALSE: Byte = 0
        val MAGIC = "ENCLYVS3".encodeToByteArray()

        fun serialize(values: Map<String, ByteArray>): ByteArray {
            val keys = values.keys.map { ModifiedUtf8.encode(it) }
            val bodyLength = MAGIC.size + Int.SIZE_BYTES +
                keys.sumOf { UTF_LENGTH_BYTES + it.size + Int.SIZE_BYTES } + values.values.sumOf { it.size }
            val body = ByteArray(bodyLength + DIGEST_LENGTH)
            MAGIC.copyInto(body)
            var at = BigEndian.write(body, MAGIC.size, values.size)
            values.values.forEachIndexed { index, value ->
                val key = keys[index]
                body[at++] = (key.size ushr Byte.SIZE_BITS).toByte()
                body[at++] = key.size.toByte()
                key.copyInto(body, at)
                at += key.size
                at = BigEndian.write(body, at, value.size)
                value.copyInto(body, at)
                at += value.size
            }
            val digest = Crypto.sha256(body, 0, bodyLength)
            digest.copyInto(body, bodyLength)
            SensitiveDataCleaner.clear(digest)
            return body
        }

        /** The entries of [bytes], or null when it is not an intact store file. */
        @Suppress("ReturnCount") // Each structural check fails closed.
        fun parse(bytes: ByteArray): Map<String, ByteArray>? {
            try {
                if (bytes.size < MAGIC.size + Int.SIZE_BYTES + DIGEST_LENGTH) return null
                val bodyLength = bytes.size - DIGEST_LENGTH
                val expected = Crypto.sha256(bytes, 0, bodyLength)
                if (!Crypto.constantTimeEquals(expected, bytes.copyOfRange(bodyLength, bytes.size))) return null
                val input = Cursor(bytes, bodyLength)
                val magic = input.readBytes(MAGIC.size)
                if (!magic.contentEquals(MAGIC)) return null
                val count = input.readInt()
                if (count !in 0..MAX_ENTRIES) return null
                val values = LinkedHashMap<String, ByteArray>(count)
                repeat(count) {
                    val key = ModifiedUtf8.decode(input.readBytes(input.readUnsignedShort()))
                    val length = input.readInt()
                    if (length !in 0..MAX_VALUE_LENGTH) return null
                    values[key] = input.readBytes(length)
                }
                return if (input.remaining == 0) values else null
            } catch (_: IOException) {
                return null
            } finally {
                SensitiveDataCleaner.clear(bytes)
            }
        }
    }

    /** A read position in [bytes], up to [limit]; running out is an [IOException], as with a stream. */
    private class Cursor(private val bytes: ByteArray, private val limit: Int) {
        private var at = 0

        val remaining: Int get() = limit - at

        fun readBytes(length: Int): ByteArray {
            if (length > remaining) throw IOException("end of file")
            return bytes.copyOfRange(at, at + length).also { at += length }
        }

        fun readInt(): Int {
            if (Int.SIZE_BYTES > remaining) throw IOException("end of file")
            return BigEndian.readInt(bytes, at).also { at += Int.SIZE_BYTES }
        }

        fun readUnsignedShort(): Int {
            if (UTF_LENGTH_BYTES > remaining) throw IOException("end of file")
            return (((bytes[at].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or (bytes[at + 1].toInt() and BYTE_MASK))
                .also { at += UTF_LENGTH_BYTES }
        }
    }
}
