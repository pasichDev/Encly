package com.pasich.encly.data.importer

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonScanTest {
    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    /** The members of the root and what [read] made of each; null if the scan gave up. */
    private fun scan(
        text: String,
        maxDepth: Int = 64,
        read: JsonCursor.(String) -> String = {
            skip()
            "-"
        },
    ): List<String>? {
        val seen = ArrayList<String>()
        val ok = JsonScan.root(bytes(text), maxDepth) { key -> seen += "$key=${read(key)}" }
        return if (ok) seen else null
    }

    @Test
    fun visitsTheMembersOfTheRootInOrderAndSkipsWhatItIsNotAskedFor() {
        val text = """ {"a": [1, {"x": "]"}], "b": "}", "c": {"d": {}} , "e": null } """
        assertEquals(listOf("a=-", "b=-", "c=-", "e=-"), scan(text))
    }

    @Test
    fun aByteOrderMarkAndAnyWhiteSpaceAreAccepted() {
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes("\r\n\t{ \"a\" :\n1 }\r\n")
        val seen = ArrayList<String>()
        assertTrue(
            JsonScan.root(withBom, 64) { key ->
                seen += key
                skip()
            },
        )
        assertEquals(listOf("a"), seen)
    }

    @Test
    fun aValueReadThroughTheCursorIsParsedStrictly() {
        val values = ArrayList<Any?>()
        val ok = JsonScan.root(bytes("""{"n":12,"s":"té\n","o":{"k":[true]}}"""), 64) { _ ->
            values += element()
        }
        assertTrue(ok)
        assertEquals(JsonPrimitive(12), values[0])
        assertEquals(JsonPrimitive("té\n"), values[1])
        assertEquals("""{"k":[true]}""", values[2].toString())
    }

    @Test
    fun aValueThatIsNotJsonFailsTheWholeFile() {
        assertNull(scan("""{"a":{"b":}}""") { element().toString() })
        assertNull(scan("""{"a":tru}""") { element().toString() })
        assertNull(scan("""{"a":'x'}""") { element().toString() })
    }

    @Test
    fun aBareTokenMustBeALiteralOrANumberWhetherReadOrSkipped() {
        for (bad in listOf("tru", "'x'", "01", "1.", "+1", "NaN", "-", "1e", ".5", "Infinity", "True")) {
            assertNull("read $bad", scan("""{"a":$bad}""") { element().toString() })
            assertNull("skipped $bad", scan("""{"a":$bad}"""))
            assertNull("nested $bad", scan("""{"a":[1,{"b":$bad}]}"""))
            assertFalse("in array $bad", JsonScan.root(bytes("""{"a":[$bad]}"""), 64) { forEachElement { } })
        }
        for (good in listOf("true", "false", "null", "0", "-0", "12", "-1.5", "1e5", "1E+5", "2.5e-3")) {
            assertEquals("read $good", listOf("a=$good"), scan("""{"a":$good}""") { element().toString() })
            assertEquals("skipped $good", listOf("a=-"), scan("""{"a":[$good, {"b": $good}]}"""))
        }
    }

    @Test
    fun anArrayIsReadOneElementAtATime() {
        val items = ArrayList<String>()
        val ok = JsonScan.root(bytes("""{"items":[{"a":1},[2],"3",4,null],"after":1}"""), 64) { key ->
            if (key == "items" && isArray()) forEachElement { items += it.toString() } else skip()
        }
        assertTrue(ok)
        assertEquals(listOf("""{"a":1}""", "[2]", "\"3\"", "4", "null"), items)
    }

    @Test
    fun anElementIsHandedOverBeforeTheRestOfTheFileHasBeenSeen() {
        // A truncated tail: the scan reports it only at the end, after the earlier elements were visited,
        // so a caller must drop what it gathered when the scan gives up.
        val items = ArrayList<String>()
        val ok = JsonScan.root(bytes("""{"items":[{"a":1},{"a":2},{"a":"""), 64) { key ->
            if (key == "items") forEachElement { items += it.toString() } else skip()
        }
        assertFalse(ok)
        assertEquals(2, items.size)
    }

    @Test
    fun emptyAndNotJsonObjectsAreNotUsable() {
        assertEquals(emptyList<String>(), scan("{}"))
        assertEquals(emptyList<String>(), scan("  {  }  "))
        assertNull(scan(""))
        assertNull(scan("[1,2,3]"))
        assertNull(scan("\"text\""))
        assertNull(scan("not json at all"))
    }

    @Test
    fun truncatedUnbalancedAndTrailingBytesAreRefused() {
        assertNull(scan("""{"a":1"""))
        assertNull(scan("""{"a":[1,2}"""))
        assertNull(scan("""{"a":{"b":1}"""))
        assertNull(scan("""{"a":"unterminated}"""))
        assertNull(scan("""{"a":1} x"""))
        assertNull(scan("""{"a":1}{"b":2}"""))
        assertNull(scan("""{"a":1,}"""))
        assertNull(scan("""{"a":1 "b":2}"""))
        assertNull(scan("""{"a" 1}"""))
        assertNull(scan("""{a:1}"""))
        assertNull(scan("""{"a":,"b":1}"""))
    }

    @Test
    fun anArrayWithATrailingCommaOrAGapIsRefused() {
        fun elements(text: String) = JsonScan.root(bytes(text), 64) { forEachElement { } }
        assertTrue(elements("""{"a":[]}"""))
        assertTrue(elements("""{"a":[1,2]}"""))
        assertFalse(elements("""{"a":[1,]}"""))
        assertFalse(elements("""{"a":[,1]}"""))
        assertFalse(elements("""{"a":[1 2]}"""))
        assertFalse(elements("""{"a":[1"""))
    }

    @Test
    fun nestingIsCountedWhileScanningAndBracketsInStringsAreNot() {
        fun nested(levels: Int) = """{"a":""" + "[".repeat(levels) + "]".repeat(levels) + "}"
        // The root is level 1, so `levels` arrays inside it reach level levels + 1.
        assertEquals(listOf("a=-"), scan(nested(63), maxDepth = 64))
        assertNull(scan(nested(64), maxDepth = 64))
        assertNull(scan(nested(5000)))
        assertEquals(listOf("a=-"), scan("""{"a":"[[[[ \" [[[["}""", maxDepth = 1))
    }

    @Test
    fun theDepthOfAnElementReadFromAnArrayCountsFromTheArray() {
        // Root (1) + items array (2) + an element nested 62 deep reaches 64; one more is refused.
        fun item(levels: Int) = "[".repeat(levels) + "]".repeat(levels)
        fun scanItems(levels: Int) = JsonScan.root(bytes("""{"items":[${item(levels)}]}"""), 64) { forEachElement { } }
        assertTrue(scanItems(62))
        assertFalse(scanItems(63))
    }

    @Test
    fun aKeyWithEscapesIsTheKeyAFullParseWouldGive() {
        assertEquals(listOf("items=-"), scan("""{"items":[]}"""))
        assertEquals(listOf("a\"b=-"), scan("""{"a\"b":1}"""))
    }

    @Test
    fun aValueNobodyReadsIsSkippedByTheNextKey() {
        val keys = ArrayList<String>()
        assertTrue(JsonScan.root(bytes("""{"a":[1],"b":{"c":2},"d":3}"""), 64) { key -> keys += key })
        assertEquals(listOf("a", "b", "d"), keys)
    }

    @Test
    fun scalarReadsPrimitivesAndLeavesContainersAlone() {
        val seen = ArrayList<String?>()
        JsonScan.root(bytes("""{"a":1,"b":[1],"c":"x","d":{"e":1}}"""), 64) { seen += scalar()?.toString() }
        assertEquals(listOf("1", null, "\"x\"", null), seen)
    }

    @Test
    fun infoFindsTheKeepTimestampsAndTheWatchedKeys() {
        val keepJson = """{"createdTimestampUsec":1,"userEditedTimestampUsec":2,"title":"t"}"""
        val keep = JsonScan.info(bytes(keepJson), 64)!!
        assertEquals(1L, keep.created)
        assertEquals(2L, keep.edited)
        val chunk = JsonScan.info(bytes("""{"hash_type":"md5","compressed":true,"encrypted":false,"other":1}"""), 64)!!
        assertEquals(setOf("hash_type", "compressed", "encrypted"), chunk.keys)
        val sn = JsonScan.info(bytes("""{"version":"004","items":[]}"""), 64)!!
        assertEquals(setOf("items"), sn.keys)
    }

    @Test
    fun infoDoesNotTakeAStringOrAContainerForATimestamp() {
        val strings = JsonScan.info(bytes("""{"createdTimestampUsec":"1","userEditedTimestampUsec":"2"}"""), 64)!!
        assertNull(strings.created)
        assertNull(strings.edited)
        val containerJson = """{"createdTimestampUsec":[1],"userEditedTimestampUsec":{"a":1}}"""
        val containers = JsonScan.info(bytes(containerJson), 64)!!
        assertNull(containers.created)
        assertNull(containers.edited)
        assertNull(JsonScan.info(bytes("[1]"), 64))
        assertNull(JsonScan.info(bytes("""{"createdTimestampUsec":1"""), 64))
    }

    @Test
    fun utf8IsCheckedAcrossTheSlicesOfALargeFile() {
        // A two-byte character that straddles the end of the first 64 KiB slice must not look invalid.
        for (offset in 65_530..65_540) {
            val text = "a".repeat(offset) + "é€😀" + "b".repeat(70_000)
            assertTrue("offset $offset", Utf8.isValid(text.toByteArray(Charsets.UTF_8)))
        }
        assertTrue(Utf8.isValid(ByteArray(0)))
    }

    @Test
    fun invalidUtf8IsFoundAnywhereInTheFile() {
        val valid = ByteArray(200_000) { 'a'.code.toByte() }
        assertTrue(Utf8.isValid(valid))
        listOf(
            byteArrayOf(0xff.toByte()),
            byteArrayOf(0x80.toByte()),
            byteArrayOf(0xC0.toByte(), 0x80.toByte()),
            byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()),
            byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xE2.toByte(), 0x82.toByte()),
        ).forEach { bad ->
            listOf(0, 65_535, 65_536, valid.size - bad.size).forEach { at ->
                val bytes = valid.copyOf()
                bad.copyInto(bytes, at)
                assertFalse("${bad.toList()} at $at", Utf8.isValid(bytes.copyOf(at + bad.size)))
            }
        }
    }
}
