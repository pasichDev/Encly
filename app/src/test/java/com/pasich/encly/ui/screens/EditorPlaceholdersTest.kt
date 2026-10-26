package com.pasich.encly.ui.screens

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPlaceholdersTest : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }
    private lateinit var editor: EditNoteViewModel

    private fun open(json: String) {
        app.withNotes(Note(id = NOTE_ID, title = "Trip", value = json, date = 1L))
        editor = app.editor(noteId = NOTE_ID)
        setNavScreen(
            viewModels(editor, app.tagList(), app.markdownExport()),
            route = TestRoutes.EDIT_NOTE,
            start = NavRoutes.HomeRoute.name,
        ) { nav -> EditNoteScreen(nav) }
        navigateTo("${NavRoutes.EditNoteRoute.name}/$NOTE_ID?isReadTrashOnly=false")
        waitFor { editor.status.value != SaveStatusNote.LOADING }
    }

    private fun field(index: Int) = rule.onAllNodes(hasSetTextAction())[index]

    private fun hints() = listOf(
        R.string.list_item_placeholder,
        R.string.quote,
        R.string.block_heading_level,
    ).map { id -> if (id == R.string.block_heading_level) str(id, 1) else str(id) }

    @Test
    fun emptyBlocksShowNoHintUntilTheyAreFocused() {
        open(EMPTY_BLOCKS)

        hints().forEach { assertEquals(it, 0, countText(it)) }
    }

    @Test
    fun theFocusedEmptyBlockShowsItsHintAndOthersStayQuiet() {
        open(EMPTY_BLOCKS)

        field(2).performClick()

        waitForText(str(R.string.list_item_placeholder))
        assertEquals(0, countText(str(R.string.quote)))
        assertEquals(0, countText(str(R.string.block_heading_level, 1)))
    }

    @Test
    fun anEmptyQuoteAndHeadingShowTheirHintWhenFocused() {
        open(EMPTY_BLOCKS)

        field(3).performClick()
        waitForText(str(R.string.quote))
        assertEquals(0, countText(str(R.string.list_item_placeholder)))

        field(4).performClick()
        waitForText(str(R.string.block_heading_level, 1))
        waitFor { countText(str(R.string.quote)) == 0 }
    }

    private companion object {
        const val NOTE_ID = 7L
        const val EMPTY_BLOCKS = """[{"blockType":"TEXT","text":"a"},
            {"blockType":"LIST_BULLET","items":[{"value":"","isCheck":false}]},
            {"blockType":"QUOTE","text":""},
            {"blockType":"H1","text":""}]"""
    }
}
