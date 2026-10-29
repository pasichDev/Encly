package com.pasich.encly.presentation.screen.editnote

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.R
import com.pasich.encly.editor.EditorString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private val resources = ApplicationProvider.getApplicationContext<Application>().resources
    private val strings = ResourceEditorStrings(resources)

    @Test
    fun everyKeyTheEditorAsksForHasATextInTheApp() {
        EditorString.entries.forEach { key ->
            assertTrue("$key has no text", strings.text(key).isNotBlank())
        }
    }

    @Test
    fun theNamesOfTheBlockKindsAreTheAppsOwn() {
        val expected = mapOf(
            EditorString.TEXT to R.string.text,
            EditorString.QUOTE to R.string.quote,
            EditorString.BLOCK_LINK to R.string.block_link,
            EditorString.BLOCK_SEPARATOR to R.string.block_separator,
            EditorString.BLOCK_MOVE_UP to R.string.block_move_up,
            EditorString.BLOCK_DELETE to R.string.delete_block,
            EditorString.TAP_AGAIN_TO_DELETE to R.string.tap_again_to_delete,
        )

        expected.forEach { (key, id) ->
            assertEquals(key.name, id, key.resourceId())
            assertEquals(key.name, resources.getString(id), strings.text(key))
        }
    }

    @Test
    fun aHeadingNamesItsLevel() {
        (1..4).forEach { level -> assertTrue(strings.heading(level).contains(level.toString())) }
    }
}
