package com.pasich.encly.data.handoff

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyNotesBlockMapperTest {

    private fun note(value: String = "", valueJson: String? = null) = HandoffNote(
        id = "n",
        title = "t",
        value = value,
        valueJson = valueJson,
        date = 0,
        isTrash = false,
        isPinned = false,
        attachments = 0,
    )

    /** Maps [blocks] (Editor.js block objects) and reads them back as stored. */
    private fun mapEditor(vararg blocks: String): List<Block> =
        BlockConverter.jsonToBlocks(MyNotesBlockMapper.toBlocksJson(note(valueJson = "[${blocks.joinToString()}]")))

    private fun texts(blocks: List<Block>) = blocks.map {
        when (it) {
            is Block.TextBlock -> "TEXT:${it.text.value}"
            is Block.HBlock -> "${it.blockType}:${it.text.value}"
            is Block.ListBlock -> "${it.blockType}:" + it.items.value.joinToString("|") { item -> item.label() }
            is Block.SeparatorBlock -> "SEPARATOR"
            else -> it.toString()
        }
    }

    private fun ItemListBlock.label() = if (isCheck) "[x]$value" else value

    @Test
    fun plainNoteBecomesOneTextBlockPerParagraph() {
        val blocks = BlockConverter.jsonToBlocks(
            MyNotesBlockMapper.toBlocksJson(note(value = "First line\nsame paragraph\n\n\nSecond\r\n \r\nThird  ")),
        )
        assertEquals(listOf("TEXT:First line\nsame paragraph", "TEXT:Second", "TEXT:Third"), texts(blocks))
    }

    @Test
    fun blankPlainNoteHasNoBlocks() {
        assertEquals("[]", MyNotesBlockMapper.toBlocksJson(note(value = " \n\n ")))
    }

    @Test
    fun paragraphIsText() {
        assertEquals(listOf("TEXT:Hello"), texts(mapEditor("""{"type":"paragraph","data":{"text":"Hello"}}""")))
    }

    @Test
    fun headersKeepTheirLevelUpToFour() {
        val blocks = mapEditor(
            """{"type":"header","data":{"text":"One","level":1}}""",
            """{"type":"Headers","data":{"text":"Two","level":2}}""",
            """{"type":"Headers","data":{"text":"Three","level":3}}""",
            """{"type":"header","data":{"text":"Four","level":4}}""",
            """{"type":"header","data":{"text":"Six","level":6}}""",
            """{"type":"Headers","data":{"text":"Default"}}""",
        )
        assertEquals(
            listOf("H1:One", "H2:Two", "H3:Three", "H4:Four", "H4:Six", "H2:Default"),
            texts(blocks),
        )
    }

    @Test
    fun listsMapByStyleAndFlattenNestedItems() {
        val blocks = mapEditor(
            """{"type":"list","data":{"style":"unordered","items":["a","b"]}}""",
            """{"type":"list","data":{"style":"ordered","items":[
                {"content":"1","meta":{},"items":[{"content":"1.1","meta":{},"items":[]}]},
                {"content":"2","meta":{},"items":[]}]}}""",
            """{"type":"list","data":{"style":"checklist","items":[
                {"content":"done","meta":{"checked":true},"items":[]},
                {"content":"open","meta":{"checked":false},"items":[]}]}}""",
        )
        assertEquals(
            listOf("LIST_BULLET:a|b", "LIST_NUMBER:1|1.1|2", "LIST_CHECK:[x]done|open"),
            texts(blocks),
        )
    }

    @Test
    fun checklistToolIsAChecklist() {
        val blocks = mapEditor(
            """{"type":"checklist","data":{"items":[""" +
                """{"text":"milk","checked":true},{"text":"eggs","checked":false}]}}""",
        )
        assertEquals(listOf("LIST_CHECK:[x]milk|eggs"), texts(blocks))
    }

    @Test
    fun delimiterIsASeparatorAndSpacerIsDropped() {
        val blocks = mapEditor(
            """{"type":"delimiter","data":{}}""",
            """{"type":"spacer","data":{"height":20}}""",
        )
        assertEquals(listOf("SEPARATOR"), texts(blocks))
    }

    @Test
    fun imagesAndAttachmentsAreDropped() {
        val blocks = mapEditor(
            """{"type":"image","data":{"file":{"url":"file:///a.png"},"caption":"cap"}}""",
            """{"type":"attaches","data":{"file":{"url":"file:///b.pdf","name":"b.pdf"}}}""",
            """{"type":"paragraph","data":{"text":"kept"}}""",
        )
        assertEquals(listOf("TEXT:kept"), texts(blocks))
    }

    @Test
    fun inlineHtmlBecomesPlainText() {
        val blocks = mapEditor(
            """{"type":"paragraph","data":{"text":"<b>bold</b> <i>it</i> <mark class=\"cdx-marker\">mark</mark>""" +
                """<br><code class=\"inline-code\">x &lt; y</code> &amp; <a href=\"https://e.org\">link</a>""" +
                """&nbsp;&#8364;&#x1F600;"}}""",
            """{"type":"list","data":{"style":"unordered","items":["<b>item</b>"]}}""",
        )
        assertEquals(listOf("TEXT:bold it mark\nx < y & link €😀", "LIST_BULLET:item"), texts(blocks))
        val bold = """[{"type":"paragraph","data":{"text":"<b>b</b>"}}]"""
        val stored = MyNotesBlockMapper.toBlocksJson(note(valueJson = bold))
        assertFalse(stored.contains("<b>"))
    }

    @Test
    fun emptyBlocksAreDropped() {
        val blocks = mapEditor(
            """{"type":"paragraph","data":{"text":"<br>"}}""",
            """{"type":"header","data":{"text":"  ","level":2}}""",
            """{"type":"list","data":{"style":"unordered","items":["", "<br>"]}}""",
        )
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun unknownToolsKeepTheirText() {
        val blocks = mapEditor(
            """{"type":"quote","data":{"text":"said","caption":""}}""",
            """{"type":"table","data":{"content":[["a"]]}}""",
        )
        assertEquals(listOf("TEXT:said"), texts(blocks))
    }

    @Test
    fun editorDocumentObjectIsReadToo() {
        val json = """{"time":1,"blocks":[{"type":"paragraph","data":{"text":"inside"}}],"version":"2.30"}"""
        val blocks = BlockConverter.jsonToBlocks(MyNotesBlockMapper.toBlocksJson(note(valueJson = json)))
        assertEquals(listOf("TEXT:inside"), texts(blocks))
    }

    @Test
    fun unreadableEditorJsonFallsBackToThePlainText() {
        val blocks = BlockConverter.jsonToBlocks(
            MyNotesBlockMapper.toBlocksJson(note(value = "fallback", valueJson = "{not json")),
        )
        assertEquals(listOf("TEXT:fallback"), texts(blocks))
    }

    @Test
    fun nestedListsAreFlattenedToALimitedDepth() {
        var item = """{"content":"leaf","items":[]}"""
        repeat(50) { item = """{"content":"n","items":[$item]}""" }
        val blocks = mapEditor("""{"type":"list","data":{"style":"unordered","items":[$item]}}""")
        assertEquals(33, (blocks.single() as Block.ListBlock).items.value.size)
    }

    @Test
    fun hostileNestingFallsBackToThePlainTextInsteadOfOverflowing() {
        val deep = "[".repeat(100_000) + "]".repeat(100_000)
        val blocks = BlockConverter.jsonToBlocks(
            MyNotesBlockMapper.toBlocksJson(note(value = "kept", valueJson = deep)),
        )
        assertEquals(listOf("TEXT:kept"), texts(blocks))
    }
}
