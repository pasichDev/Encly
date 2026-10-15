package com.pasich.encly.presentation.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.R
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.dialogs.blocks.ActionBlockDialog
import com.pasich.encly.presentation.dialogs.blocks.ActionLinkBottomSheet
import com.pasich.encly.presentation.dialogs.blocks.ActionOtherBottomSheet
import com.pasich.encly.presentation.dialogs.blocks.SettingsBlockDialog
import com.pasich.encly.presentation.dialogs.blocks.describe
import com.pasich.encly.ui.screens.ComposeScreenTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the block sheets offer, what they hide when the note is read-only, and what each row reports. */
@OptIn(ExperimentalMaterial3Api::class)
class BlockSheetsTest : ComposeScreenTest() {
    private val actions = mutableListOf<ActionBlockDialog>()
    private var dismissed = 0

    private fun linkBlock(url: String, isError: Boolean = false) =
        Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "Example", url = url, isError = isError)))

    private fun showOther(settings: SettingsBlockDialog) = setScreen {
        ActionOtherBottomSheet(
            settings = settings,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onAction = { actions += it },
            onDismiss = { dismissed++ },
        )
    }

    private fun showLink(settings: SettingsBlockDialog) = setScreen {
        ActionLinkBottomSheet(
            settings = settings,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onAction = { actions += it },
            onDismiss = { dismissed++ },
        )
    }

    private fun shown(id: Int) = countText(str(id)) > 0

    // --- the block type names ------------------------------------------------------------

    @Test
    fun everyBlockTypeHasItsOwnName() {
        val names = listOf(
            Block.TextBlock() to R.string.text,
            Block.QuoteBlock() to R.string.quote,
            Block.ListBlock(blockType = BlockType.LIST_BULLET) to R.string.block_list,
            Block.HBlock(blockType = BlockType.H1) to R.string.block_heading,
            Block.LinkBlock() to R.string.block_link,
            Block.SeparatorBlock() to R.string.block_separator,
        )

        names.forEach { (block, name) -> assertEquals(block::class.simpleName, name, block.describe()) }
        assertEquals(names.size, names.map { it.second }.toSet().size)
    }

    // --- the sheet for any other block ------------------------------------------------------

    @Test
    fun aHiddenSheetShowsNothing() {
        showOther(SettingsBlockDialog(Block.QuoteBlock(), isBottomSheetVisible = false))

        assertTrue(!shown(R.string.block_move_up) && !shown(R.string.quote))
    }

    @Test
    fun aVisibleSheetNamesTheBlockAndOffersTheEditRows() {
        showOther(SettingsBlockDialog(Block.QuoteBlock(), isBottomSheetVisible = true))

        assertTrue(shown(R.string.quote))
        listOf(R.string.block_move_up, R.string.block_move_down, R.string.block_duplicate, R.string.delete_block)
            .forEach { assertTrue(str(it), shown(it)) }
    }

    @Test
    fun aReadOnlyNoteOffersNoEditRows() {
        showOther(SettingsBlockDialog(Block.QuoteBlock(), isBottomSheetVisible = true, canEdit = false))

        assertTrue(shown(R.string.quote))
        listOf(R.string.block_move_up, R.string.block_move_down, R.string.block_duplicate, R.string.delete_block)
            .forEach { assertTrue(str(it), !shown(it)) }
    }

    @Test
    fun theRowsReportTheirAction() {
        showOther(SettingsBlockDialog(Block.QuoteBlock(), isBottomSheetVisible = true))

        rule.onNodeWithText(str(R.string.block_move_up)).performClick()
        rule.onNodeWithText(str(R.string.block_move_down)).performClick()
        rule.onNodeWithText(str(R.string.block_duplicate)).performClick()

        assertEquals(
            listOf(ActionBlockDialog.Move(1), ActionBlockDialog.Move(2), ActionBlockDialog.Duplicate),
            actions,
        )
    }

    @Test
    fun deletingTakesASecondTap() {
        showOther(SettingsBlockDialog(Block.QuoteBlock(), isBottomSheetVisible = true))

        rule.onNodeWithText(str(R.string.delete_block)).performClick()
        rule.waitForIdle()
        assertTrue("the first tap only arms the row", actions.isEmpty())
        assertTrue(shown(R.string.tap_again_to_delete))

        rule.onNodeWithText(str(R.string.tap_again_to_delete)).performClick()
        assertEquals(listOf<ActionBlockDialog>(ActionBlockDialog.Delete), actions)
    }

    @Test
    fun aBlockAlreadyAtTheTopCanNotMoveUp() {
        showOther(SettingsBlockDialog(Block.QuoteBlock(), blockMove = 1, isBottomSheetVisible = true))

        rule.onNodeWithText(str(R.string.block_move_up)).performClick()
        rule.onNodeWithText(str(R.string.block_move_down)).performClick()

        assertEquals(listOf<ActionBlockDialog>(ActionBlockDialog.Move(2)), actions)
    }

    @Test
    fun aBlockAlreadyAtTheBottomCanNotMoveDown() {
        showOther(SettingsBlockDialog(Block.QuoteBlock(), blockMove = 2, isBottomSheetVisible = true))

        rule.onNodeWithText(str(R.string.block_move_down)).performClick()
        rule.onNodeWithText(str(R.string.block_move_up)).performClick()

        assertEquals(listOf<ActionBlockDialog>(ActionBlockDialog.Move(1)), actions)
    }

    // --- the sheet for a link -----------------------------------------------------------------

    @Test
    fun anOpenableLinkOffersOpenCopyAndEdit() {
        showLink(SettingsBlockDialog(linkBlock("https://example.com/page"), isBottomSheetVisible = true))

        assertTrue(shown(R.string.link_open))
        assertTrue(shown(R.string.link_copy))
        assertTrue(shown(R.string.link_edit))
        assertTrue("the whole address is shown", countText("https://example.com/page", substring = true) > 0)
    }

    @Test
    fun aBlockedAddressCanBeCopiedButNotOpened() {
        showLink(SettingsBlockDialog(linkBlock("javascript:alert(1)"), isBottomSheetVisible = true))

        assertTrue(!shown(R.string.link_open))
        assertTrue(shown(R.string.link_copy))
        assertTrue("the address is still shown to the user", countText("javascript:alert(1)", substring = true) > 0)
    }

    @Test
    fun theLinkRowsReportTheirAction() {
        showLink(SettingsBlockDialog(linkBlock("https://example.com"), isBottomSheetVisible = true))

        rule.onNodeWithText(str(R.string.link_open)).performClick()
        rule.onNodeWithText(str(R.string.link_copy)).performClick()
        rule.onNodeWithText(str(R.string.link_edit)).performClick()

        assertEquals(
            listOf(ActionBlockDialog.OpenLink, ActionBlockDialog.CopyLink, ActionBlockDialog.EditLink),
            actions,
        )
    }

    @Test
    fun aReadOnlyLinkOffersNothingThatChangesTheNote() {
        showLink(SettingsBlockDialog(linkBlock("https://example.com"), isBottomSheetVisible = true, canEdit = false))

        assertTrue(shown(R.string.link_open))
        assertTrue(shown(R.string.link_copy))
        assertTrue(!shown(R.string.link_edit))
        assertTrue(!shown(R.string.delete_block))
        assertTrue(!shown(R.string.block_duplicate))
    }

    @Test
    fun aHiddenLinkSheetShowsNothing() {
        showLink(SettingsBlockDialog(linkBlock("https://example.com"), isBottomSheetVisible = false))

        assertTrue(!shown(R.string.link_open) && !shown(R.string.link_copy))
    }

    @Test
    fun theLinkSheetOfAnEditableLinkAlsoHasTheBlockRows() {
        showLink(SettingsBlockDialog(linkBlock("https://example.com"), isBottomSheetVisible = true))

        assertTrue(shown(R.string.block_move_up))
        assertTrue(shown(R.string.delete_block))
    }
}
