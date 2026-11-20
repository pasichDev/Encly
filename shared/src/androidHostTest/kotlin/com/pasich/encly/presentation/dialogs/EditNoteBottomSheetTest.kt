package com.pasich.encly.presentation.dialogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.find_in_note
import com.pasich.encly.core.resources.markdown_copy
import com.pasich.encly.core.resources.markdown_export
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_color_teal
import com.pasich.encly.core.resources.note_discard_all_changes
import com.pasich.encly.core.resources.note_export_pdf
import com.pasich.encly.core.resources.note_print
import com.pasich.encly.core.resources.note_text_use_default
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.select_all
import com.pasich.encly.core.resources.tap_again_to_delete
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.NoteTextOverride
import com.pasich.encly.ui.screens.ComposeScreenTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The editor's "More actions" sheet: which tiles it offers and in which order, its text controls with
 * their reset buttons, and when a tile's or a row's action runs.
 */
class EditNoteBottomSheetTest : ComposeScreenTest() {
    private val log = mutableListOf<String>()
    private var visible by mutableStateOf(true)
    private var override by mutableStateOf(NoteTextOverride())

    private fun show(
        canCopy: Boolean = true,
        canSelect: Boolean = true,
        documents: Boolean = false,
        archived: Boolean = false,
    ) {
        setScreen {
            EditNoteBottomSheet(
                isVisible = visible,
                rows = EditorRows(canCopy, canSelect, documents, archived),
                look = NoteLook(
                    fontSize = override.size ?: 16,
                    lineSpacing = override.spacing ?: NoteLineSpacing.DEFAULT,
                    override = override,
                    color = NoteColor.NONE,
                    actions = NoteLookActions(
                        onSize = { override = override.copy(size = it) },
                        onSpacing = { override = override.copy(spacing = it) },
                        onDefaultSize = { override = override.copy(size = null) },
                        onDefaultSpacing = { override = override.copy(spacing = null) },
                        onColor = { log += "color:${it.key}" },
                    ),
                ),
                onDismiss = {
                    log += "dismissed"
                    visible = false
                },
                onAction = { log += it.name },
            )
        }
    }

    private val isTile = SemanticsMatcher("is a button") {
        it.config.getOrNull(SemanticsProperties.Role) == Role.Button
    }

