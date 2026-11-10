package com.pasich.encly.editor.ui.blocks

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A desktop has no keyboard with a Select all of its own: the content shows as it is and nothing is asked. */
@OptIn(ExperimentalTestApi::class)
class SelectAllImeDesktopTest {
    @Test
    fun theContentShowsAsItIs() = runComposeUiTest {
        var asked = 0
        setContent { BlockSelectAllIme(onSelectAll = { asked++ > 0 }) { Text("field") } }

        onNodeWithText("field").assertExists()
        assertEquals(0, asked)
    }
}
