package com.pasich.encly.core.serialization

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The bytes of `Note.value`, pinned as written by the shipped versions: two-space pretty
 * printing with `": "`, HTML-safe escapes, no `null`, no ids. Every string here is what an older
 * app wrote or reads; none may change when the serializer library does.
 */
class BlockJsonFormatTest {

    private fun reserialize(json: String) = BlockConverter.blocksToJson(BlockConverter.jsonToBlocks(json))

    private fun assertStable(json: String) = assertEquals(json, reserialize(json))

    @Test
    fun anEmptyNoteIsAnEmptyArray() {
        assertStable("[]")
        assertEquals("[]", BlockConverter.blocksToJson(emptyList()))
        assertEquals(emptyList<Block>(), BlockConverter.jsonToBlocks(""))
    }

    @Test
    fun textBlock() = assertStable(
        """
        [
          {
            "blockType": "TEXT",
            "text": "plain"
          }
        ]
        """.trimIndent(),
    )

    @Test
    fun everyHeadingLevel() = assertStable(
        """
        [
          {
            "blockType": "H1",
            "text": "one"
          },
          {
            "blockType": "H2",
            "text": "two"
          },
          {
            "blockType": "H3",
            "text": "three"
          },
          {
            "blockType": "H4",
            "text": "four"
          }
        ]
        """.trimIndent(),
    )

    @Test
    fun quoteBlock() = assertStable(
        """
        [
          {
            "blockType": "QUOTE",
            "text": "said"
          }
        ]
        """.trimIndent(),
    )

    @Test
    fun linkBlockWithAndWithoutImage() = assertStable(
        """
        [
          {
            "blockType": "LINK",
            "block": {
              "url": "https://example.com/a?b\u003d1\u0026c\u003d2",
              "title": "Example",
              "imageUrl": "https://example.com/i.png"
            }
          },
          {
            "blockType": "LINK",
            "block": {
              "url": "https://example.com",
              "title": "",
              "imageUrl": ""
            }
          }
        ]
        """.trimIndent(),
    )

    @Test
    fun separatorBlock() = assertStable(
        """
        [
          {
            "blockType": "SEPARATOR"
          }
        ]
        """.trimIndent(),
    )

    @Test
    fun everyListKindAndTheLockedFlag() = assertStable(
        """
        [
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
            "blockType": "LIST_CHECK",
            "items": [
              {
                "value": "kept out of reach",
                "isCheck": false
              }
            ],
            "isLocked": true
          },
          {
            "blockType": "LIST_BULLET",
            "items": [
              {
                "value": "milk",
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
          }
        ]
        """.trimIndent(),
    )

    @Test
    fun aListWithoutNonBlankItemsIsNotStored() {
        val json = BlockConverter.blocksToJson(
            listOf(
                Block.TextBlock(MutableStateFlow("a")),
                Block.ListBlock(MutableStateFlow(emptyList()), BlockType.LIST_BULLET),
            ),
        )

        assertEquals(
            """
            [
              {
                "blockType": "TEXT",
                "text": "a"
              }
            ]
            """.trimIndent(),
            json,
        )
        assertEquals("[]", reserialize("""[{"blockType":"LIST_BULLET","items":[]}]"""))
    }

    @Test
    fun aBlankLineBetweenParagraphsIsAnEmptyString() = assertStable(
        """
        [
          {
            "blockType": "TEXT",
            "text": "one"
          },
          {
            "blockType": "TEXT",
            "text": ""
          },
          {
            "blockType": "TEXT",
            "text": "two"
          }
        ]
        """.trimIndent(),
    )

    /**
     * Non-ASCII is written as is (an emoji stays a surrogate pair, not `😀`); control
     * characters, quotes, backslashes, U+2028/2029 and the HTML-sensitive `< > & = '` are escaped.
     */
    @Test
    fun unicodeEmojiAndEscapesAreWrittenByteForByte() = assertStable(
        """
        [
          {
            "blockType": "TEXT",
            "text": "Привіт, світе! 你好 ✓ 😀 café"
          },
          {
            "blockType": "TEXT",
            "text": "line\nbreak\ttab\rreturn \"quoted\" back\\slash \u0000 \u001f \b \f \u000b / end"
          },
          {
            "blockType": "TEXT",
            "text": "\u003cb\u003e \u0026 \u003d \u0027 \u2028 \u2029"
          }
        ]
        """.trimIndent(),
    )

    /** DEL is the one ASCII control character Gson left unescaped. */
    @Test
    fun deleteIsWrittenRaw() {
        val json = BlockConverter.blocksToJson(listOf(Block.TextBlock(MutableStateFlow("a\u007fb"))))

        assertEquals(true, json.contains("a" + 127.toChar() + "b"))
    }

