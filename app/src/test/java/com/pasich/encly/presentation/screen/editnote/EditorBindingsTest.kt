package com.pasich.encly.presentation.screen.editnote

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.block_heading_level
import com.pasich.encly.core.resources.block_link
import com.pasich.encly.core.resources.block_move_up
import com.pasich.encly.core.resources.block_separator
import com.pasich.encly.core.resources.delete_block
import com.pasich.encly.core.resources.quote
import com.pasich.encly.core.resources.tap_again_to_delete
import com.pasich.encly.core.resources.text
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.EditorStrings
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor ships no string resources; the app maps each of its keys onto its own. A key without a
 * string, or one mapped to the wrong string, would show in a sheet or a menu of the editor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EditorBindingsTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var strings: EditorStrings

    @Before
    fun readTheStrings() {
        rule.setContent { strings = rememberEditorStrings() }
        rule.waitForIdle()
    }

    @Test
    fun everyKeyTheEditorAsksForHasATextInTheApp() {
        EditorString.entries.forEach { key ->
            assertTrue("$key has no text", strings.text(key).isNotBlank())
        }
    }

    @Test
    fun theNamesOfTheBlockKindsAreTheAppsOwn() {
        val expected = mapOf(
            EditorString.TEXT to Res.string.text,
            EditorString.QUOTE to Res.string.quote,
            EditorString.BLOCK_LINK to Res.string.block_link,
            EditorString.BLOCK_SEPARATOR to Res.string.block_separator,
            EditorString.BLOCK_MOVE_UP to Res.string.block_move_up,
            EditorString.BLOCK_DELETE to Res.string.delete_block,
            EditorString.TAP_AGAIN_TO_DELETE to Res.string.tap_again_to_delete,
        )

        expected.forEach { (key, id) ->
            assertEquals(key.name, id, key.resource())
            assertEquals(key.name, runBlocking { getString(id) }, strings.text(key))
        }
    }

    @Test
    fun aHeadingNamesItsLevel() {
        (1..4).forEach { level ->
            assertEquals(runBlocking { getString(Res.string.block_heading_level, level) }, strings.heading(level))
        }
    }
}
