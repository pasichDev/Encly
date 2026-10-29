package com.pasich.encly.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.back
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.block_bulleted_list
import com.pasich.encly.core.resources.block_checklist
import com.pasich.encly.core.resources.block_heading
import com.pasich.encly.core.resources.block_heading_level
import com.pasich.encly.core.resources.block_link
import com.pasich.encly.core.resources.block_more_tools
import com.pasich.encly.core.resources.block_move_down
import com.pasich.encly.core.resources.block_move_up
import com.pasich.encly.core.resources.block_numbered_list
import com.pasich.encly.core.resources.block_separator
import com.pasich.encly.core.resources.checklist_lock
import com.pasich.encly.core.resources.checklist_unlock
import com.pasich.encly.core.resources.close
import com.pasich.encly.core.resources.create_new_tag
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.delete_from_trash
import com.pasich.encly.core.resources.dialog_title
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.edit_tags
import com.pasich.encly.core.resources.editor_lock
import com.pasich.encly.core.resources.markdown_export
import com.pasich.encly.core.resources.markdown_export_warning_title
import com.pasich.encly.core.resources.more_options
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_color_teal
import com.pasich.encly.core.resources.note_color_title
import com.pasich.encly.core.resources.note_discard_all_changes
import com.pasich.encly.core.resources.note_discard_confirm_message
import com.pasich.encly.core.resources.note_discard_confirm_title
import com.pasich.encly.core.resources.note_load_failed
import com.pasich.encly.core.resources.note_pin
import com.pasich.encly.core.resources.note_text_use_default
import com.pasich.encly.core.resources.note_title_placeholder
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.quote
import com.pasich.encly.core.resources.redo
import com.pasich.encly.core.resources.restore
import com.pasich.encly.core.resources.tags_manage
import com.pasich.encly.core.resources.text
import com.pasich.encly.core.resources.undo
import com.pasich.encly.data.model.Note
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.verifyNoInteractions

class EditNoteScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }
    private lateinit var editor: EditNoteViewModel

    /** The notes list with the editor opened on top of it, as from a tap on a card. */
    private fun open(noteId: Long = -1L, readTrashOnly: Boolean = false) {
        editor = app.editor(noteId = noteId, readTrashOnly = readTrashOnly)
        setNavScreen(
            viewModels(editor, app.tagList(), app.markdownExport()),
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

    private fun tool(id: StringResource) = rule.onNode(hasContentDescription(str(id)) and hasClickAction())

    private fun hasTool(label: String) =
        rule.onAllNodes(hasContentDescription(label) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()

    private fun moreToolsToggles() =
        rule.onAllNodes(hasContentDescription(str(Res.string.block_more_tools))).fetchSemanticsNodes()

    private fun back() = rule.onNodeWithContentDescription(str(Res.string.back)).performClick()

    @Test
    fun aNewNoteShowsTheTitleAParagraphAndTheToolbar() {
        open()

        rule.onNodeWithText(str(Res.string.note_title_placeholder), useUnmergedTree = true).assertExists()
        listOf(
            Res.string.block_more_tools,
            Res.string.block_heading,
            Res.string.block_checklist,
            Res.string.block_bulleted_list,
            Res.string.block_numbered_list,
            Res.string.quote,
            Res.string.undo,
            Res.string.redo,
        ).forEach { tool(it).assertExists() }
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2)
        rule.onNodeWithContentDescription(str(Res.string.edit_tags)).assertExists()
        rule.onNodeWithContentDescription(str(Res.string.editor_lock)).assertExists()
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
        val tap = rule.onNodeWithContentDescription(str(Res.string.back)).fetchSemanticsNode()
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

        tool(Res.string.block_checklist).performClick()

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
    fun aLockedChecklistStillTicksButItsTextTakesNoFocus() {
        stored(
            Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("milk"))), blockType = BlockType.LIST_CHECK),
        )
        open(NOTE_ID)
        field(1).performClick()
        tool(Res.string.block_more_tools).performClick()
        waitFor { hasTool(str(Res.string.checklist_lock)) }

        // The submenu scrolls sideways; the lock is past the edge of a phone-wide row.
        tool(Res.string.checklist_lock).performScrollTo().performClick()

        val list = editor.blocks.first() as Block.ListBlock
        waitFor { list.locked.value }
        waitFor { hasTool(str(Res.string.checklist_unlock)) }
        rule.onNode(isCheckbox and hasContentDescription("milk")).performClick()
        waitFor { list.items.value.single().isCheck }
        assertEquals(
            0,
            rule.onAllNodes(hasSetTextAction() and hasText("milk") and isEnabled()).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun aLockedChecklistCanBeUnlockedAfterWorkingElsewhere() {
        stored(
            Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("milk"))), blockType = BlockType.LIST_CHECK),
            text("after"),
        )
        open(NOTE_ID)
        field(1).performClick()
        tool(Res.string.block_more_tools).performClick()
        waitFor { hasTool(str(Res.string.checklist_lock)) }
        tool(Res.string.checklist_lock).performScrollTo().performClick()
        val list = editor.blocks.first() as Block.ListBlock
        waitFor { list.locked.value }
        rule.onNode(hasText("after") and hasSetTextAction()).performClick()
        waitFor { !hasTool(str(Res.string.checklist_unlock)) }

        // A tap on the locked list makes it the block the toolbar acts on again.
        rule.onNode(isCheckbox and hasContentDescription("milk")).performClick()

        waitFor { hasTool(str(Res.string.checklist_unlock)) }
        tool(Res.string.checklist_unlock).performScrollTo().performClick()
        waitFor { !list.locked.value }
    }

    @Test
    fun theHeadingToolTurnsTheParagraphIntoAHeading() {
        stored(text("Plan"))
        open(NOTE_ID)
        field(1).performClick()

        tool(Res.string.block_heading).performClick()

        waitFor { editor.blocks.first() is Block.HBlock }
        rule.onNodeWithText("Plan").assertExists()
    }

    @Test
    fun addBlockOpensTheSecondRowAndCloseReturns() {
        open()

        tool(Res.string.block_more_tools).performClick()

        val second = (1..4).map { str(Res.string.block_heading_level, it) } +
            listOf(
                Res.string.text,
                Res.string.block_link,
                Res.string.block_separator,
                Res.string.block_move_up,
                Res.string.block_move_down,
            ).map(::str)
        second.forEach { label -> waitFor { hasTool(label) } }
        tool(Res.string.close).performClick()
        waitFor { hasTool(str(Res.string.block_checklist)) }
    }

    @Test
    fun aHeadingLevelFromTheSecondRowIsApplied() {
        stored(text("Plan"))
        open(NOTE_ID)
        field(1).performClick()
        tool(Res.string.block_more_tools).performClick()
        waitFor { hasTool(str(Res.string.block_heading_level, 3)) }

        rule.onNode(hasContentDescription(str(Res.string.block_heading_level, 3)) and hasClickAction()).performClick()

        waitFor { (editor.blocks.first() as? Block.HBlock)?.blockType == BlockType.H3 }
    }

    @Test
    fun theSeparatorToolAddsASeparator() {
        stored(text("above"))
        open(NOTE_ID)
        field(1).performClick()
        tool(Res.string.block_more_tools).performClick()
        waitFor { hasTool(str(Res.string.block_separator)) }

        tool(Res.string.block_separator).performClick()

        waitFor { editor.blocks.any { it is Block.SeparatorBlock } }
    }

    @Test
    fun lockingHidesTheToolbarAndKeepsTheText() {
        stored(text("secret plan"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.editor_lock)).performClick()

        waitFor { moreToolsToggles().isEmpty() }
        rule.onNodeWithText("secret plan").assertExists()
        assertTrue(editor.lockEditor.value)
    }

    @Test
    fun aNoteInTheTrashIsReadOnlyWithRestoreAndDelete() {
        stored(text("old"))
        open(NOTE_ID, readTrashOnly = true)

        rule.onNodeWithText(str(Res.string.restore)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.delete_from_trash)).assertIsDisplayed()
        assertEquals(0, moreToolsToggles().size)
        rule.onNodeWithText("old").assertExists()
    }

    @Test
    fun deletingFromTheTrashAsksFirst() {
        stored(text("old"))
        open(NOTE_ID, readTrashOnly = true)

        rule.onNodeWithText(str(Res.string.delete_from_trash)).performClick()

        waitForText(str(Res.string.dialog_title))
    }

    @Test
    fun theMoreMenuOffersDuplicateDiscardAndDelete() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.more_options)).performClick()

        waitForText(str(Res.string.duplicate))
        rule.onNodeWithText(str(Res.string.note_discard_all_changes)).assertExists()
        rule.onNodeWithText(str(Res.string.delete)).assertExists()
    }

    @Test
    fun theMoreMenuOffersArchiveColourAndMarkdownExportButNoPin() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.more_options)).performClick()

        waitForText(str(Res.string.note_archive))
        rule.onNodeWithText(str(Res.string.markdown_export)).assertExists()
        // Pinning orders the notes list, so it is offered there, not inside a note.
        assertEquals(0, countText(str(Res.string.note_pin)))
        // Section headings are drawn in capitals.
        rule.onNodeWithText(str(Res.string.note_color_title).uppercase()).assertExists()
        rule.onNodeWithContentDescription(str(Res.string.note_color_teal)).assertExists()
    }

    @Test
    fun useDefaultTextAppearsOnlyWhileTheNoteHasItsOwn() {
        stored(text("body"))
        open(NOTE_ID)
        rule.onNodeWithContentDescription(str(Res.string.more_options)).performClick()
        waitForText(str(Res.string.note_archive))
        assertEquals(0, countText(str(Res.string.note_text_use_default)))

        rule.runOnIdle { editor.updateNoteLineSpacing(com.pasich.encly.domain.model.NoteLineSpacing.COMFORTABLE) }

        waitForText(str(Res.string.note_text_use_default))
        rule.onNodeWithText(str(Res.string.note_text_use_default)).performScrollTo().performClick()
        waitFor { countText(str(Res.string.note_text_use_default)) == 0 }
    }

    @Test
    fun archivingFromTheMenuStoresTheNoteArchivedAndLeaves() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.more_options)).performClick()
        waitForText(str(Res.string.note_archive))
        rule.onNodeWithText(str(Res.string.note_archive)).performScrollTo().performClick()

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        assertEquals(listOf("archived:$NOTE_ID=true"), app.notes.columnWrites)
    }

    @Test
    fun anArchivedNoteOffersUnarchive() {
        Note(id = NOTE_ID, title = "Trip", value = "[]", date = 1L, isArchived = true).also { app.withNotes(it) }
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.more_options)).performClick()

        waitForText(str(Res.string.note_unarchive))
        rule.onNodeWithText(str(Res.string.note_unarchive)).performScrollTo().performClick()
        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        assertEquals(listOf("archived:$NOTE_ID=false"), app.notes.columnWrites)
    }

    @Test
    fun colouringIsSavedWithTheNote() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.more_options)).performClick()
        waitForText(str(Res.string.note_archive))
        rule.onNodeWithContentDescription(str(Res.string.note_color_teal)).performScrollTo().performClick()
        waitFor { editor.state.value.note.color == "teal" }
        // Leaving the editor saves the note, with its colour.
        rule.onNodeWithContentDescription(str(Res.string.back)).performClick()
        waitFor { currentRoute() == NavRoutes.HomeRoute.name }

        assertEquals("teal", app.notes.updatedNotes.last().color)
    }

    @Test
    fun exportingAsMarkdownSavesTheNoteThenWarnsAndTouchesNoFile() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.more_options)).performClick()
        waitForText(str(Res.string.markdown_export))
        rule.onNodeWithText(str(Res.string.markdown_export)).performScrollTo().performClick()

        waitForText(str(Res.string.markdown_export_warning_title))
        assertEquals(0, countText(str(Res.string.backup_reauth_title)))
        verifyNoInteractions(app.markdownDocuments)
        // The editor stays open behind the warning.
        assertEquals(NavRoutes.EditNoteRoute.name, currentRoute()?.substringBefore('/'))
    }

    private fun anyTextFieldFocused() =
        rule.onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes().isNotEmpty()

    /** Waits until block [type] is the note's first and its field really has the focus (not just been asked to). */
    private fun settleOn(isList: Boolean) {
        waitFor { (editor.blocks.first() is Block.ListBlock) == isList }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        waitFor { editor.focusedBlockId.value == editor.blocks.first().id }
    }

    /** Runs [action] a frame at a time and says whether a text field (so the keyboard) had focus in every frame. */
    private fun focusNeverLapsesDuring(action: () -> Unit): Boolean {
        rule.mainClock.autoAdvance = false
        action()
        var always = true
        repeat(FRAMES_TO_WATCH) {
            rule.mainClock.advanceTimeByFrame()
            always = always && anyTextFieldFocused()
        }
        rule.mainClock.autoAdvance = true
        return always
    }

    @Test
    fun convertingTheFocusedParagraphToAListNeverLeavesNothingFocused() {
        stored(text("milk"))
        open(NOTE_ID)
        field(1).performClick()
        waitFor { anyTextFieldFocused() }

        val kept = focusNeverLapsesDuring { tool(Res.string.block_checklist).performClick() }

        assertTrue("a frame had no focused text field", kept)
        // The list's own field has the focus in the end, the anchor has let go.
        settleOn(isList = true)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun deletingTheLastListItemWithBackspaceNeverLeavesNothingFocused() {
        stored(
            Block.ListBlock(
                MutableStateFlow(listOf(ItemListBlock("one"), ItemListBlock("two"))),
                blockType = BlockType.LIST_BULLET,
            ),
        )
        open(NOTE_ID)
        rule.onNode(hasSetTextAction() and hasText("two")).performClick()
        rule.onNode(hasSetTextAction() and hasText("two")).performTextInputSelection(TextRange(0))
        waitFor { editor.focusedBlockId.value == editor.blocks.first().id }

        val kept = focusNeverLapsesDuring {
            rule.onNode(hasSetTextAction() and hasText("two")).performKeyInput { pressKey(Key.Backspace) }
        }

        assertTrue("a frame had no focused text field", kept)
        waitFor { (editor.blocks.first() as Block.ListBlock).items.value.size == 1 }
    }

    @Test
    fun undoingTheConversionAndRedoingItNeverLeavesNothingFocusedEither() {
        stored(text("milk"))
        open(NOTE_ID)
        field(1).performClick()
        tool(Res.string.block_checklist).performClick()
        settleOn(isList = true)

        assertTrue(focusNeverLapsesDuring { tool(Res.string.undo).performClick() })
        settleOn(isList = false)
        assertTrue(focusNeverLapsesDuring { tool(Res.string.redo).performClick() })
        settleOn(isList = true)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun controlZInTheTitleLeavesTheEditorHistoryAlone() {
        stored(text("body"))
        open(NOTE_ID)
        field(1).performTextInput(" changed")
        field(0).performClick()

        field(0).performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Z)
            keyUp(Key.CtrlLeft)
        }

        assertEquals("body changed", (editor.blocks.first() as Block.TextBlock).text.value)
    }

    @Test
    fun discardingEditsAsksFirst() {
        stored(text("body"))
        open(NOTE_ID)
        field(1).performTextInput(" changed")

        rule.onNodeWithContentDescription(str(Res.string.more_options)).performClick()
        waitForText(str(Res.string.note_discard_all_changes))
        rule.onNodeWithText(str(Res.string.note_discard_all_changes)).performClick()

        waitForText(str(Res.string.note_discard_confirm_title))
        rule.onNodeWithText(str(Res.string.note_discard_confirm_message)).assertIsDisplayed()
    }

    @Test
    fun discardingWithoutEditsJustLeaves() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.more_options)).performClick()
        waitForText(str(Res.string.note_discard_all_changes))
        rule.onNodeWithText(str(Res.string.note_discard_all_changes)).performClick()

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        assertEquals(0, countText(str(Res.string.note_discard_confirm_title)))
    }

    @Test
    fun anUnreadableNoteSaysSoAndCannotBeUnlocked() {
        Note(id = NOTE_ID, title = "Broken", value = "{not json", date = 1L).also { app.withNotes(it) }
        open(NOTE_ID)

        waitForText(str(Res.string.note_load_failed))
        assertEquals(0, rule.onAllNodes(hasContentDescription(str(Res.string.editor_lock))).fetchSemanticsNodes().size)
    }

    @Test
    fun theTagButtonOffersToCreateATagWhenThereAreNone() {
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.edit_tags)).performClick()

        waitFor { countText(str(Res.string.create_new_tag)) + countText(str(Res.string.tags_manage)) > 0 }
    }

    @Test
    fun choosingATagTagsTheNote() {
        app.withTags(com.pasich.encly.data.model.Tag(id = 4, nameTag = "Travel"))
        stored(text("body"))
        open(NOTE_ID)

        rule.onNodeWithContentDescription(str(Res.string.edit_tags)).performClick()
        waitForText("Travel")
        rule.onNode(hasText("Travel") and hasClickAction()).performClick()

        waitFor { editor.state.value.note.tagId == 4L }
        waitForText("TRAVEL", substring = true)
    }

    private companion object {
        const val NOTE_ID = 91L
        const val LEAVE_MS = 1_000L
        const val FRAMES_TO_WATCH = 40
        const val SETTLE_MS = 1_000L
        val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
    }
}
