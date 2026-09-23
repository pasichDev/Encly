package com.pasich.encly.core.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupPayloadCodecTest {

    private val tag = BackupTag(uid = "t1", name = "Work", visible = true, position = 0)
    private val payload = BackupPayload(
        exportedAt = 1_700_000_000_000,
        tags = listOf(tag),
        notes = listOf(
            BackupNote("n1", "Title", "[]", "desc", 2L, 1L, tagUid = "t1", isTrash = true),
        ),
        tasks = listOf(
            BackupTask("k1", "Task", null, false, 3L, null, priority = 2, categoryTagUid = "t1", position = 5),
        ),
    )

    @Test
    fun encodeDecodeRoundTrips() {
        assertEquals(payload, BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload)))
    }

    @Test
    fun emptyVaultRoundTrips() {
        val empty = BackupPayload(exportedAt = 0, tags = emptyList(), notes = emptyList(), tasks = emptyList())

        assertEquals(empty, BackupPayloadCodec.decode(BackupPayloadCodec.encode(empty)))
    }

    @Test
    fun malformedJsonIsInvalid() {
        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode("{not json".toByteArray()) }
        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode("{}".toByteArray()) }
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
        val decoded = BackupPayloadCodec.decode(schema1Json().toByteArray(Charsets.UTF_8))

        assertEquals(payload, decoded)
        assertEquals(BackupPayload.SCHEMA_VERSION, decoded.schema)
    }

    @Test
    fun schema1BackupStillRejectsOtherUnknownFields() {
        val json = schema1Json().replaceFirst("{", "{\"pinnedNotes\":[\"n1\"],")

        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json.toByteArray(Charsets.UTF_8)) }
    }

    @Test
    fun reminderInTheCurrentSchemaIsInvalid() {
        val json = String(BackupPayloadCodec.encode(payload), Charsets.UTF_8)
            .replace("\"completedDate\":null,", "\"completedDate\":null,\"reminderDate\":4,")

        assertError(BackupError.INVALID_PAYLOAD) { BackupPayloadCodec.decode(json.toByteArray(Charsets.UTF_8)) }
    }

    /** [payload] as the schema-1 app wrote it: every task carried a `reminderDate`. */
    private fun schema1Json(): String = """{"schema":1,"exportedAt":1700000000000,""" +
        """"tags":[{"uid":"t1","name":"Work","visible":true,"position":0}],""" +
        """"notes":[{"uid":"n1","title":"Title","value":"[]","description":"desc","date":2,""" +
        """"dateCreate":1,"tagUid":"t1","isTrash":true}],""" +
        """"tasks":[{"uid":"k1","title":"Task","description":null,"isCompleted":false,""" +
        """"createdDate":3,"completedDate":null,"reminderDate":4,"priority":2,""" +
        """"categoryTagUid":"t1","position":5}]}"""

    private fun withExtraField(value: BackupPayload): ByteArray {
        val json = String(BackupPayloadCodec.encode(value), Charsets.UTF_8)
        return json.replaceFirst("{", "{\"pinnedNotes\":[\"n1\"],").toByteArray(Charsets.UTF_8)
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
