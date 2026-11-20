package com.pasich.encly.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** List previews and search run this for every note; one bad note must not crash them. */
class NotesTextFormatterTest {

    @Test
    fun unknownBlockTypeFromANewerVersionIsUnreadableNotACrash() {
        assertNull(NotesTextFormatter.jsonToPlainText("""[{"blockType":"FUTURE"}]"""))
    }

    @Test
    fun malformedJsonIsUnreadableNotACrash() {
        assertNull(NotesTextFormatter.jsonToPlainText("{not-json"))
    }

    @Test
    fun readableContentIsFormatted() {
        assertEquals(
            "hello",
            NotesTextFormatter.jsonToPlainText("""[{"blockType":"TEXT","text":"hello"}]"""),
        )
        assertEquals("", NotesTextFormatter.jsonToPlainText(""))
    }
}
