package com.pasich.encly.editor.ui.components

import android.app.Application
import android.view.View
import android.view.Window
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pasich.encly.editor.EditorProvider
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/** In a wide window the editor's sheet is a centred dialog; a window gets the editor's privacy flags. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorSheetDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(title: String?) {
        rule.setContent {
            MaterialTheme {
                EditorProvider(sheetsAsDialogs = true) {
                    EditorSheet(onDismissRequest = {}, sheetState = rememberModalBottomSheetState(), title = title) {
                        Text("body")
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theDialogShowsItsTitleAndContent() {
        show("Heading")

        rule.onNodeWithText("Heading").assertExists()
        rule.onNodeWithText("body").assertExists()
    }

    @Test
    fun theDialogWithoutATitleShowsItsContent() {
        show(null)

        rule.onNodeWithText("body").assertExists()
    }

    @Test
    fun aWindowIsMarkedToHideFromAutofill() {
        val decor = View(RuntimeEnvironment.getApplication())
        val window = Mockito.mock(Window::class.java)
        Mockito.`when`(window.decorView).thenReturn(decor)

        window.applyWindowPrivacy()

        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, decor.importantForAutofill)
    }
}
