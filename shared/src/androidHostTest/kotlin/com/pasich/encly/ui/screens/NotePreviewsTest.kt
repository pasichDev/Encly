package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.security_previews_title
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.presentation.components.tiles.LocalShowNotePreviews
import com.pasich.encly.presentation.components.tiles.NoteItem
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.presentation.screen.settings.SecuritySettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Show note previews": off, a card is the title, tag and date; search still reads the whole note. */
class NotePreviewsTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private fun note(id: Long, title: String, text: String) = Note(
        id = id,
        title = title,
        value = BlockConverter.blocksToJson(listOf(Block.TextBlock(MutableStateFlow(text)))),
        date = 1_700_000_000_000 + id,
    )

    private fun showCard(show: Boolean) {
        val home = Tag(id = 1, nameTag = "Home")
        val item = NoteListItem.of(NoteWithTags(note(1, "Groceries", "milk and bread"), listOf(home)))
        setScreen {
            CompositionLocalProvider(LocalShowNotePreviews provides show) {
                NoteItem(onItemClick = {}, onItemLongClick = {}, item = item)
            }
        }
    }

    @Test
    fun aCardNamesTwoTagsAndCountsTheRest() {
        val tags = listOf(
            Tag(id = 1, nameTag = "Home"),
            Tag(id = 2, nameTag = "Work", position = 1),
            Tag(id = 3, nameTag = "Trips", position = 2),
        )
        val item = NoteListItem.of(NoteWithTags(note(1, "Groceries", "milk"), tags))
        setScreen { NoteItem(onItemClick = {}, onItemLongClick = {}, item = item) }

        rule.onNodeWithText("Home, Work").assertIsDisplayed()
        rule.onNodeWithText("+1").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Trips").fetchSemanticsNodes().size)
    }

    @Test
    fun aCardShowsTheStartOfTheTextByDefault() {
        showCard(show = true)

        rule.onNodeWithText("Groceries").assertIsDisplayed()
        rule.onNodeWithText("milk and bread").assertIsDisplayed()
    }

    @Test
    fun withPreviewsOffACardIsTheTitleAndTheTag() {
        showCard(show = false)

        rule.onNodeWithText("Groceries").assertIsDisplayed()
        rule.onNodeWithText("Home").assertIsDisplayed()
        assertEquals(0, countText("milk and bread"))
    }

    @Test
    fun aDescriptionIsAPreviewToo() {
        val summarized = note(1, "Plan", "body").copy(description = "A short summary")
        val item = NoteListItem.of(NoteWithTags(summarized, emptyList()))
        setScreen {
            CompositionLocalProvider(LocalShowNotePreviews provides false) {
                NoteItem(onItemClick = {}, onItemLongClick = {}, item = item)
            }
        }

        rule.onNodeWithText("Plan").assertIsDisplayed()
        assertEquals(0, countText("A short summary"))
    }

    @Test
    fun theTrashAndAnyBareNoteHidesItsTextToo() {
        setScreen {
            CompositionLocalProvider(LocalShowNotePreviews provides false) {
                val trashed = note(1, "Old", "secret text")
                NoteItem(onItemClick = {}, onItemLongClick = {}, itemNote = trashed, dimmed = true)
            }
        }

        rule.onNodeWithText("Old").assertIsDisplayed()
        assertEquals(0, countText("secret text"))
    }

    @Test
    fun theHomeListHidesTheTextButSearchStillFindsIt() {
        app.withNotes(note(1, "Groceries", "milk"), note(2, "Trip", "tickets"))
        setNavScreen(viewModels(*app.home()), route = NavRoutes.HomeRoute.name) { nav ->
            CompositionLocalProvider(LocalShowNotePreviews provides false) { MainRootScreen(nav) }
        }
        waitForText("Trip")
        assertEquals(0, countText("tickets"))

        rule.onNode(hasSetTextAction()).performTextInput("tick")

        waitFor { countText("Groceries") == 0 && countText("Trip") > 0 }
        assertEquals(0, countText("tickets"))
    }

    @Test
    fun theSecuritySwitchTurnsPreviewsOffAndOn() {
        setNavScreen(
            viewModels(app.securitySettings(), app.notePreviewsVm(), app.toolbarVm(), app.backup()),
            route = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> SecuritySettingsScreen(nav) }
        val title = str(Res.string.security_previews_title)
        waitForText(title)
        assertTrue(app.notePreviews.show.value)

        rule.onNode(isToggleable() and hasText(title)).performScrollTo().performClick()
        waitFor { !app.notePreviews.show.value }

        rule.onNode(isToggleable() and hasText(title)).performScrollTo().performClick()
        waitFor { app.notePreviews.show.value }
    }
}
