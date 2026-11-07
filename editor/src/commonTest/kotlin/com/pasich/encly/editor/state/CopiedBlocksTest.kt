package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.TableData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pasting back what this editor copied or cut: the blocks go in as they were, not the escaped text. */
class CopiedBlocksTest {
    private val editor = BlockEditorState { 0L }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun table() = Block.TableBlock(MutableStateFlow(TableData.of(listOf(listOf("a", "b"), listOf("c|d", "*")))))

    private fun nested() = Block.ListBlock(
        MutableStateFlow(
            listOf(
                ItemListBlock("one"),
                ItemListBlock("two", level = 1),
                ItemListBlock("three", level = 2),
            ),
        ),
        BlockType.LIST_BULLET,
    )

    private fun contents() = editor.blocks.map {
        when (it) {
            is Block.TextBlock -> "P:" + it.text.value

            is Block.TableBlock ->
                "T:" + it.table.value.rows.joinToString("/") { r -> r.cells.joinToString(",") { c -> c.value } }

            is Block.ListBlock -> "L:" + it.items.value.joinToString("|") { i -> "${i.level}${i.value}" }

            else -> it::class.simpleName.orEmpty()
        }
    }

    @Test
    fun aPastedCutParagraphKeepsItsCharactersInsteadOfTheEscapes() {
        val target = text("")
        editor.load(listOf(target))
        editor.rememberCopied("price\\*2", listOf(text("price*2")))

        val kept = editor.pasteCopied(target.id, null, "", "price\\*2", "")

        assertEquals("price*2", kept)
        assertEquals(listOf("P:price*2"), contents())
    }

    @Test
    fun theTextGoesInAtTheCursorBetweenWhatWasThereAndAWindowsLineEndingIsTheSameText() {
        val target = text("ab")
        editor.load(listOf(target))
        editor.rememberCopied("x\\_\ny", listOf(text("x_\ny")))

        val kept = editor.pasteCopied(target.id, null, "a", "x\\_\r\ny", "b")

        assertEquals("ax_\nyb", kept)
        assertEquals(listOf("P:ax_\nyb"), contents())
    }

    @Test
    fun aTableAndANestedListComeBackWhole() {
        val target = text("")
        editor.load(listOf(target))
        val source = listOf(table(), nested())
        editor.rememberCopied("md", source)

        val kept = editor.pasteCopied(target.id, null, "", "md", "")

        assertEquals("", kept)
        assertEquals(listOf("T:a,b/c|d,*", "L:0one|1two|2three"), contents().take(2))
        // New blocks: nothing is shared with the originals.
        assertTrue(editor.blocks.none { b -> source.any { it.id == b.id } })
        val pasted = (editor.blocks[0] as Block.TableBlock).table.value
        val original = (source[0] as Block.TableBlock).table.value
        assertTrue(pasted.rows.flatMap { r -> r.cells.map { it.id } }.none { original.cell(it) != null })
    }

    @Test
    fun blocksPastedInsideAParagraphSplitItAndTheTextAfterTheCursorGoesOn() {
        val target = text("headtail")
        editor.load(listOf(target))
        editor.rememberCopied("md", listOf(text("first"), table(), text("last")))

        val kept = editor.pasteCopied(target.id, null, "head", "md", "tail")

        assertEquals("headfirst", kept)
        assertEquals(listOf("P:headfirst", "T:a,b/c|d,*", "P:lasttail"), contents())
    }

    @Test
    fun aPasteThatIsNotTheCopiedTextIsLeftToTheNormalPaste() {
        val target = text("")
        editor.load(listOf(target))
        assertNull(editor.pasteCopied(target.id, null, "", "price\\*2", ""))

        editor.rememberCopied("price\\*2", listOf(text("price*2")))
        assertNull(editor.pasteCopied(target.id, null, "", "price\\*3", ""))
        assertEquals(listOf("P:"), contents())
    }

    @Test
    fun theLastCopyReplacesTheOneBefore() {
        val target = text("")
        editor.load(listOf(target))
        editor.rememberCopied("one", listOf(text("1")))
        editor.rememberCopied("two", listOf(text("2")))

        assertNull(editor.pasteCopied(target.id, null, "", "one", ""))
        assertEquals("2", editor.pasteCopied(target.id, null, "", "two", ""))
    }

    @Test
    fun nothingEmptyIsRemembered() {
        val target = text("")
        editor.load(listOf(target))
        editor.rememberCopied("", listOf(text("x")))
        assertNull(editor.pasteCopied(target.id, null, "", "", ""))

        editor.rememberCopied("x", emptyList())
        assertNull(editor.pasteCopied(target.id, null, "", "x", ""))
    }

    @Test
    fun editingTheOriginalAfterTheCopyChangesNothing() {
        val original = text("kept")
        editor.load(listOf(original, text("")))
        editor.rememberCopied("kept", listOf(original))
        original.text.value = "changed"

        editor.pasteCopied(editor.blocks[1].id, null, "", "kept", "")

        assertEquals(listOf("P:changed", "P:kept"), contents())
    }

    @Test
    fun loadingANoteForgetsWhatWasCopied() {
        editor.load(listOf(text("")))
        editor.rememberCopied("secret", listOf(text("secret")))

        editor.load(emptyList())

        assertNull(editor.pasteCopied(editor.blocks[0].id, null, "", "secret", ""))
    }

    @Test
    fun aHeadingTakesTheCopiedTextOnOneLine() {
        val heading = Block.HBlock(MutableStateFlow(""), BlockType.H2)
        editor.load(listOf(heading))
        editor.rememberCopied("a\nb", listOf(text("a\nb")))

        assertEquals("a b", editor.pasteCopied(heading.id, null, "", "a\nb", ""))
    }

    @Test
    fun codeAndOtherBlocksTakeNoCopiedPaste() {
        val code = Block.CodeBlock(MutableStateFlow(""))
        val rule = Block.SeparatorBlock()
        editor.load(listOf(code, rule))
        editor.rememberCopied("x\\*", listOf(text("x*")))

        assertNull(editor.pasteCopied(code.id, null, "", "x\\*", ""))
        assertNull(editor.pasteCopied(rule.id, null, "", "x\\*", ""))
        assertNull(editor.pasteCopied("gone", null, "", "x\\*", ""))
        assertEquals("", code.text.value)
    }

    @Test
    fun aListItemTakesTheCopiedTextAndAListWithoutThatItemTakesNothing() {
        val list = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("ab"))), BlockType.LIST_BULLET)
        editor.load(listOf(list))
        editor.rememberCopied("x\\*", listOf(text("x*")))
        val item = list.items.value[0].id

        assertNull(editor.pasteCopied(list.id, null, "a", "x\\*", "b"))
        assertNull(editor.pasteCopied(list.id, "gone", "a", "x\\*", "b"))
        assertEquals("ax*b", editor.pasteCopied(list.id, item, "a", "x\\*", "b"))
        assertEquals(listOf("0ax*b"), list.items.value.map { "${it.level}${it.value}" })
    }

    @Test
    fun severalBlocksPastedInAListItemBecomeItems() {
        val list = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("ab"))), BlockType.LIST_BULLET)
        editor.load(listOf(list))
        editor.rememberCopied("md", listOf(text("first"), text("second")))

        val kept = editor.pasteCopied(list.id, list.items.value[0].id, "a", "md", "b")

        assertEquals("afirst", kept)
        assertEquals(listOf("afirst", "secondb"), list.items.value.map { it.value })
        assertNotEquals(list.items.value[0].id, list.items.value[1].id)
    }
}
