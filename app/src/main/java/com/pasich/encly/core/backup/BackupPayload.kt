package com.pasich.encly.core.backup

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

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
 * - 3: each task carries its `subtasks` (a list, empty for a task without any).
 * - 4: each note carries `isPinned`, `isArchived`, a `color` key and its own `textSize` and
 *   `lineSpacing` (null: the default). An app that reads only schema 3 reports such a file as
 *   [BackupError.UNSUPPORTED_VERSION], it does not fail on the keys.
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
        const val SCHEMA_VERSION = 4

        /** Tasks carried a `reminderDate`, dropped on import. */
        const val SCHEMA_V1 = 1

        /** Tasks had no sub-tasks; they are imported with none. */
        const val SCHEMA_V2 = 2

        /** Notes had no pin, archive or colour; they are imported unpinned, not archived, uncoloured. */
        const val SCHEMA_V3 = 3
    }
}

@Serializable
data class BackupTag(val uid: String, val name: String, val visible: Boolean, val position: Int)

/**
 * [value] is the note's serialized block list, carried verbatim so every block type survives.
 * [color] is a stable palette key (`none`, `red`, ...), never a colour value.
 */
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
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val color: String = "none",
    /** The note's own text size, or null for the default. */
    val textSize: Int? = null,
    /** The note's own line spacing (an ordinal of NoteLineSpacing), or null for the default. */
    val lineSpacing: Int? = null,
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
    val subtasks: List<BackupSubtask>,
)

/** A task's checklist item. Its task is the [BackupTask] that holds it; [uid]s are vault-wide unique. */
@Serializable
data class BackupSubtask(val uid: String, val title: String, val isCompleted: Boolean, val position: Int)

/** UTF-8 JSON encoding of [BackupPayload] with strict validation on the way in. */
@OptIn(ExperimentalSerializationApi::class)
object BackupPayloadCodec {
    const val MAX_UID_LENGTH = 64
    const val MAX_COLOR_LENGTH = 32
    private const val REMINDER_DATE_KEY = "reminderDate"
    private const val TASKS_KEY = "tasks"
    private const val SUBTASKS_KEY = "subtasks"
    private const val NOTES_KEY = "notes"
    private val NOTE_ORGANIZING_KEYS = listOf("isPinned", "isArchived", "color", "textSize", "lineSpacing")
    private val PRIORITIES = 0..2
    private const val MIN_TEXT_SIZE = 10
    private const val MAX_TEXT_SIZE = 32
    private const val LINE_SPACING_COUNT = 3
    private val TEXT_SIZES = MIN_TEXT_SIZE..MAX_TEXT_SIZE
    private val LINE_SPACINGS = 0 until LINE_SPACING_COUNT

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
    fun encode(payload: BackupPayload): ByteArray = json.encodeToWipedBytes(BackupPayload.serializer(), payload)

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
            if (schema in BackupPayload.SCHEMA_V1..BackupPayload.SCHEMA_V3) {
                val tree = decodeStream(json, JsonElement.serializer(), plaintext)
                json.decodeFromJsonElement(BackupPayload.serializer(), upgrade(tree.jsonObject, schema))
            } else {
                decodeStream(json, BackupPayload.serializer(), plaintext)
            }
        }
        validate(payload)
        return payload
    }

    /**
     * Schema 1, 2 or 3 -> current. 1 -> 2 drops each task's `reminderDate`; 2 -> 3 gives each task
     * an empty `subtasks` list; 3 -> 4 leaves notes to the defaults of the new fields (unpinned,
     * not archived, no colour). Everything else is unchanged. A key an older schema did not have
     * (`subtasks` in 1 or 2, a note's pin, archive or colour in 1 to 3) fails like any other
     * unknown key.
     */
    private fun upgrade(old: JsonObject, schema: Int): JsonObject {
        val tasks = old[TASKS_KEY]?.jsonArray?.map { element ->
            val task = element.jsonObject.let { if (schema == BackupPayload.SCHEMA_V1) it - REMINDER_DATE_KEY else it }
            if (schema < BackupPayload.SCHEMA_V3) {
                require(SUBTASKS_KEY !in task) { "Unexpected sub-tasks in schema $schema" }
                JsonObject(task + (SUBTASKS_KEY to JsonArray(emptyList())))
            } else {
                element
            }
        }
        old[NOTES_KEY]?.jsonArray?.forEach { note ->
            require(NOTE_ORGANIZING_KEYS.none { it in note.jsonObject }) { "Unexpected note field in schema $schema" }
        }
        return JsonObject(
            old + ("schema" to JsonPrimitive(BackupPayload.SCHEMA_VERSION)) +
                (tasks?.let { mapOf(TASKS_KEY to JsonArray(it)) }.orEmpty()),
        )
    }

    private fun <T> decodeStream(format: Json, deserializer: DeserializationStrategy<T>, bytes: ByteArray): T =
        format.decodeFromBytes(deserializer, bytes)

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
            uniqueValidUids(payload.tasks.flatMap { task -> task.subtasks.map { it.uid } }) &&
            payload.notes.all { it.tagUid == null || it.tagUid in tagUids } &&
            payload.notes.all(::validNoteStyle) &&
            payload.tasks.all {
                it.priority in PRIORITIES && (it.categoryTagUid == null || it.categoryTagUid in tagUids)
            }
        if (!valid) throw BackupException(BackupError.INVALID_PAYLOAD)
    }

    /** A note's colour key and its own text size and line spacing are within what the app can show. */
    private fun validNoteStyle(note: BackupNote): Boolean =
        note.color.isNotBlank() && note.color.length <= MAX_COLOR_LENGTH &&
            (note.textSize == null || note.textSize in TEXT_SIZES) &&
            (note.lineSpacing == null || note.lineSpacing in LINE_SPACINGS)

    private fun uniqueValidUids(uids: List<String>): Boolean =
        uids.all { it.isNotBlank() && it.length <= MAX_UID_LENGTH } && uids.toSet().size == uids.size
}
