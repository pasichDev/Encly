package com.pasich.encly.domain.model

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteListItemTest {

    @Test
    fun descriptionIsThePreviewAndIsSearched() {
        val item = item(description = "summary", value = """[{"blockType":"TEXT","text":"body"}]""")

        assertEquals("summary", item.preview)
        assertTrue(item.matches("SUMMARY"))
        assertTrue(item.matches("body"))
    }

    @Test
    fun unreadableContentHasNoPreviewButTheTitleStillMatches() {
        val item = item(title = "Plans", value = "{not-json")

        assertNull(item.preview)
        assertTrue(item.matches("plan"))
        assertFalse(item.matches("json"))
    }

    @Test
    fun separatorsAndListMarkersAreNotSearchable() {
        val item = item(
            value = """[{"blockType":"SEPARATOR"},{"blockType":"LIST_NUMBER","items":[{"value":"eggs"}]}]""",
        )

        assertTrue(item.matches("eggs"))
        assertFalse(item.matches("---"))
        assertFalse(item.matches("1."))
    }

    private fun item(title: String = "", description: String = "", value: String = "") =
        NoteListItem.of(NoteWithTag(Note(id = 1, title = title, description = description, value = value), null))
}
