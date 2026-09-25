package com.pasich.encly.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import com.pasich.encly.R
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Writing in the editor as a user does: Enter, a typed mark, a paste, Backspace, undo. */
@OptIn(ExperimentalTestApi::class)
class EditorWritingTest : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }
    private lateinit var editor: EditNoteViewModel

    private fun open(vararg blocks: Block) {
        Note(id = NOTE_ID, title = "Trip", value = BlockConverter.blocksToJson(blocks.toList()), date = 1L)
            .also { app.withNotes(it) }
        editor = app.editor(noteId = NOTE_ID)
        setNavScreen(
            viewModels(editor, app.tagList()),
            route = TestRoutes.EDIT_NOTE,
            start = NavRoutes.HomeRoute.name,
        ) { nav -> EditNoteScreen(nav) }
        navigateTo("${NavRoutes.EditNoteRoute.name}/$NOTE_ID?isReadTrashOnly=false")
        waitFor { editor.status.value != SaveStatusNote.LOADING }
    }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun paragraph(value: String) = rule.onNode(hasSetTextAction() and hasText(value))

    private fun texts(): List<String> = editor.blocks.map {
        (it as? Block.TextBlock)?.text?.value
            ?: it::class.simpleName.orEmpty()
    }

    @Test
    fun enterSplitsTheParagraphAtTheCursor() {
        open(text("Hello world"))
        paragraph("Hello world").performClick()
        paragraph("Hello world").performTextInputSelection(TextRange(5))

        paragraph("Hello world").performTextInput("\n")

        waitFor { editor.blocks.size == 2 }
        assertEquals(listOf("Hello", " world"), texts())
    }

    @Test
    fun aTypedMarkTurnsTheParagraphIntoAHeading() {
        open(text(""))
        val field = rule.onAllNodes(hasSetTextAction())[1]
        field.performClick()

        field.performTextInput("#")
        field.performTextInput(" ")

        waitFor { editor.blocks.first() is Block.HBlock }
        assertEquals(BlockType.H1, (editor.blocks.first() as Block.HBlock).blockType)
    }

    @Test
    fun pastedLinesBecomeBlocksAndOneUndoTakesThemBack() {
        open(text("Start"))
        paragraph("Start").performClick()

        paragraph("Start").performTextInput(" one\n- milk\n- eggs\nlast")

        waitFor { editor.blocks.size == 3 }
        assertEquals(listOf("Start one", "ListBlock", "last"), texts())
        rule.runOnUiThread { editor.undo() }
        waitFor { editor.blocks.size == 1 }
        rule.onNodeWithText("Start").assertExists()
    }

    @Test
    fun backspaceAtTheStartJoinsTheParagraphToTheOneAbove() {
        open(text("Hello"), text(" world"))
        paragraph(" world").performClick()
        paragraph(" world").performTextInputSelection(TextRange(0))

        paragraph(" world").performKeyInput { pressKey(Key.Backspace) }

        waitFor { editor.blocks.size == 1 }
        assertEquals(listOf("Hello world"), texts())
    }

    @Test
    fun theKeyboardToggleFocusesTheWorkingBlock() {
        open(text("body"))

        rule.onNode(hasContentDescription(str(R.string.keyboard_show)) and hasClickAction()).performClick()

        waitFor { editor.focusedBlockId.value == editor.blocks.first().id }
        paragraph("body").assertIsFocused()
    }

    private companion object {
        const val NOTE_ID = 92L
    }
}
