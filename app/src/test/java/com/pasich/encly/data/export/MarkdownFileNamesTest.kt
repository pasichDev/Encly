package com.pasich.encly.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownFileNamesTest {

    private fun names(vararg reserved: String) = MarkdownFileNames(reserved.toList())

    @Test
    fun aTitleBecomesItsFileName() {
        assertEquals("Shopping list.md", names().next("Shopping list", "Untitled"))
    }

    @Test
    fun pathSeparatorsAndReservedCharactersAreDropped() {
        assertEquals("abc.md", names().next("a/b\\c", "Untitled"))
        assertEquals("what.md", names().next("wh:at?*\"<>|", "Untitled"))
    }

    @Test
    fun aTraversalTitleCannotEscapeTheFolder() {
        val name = names().next("../../etc/passwd", "Untitled")
        assertFalse(name.contains("/"))
        assertFalse(name.startsWith("."))
        assertEquals("etcpasswd.md", name)
        assertEquals("Untitled.md", names().next("..", "Untitled"))
    }

    @Test
    fun leadingDotsAndTrailingDotsAndSpacesAreStripped() {
        assertEquals("hidden.md", names().next("...hidden", "Untitled"))
        assertEquals("end.md", names().next("end. . .", "Untitled"))
        assertEquals("both.md", names().next("  ..both..  ", "Untitled"))
    }

    @Test
    fun controlFormatAndBidiCharactersAreDropped() {
        assertEquals("abc.md", names().next("a\u0000b\u001Fc", "Untitled"))
        // U+202E (right-to-left override) can make "exe.md" read like "dm.exe".
        assertEquals("txt.exe.md", names().next("\u202Etxt.exe", "Untitled"))
        assertEquals("ab.md", names().next("a\u200Bb", "Untitled"))
        assertEquals("a b.md", names().next("a\u2028b", "Untitled"))
    }

    @Test
    fun whitespaceRunsBecomeOneSpace() {
        assertEquals("a b c.md", names().next("a \t\n  b\u00A0 c", "Untitled"))
    }

    @Test
    fun windowsDeviceNamesAreAvoided() {
        assertEquals("_CON.md", names().next("CON", "Untitled"))
        assertEquals("_nul.md", names().next("nul", "Untitled"))
        assertEquals("_COM1.md", names().next("COM1", "Untitled"))
        assertEquals("_LPT9.md", names().next("LPT9", "Untitled"))
        assertEquals("CONSOLE.md", names().next("CONSOLE", "Untitled"))
    }

    @Test
    fun anEmptyTitleUsesTheFallback() {
        assertEquals("Untitled.md", names().next("", "Untitled"))
        assertEquals("Untitled.md", names().next("///", "Untitled"))
        assertEquals("note.md", names().next("", ""))
    }

    @Test
    fun theNameIsNormalisedToNfc() {
        val decomposed = "Café"
        assertEquals("Café.md", names().next(decomposed, "Untitled"))
    }

    @Test
    fun aLongTitleIsCutOnACharacterBoundary() {
        val name = names().next("я".repeat(200), "Untitled")
        assertTrue(name.toByteArray().size <= MarkdownFileNames.MAX_NAME_BYTES)
        assertTrue(name.endsWith(".md"))
        // Two bytes per letter: no half character at the cut.
        assertEquals(name.removeSuffix(".md"), "я".repeat(name.length - 3))
        val emoji = names().next("😀".repeat(100), "Untitled")
        assertTrue(emoji.toByteArray().size <= MarkdownFileNames.MAX_NAME_BYTES)
        assertEquals(emoji.removeSuffix(".md"), "😀".repeat(emoji.removeSuffix(".md").length / 2))
    }

    @Test
    fun equalTitlesGetANumberedSuffix() {
        val names = names()
        assertEquals("Plan.md", names.next("Plan", "Untitled"))
        assertEquals("Plan (2).md", names.next("Plan", "Untitled"))
        assertEquals("Plan (3).md", names.next("Plan", "Untitled"))
    }

    @Test
    fun namesThatDifferOnlyInCaseAreTheSameName() {
        val names = names()
        assertEquals("Plan.md", names.next("Plan", "Untitled"))
        assertEquals("plan (2).md", names.next("plan", "Untitled"))
        assertEquals("PLAN (3).md", names.next("PLAN", "Untitled"))
    }

    @Test
    fun aDecomposedAndAComposedTitleCollide() {
        val names = names()
        assertEquals("Café.md", names.next("Café", "Untitled"))
        assertEquals("Café (2).md", names.next("Café", "Untitled"))
    }

    @Test
    fun theSuffixStillFitsTheByteBudget() {
        val names = names()
        val long = "я".repeat(200)
        repeat(12) { assertTrue(names.next(long, "Untitled").toByteArray().size <= MarkdownFileNames.MAX_NAME_BYTES) }
    }

    @Test
    fun reservedNamesAreNeverGivenOut() {
        assertEquals("tasks (2).md", names("tasks.md").next("tasks", "Untitled"))
        assertEquals("TASKS (2).md", names("tasks.md").next("TASKS", "Untitled"))
    }

    @Test
    fun theSameTitleInAnotherFolderIsAnotherFile() {
        val names = names()
        assertEquals("Plan.md", names.next("Plan", "Untitled"))
        assertEquals("archive/Plan.md", names.next("Plan", "Untitled", "archive/"))
        assertEquals("archive/Plan (2).md", names.next("Plan", "Untitled", "archive/"))
    }
}
