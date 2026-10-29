package com.pasich.encly.editor.state

import com.pasich.encly.editor.codec.BlockConverter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stored format is untouched by Writing Comfort: a note written by an earlier version
 * (every block kind) opens, goes through the editor and the new features' read-only parts, and
 * serializes to exactly the same bytes. Nothing here is saved unless the user changes the note.
 */
class NoteJsonGoldenTest {
    @Test
    fun theEarlierFormatIsWhatTheSerializerStillWrites() {
        assertEquals(GOLDEN, BlockConverter.blocksToJson(BlockConverter.jsonToBlocks(GOLDEN)))
    }

    @Test
    fun openingTheNoteInTheEditorLeavesItsBytesAlone() {
        val editor = BlockEditorState { 0L }

        editor.load(BlockConverter.jsonToBlocks(GOLDEN))

        assertEquals(GOLDEN, BlockConverter.blocksToJson(editor.blocks))
        assertEquals(false, editor.canUndo.value)
    }

    @Test
    fun findSlashAndHistoryUsedWithoutAChangeLeaveTheBytesAlone() {
        val editor = BlockEditorState { 0L }
        editor.load(BlockConverter.jsonToBlocks(GOLDEN))

        editor.find.open()
        editor.find.search("e", editor.blocks)
        editor.find.next()
        editor.find.previous()
        editor.find.close()
        editor.slash.open(editor.blocks.first().id)
        editor.slash.onText(editor.blocks.first().id, "/he")
        editor.slash.dismiss()
        editor.undo()
        editor.redo()

        assertEquals(GOLDEN, BlockConverter.blocksToJson(editor.blocks))
    }

    @Test
    fun aTemplateIsNotAppliedToANoteThatHasText() {
        val editor = BlockEditorState { 0L }
        editor.load(BlockConverter.jsonToBlocks(GOLDEN))

        editor.applyTemplate(NoteTemplate.MEETING.blocks { "x" })

        assertEquals(GOLDEN, BlockConverter.blocksToJson(editor.blocks))
    }

    @Test
    fun aPasteOfASingleLineIsLeftToTheFieldAndTheBytesStayToo() {
        val editor = BlockEditorState { 0L }
        editor.load(BlockConverter.jsonToBlocks(GOLDEN))

        editor.duplicateBlockOf("missing")
        editor.removeInteracted()
        editor.undo()

        assertEquals(GOLDEN, BlockConverter.blocksToJson(editor.blocks))
    }

    private companion object {
        /** One of every block kind, as `Note.value` has been written since the block editor shipped. */
        val GOLDEN = """
            [
              {
                "blockType": "TEXT",
                "text": "A paragraph\nwith two lines"
              },
              {
                "blockType": "H1",
                "text": "One"
              },
              {
                "blockType": "H2",
                "text": "Two"
              },
              {
                "blockType": "H3",
                "text": "Three"
              },
              {
                "blockType": "H4",
                "text": "Four"
              },
              {
                "blockType": "QUOTE",
                "text": "Quoted"
              },
              {
                "blockType": "LIST_CHECK",
                "items": [
                  {
                    "value": "open",
                    "isCheck": false
                  },
                  {
                    "value": "done",
                    "isCheck": true
                  }
                ]
              },
              {
                "blockType": "LIST_BULLET",
                "items": [
                  {
                    "value": "bullet",
                    "isCheck": false
                  }
                ]
              },
              {
                "blockType": "LIST_NUMBER",
                "items": [
                  {
                    "value": "first",
                    "isCheck": false
                  },
                  {
                    "value": "second",
                    "isCheck": false
                  }
                ]
              },
              {
                "blockType": "LINK",
                "block": {
                  "url": "https://example.com/a",
                  "title": "example.com",
                  "imageUrl": ""
                }
              },
              {
                "blockType": "SEPARATOR"
              }
            ]
        """.trimIndent()
    }
}