    @Test
    fun everyBlockKindWrittenFromTheEditorSide() {
        val json = BlockConverter.blocksToJson(
            listOf(
                Block.TextBlock(MutableStateFlow("t")),
                Block.HBlock(MutableStateFlow("h"), BlockType.H4),
                Block.QuoteBlock(MutableStateFlow("q")),
                Block.LinkBlock(MutableStateFlow(LinkDataBlock(url = "u", title = "ti", imageUrl = "i"))),
                Block.SeparatorBlock(),
                Block.ListBlock(
                    MutableStateFlow(listOf(ItemListBlock("a", isCheck = true), ItemListBlock(""))),
                    BlockType.LIST_CHECK,
                    locked = MutableStateFlow(true),
                ),
            ),
        )

        assertEquals(
            """
            [
              {
                "blockType": "TEXT",
                "text": "t"
              },
              {
                "blockType": "H4",
                "text": "h"
              },
              {
                "blockType": "QUOTE",
                "text": "q"
              },
              {
                "blockType": "LINK",
                "block": {
                  "url": "u",
                  "title": "ti",
                  "imageUrl": "i"
                }
              },
              {
                "blockType": "SEPARATOR"
              },
              {
                "blockType": "LIST_CHECK",
                "items": [
                  {
                    "value": "a",
                    "isCheck": true
                  }
                ],
                "isLocked": true
              }
            ]
            """.trimIndent(),
            json,
        )
    }

    // Shapes older or hand-made notes have: read as before, written back in the current form.

    @Test
    fun theCompactFormAndAnyKeyOrderAreRead() {
        assertEquals(
            """
            [
              {
                "blockType": "TEXT",
                "text": "a"
              },
              {
                "blockType": "LIST_CHECK",
                "items": [
                  {
                    "value": "x",
                    "isCheck": true
                  }
                ]
              }
            ]
            """.trimIndent(),
            reserialize(
                """[{"text":"a","blockType":"TEXT"},""" +
                    """{"items":[{"isCheck":true,"value":"x"}],"blockType":"LIST_CHECK"}]""",
            ),
        )
    }

    @Test
    fun missingFieldsFallBackToTheirDefaults() {
        assertEquals(
            """
            [
              {
                "blockType": "TEXT",
                "text": "x"
              },
              {
                "blockType": "TEXT",
                "text": ""
              },
              {
                "blockType": "LIST_CHECK",
                "items": [
                  {
                    "value": "only value",
                    "isCheck": false
                  }
                ]
              }
            ]
            """.trimIndent(),
            reserialize(
                """[{"blockType":"TEXT","text":"x"},{"blockType":"TEXT"},""" +
                    """{"blockType":"LIST","items":[{"value":"only value"},{"isCheck":true}]}]""",
            ),
        )
        // A text block without text is a blank line; a link without its data and a list without
        // items are blocks without content.
        assertEquals("[]", reserialize("""[{"blockType":"LINK"},{"blockType":"LIST_BULLET"}]"""))
    }

    @Test
    fun theLegacyListNameIsAChecklistAndUnknownKeysAreIgnored() {
        assertEquals(
            """
            [
              {
                "blockType": "LIST_CHECK",
                "items": [
                  {
                    "value": "old",
                    "isCheck": true
                  }
                ]
              }
            ]
            """.trimIndent(),
            reserialize(
                """[{"blockType":"LIST","id":"abc","extra":1,"items":[{"value":"old","isCheck":true,"id":"z"}]}]""",
            ),
        )
    }

    @Test
    fun anExplicitlyFalseLockIsNotWrittenBack() {
        assertEquals(
            """
            [
              {
                "blockType": "LIST_NUMBER",
                "items": [
                  {
                    "value": "a",
                    "isCheck": false
                  }
                ]
              }
            ]
            """.trimIndent(),
            reserialize("""[{"blockType":"LIST_NUMBER","isLocked":false,"items":[{"value":"a"}]}]"""),
        )
    }

    @Test
    fun escapedAndRawFormsOfTheSameTextReadAlike() {
        val raw = BlockConverter.jsonToBlocks("""[{"blockType":"TEXT","text":"<a> & ' é ✓ 😀"}]""")
        val escaped = BlockConverter.jsonToBlocks(
            """[{"blockType":"TEXT","text":"\u003ca\u003e \u0026 \u0027 \u00e9 \u2713 \ud83d\ude00"}]""",
        )

        assertEquals("<a> & ' é ✓ 😀", (raw.single() as Block.TextBlock).text.value)
        assertEquals((raw.single() as Block.TextBlock).text.value, (escaped.single() as Block.TextBlock).text.value)
    }

    @Test
    fun wrongShapesAreUnreadableNotCrashes() {
        listOf(
            """{"blockType":"TEXT","text":"not an array"}""",
            """[{"blockType":"TEXT","text":null}]""",
            """[{"blockType":null}]""",
            """[{"blockType":"H9","text":"x"}]""",
            """[{"blockType":"LINK","block":"nope"}]""",
            """[{"blockType":"LIST_CHECK","items":{"value":"x"}}]""",
            """[{"blockType":"LIST_CHECK","items":[null]}]""",
            """[{"blockType":"LIST_CHECK","items":["x"]}]""",
            """[{"blockType":"TEXT","text":{"a":1}}]""",
            "[",
            """[{"blockType":"TEXT","text":"cut""",
        ).forEach { json -> assertNull(json, BlockConverter.jsonToBlocksOrNull(json)) }
    }

    @Test
    fun aJsonNullIsAnEmptyNote() {
        assertEquals(emptyList<Block>(), BlockConverter.jsonToBlocks("null"))
    }

    @Test
    fun scalarValuesAreReadAsTheirText() {
        val blocks = BlockConverter.jsonToBlocks(
            """[{"blockType":"TEXT","text":42},{"blockType":"LIST_CHECK","items":[{"value":"a","isCheck":"true"}]}]""",
        )

        assertEquals("42", (blocks[0] as Block.TextBlock).text.value)
        assertEquals(true, (blocks[1] as Block.ListBlock).items.value.single().isCheck)
    }
}
