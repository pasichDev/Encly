package com.pasich.encly.editor.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.pasich.encly.editor.ui.blocks.NoSuggestionsInput
import kotlin.test.Test

/** A desktop has no window privacy flags and no keyboard suggestions: both leave the content as it is. */
@OptIn(ExperimentalTestApi::class)
class DesktopGlueTest {
    @Test
    fun theDialogProtectionAndTheSuggestionsSwitchLeaveTheContentAlone() = runComposeUiTest {
        setContent {
            ProtectDialogWindow()
            NoSuggestionsInput { Text("field") }
        }

        onNodeWithText("field").assertExists()
    }
}
