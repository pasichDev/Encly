package com.pasich.encly.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupPayloadCodecTest {

    private val tag = BackupTag(uid = "t1", name = "Work", visible = true, position = 0)
    private val payload = BackupPayload(
        exportedAt = 1_700_000_000_000,
        tags = listOf(tag),
        notes = listOf(
            BackupNote("n1", "Title", "[]", "desc", 2L, 1L, tagUids = listOf("t1"), isTrash = true),
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
    private val withSettings = withSubtasks.copy(
        settings = linkedMapOf("theme_type" to "DARK", "font_size" to "18", "is_grid_note_list" to "true"),
    )

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
     * This is the schema-6 vector, kept byte for byte: the schema-4 file with the note's tags as a list.
     */
    @Test
    fun encodedKeyNamesArePinned() {
        assertEquals(schema6Json(), BackupPayloadCodec.encode(withSubtasks).decodeToString())
    }

    /** The same file plus the `settings` section, last, in the order given. */
    @Test
    fun encodedSettingsSectionIsPinned() {
        val expected = schema6Json().removeSuffix("}") +
            ""","settings":{"theme_type":"DARK","font_size":"18","is_grid_note_list":"true"}}"""

        assertEquals(expected, BackupPayloadCodec.encode(withSettings).decodeToString())
    }

    @Test
    fun schema5BackupDecodesWithItsSettingsAndTheNotesOneTagAsAList() {
        val json = schema4Json().replaceFirst("\"schema\":4", "\"schema\":5").removeSuffix("}") +
            ""","settings":{"theme_type":"DARK","font_size":"18","is_grid_note_list":"true"}}"""

        val decoded = BackupPayloadCodec.decode(json.encodeToByteArray())

        assertEquals(withSettings, decoded)
        assertEquals(listOf("t1"), decoded.notes.single().tagUids)
    }

    @Test
    fun schema5NoteWithoutATagHasNone() {
        val json = schema4Json().replaceFirst("\"schema\":4", "\"schema\":5")
            .replace("\"tagUid\":\"t1\"", "\"tagUid\":null")

        assertEquals(emptyList<String>(), BackupPayloadCodec.decode(json.encodeToByteArray()).notes.single().tagUids)
    }

    @Test
    fun schema5BackupWithTheTagListIsInvalidAndSchema6WithOneTagIsInvalid() {
        // Each schema has its own key: a file that mixes them is malformed, not a newer one.
        val schema5 = schema4Json().replaceFirst("\"schema\":4", "\"schema\":5")
            .replace("\"tagUid\":\"t1\"", "\"tagUids\":[\"t1\"]")
        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(schema5.encodeToByteArray()) }

        val schema6 = schema6Json().replace("\"tagUids\":[\"t1\"]", "\"tagUid\":\"t1\"")
        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(schema6.encodeToByteArray()) }
    }

    @Test
    fun aNoteWithSeveralTagsRoundTrips() {
        val second = BackupTag(uid = "t2", name = "Home", visible = false, position = 1)
        val tagged = payload.copy(
            tags = listOf(tag, second),
            notes = payload.notes.map { it.copy(tagUids = listOf("t2", "t1")) } +
                payload.notes.single().copy(uid = "n2", tagUids = emptyList()),
        )

        assertEquals(tagged, BackupPayloadCodec.decode(BackupPayloadCodec.encode(tagged)))
    }

    @Test
    fun aPayloadWithoutSettingsLeavesTheSectionOut() {
        assertEquals(false, BackupPayloadCodec.encode(payload).decodeToString().contains("settings"))
    }

    @Test
    fun settingsRoundTrip() {
        assertEquals(withSettings, BackupPayloadCodec.decode(BackupPayloadCodec.encode(withSettings)))
    }

    @Test
    fun schema4BackupDecodesWithNoSettings() {
        val decoded = BackupPayloadCodec.decode(schema4Json().encodeToByteArray())

        assertEquals(withSubtasks.copy(schema = BackupPayload.SCHEMA_VERSION), decoded)
        assertEquals(emptyMap(), decoded.settings)
    }

    @Test
    fun schema4BackupWithSettingsIsInvalid() {
        // Schema 4 never had the section: such a file is malformed, not a newer one.
        val json = schema4Json().removeSuffix("}") + ""","settings":{"theme_type":"DARK"}}"""

        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json.encodeToByteArray()) }
    }

    @Test
    fun aSchemaAboveTheKnownOneIsUnsupportedEvenWithASettingsSection() {
        // How a reader treats a file newer than itself: the schema number is read first, before any field,
        // so a section it does not know is "update the app", never "damaged". An app that reads 4 does
        // exactly this to a schema-5 file; this build is shown the same with the schema after its own.
        val future = withSettings.copy(schema = BackupPayload.SCHEMA_VERSION + 1)

        assertError(BackupError.UNSUPPORTED_VERSION) { BackupPayloadCodec.decode(BackupPayloadCodec.encode(future)) }
    }

    @Test
    fun settingsAreNeverValidatedIntoAFailure() {
        // Unknown keys and bad values are the restore's to ignore, key by key; the file stays valid.
        val odd = payload.copy(settings = mapOf("from_the_future" to "x", "theme_type" to "NEON"))

        assertEquals(odd, BackupPayloadCodec.decode(BackupPayloadCodec.encode(odd)))
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

    /** [withSubtasks] as the schema-4 app wrote it: the final shape before preferences. */
    private fun schema4Json(): String = """{"schema":4,"exportedAt":1700000000000,""" +
        """"tags":[{"uid":"t1","name":"Work","visible":true,"position":0}],""" +
        """"notes":[{"uid":"n1","title":"Title","value":"[]","description":"desc","date":2,""" +
        """"dateCreate":1,"tagUid":"t1","isTrash":true,"isPinned":false,"isArchived":false,""" +
        """"color":"none","textSize":null,"lineSpacing":null}],""" +
        """"tasks":[{"uid":"k1","title":"Task","description":null,"isCompleted":false,""" +
        """"createdDate":3,"completedDate":null,"priority":2,"categoryTagUid":"t1","position":5,""" +
        """"subtasks":[{"uid":"s1","title":"Milk","isCompleted":true,"position":0},""" +
        """{"uid":"s2","title":"Bread","isCompleted":false,"position":1}]}]}"""

    /** [withSubtasks] as this app writes it (schema 6): the schema-4 file with the note's tags as a list. */
    private fun schema6Json(): String = schema4Json().replaceFirst("\"schema\":4", "\"schema\":6")
        .replace("\"tagUid\":\"t1\"", "\"tagUids\":[\"t1\"]")

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
        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(tagUids = listOf("missing")) }))
        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(tagUids = listOf("t1", "missing")) }))
        // A tag at most once per note.
        assertInvalid(payload.copy(notes = payload.notes.map { it.copy(tagUids = listOf("t1", "t1")) }))
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
