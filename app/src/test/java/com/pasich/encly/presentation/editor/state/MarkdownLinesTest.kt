package com.pasich.encly.presentation.editor.state

import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinesTest {
    @Test
    fun aMarkCountsOnlyWhenItsSpaceWasJustTypedAtTheStart() {
        assertEquals(BlockType.H2, typedShortcut("## ", 3)?.type)
        assertEquals(BlockType.H1, typedShortcut("# Title", 2)?.type)
        // Typed further on, or the cursor elsewhere: text.
        assertNull(typedShortcut("# Title", 7))
        assertNull(typedShortcut("Price # 1", 9))
        assertNull(typedShortcut("#hashtag", 8))
        assertNull(typedShortcut("##### ", 6))
    }

    @Test
    fun checklistMarksWinOverBullets() {
        assertEquals(Shortcut(BlockType.LIST_CHECK, "- [ ] "), leadingShortcut("- [ ] milk"))
        assertEquals(Shortcut(BlockType.LIST_CHECK, "[x] ", checked = true), leadingShortcut("[x] done"))
        assertEquals(Shortcut(BlockType.LIST_BULLET, "- "), leadingShortcut("- milk"))
        assertEquals(Shortcut(BlockType.LIST_NUMBER, "12. "), leadingShortcut("12. step"))
        assertEquals(Shortcut(BlockType.LIST_NUMBER, "3) "), leadingShortcut("3) step"))
        assertNull(leadingShortcut("2024.05 report"))
    }

    @Test
    fun threeDashesAreASeparatorOnlyAsTheWholeParagraph() {
        assertEquals(BlockType.SEPARATOR, typedShortcut("---", 3)?.type)
        assertNull(typedShortcut("---x", 3))
    }

    @Test
    fun pastedBlankLinesOnlySeparateAndListLinesOfOneKindJoin() {
        val blocks = pastedBlocks(listOf("Intro", "", "- a", "- b", "1. c", "", "***"))

        assertEquals(4, blocks.size)
        assertEquals(BlockType.LIST_BULLET, (blocks[1] as Block.ListBlock).blockType)
        assertEquals(2, (blocks[1] as Block.ListBlock).items.value.size)
        assertEquals(BlockType.LIST_NUMBER, (blocks[2] as Block.ListBlock).blockType)
        assertTrue(blocks[3] is Block.SeparatorBlock)
    }

    @Test
    fun onlyAWebAddressAloneIsALink() {
        assertTrue(isLoneWebAddress(" https://example.com/a?b=1 "))
        assertFalse(isLoneWebAddress("see https://example.com"))
        assertFalse(isLoneWebAddress("file:///sdcard/notes.txt"))
        assertFalse(isLoneWebAddress("https://evil.tld\\@bank.com"))
    }

    @Test
    fun everyKindOfLineBreakBecomesOne() {
        assertEquals("a\nb\nc\nd\ne", normalizeLineBreaks("a\r\nb\rc d e"))
    }
}
