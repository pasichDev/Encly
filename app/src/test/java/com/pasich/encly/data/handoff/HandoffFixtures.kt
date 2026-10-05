package com.pasich.encly.data.handoff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds hand-off JSON and ZIPs the way My Notes sends them (contract v1). */
internal object HandoffFixtures {

    fun tag(id: String, name: String, position: Int = 0) = buildJsonObject {
        put("id", id)
        put("name", name)
        put("position", position)
    }

    fun category(id: String, name: String, position: Int = 0) = tag(id, name, position)

    @Suppress("LongParameterList") // Mirrors the contract's note record.
    fun note(
        id: String,
        title: String = "Title $id",
        value: String = "Text of $id",
        valueJson: String? = null,
        tag: String? = null,
        isTrash: Boolean = false,
        isPinned: Boolean = false,
        attachments: Int = 0,
    ) = buildJsonObject {
        put("id", id)
        put("title", title)
        put("value", value)
        put("valueJson", valueJson?.let(::JsonPrimitive) ?: JsonNull)
        put("date", 1_700_000_000_000L)
        put("tag", tag?.let(::JsonPrimitive) ?: JsonNull)
        put("isTrash", isTrash)
        put("isPinned", isPinned)
        put("attachments", attachments)
    }

    fun task(id: String, description: String, isDone: Boolean = false, categoryId: String? = null, position: Int = 0) =
        buildJsonObject {
            put("id", id)
            put("description", description)
            put("isDone", isDone)
            put("createdAt", 1_600_000_000_000L)
            put("categoryId", categoryId?.let(::JsonPrimitive) ?: JsonNull)
            put("position", position)
        }

    fun handoff(
        tags: List<JsonObject> = emptyList(),
        notes: List<JsonObject> = emptyList(),
        categories: List<JsonObject> = emptyList(),
        tasks: List<JsonObject> = emptyList(),
        schema: Int = 1,
        format: String = MyNotesHandoff.FORMAT,
    ) = buildJsonObject {
        put("format", format)
        put("schema", schema)
        put("exportedAt", 1_750_000_000_000L)
        put("tags", JsonArray(tags))
        put("notes", JsonArray(notes))
        put("taskCategories", JsonArray(categories))
        put("tasks", JsonArray(tasks))
    }

    /** A ZIP with the given entries (name -> UTF-8 content), written to [dir]. */
    fun zip(dir: File, vararg entries: Pair<String, ByteArray>): File {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return File.createTempFile("handoff", ".zip", dir).apply { writeBytes(bytes.toByteArray()) }
    }

    fun handoffZip(dir: File, json: JsonElement): File =
        zip(dir, MyNotesHandoffReader.ENTRY_NAME to json.toString().toByteArray(Charsets.UTF_8))

    fun read(dir: File, json: JsonElement): MyNotesHandoff = MyNotesHandoffReader.read(handoffZip(dir, json))
}
