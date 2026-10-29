package com.pasich.encly.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupPayloadCodecTest {

    private val tag = BackupTag(uid = "t1", name = "Work", visible = true, position = 0)
    private val payload = BackupPayload(
        exportedAt = 1_700_000_000_000,
        tags = listOf(tag),
        notes = listOf(
            BackupNote("n1", "Title", "[]", "desc", 2L, 1L, tagUid = "t1", isTrash = true),
        ),
        tasks = listOf(
            BackupTask(
                "k1",
                "Task",
                null,
                false,
                3L,
                null,
                priority = 2,
                categoryTagUid = "t1",
                position = 5,
                subtasks = emptyList(),
            ),
        ),
    )
    private val subtasks = listOf(
        BackupSubtask(uid = "s1", title = "Milk", isCompleted = true, position = 0),
        BackupSubtask(uid = "s2", title = "Bread", isCompleted = false, position = 1),
    )
    private val withSubtasks = payload.copy(tasks = payload.tasks.map { it.copy(subtasks = subtasks) })

    @Test
    fun encodeDecodeRoundTrips() {
        assertEquals(payload, BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload)))
    }

    @Test
    fun subtasksRoundTrip() {
        assertEquals(withSubtasks, BackupPayloadCodec.decode(BackupPayloadCodec.encode(withSubtasks)))
    }

    @Test
    fun emptyVaultRoundTrips() {
        val empty = BackupPayload(exportedAt = 0, tags = emptyList(), notes = emptyList(), tasks = emptyList())

        assertEquals(empty, BackupPayloadCodec.decode(BackupPayloadCodec.encode(empty)))
    }

    @Test
    fun malformedJsonIsInvalid() {
        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode("{not json".encodeToByteArray()) }
        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode("{}".encodeToByteArray()) }
    }

    @Test
    fun newerSchemaIsUnsupportedNotCorrupted() {
        val newer = BackupPayloadCodec.encode(payload.copy(schema = BackupPayload.SCHEMA_VERSION + 1))

        assertError(BackupError.UNSUPPORTED_VERSION) { BackupPayloadCodec.decode(newer) }
    }

    @Test
    fun newerSchemaWithFieldsThisVersionDoesNotKnowIsUnsupported() {
        // What a future app writes: a bumped schema plus a field this build has never heard of.
        val newer = withExtraField(payload.copy(schema = BackupPayload.SCHEMA_VERSION + 1))

        assertError(BackupError.UNSUPPORTED_VERSION) { BackupPayloadCodec.decode(newer) }
    }

    @Test
    fun unknownFieldInTheCurrentSchemaIsInvalid() {
        // Every payload change must bump SCHEMA_VERSION, so this is a malformed file.
        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(withExtraField(payload)) }
    }

    @Test
    fun olderUnknownSchemaIsInvalid() {
        assertInvalid(payload.copy(schema = 0))
    }

    @Test
    fun schema1BackupDecodesWithoutItsReminders() {
        val decoded = BackupPayloadCodec.decode(schema1Json().encodeToByteArray())

        assertEquals(payload, decoded)
        assertEquals(BackupPayload.SCHEMA_VERSION, decoded.schema)
    }

    @Test
    fun schema2BackupDecodesWithNoSubtasks() {
        val decoded = BackupPayloadCodec.decode(schema2Json().encodeToByteArray())

        assertEquals(payload, decoded)
        assertEquals(BackupPayload.SCHEMA_VERSION, decoded.schema)
    }

    @Test
    fun schema2BackupWithSubtasksIsInvalid() {
        // Schema 2 never had sub-tasks: such a file is malformed, not a newer one.
        val json = schema2Json().replace("\"position\":5}", "\"position\":5,\"subtasks\":[]}")

        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json.encodeToByteArray()) }
    }

    @Test
    fun schema3BackupDecodesWithDefaultNoteOrganizing() {
        val decoded = BackupPayloadCodec.decode(schema3Json().encodeToByteArray())

        assertEquals(withSubtasks, decoded)
        assertEquals(BackupPayload.SCHEMA_VERSION, decoded.schema)
        with(decoded.notes.single()) {
            assertEquals(false, isPinned)
            assertEquals(false, isArchived)
            assertEquals("none", color)
        }
    }

    @Test
    fun schema3BackupWithNoteOrganizingKeysIsInvalid() {
        // Schema 3 never had these keys: such a file is malformed, not a newer one.
        val json = schema3Json().replace("\"isTrash\":true}", "\"isTrash\":true,\"isPinned\":true}")

        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json.encodeToByteArray()) }
    }

    @Test
    fun noteOrganizingRoundTrips() {
        val organized = payload.copy(
            notes = payload.notes.map { it.copy(isPinned = true, isArchived = true, color = "teal") },
        )

        assertEquals(organized, BackupPayloadCodec.decode(BackupPayloadCodec.encode(organized)))
    }

    @Test
    fun aNotesOwnTextRoundTripsAndOnlyKnownValuesAreValid() {
        val own = payload.copy(notes = payload.notes.map { it.copy(textSize = 24, lineSpacing = 2) })
        assertEquals(own, BackupPayloadCodec.decode(BackupPayloadCodec.encode(own)))

        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(textSize = 9) }))
        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(textSize = 33) }))
        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(lineSpacing = 3) }))
        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(lineSpacing = -1) }))
    }

    @Test
    fun schema3BackupWithATextSizeKeyIsInvalid() {
        val json = schema3Json().replace("\"isTrash\":true}", "\"isTrash\":true,\"textSize\":20}")

        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json.encodeToByteArray()) }
    }

    @Test
    fun blankOrOverlongColorIsInvalid() {
        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(color = "") }))
        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(color = "x".repeat(33)) }))
    }

    @Test
    fun currentSchemaRequiresTheSubtasksList() {
        val json = BackupPayloadCodec.encode(payload).decodeToString().replace(",\"subtasks\":[]", "")

        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json.encodeToByteArray()) }
    }

    @Test
    fun schema1BackupStillRejectsOtherUnknownFields() {
        val json = schema1Json().replaceFirst("{", "{\"pinnedNotes\":[\"n1\"],")

        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json.encodeToByteArray()) }
    }

    @Test
    fun reminderInTheCurrentSchemaIsInvalid() {
        val json = BackupPayloadCodec.encode(payload).decodeToString()
            .replace("\"completedDate\":null,", "\"completedDate\":null,\"reminderDate\":4,")

        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json.encodeToByteArray()) }
    }

    /**
     * The key names of a backup file are its format: backups written by the release build must
     * be readable by any other build, so none of them may follow a (renamable) property name.
     */
    @Test
    fun encodedKeyNamesArePinned() {
        val expected = """{"schema":4,"exportedAt":1700000000000,""" +
            """"tags":[{"uid":"t1","name":"Work","visible":true,"position":0}],""" +
            """"notes":[{"uid":"n1","title":"Title","value":"[]","description":"desc","date":2,""" +
            """"dateCreate":1,"tagUid":"t1","isTrash":true,"isPinned":false,"isArchived":false,""" +
            """"color":"none","textSize":null,"lineSpacing":null}],""" +
            """"tasks":[{"uid":"k1","title":"Task","description":null,"isCompleted":false,""" +
            """"createdDate":3,"completedDate":null,"priority":2,"categoryTagUid":"t1","position":5,""" +
            """"subtasks":[{"uid":"s1","title":"Milk","isCompleted":true,"position":0},""" +
            """{"uid":"s2","title":"Bread","isCompleted":false,"position":1}]}]}"""

        assertEquals(expected, BackupPayloadCodec.encode(withSubtasks).decodeToString())
    }

    /** [payload] as the schema-1 app wrote it: every task carried a `reminderDate`. */
    private fun schema1Json(): String = """{"schema":1,"exportedAt":1700000000000,""" +
        """"tags":[{"uid":"t1","name":"Work","visible":true,"position":0}],""" +
        """"notes":[{"uid":"n1","title":"Title","value":"[]","description":"desc","date":2,""" +
        """"dateCreate":1,"tagUid":"t1","isTrash":true}],""" +
        """"tasks":[{"uid":"k1","title":"Task","description":null,"isCompleted":false,""" +
        """"createdDate":3,"completedDate":null,"reminderDate":4,"priority":2,""" +
        """"categoryTagUid":"t1","position":5}]}"""

    /** [payload] as the schema-2 app wrote it: tasks had no `subtasks`. */
    private fun schema2Json(): String = """{"schema":2,"exportedAt":1700000000000,""" +
        """"tags":[{"uid":"t1","name":"Work","visible":true,"position":0}],""" +
        """"notes":[{"uid":"n1","title":"Title","value":"[]","description":"desc","date":2,""" +
        """"dateCreate":1,"tagUid":"t1","isTrash":true}],""" +
        """"tasks":[{"uid":"k1","title":"Task","description":null,"isCompleted":false,""" +
        """"createdDate":3,"completedDate":null,"priority":2,"categoryTagUid":"t1","position":5}]}"""

    /** [withSubtasks] as the schema-3 app wrote it: notes had no pin, archive or colour. */
    private fun schema3Json(): String = """{"schema":3,"exportedAt":1700000000000,""" +
        """"tags":[{"uid":"t1","name":"Work","visible":true,"position":0}],""" +
        """"notes":[{"uid":"n1","title":"Title","value":"[]","description":"desc","date":2,""" +
        """"dateCreate":1,"tagUid":"t1","isTrash":true}],""" +
        """"tasks":[{"uid":"k1","title":"Task","description":null,"isCompleted":false,""" +
        """"createdDate":3,"completedDate":null,"priority":2,"categoryTagUid":"t1","position":5,""" +
        """"subtasks":[{"uid":"s1","title":"Milk","isCompleted":true,"position":0},""" +
        """{"uid":"s2","title":"Bread","isCompleted":false,"position":1}]}]}"""

    private fun withExtraField(value: BackupPayload): ByteArray {
        val json = BackupPayloadCodec.encode(value).decodeToString()
        return json.replaceFirst("{", "{\"pinnedNotes\":[\"n1\"],").encodeToByteArray()
    }

    @Test
    fun linksMustPointAtTagsInTheBackup() {
        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(tagUid = "missing") }))
        assertInvalid(payload.copy(tasks = payload.tasks.map { it.copy(categoryTagUid = "missing") }))
    }

    @Test
    fun uidsMustBeUniqueAndPresent() {
        assertInvalid(payload.copy(tags = listOf(tag, tag.copy(name = "Other"))))
        assertInvalid(payload.copy(notes = payload.notes + payload.notes))
        assertInvalid(payload.copy(tasks = payload.tasks.map { it.copy(uid = " ") }))
        assertInvalid(payload.copy(tasks = payload.tasks.map { it.copy(uid = "x".repeat(65)) }))
    }

    @Test
    fun subtaskUidsMustBeUniqueAcrossTheVaultAndPresent() {
        val second = payload.tasks.single().copy(uid = "k2", subtasks = listOf(subtasks.first()))
        assertInvalid(withSubtasks.copy(tasks = withSubtasks.tasks + second))
        assertInvalid(payload.copy(tasks = payload.tasks.map { it.copy(subtasks = subtasks + subtasks) }))
        assertInvalid(
            payload.copy(
                tasks = payload.tasks.map {
                    it.copy(subtasks = listOf(subtasks[0].copy(uid = "")))
                },
            ),
        )
        assertInvalid(
            payload.copy(
                tasks = payload.tasks.map {
                    it.copy(subtasks = listOf(subtasks[0].copy(uid = "x".repeat(65))))
                },
            ),
        )
    }

    @Test
    fun priorityMustBeKnown() {
        assertInvalid(payload.copy(tasks = payload.tasks.map { it.copy(priority = 3) }))
    }

    private fun assertInvalid(bad: BackupPayload) {
        // encode() does not validate, so this is what a hand-crafted (authentic) file would hold.
        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.validate(bad) }
        val json = BackupPayloadCodec.encode(bad)
        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json) }
    }
}
