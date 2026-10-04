package com.pasich.encly.data.handoff

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

/**
 * Size limits of a hand-off. Every one is enforced on the bytes actually read, never on a
 * size a header claims, so a ZIP bomb or an endless stream stops at the limit.
 */
data class HandoffLimits(
    /** The ZIP as copied from My Notes. */
    val maxZipBytes: Long = MAX_BYTES,
    /** `handoff.json` uncompressed. */
    val maxJsonBytes: Long = MAX_BYTES,
    /** Records per list (notes, tasks, tags, task categories). */
    val maxRecords: Int = 100_000,
    /** A note's title, text or Editor.js JSON, a task's text: characters each. */
    val maxTextChars: Int = MIB,
    /** A tag or category name, and a record id. */
    val maxNameChars: Int = 1_000,
) {
    private companion object {
        const val MIB = 1024 * 1024
        const val MAX_BYTES = 32L * MIB
    }
}

/**
 * Reads a My Notes hand-off ZIP strictly: exactly one entry, `handoff.json`, in the v1 shape,
 * within [HandoffLimits]. Throws [HandoffException] with [HandoffError.TOO_LARGE],
 * [HandoffError.UNSUPPORTED_SCHEMA] or [HandoffError.INVALID_PAYLOAD]; nothing else escapes.
 */
@OptIn(ExperimentalSerializationApi::class)
object MyNotesHandoffReader {
    const val ENTRY_NAME = "handoff.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }

    /** Reads `format` and `schema` only, before the full decode. */
    @Serializable
    private class Probe(val format: String? = null, val schema: Int? = null)

    /**
     * Copies [input] (the content URI's stream) to [target], stopping with [HandoffError.TOO_LARGE]
     * past [HandoffLimits.maxZipBytes]. The caller deletes [target] whatever the outcome.
     */
    fun copyLimited(input: InputStream, target: OutputStream, limits: HandoffLimits = HandoffLimits()) {
        try {
            LimitedInputStream(input, limits.maxZipBytes).copyTo(target)
        } catch (_: LimitExceededException) {
            throw HandoffException(HandoffError.TOO_LARGE)
        }
    }

    fun read(zip: File, limits: HandoffLimits = HandoffLimits()): MyNotesHandoff {
        ensure(zip.length() <= limits.maxZipBytes, HandoffError.TOO_LARGE)
        val handoff = guarded {
            ZipFile(zip).use { archive ->
                ensure(archive.size() == 1, HandoffError.INVALID_PAYLOAD)
                val entry = archive.getEntry(ENTRY_NAME)?.takeUnless { it.isDirectory }
                    ?: throw HandoffException(HandoffError.INVALID_PAYLOAD)
                // A declared size is only a hint (it can lie); the stream limit below is the check.
                ensure(entry.size <= limits.maxJsonBytes, HandoffError.TOO_LARGE)
                val open = { LimitedInputStream(archive.getInputStream(entry), limits.maxJsonBytes) }
                val probe = open().use { decode(Probe.serializer(), it) }
                val schema = probe.schema?.takeIf { probe.format == MyNotesHandoff.FORMAT }
                ensure(schema != null && schema >= MyNotesHandoff.SCHEMA, HandoffError.INVALID_PAYLOAD)
                ensure(schema == MyNotesHandoff.SCHEMA, HandoffError.UNSUPPORTED_SCHEMA)
                open().use { decode(MyNotesHandoff.serializer(), it) }
            }
        }
        validate(handoff, limits)
        return handoff
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, input: InputStream): T =
        json.decodeFromStream(deserializer, input)

    /** Maps every parse failure to a [HandoffError]; a [HandoffException] passes through. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        rejected(e)
    } catch (e: SerializationException) {
        rejected(e)
    } catch (e: IllegalArgumentException) {
        rejected(e)
    }

    /** The cause is dropped on purpose: a parser message can quote note content. */
    private fun rejected(e: Exception): Nothing = throw HandoffException(
        if (e is LimitExceededException) HandoffError.TOO_LARGE else HandoffError.INVALID_PAYLOAD,
    )

    private fun validate(handoff: MyNotesHandoff, limits: HandoffLimits) {
        val lists = listOf(handoff.tags, handoff.notes, handoff.taskCategories, handoff.tasks)
        ensure(lists.all { it.size <= limits.maxRecords }, HandoffError.TOO_LARGE)
        val texts = handoff.notes.flatMap { listOf(it.title, it.value, it.valueJson.orEmpty()) } +
            handoff.tasks.map { it.description }
        ensure(texts.all { it.length <= limits.maxTextChars }, HandoffError.TOO_LARGE)
        val names = handoff.tags.flatMap { listOf(it.id, it.name) } +
            handoff.taskCategories.flatMap { listOf(it.id, it.name) } +
            handoff.notes.flatMap { listOf(it.id, it.tag.orEmpty()) } +
            handoff.tasks.flatMap { listOf(it.id, it.categoryId.orEmpty()) }
        ensure(names.all { it.length <= limits.maxNameChars }, HandoffError.TOO_LARGE)

        val valid = uniqueIds(handoff.tags.map { it.id }) &&
            uniqueIds(handoff.notes.map { it.id }) &&
            uniqueIds(handoff.taskCategories.map { it.id }) &&
            uniqueIds(handoff.tasks.map { it.id }) &&
            handoff.notes.all { it.attachments >= 0 }
        ensure(valid, HandoffError.INVALID_PAYLOAD)
        val categoryIds = handoff.taskCategories.mapTo(HashSet()) { it.id }
        val knownCategories = handoff.tasks.all { it.categoryId == null || it.categoryId in categoryIds }
        ensure(knownCategories, HandoffError.INVALID_PAYLOAD)
    }

    private fun uniqueIds(ids: List<String>): Boolean = ids.all { it.isNotBlank() } && ids.toSet().size == ids.size

    private fun ensure(condition: Boolean, error: HandoffError) {
        if (!condition) throw HandoffException(error)
    }

    private class LimitExceededException : IOException("limit exceeded")

    /** Fails with [LimitExceededException] once more than [limit] bytes were read. */
    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) count(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) count(n.toLong())
            return n
        }

        override fun skip(n: Long): Long = super.skip(n).also(::count)

        override fun markSupported(): Boolean = false

        private fun count(n: Long) {
            count += n
            if (count > limit) throw LimitExceededException()
        }
    }
}
