package com.pasich.encly.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
