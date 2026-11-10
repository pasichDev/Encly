package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.empty_notes
import com.pasich.encly.core.resources.note_add
import com.pasich.encly.core.resources.open_menu
import com.pasich.encly.core.resources.pin_delete_digit
import com.pasich.encly.core.resources.settings_appearance
import com.pasich.encly.core.resources.settings_general
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.WordGrid
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.presentation.screen.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`

/**
 * The key screens in a right-to-left layout. Each test lays the screen out left to right, notes where
 * things are, flips the same composition to right to left and expects the layout to be its mirror image
 * (what sits at the start sits at the start of the other side), except what must not move: the PIN keypad
 * and the recovery words.
 */
class RtlScreensTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private var direction by mutableStateOf(LayoutDirection.Ltr)

    private fun flip() {
        direction = LayoutDirection.Rtl
        rule.waitForIdle()
    }

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

    private fun windowWidth(): Float = bounds(rule.onRoot()).width

    /** [find] sits on the mirror image of where it was, once the layout is flipped. */
    private fun assertMirrored(name: String, find: () -> SemanticsNodeInteraction) {
        val before = bounds(find())
        val width = windowWidth()
        flip()
        val after = bounds(find())
        assertEquals("$name: left edge", width - before.right, after.left, TOLERANCE)
        assertEquals("$name: width", before.width, after.width, TOLERANCE)
        assertEquals("$name: top", before.top, after.top, TOLERANCE)
        direction = LayoutDirection.Ltr
        rule.waitForIdle()
    }

    /** [find] stays exactly where it was, once the layout is flipped. */
    private fun assertStays(name: String, find: () -> SemanticsNodeInteraction) {
        val before = bounds(find())
        flip()
        val after = bounds(find())
        assertEquals("$name: left edge", before.left, after.left, TOLERANCE)
        assertEquals("$name: top", before.top, after.top, TOLERANCE)
        direction = LayoutDirection.Ltr
        rule.waitForIdle()
    }

    // --- the notes list --------------------------------------------------------------------------

    private fun showHome() {
        setNavScreen(viewModels(*app.home(app.noteList(app.notes))), route = NavRoutes.HomeRoute.name) { nav ->
            CompositionLocalProvider(LocalLayoutDirection provides direction) { MainRootScreen(nav) }
        }
        waitForText(str(Res.string.empty_notes))
    }

    @Test
    fun theNotesListMirrorsItsMenuButtonAndItsNewNoteButton() {
        showHome()

        assertMirrored("menu button") { rule.onNodeWithContentDescription(str(Res.string.open_menu)) }
        assertMirrored("new note button") {
            rule.onNodeWithText(str(Res.string.note_add), useUnmergedTree = true)
        }
    }

    @Test
    fun theMenuButtonIsOnTheLeftInALeftToRightLayoutAndOnTheRightInARightToLeftOne() {
        showHome()
        val window = windowWidth()
        assertTrue(bounds(rule.onNodeWithContentDescription(str(Res.string.open_menu))).center.x < window / 2)

        flip()

        assertTrue(bounds(rule.onNodeWithContentDescription(str(Res.string.open_menu))).center.x > window / 2)
    }

    // --- settings -----------------------------------------------------------------------------------

    @Test
    fun settingsMirrorTheirSectionsAndRows() {
        setNavScreen(viewModels(app.settingsVm()), route = NavRoutes.SettingsRoute.name) { nav ->
            CompositionLocalProvider(LocalLayoutDirection provides direction) { SettingsScreen(nav) }
        }

        assertMirrored("section title") {
            rule.onAllNodesWithText(str(Res.string.settings_general), ignoreCase = true).onFirst()
        }
        assertMirrored("row title") {
            rule.onAllNodesWithText(str(Res.string.settings_appearance)).onFirst()
        }
    }

    // --- the lock screen ------------------------------------------------------------------------------

    private fun showLock() {
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        setNavScreen(viewModels(app.lock()), route = NavRoutes.LockRoute.name) { nav ->
            CompositionLocalProvider(LocalLayoutDirection provides direction) { LockScreen(nav) }
        }
    }

    private fun key(digit: Int) = rule.onNode(hasText(digit.toString()) and hasClickAction())

    @Test
    fun thePinKeypadKeepsItsDigitsInPlaceInARightToLeftLayout() {
        showLock()

        (0..9).forEach { digit -> assertStays("key $digit") { key(digit) } }
    }

    @Test
    fun thePinKeypadReadsOneTwoThreeFromLeftToRightAndDeleteSitsAtTheBottomRight() {
        showLock()
        flip()

        assertTrue(bounds(key(1)).center.x < bounds(key(2)).center.x)
        assertTrue(bounds(key(2)).center.x < bounds(key(3)).center.x)
        assertTrue(bounds(key(7)).center.y > bounds(key(1)).center.y)
        val delete = bounds(rule.onNodeWithContentDescription(str(Res.string.pin_delete_digit)))
        assertTrue(delete.center.x > bounds(key(0)).center.x)
        assertEquals(bounds(key(0)).center.y, delete.center.y, TOLERANCE)
    }

    // --- the recovery words -----------------------------------------------------------------------------

    @Test
    fun theRecoveryWordsKeepTheirGridInARightToLeftLayout() {
        setScreen {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                WordGrid(words = WORDS, hidden = false)
            }
        }

        WORDS.forEach { word -> assertStays(word) { rule.onNodeWithText(word) } }
        flip()
        assertTrue(bounds(rule.onNodeWithText(WORDS[0])).center.x < bounds(rule.onNodeWithText(WORDS[1])).center.x)
        assertEquals(
            bounds(rule.onNodeWithText(WORDS[0])).center.y,
            bounds(rule.onNodeWithText(WORDS[1])).center.y,
            TOLERANCE,
        )
    }

    // --- icons ------------------------------------------------------------------------------------------------

    @Test
    fun theDirectionalIconsMirrorAndTheOthersDoNot() {
        listOf(
            EnclyIcons.Back,
            EnclyIcons.Chevron,
            EnclyIcons.Undo,
            EnclyIcons.Redo,
            EnclyIcons.Send,
            EnclyIcons.ListBullet,
            EnclyIcons.Checklist,
            EnclyIcons.Quote,
            EnclyIcons.External,
        ).forEach { assertTrue(it.name, it.autoMirror) }
        listOf(EnclyIcons.Plus, EnclyIcons.Check, EnclyIcons.Close, EnclyIcons.ArrowUp, EnclyIcons.ChevronDown)
            .forEach { assertFalse(it.name, it.autoMirror) }
    }

    private companion object {
        const val TOLERANCE = 2f
        val WORDS = listOf("abandon", "ability", "able", "about", "above", "absent")
    }
}
