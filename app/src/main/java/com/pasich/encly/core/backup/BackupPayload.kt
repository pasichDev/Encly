package com.pasich.encly.core.backup

import com.pasich.encly.core.security.SensitiveDataCleaner
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The decrypted backup content. Records reference each other by their stable `uid`, never by
 * the per-device autoincrement ids. It deliberately holds no vault material: no DEK, PIN or
 * biometric slot, recovery phrase or backup key.
 *
 * Decoding is strict: an unknown key is rejected as [BackupError.INVALID_PAYLOAD]. So **every**
 * change to these classes (a new field, even an optional one) must bump [SCHEMA_VERSION]; an
 * older app then reports such a file as [BackupError.UNSUPPORTED_VERSION] ("update the app")
 * instead of "damaged". Keep reading every older schema: see [BackupPayloadCodec.decode].
 *
 * Schema history:
 * - 1: first format; tasks carried a `reminderDate`.
 * - 2: task reminders removed from the app, so tasks no longer carry `reminderDate`.
 */
@Serializable
data class BackupPayload(
    val schema: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val tags: List<BackupTag>,
    val notes: List<BackupNote>,
    val tasks: List<BackupTask>,
) {
    companion object {
        const val SCHEMA_VERSION = 2

        /** Tasks carried a `reminderDate`, dropped on import. */
        const val SCHEMA_V1 = 1
    }
}

@Serializable
data class BackupTag(val uid: String, val name: String, val visible: Boolean, val position: Int)

/** [value] is the note's serialized block list, carried verbatim so every block type survives. */
@Serializable
data class BackupNote(
    val uid: String,
    val title: String,
    val value: String,
    val description: String,
    val date: Long,
    val dateCreate: Long,
    val tagUid: String?,
    val isTrash: Boolean,
)

@Serializable
data class BackupTask(
    val uid: String,
    val title: String,
    val description: String?,
    val isCompleted: Boolean,
    val createdDate: Long,
    val completedDate: Long?,
    val priority: Int,
    val categoryTagUid: String?,
    val position: Int,
)

/** UTF-8 JSON encoding of [BackupPayload] with strict validation on the way in. */
@OptIn(ExperimentalSerializationApi::class)
object BackupPayloadCodec {
    const val MAX_UID_LENGTH = 64
    private const val REMINDER_DATE_KEY = "reminderDate"
    private val PRIORITIES = 0..2

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    /** Reads only `schema`, skipping everything else, before the strict decode. */
    private val schemaJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private class SchemaProbe(val schema: Int = BackupPayload.SCHEMA_VERSION)

    /** The returned buffer is plaintext: the caller must wipe it once it has been sealed. */
    fun encode(payload: BackupPayload): ByteArray {
        val out = WipeableByteArrayOutputStream()
        return try {
            json.encodeToStream(BackupPayload.serializer(), payload, out)
            out.toByteArray()
        } finally {
            out.wipe()
        }
    }

    /**
     * A payload from a newer schema is [BackupError.UNSUPPORTED_VERSION]: it authenticated, so
     * it is intact, and this app merely cannot read it. Anything else malformed is
     * [BackupError.INVALID_PAYLOAD]. An older schema is upgraded to the current one before the
     * strict decode, so it is validated exactly like a current file.
     */
    fun decode(plaintext: ByteArray): BackupPayload {
        val schema = invalidPayloadOn { decodeStream(schemaJson, SchemaProbe.serializer(), plaintext).schema }
        if (schema > BackupPayload.SCHEMA_VERSION) throw BackupException(BackupError.UNSUPPORTED_VERSION)
        val payload = invalidPayloadOn {
            if (schema == BackupPayload.SCHEMA_V1) {
                val tree = decodeStream(json, JsonElement.serializer(), plaintext)
                json.decodeFromJsonElement(BackupPayload.serializer(), upgradeFromV1(tree.jsonObject))
            } else {
                decodeStream(json, BackupPayload.serializer(), plaintext)
            }
        }
        validate(payload)
        return payload
    }

    /** Schema 1 -> 2: drop each task's `reminderDate`; everything else is unchanged. */
    private fun upgradeFromV1(v1: JsonObject): JsonObject {
        val tasks = v1["tasks"]?.jsonArray?.map { JsonObject(it.jsonObject - REMINDER_DATE_KEY) }
        return JsonObject(
            v1 + ("schema" to JsonPrimitive(BackupPayload.SCHEMA_VERSION)) +
                (tasks?.let { mapOf("tasks" to JsonArray(it)) }.orEmpty()),
        )
    }

    private fun <T> decodeStream(format: Json, deserializer: DeserializationStrategy<T>, bytes: ByteArray): T =
        format.decodeFromStream(deserializer, ByteArrayInputStream(bytes))

    private inline fun <T> invalidPayloadOn(block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw BackupException(BackupError.INVALID_PAYLOAD, e)
    } catch (e: IllegalArgumentException) {
        throw BackupException(BackupError.INVALID_PAYLOAD, e)
    }

    fun validate(payload: BackupPayload) {
        val tagUids = payload.tags.map { it.uid }.toSet()
        val valid = payload.schema == BackupPayload.SCHEMA_VERSION &&
            uniqueValidUids(payload.tags.map { it.uid }) &&
            uniqueValidUids(payload.notes.map { it.uid }) &&
            uniqueValidUids(payload.tasks.map { it.uid }) &&
            payload.notes.all { it.tagUid == null || it.tagUid in tagUids } &&
            payload.tasks.all {
                it.priority in PRIORITIES && (it.categoryTagUid == null || it.categoryTagUid in tagUids)
            }
        if (!valid) throw BackupException(BackupError.INVALID_PAYLOAD)
    }

    private fun uniqueValidUids(uids: List<String>): Boolean =
        uids.all { it.isNotBlank() && it.length <= MAX_UID_LENGTH } && uids.toSet().size == uids.size

    /** Lets the serializer's internal buffer be zeroized instead of left for the GC. */
    private class WipeableByteArrayOutputStream : ByteArrayOutputStream() {
        fun wipe() {
            SensitiveDataCleaner.clear(buf)
            reset()
        }
    }
}
