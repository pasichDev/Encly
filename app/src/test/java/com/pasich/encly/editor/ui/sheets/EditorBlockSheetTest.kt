package com.pasich.encly.editor.ui.sheets

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.editor.EditorClipboard
import com.pasich.encly.editor.EditorProvider
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LinkOpener
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.ui.EditorController
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sheet of a block: what it offers, and that each row reaches the right service or controller call. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorBlockSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val link = Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "x", url = "https://example.com/a")))
    private val quote = Block.QuoteBlock()
    private val controller = Mockito.mock(EditorController::class.java).also {
        Mockito.`when`(it.blocks).thenReturn(listOf(link, quote))
        Mockito.`when`(it.interactedBlockPosition()).thenReturn(-1)
    }
    private val copied = mutableListOf<Triple<String, String, Boolean>>()
    private val opened = mutableListOf<String>()
    private var closed = 0

    private fun show(kind: BlockSheetKind?, blockId: String?, canEdit: Boolean = true) {
        rule.setContent {
            MaterialTheme {
                EditorProvider(
                    clipboard = EditorClipboard { label, text, sensitive -> copied += Triple(label, text, sensitive) },
                    linkOpener = LinkOpener { opened += it },
                ) {
                    EditorBlockSheet(kind, blockId, canEdit, controller) { closed++ }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun shown(key: EditorString) = rule.onAllNodes(androidx.compose.ui.test.hasText(key.english))
        .fetchSemanticsNodes().isNotEmpty()

    private fun tap(key: EditorString) {
        rule.onNodeWithText(key.english).performClick()
        rule.waitForIdle()
    }

    private fun awaitClosed() = rule.waitUntil(timeoutMillis = 5_000) { closed > 0 }

    @Test
    fun withoutAKindOrABlockNothingShows() {
        show(null, link.id)
        assertTrue(!shown(EditorString.LINK_COPY))
    }

    @Test
    fun aBlockThatIsGoneShowsNothing() {
        show(BlockSheetKind.LINK, "gone")
        assertTrue(!shown(EditorString.LINK_COPY))
    }

    @Test
    fun copyingALinkGoesToTheClipboardAsSensitiveTextAndClosesTheSheet() {
        show(BlockSheetKind.LINK, link.id)

        tap(EditorString.LINK_COPY)
        awaitClosed()

        assertEquals(listOf(Triple("Link", "https://example.com/a", true)), copied)
        assertEquals(1, closed)
    }

    @Test
    fun openingALinkHandsItToTheOpener() {
        show(BlockSheetKind.LINK, link.id)

        tap(EditorString.LINK_OPEN)
        awaitClosed()

        assertEquals(listOf("https://example.com/a"), opened)
    }

    @Test
    fun editingALinkReopensItsAddress() {
        show(BlockSheetKind.LINK, link.id)

        tap(EditorString.LINK_EDIT)
        awaitClosed()

        Mockito.verify(controller).editLink(link)
    }

    @Test
    fun aReadOnlyLinkCanBeCopiedButNotEdited() {
        show(BlockSheetKind.LINK, link.id, canEdit = false)

        assertTrue(shown(EditorString.LINK_COPY))
        assertTrue(!shown(EditorString.LINK_EDIT))
    }

    @Test
    fun movingKeepsTheSheetOpenAndDuplicatingClosesIt() {
        show(BlockSheetKind.OTHER, quote.id)

        tap(EditorString.BLOCK_MOVE_UP)
        tap(EditorString.BLOCK_MOVE_DOWN)
        Mockito.verify(controller).moveBlock(true)
        Mockito.verify(controller).moveBlock(false)
        assertEquals(0, closed)

        tap(EditorString.BLOCK_DUPLICATE)
        awaitClosed()
        Mockito.verify(controller).duplicateBlock(quote)
    }

    @Test
    fun aLinkSheetOnAnotherKindOfBlockOffersTheCopyRowOnly() {
        show(BlockSheetKind.LINK, quote.id)

        assertTrue(shown(EditorString.LINK_COPY))
        assertTrue(!shown(EditorString.LINK_OPEN))
    }

    @Test
    fun everyKindOfBlockHasItsOwnName() {
        val names = listOf(
            text() to EditorString.TEXT,
            Block.QuoteBlock() to EditorString.QUOTE,
            Block.ListBlock(blockType = BlockType.LIST_BULLET) to EditorString.BLOCK_LIST,
            Block.HBlock(blockType = BlockType.H1) to EditorString.BLOCK_HEADING,
            Block.LinkBlock() to EditorString.BLOCK_LINK,
            Block.SeparatorBlock() to EditorString.BLOCK_SEPARATOR,
        )
        names.forEach { (block, name) -> assertEquals(name, block.describe()) }
    }

    private fun text() = Block.TextBlock()

    @Test
    fun anArmedDeleteRowDisarmsAfterItsWindow() {
        show(BlockSheetKind.OTHER, quote.id)

        tap(EditorString.BLOCK_DELETE)
        assertTrue(shown(EditorString.TAP_AGAIN_TO_DELETE))
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()

        assertTrue(shown(EditorString.BLOCK_DELETE))
    }

    @Test
    fun deletingTakesASecondTap() {
        show(BlockSheetKind.OTHER, quote.id)

        tap(EditorString.BLOCK_DELETE)
        Mockito.verify(controller, Mockito.never()).removeBlock(quote, BlockRemoveAction.REMOVE)
        tap(EditorString.TAP_AGAIN_TO_DELETE)
        awaitClosed()

        Mockito.verify(controller).removeBlock(quote, BlockRemoveAction.REMOVE)
    }
}
