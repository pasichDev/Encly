package com.pasich.encly.editor

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.theme.EditorIcons
import com.pasich.encly.editor.theme.EditorLineSpacing
import com.pasich.encly.editor.theme.EditorTheme
import com.pasich.encly.editor.theme.LocalEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the editor takes from its host, and what it does when the host gives nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorServicesTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun englishIsTheDefaultTextOfEveryKey() {
        EditorString.entries.forEach { assertEquals(it.english, EnglishEditorStrings.text(it)) }
        assertEquals("Heading 3", EnglishEditorStrings.heading(3))
    }

    @Test
    fun everyKeyHasATextOfItsOwn() {
        assertEquals(EditorString.entries.size, EditorString.entries.map { it.name }.toSet().size)
        assertTrue(EditorString.entries.all { it.english.isNotBlank() })
    }

    @Test
    fun withoutAHostTheEditorUsesEnglishAndInertServices() {
        var seen: List<Any>? = null
        rule.setContent {
            seen = listOf(
                LocalEditorStrings.current,
                LocalEditorClipboard.current,
                LocalLinkOpener.current,
                LocalEditorLogger.current,
                LocalEditorTheme.current,
            )
        }

        assertSame(EnglishEditorStrings, seen!![0])
        assertSame(EditorClipboard.None, seen!![1])
        assertSame(LinkOpener.None, seen!![2])
        assertSame(EditorLogger.None, seen!![3])
        assertEquals(EditorTheme().spacing, (seen!![4] as EditorTheme).spacing)
        // The inert services take a call and do nothing.
        EditorClipboard.None.copy("label", "text", sensitive = true)
        LinkOpener.None.open("https://example.com")
        EditorLogger.None.warn("tag", "event")
    }

    @Test
    fun theProviderHandsTheHostsServicesToTheContent() {
        val strings = object : EditorStrings {
            override fun text(key: EditorString) = "t:${key.name}"

            override fun heading(level: Int) = "h$level"
        }
        val theme = EditorTheme(lineSpacing = EditorLineSpacing.COMPACT)
        val clipboard = EditorClipboard { _, _, _ -> }
        val opener = LinkOpener { }
        val logger = EditorLogger { _, _ -> }
        var seen: List<Any>? = null

        rule.setContent {
            MaterialTheme {
                EditorProvider(strings, theme, clipboard, opener, logger) {
                    seen = listOf(
                        LocalEditorStrings.current,
                        LocalEditorTheme.current,
                        LocalEditorClipboard.current,
                        LocalLinkOpener.current,
                        LocalEditorLogger.current,
                    )
                }
            }
        }

        assertEquals(listOf<Any>(strings, theme, clipboard, opener, logger), seen)
    }

    @Test
    fun anUnreadableNoteIsReportedAsAFixedEventWithoutItsContent() {
        val events = mutableListOf<Pair<String, String>>()

        assertNull(BlockConverter.jsonToBlocksOrNull("secret {", EditorLogger { tag, event -> events += tag to event }))

        assertEquals(1, events.size)
        assertEquals("BlockConverter", events.single().first)
        assertTrue(events.single().second, "secret" !in events.single().second)
        assertEquals(emptyList<Block>(), BlockConverter.jsonToBlocksOrNull("[]"))
    }

    @Test
    fun theDefaultIconsAreDistinctDrawings() {
        val icons = EditorIcons()
        val all = listOf(
            icons.text, icons.heading1, icons.heading2, icons.heading3, icons.heading4, icons.checklist,
            icons.bulletList, icons.numberedList, icons.quote, icons.link, icons.separator, icons.globe,
            icons.plus, icons.check, icons.external, icons.copy, icons.edit, icons.arrowUp, icons.arrowDown,
            icons.duplicate, icons.trash,
        )
        assertEquals(all.size, all.map { it.name }.toSet().size)
    }
}