    /** The tiles as shown: line by line, start to end. */
    private fun tiles(): List<String> = rule
        .onAllNodes(isTile and hasAnyAncestor(hasTestTag(EDIT_NOTE_SHEET_TAG)) and hasAnyText)
        .fetchSemanticsNodes()
        .filter { it.config.getOrNull(SemanticsProperties.ContentDescription) == null }
        .sortedWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x }))
        .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } }
        .filter { it !in setOf(str(Res.string.note_discard_all_changes), str(Res.string.delete)) }

    private fun sheetSize() = rule.onNodeWithTag(EDIT_NOTE_SHEET_TAG).fetchSemanticsNode().size

    private fun resetButtons() = rule.onAllNodes(hasContentDescription(str(Res.string.note_text_use_default)))

    @Test
    fun aPhoneOffersItsQuickActionsAsTilesInOrder() {
        show()

        assertEquals(
            listOf(
                str(Res.string.find_in_note),
                str(Res.string.select_all),
                str(Res.string.markdown_copy),
                str(Res.string.duplicate),
                str(Res.string.markdown_export),
                str(Res.string.note_archive),
            ),
            tiles(),
        )
        assertEquals(1, countText(str(Res.string.note_discard_all_changes)))
        assertEquals(1, countText(str(Res.string.delete)))
    }

    @Test
    fun aHostThatMakesDocumentsAddsPdfAndPrintAfterTheMarkdownExport() {
        show(documents = true)

        val tiles = tiles()
        val export = tiles.indexOf(str(Res.string.markdown_export))
        assertEquals(str(Res.string.note_export_pdf), tiles[export + 1])
        assertEquals(str(Res.string.note_print), tiles[export + 2])
        assertEquals(str(Res.string.note_archive), tiles[export + 3])
    }

    @Test
    fun anUnreadableNoteOffersNeitherCopyNorSelectAll() {
        show(canCopy = false, canSelect = false)

        assertEquals(
            listOf(
                str(Res.string.find_in_note),
                str(Res.string.duplicate),
                str(Res.string.markdown_export),
                str(Res.string.note_archive),
            ),
            tiles(),
        )
    }

    @Test
    fun simpleEditingOffersCopyButNotSelectAll() {
        show(canSelect = false)

        assertEquals(1, countText(str(Res.string.markdown_copy)))
        assertEquals(0, countText(str(Res.string.select_all)))
    }

    @Test
    fun anArchivedNoteOffersUnarchive() {
        show(archived = true)

        assertEquals(1, countText(str(Res.string.note_unarchive)))
        assertEquals(0, countText(str(Res.string.note_archive)))
    }

    @Test
    fun aTileRunsItsActionOnlyOnceTheSheetHasSlidAway() {
        show()
        rule.mainClock.autoAdvance = false

        rule.onNodeWithText(str(Res.string.duplicate)).performClick()
        rule.waitForIdle()
        repeat(2) { rule.mainClock.advanceTimeByFrame() }

        assertEquals("nothing runs under the sheet", emptyList<String>(), log)
        assertEquals("the sheet is still there, sliding away", 1, countText(str(Res.string.duplicate)))

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(listOf("dismissed", "DUPLICATE"), log)
        assertEquals(0, countText(str(Res.string.duplicate)))
    }

    @Test
    fun deleteTakesASecondTapAndThenRunsAfterTheSheet() {
        show()

        rule.onNodeWithText(str(Res.string.delete)).performClick()
        rule.waitForIdle()
        assertEquals("armed, nothing deleted, the sheet still open", emptyList<String>(), log)

        rule.onNodeWithText(str(Res.string.tap_again_to_delete)).performClick()
        rule.waitForIdle()

        assertEquals(listOf("dismissed", "TRASH"), log)
    }

    @Test
    fun discardClosesTheSheetAndReportsIt() {
        show()

        rule.onNodeWithText(str(Res.string.note_discard_all_changes)).performClick()
        rule.waitForIdle()

        assertEquals(listOf("dismissed", "CLOSE_NO_SAVE"), log)
    }

    @Test
    fun theSheetIsTheSameSizeWhateverTheResetButtonsDo() {
        show()
        rule.waitForIdle()
        assertEquals("one reset per row, always laid out", 2, resetButtons().fetchSemanticsNodes().size)
        resetButtons()[0].assertIsNotEnabled()
        resetButtons()[1].assertIsNotEnabled()
        val before = sheetSize()

        rule.runOnIdle { override = NoteTextOverride(size = 22, spacing = NoteLineSpacing.COMFORTABLE) }
        rule.waitForIdle()
        resetButtons()[0].assertIsEnabled()
        resetButtons()[1].assertIsEnabled()
        assertEquals("both resets alive", before, sheetSize())

        resetButtons()[0].performClick()
        rule.waitForIdle()
        resetButtons()[0].assertIsNotEnabled()
        resetButtons()[1].assertIsEnabled()
        assertEquals(
            "the size reset used: only the spacing is the note's own",
            NoteLineSpacing.COMFORTABLE,
            override.spacing,
        )
        assertEquals(before, sheetSize())

        resetButtons()[1].performClick()
        rule.waitForIdle()
        assertEquals(NoteTextOverride(), override)
        assertEquals(before, sheetSize())
        assertEquals("the text controls never close the sheet", emptyList<String>(), log)
    }

    @Test
    fun aSwatchAppliesAtOnceAndKeepsTheSheetOpen() {
        show()

        rule.onNodeWithContentDescription(str(Res.string.note_color_teal)).performClick()
        rule.waitForIdle()

        assertEquals(listOf("color:${NoteColor.TEAL.key}"), log)
        assertEquals(1, countText(str(Res.string.duplicate)))
    }
}
