package com.pasich.encly.data.importer

import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.importer.ImportFixtures.describe
import org.junit.Assert.assertEquals

internal class ExpectedNote(
    val title: String,
    val blocks: List<String>,
    val tag: String? = null,
    val created: Long?,
    val edited: Long?,
    val trashed: Boolean = false,
    val losses: ImportLosses = ImportLosses(),
)

internal fun checkPlan(plan: ImportPlan, expected: List<ExpectedNote>, attachments: Int = 0) {
    val rows = plan.payload.notes
    assertEquals(expected.map { it.title }.sorted(), rows.map { it.title }.sorted())
    val tagNames = plan.payload.tags.associate { it.uid to it.name }
    for (want in expected) {
        val note = rows.first { it.title == want.title }
        assertEquals(want.title, want.blocks, describe(BlockConverter.jsonToBlocks(note.value)))
        assertEquals(want.title, want.tag, note.tagUid?.let(tagNames::get))
        if (want.created != null) assertEquals(want.title, want.created, note.dateCreate)
        if (want.edited != null) assertEquals(want.title, want.edited, note.date)
        assertEquals(want.title, want.trashed, note.isTrash)
    }
    BackupPayloadCodec.validate(plan.payload)
    assertEquals(
        expected.fold(ImportLosses(attachments = attachments)) { sum, note -> sum + note.losses },
        plan.preview.losses,
    )
}
