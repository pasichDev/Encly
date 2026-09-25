package com.pasich.encly.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pasich.encly.R
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditNoteScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }
    private lateinit var editor: EditNoteViewModel

    /** The notes list with the editor opened on top of it, as from a tap on a card. */
    private fun open(noteId: Long = -1L, readTrashOnly: Boolean = false) {
        editor = app.editor(noteId = noteId, readTrashOnly = readTrashOnly)
        setNavScreen(
            viewModels(editor, app.tagList()),
            route = TestRoutes.EDIT_NOTE,
            start = NavRoutes.HomeRoute.name,
        ) { nav -> EditNoteScreen(nav) }
        navigateTo("${NavRoutes.EditNoteRoute.name}/$noteId?isReadTrashOnly=$readTrashOnly")
        waitFor { editor.status.value != SaveStatusNote.LOADING }
    }

    private fun stored(vararg blocks: Block, title: String = "Trip", id: Long = NOTE_ID) = Note(
        id = id,
        title = title,
        value = BlockConverter.blocksToJson(blocks.toList()),
        date = 1_700_000_000_000,
    ).also { app.withNotes(it) }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun field(index: Int) = rule.onAllNodes(hasSetTextAction())[index]

    private fun tool(id: Int) = rule.onNode(hasContentDescription(str(id)) and hasClickAction())

    private fun hasTool(label: String) =
        rule.onAllNodes(hasContentDescription(label) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()

    private fun back() = rule.onNodeWithContentDescription(str(R.string.back)).performClick()

    @Test
    fun aNewNoteShowsTheTitleAParagraphAndTheToolbar() {
        open()

        rule.onNodeWithText(str(R.string.note_title_placeholder), useUnmergedTree = true).assertExists()
        listOf(
            R.string.block_add,
            R.string.block_heading,
            R.string.block_checklist,
            R.string.block_bulleted_list,
            R.string.block_numbered_list,
            R.string.quote,
            R.string.block_link,
            R.string.block_separator,
        ).forEach { tool(it).assertExists() }
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2)
        rule.onNodeWithContentDescription(str(R.string.edit_tags)).assertExists()
        rule.onNodeWithContentDescription(str(R.string.editor_lock)).assertExists()
    }

    @Test
    fun typingAndBackSavesTheNewNote() {
        open()

        field(0).performTextInput("Shopping")
        field(1).performTextInput("eggs and flour")
        back()

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        val saved = app.notes.insertedNotes.last()
        assertEquals("Shopping", saved.title)
        assertTrue(saved.value, saved.value.contains("eggs and flour"))
    }

    @Test
    fun aBlankNewNoteIsNotSaved() {
        open()

        back()

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        assertEquals(0, app.notes.insertCalls)
    }

    @Test
    fun twoQuickBacksLeaveOnlyTheEditor() {
        stored(text("body"))
        open(NOTE_ID)

        // Both taps land before the first save finishes, as a fast double tap does.
        val tap = rule.onNodeWithContentDescription(str(R.string.back)).fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action
        rule.runOnUiThread {
            tap?.invoke()
            tap?.invoke()
        }

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        rule.mainClock.advanceTimeBy(LEAVE_MS)
        rule.waitForIdle()
        assertEquals(NavRoutes.HomeRoute.name, currentRoute())
    }

    @Test
    fun anExistingNoteShowsItsContentAndWhenItWasEdited() {
        stored(text("first line"), text("second line"), title = "Trip plan")
        open(NOTE_ID)

        waitForText("Trip plan")
        rule.onNodeWithText("first line").assertExists()
        rule.onNodeWithText("second line").assertExists()
        assertTrue(visibleTexts().map(::textOf).any { it.contains("2023") })
        assertBodyNotEmpty(minTexts = 3)
    }

    @Test
    fun theChecklistToolTurnsTheParagraphIntoAChecklist() {
        stored(text("milk"))
        open(NOTE_ID)
        field(1).performClick()

        tool(R.string.block_checklist).performClick()

        waitFor { editor.blocks.first() is Block.ListBlock }
        assertEquals(BlockType.LIST_CHECK, (editor.blocks.first() as Block.ListBlock).blockType)
        rule.onNodeWithText("milk").assertExists()
        rule.onNode(isCheckbox and hasContentDescription("milk")).assertExists()
    }

    @Test
    fun aChecklistItemCanBeTicked() {
        stored(
            Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("milk"))), blockType = BlockType.LIST_CHECK),
        )
        open(NOTE_ID)

        rule.onNode(isCheckbox and hasContentDescription("milk")).performClick()

        waitFor {
            (editor.blocks.first() as Block.ListBlock).items.value.single().isCheck
        }
        assertEquals(
            ToggleableState.On,
            rule.onNode(
                isCheckbox and hasContentDescription("milk"),
            ).fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )
    }

    @Test
    fun theHeadingToolTurnsTheParagraphIntoAHeading() {
        stored(text("Plan"))
        open(NOTE_ID)
        field(1).performClick()

        tool(R.string.block_heading).performClick()

        waitFor { editor.blocks.first() is Block.HBlock }
        rule.onNodeWithText("Plan").assertExists()
    }

    @Test
    fun addBlockOpensTheSecondRowAndCloseReturns() {
        open()

        tool(R.string.block_add).performClick()

        val second = (1..4).map { str(R.string.block_heading_level, it) } +
            listOf(
                R.string.text,
                R.string.undo,
                R.string.redo,
                R.string.block_move_up,
                R.string.block_move_down,
            ).map(::str)
        second.forEach { label -> waitFor { hasTool(label) } }
        tool(R.string.close).performClick()
        waitFor { hasTool(str(R.string.block_checklist)) }
    }

    @Test
    fun aHeadingLevelFromTheSecondRowIsApplied() {
        stored(text("Plan"))
        open(NOTE_ID)
        field(1).performClick()
        tool(R.string.block_add).performClick()
        waitFor { hasTool(str(R.string.block_heading_level, 3)) }

        rule.onNode(hasContentDescription(str(R.string.block_heading_level, 3)) and hasClickAction()).performClick()

        waitFor { (editor.blocks.first() as? Block.HBlock)?.blockType == BlockType.H3 }
    }

    @Test
    fun theSeparatorToolAddsASeparator() {
        stored(text("above"))
        open(NOTE_ID)
        field(1).performClick()

        tool(R.string.block_separator).performClick()

        waitFor { editor.blocks.any { it is Block.SeparatorBlock } }
    }

    @Test
    fun lockingHidesTheToolbarAndKeepsTheText() {
        stored(text("secret plan"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(R.string.editor_lock)).performClick()

        waitFor { rule.onAllNodes(hasContentDescription(str(R.string.block_add))).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("secret plan").assertExists()
        assertTrue(editor.lockEditor.value)
    }

    @Test
    fun aNoteInTheTrashIsReadOnlyWithRestoreAndDelete() {
        stored(text("old"))
        open(NOTE_ID, readTrashOnly = true)

        rule.onNodeWithText(str(R.string.restore)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.delete_from_trash)).assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(hasContentDescription(str(R.string.block_add))).fetchSemanticsNodes().size)
        rule.onNodeWithText("old").assertExists()
    }

    @Test
    fun deletingFromTheTrashAsksFirst() {
        stored(text("old"))
        open(NOTE_ID, readTrashOnly = true)

        rule.onNodeWithText(str(R.string.delete_from_trash)).performClick()

        waitForText(str(R.string.dialog_title))
    }

    @Test
    fun theMoreMenuOffersDuplicateDiscardAndDelete() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(R.string.more_options)).performClick()

        waitForText(str(R.string.duplicate))
        rule.onNodeWithText(str(R.string.note_discard_all_changes)).assertExists()
        rule.onNodeWithText(str(R.string.delete)).assertExists()
    }

    @Test
    fun discardingEditsAsksFirst() {
        stored(text("body"))
        open(NOTE_ID)
        field(1).performTextInput(" changed")

        rule.onNodeWithContentDescription(str(R.string.more_options)).performClick()
        waitForText(str(R.string.note_discard_all_changes))
        rule.onNodeWithText(str(R.string.note_discard_all_changes)).performClick()

        waitForText(str(R.string.note_discard_confirm_title))
        rule.onNodeWithText(str(R.string.note_discard_confirm_message)).assertIsDisplayed()
    }

    @Test
    fun discardingWithoutEditsJustLeaves() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(R.string.more_options)).performClick()
        waitForText(str(R.string.note_discard_all_changes))
        rule.onNodeWithText(str(R.string.note_discard_all_changes)).performClick()

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        assertEquals(0, countText(str(R.string.note_discard_confirm_title)))
    }

    @Test
    fun anUnreadableNoteSaysSoAndCannotBeUnlocked() {
        Note(id = NOTE_ID, title = "Broken", value = "{not json", date = 1L).also { app.withNotes(it) }
        open(NOTE_ID)

        waitForText(str(R.string.note_load_failed))
        assertEquals(0, rule.onAllNodes(hasContentDescription(str(R.string.editor_lock))).fetchSemanticsNodes().size)
    }

    @Test
    fun theTagButtonOffersToCreateATagWhenThereAreNone() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(R.string.edit_tags)).performClick()

        waitFor { countText(str(R.string.create_new_tag)) + countText(str(R.string.tags_manage)) > 0 }
    }

    @Test
    fun choosingATagTagsTheNote() {
        app.withTags(com.pasich.encly.data.model.Tag(id = 4, nameTag = "Travel"))
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(R.string.edit_tags)).performClick()
        waitForText("Travel")
        rule.onNode(hasText("Travel") and hasClickAction()).performClick()

        waitFor { editor.state.value.note.tagId == 4L }
        waitForText("TRAVEL", substring = true)
    }

    private companion object {
        const val NOTE_ID = 91L
        const val LEAVE_MS = 1_000L
        val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
    }
}
