package com.pasich.encly.data.importer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

/**
 * What [ImportTime] makes of every shape of date the supported note apps write, in the time zone
 * Europe/Kyiv (UTC+3 in summer, UTC+2 in winter). The expectations were taken from the
 * implementation that used java.time, so they pin what an import did before kotlinx-datetime.
 */
class ImportTimeTest {
    private val savedZone = TimeZone.getDefault()

    @Before
    fun useKyiv() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))

    @After
    fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun parsed(raw: String?): String? = ImportTime.parse(raw)?.let { Instant.ofEpochMilli(it).toString() }

    @Test
    fun everyKnownShapeParsesAsItAlwaysDid() {
        val wrong = CASES.filter { (raw, expected) -> parsed(raw) != expected }
            .map { (raw, expected) -> "$raw: expected $expected, got ${parsed(raw)}" }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun aLocalTimeInTheSpringGapMovesForwardAndInTheAutumnOverlapTakesTheEarlierOffset() {
        assertEquals("2026-03-29T01:30:00Z", parsed("2026-03-29 03:30"))
        assertEquals("2026-03-29T00:30:00Z", parsed("2026-03-29 02:30"))
        assertEquals("2026-10-25T00:30:00Z", parsed("2026-10-25 03:30"))
        assertEquals("2026-10-25T02:00:00Z", parsed("2026-10-25 04:00"))
    }

    @Test
    fun theSameLocalTextFollowsThePhonesZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("2026-10-14T22:48:00Z", parsed("14-10-2026 10:48 PM"))
        assertEquals("2026-10-14T19:32:53Z", parsed("2026-10-14T19:32:53Z"))
    }

    private companion object {
        val CASES: List<Pair<String?, String?>> = listOf(
            null to null,
            "" to null,
            "   " to null,
            "0" to null,
            "-5" to null,
            "1700000000" to "2023-11-14T22:13:20Z",
            "1700000000000" to "2023-11-14T22:13:20Z",
            "1700000000123" to "2023-11-14T22:13:20.123Z",
            "99999999999999999999" to null,
            "32503680000000" to "3000-01-01T00:00:00Z",
            "32503680000001" to null,
            "99999999999" to null,
            "00000000001" to "1970-01-01T00:00:01Z",
            "1" to "1970-01-01T00:00:01Z",
            "2026-10-14T19:32:53Z" to "2026-10-14T19:32:53Z",
            "2026-10-14T19:32:53.123Z" to "2026-10-14T19:32:53.123Z",
            "2026-10-14T19:32:53.123456789Z" to "2026-10-14T19:32:53.123Z",
            "2026-10-14T19:32:53.1234567891Z" to null,
            "2026-10-14T19:32:53+02:00" to "2026-10-14T17:32:53Z",
            "2026-10-14T19:32:53+0200" to null,
            "2026-10-14T19:32:53+02" to "2026-10-14T17:32:53Z",
            "2026-10-14T19:32:53-05:30" to "2026-10-15T01:02:53Z",
            "2026-10-14T19:32:53+02:00:30" to "2026-10-14T17:32:23Z",
            "2026-10-14T19:32+02:00" to "2026-10-14T17:32:00Z",
            "2026-10-14T19:32Z" to "2026-10-14T19:32:00Z",
            "2026-10-14T19:32:53" to "2026-10-14T16:32:53Z",
            "2026-10-14T19:32" to "2026-10-14T16:32:00Z",
            "2026-10-14T19:32:53.5" to "2026-10-14T16:32:53.500Z",
            "2026-10-14" to "2026-10-13T21:00:00Z",
            "2026-10-14T19:32:53+02:00[Europe/Paris]" to null,
            "2026-10-14t19:32:53z" to "2026-10-14T19:32:53Z",
            "2026-10-14T19:32:53z" to "2026-10-14T19:32:53Z",
            "2026-10-14 19:32:53Z" to "2026-10-14T19:32:53Z",
            "2026-10-14 19:32:53+02:00" to "2026-10-14T17:32:53Z",
            "2026-10-14 19:32:53" to "2026-10-14T16:32:53Z",
            "2026-10-14 19:32" to "2026-10-14T16:32:00Z",
            "2026-10-14 19:32:53.123Z" to "2026-10-14T19:32:53.123Z",
            "2026-10-14 19:32:53.123" to "2026-10-14T16:32:53.123Z",
            "2026-10-14T24:00:00Z" to "2026-10-15T00:00:00Z",
            "2026-10-14T24:00:00" to null,
            "2026-06-30T23:59:60Z" to "2026-06-30T23:59:59Z",
            "2026-06-30T23:59:60" to null,
            "2026-02-30" to null,
            "2026-02-30T10:00:00Z" to null,
            "2026-13-01" to null,
            "20261014T193253Z" to null,
            "20261014" to "1970-08-23T12:03:34Z",
            "+002026-10-14T19:32:53Z" to "2026-10-14T19:32:53Z",
            "+12026-10-14T19:32:53Z" to null,
            "0001-01-01" to null,
            "0001-01-01T00:00:00Z" to null,
            "1970-01-01T00:00:00Z" to null,
            "1970-01-01T00:00:01Z" to "1970-01-01T00:00:01Z",
            "2999-12-31T23:59:59Z" to "2999-12-31T23:59:59Z",
            "3000-01-01T00:00:00Z" to "3000-01-01T00:00:00Z",
            "3000-01-01T00:00:01Z" to null,
            "2026-10-14T19:32:53 Z" to null,
            " 2026-10-14T19:32:53Z " to "2026-10-14T19:32:53Z",
            "2026-10-14T19:32:53ZZ" to null,
            "2026-10-14T19:32:53+14:00" to "2026-10-14T05:32:53Z",
            "2026-10-14T19:32:53+18:00" to "2026-10-14T01:32:53Z",
            "2026-10-14T19:32:53+19:00" to null,
            "2026-10-14T19:32:53-18:00" to "2026-10-15T13:32:53Z",
            "2026-10-14T19:32:53+2:00" to null,
            "2026-10-14T19:32:53+02:0" to null,
            "2026-10-14T19:32:53UTC" to null,
            "2026-10-14T19:32:53GMT" to null,
            "2026-10-14T9:32:53Z" to null,
            "2026-10-14T19:3:53Z" to null,
            "2026-1-14T19:32:53Z" to null,
            "2026-1-14" to null,
            "2026-10-14T19:32:53,123Z" to null,
            "2026-10-14T19:32:53.Z" to "2026-10-14T19:32:53Z",
            "2026-10-14T19:32:5Z" to null,
            "2026-10-14T19Z" to null,
            "2026-10-14T19" to null,
            "2026-10-14T" to null,
            "2026-10-14 10:48 PM" to "2026-10-14T19:48:00Z",
            "2026-10-14, 10:48 am" to "2026-10-14T07:48:00Z",
            "2026-10-14 12:30 AM" to "2026-10-13T21:30:00Z",
            "2026-10-14 12:30 pm" to "2026-10-14T09:30:00Z",
            "2026-10-14 13:00 PM" to "2026-10-14T10:00:00Z",
            "2026-10-14 00:30 AM" to "2026-10-13T21:30:00Z",
            "2026-10-14 10:48pm" to "2026-10-14T19:48:00Z",
            "2026/10/14 22:48" to "2026-10-14T19:48:00Z",
            "2026.10.14 22:48:10" to "2026-10-14T19:48:10Z",
            "2026-10-14 22:48:10 PM" to "2026-10-14T19:48:10Z",
            "2026-10-14 25:00" to null,
            "2026-10-14 23:60" to null,
            "2026-10-14 23:59:60" to null,
            "2026-10-14 23:59:59" to "2026-10-14T20:59:59Z",
            "2026-02-30 10:00" to null,
            "2026-1-4 5:06" to "2026-01-04T03:06:00Z",
            "2026-10-14T10:00 PM" to "2026-10-14T19:00:00Z",
            "14-10-2026 10:48 PM" to "2026-10-14T19:48:00Z",
            "10/14/2026 22:48" to "2026-10-14T19:48:00Z",
            "14.10.2026" to "2026-10-13T21:00:00Z",
            "14.10.2026 22:48:10" to "2026-10-14T19:48:10Z",
            "14/10/2026" to "2026-10-13T21:00:00Z",
            "05-06-2026" to null,
            "05-05-2026" to "2026-05-04T21:00:00Z",
            "13-12-2026" to "2026-12-12T22:00:00Z",
            "12-13-2026" to "2026-12-12T22:00:00Z",
            "5-5-2026 7:05 am" to "2026-05-05T04:05:00Z",
            "1.1.2026" to "2025-12-31T22:00:00Z",
            "31-02-2026" to null,
            "02-31-2026" to null,
            "31-04-2026" to null,
            "29-02-2024" to "2024-02-28T22:00:00Z",
            "29-02-2025" to null,
            "14-10-2026 25:00" to null,
            "14-10-2026 23:59:60" to null,
            "14-10-2026,10:48 PM" to "2026-10-14T19:48:00Z",
            "14-10-2026T10:48" to "2026-10-14T07:48:00Z",
            "14-10-2026 10:48PM" to "2026-10-14T19:48:00Z",
            "14-10-2026 10:48 P.M." to null,
            "14-10-2026  10:48" to "2026-10-14T07:48:00Z",
            "14-10-2026 10:48:10 am" to "2026-10-14T07:48:10Z",
            "00-10-2026" to null,
            "10-00-2026" to null,
            "32-10-2026" to null,
            "10-32-2026" to null,
            "13-13-2026" to null,
            "12-12-2026" to "2026-12-11T22:00:00Z",
            "99-99-2026" to null,
            "14-10-0000" to null,
            "14-10-0001" to null,
            "2026-03-29 03:30" to "2026-03-29T01:30:00Z",
            "2026-03-29 02:30" to "2026-03-29T00:30:00Z",
            "2026-03-29 04:00" to "2026-03-29T01:00:00Z",
            "2026-10-25 03:30" to "2026-10-25T00:30:00Z",
            "2026-10-25 04:00" to "2026-10-25T02:00:00Z",
            "2026-10-25 02:59" to "2026-10-24T23:59:00Z",
            "2026-03-29T03:30:00" to "2026-03-29T01:30:00Z",
            "2026-03-29" to "2026-03-28T22:00:00Z",
            "2026-10-25" to "2026-10-24T21:00:00Z",
            "Oct 14, 2026" to null,
            "hello" to null,
            "2026" to "1970-01-01T00:33:46Z",
            "2026-10" to null,
            "12:00" to null,
            "2026-10-14T19:32:53Z\n" to "2026-10-14T19:32:53Z",
            "\u0661\u0664-\u0661\u0660-\u0662\u0660\u0662\u0666" to null,
            "\uff12\uff10\uff12\uff16-10-14" to null,
            "2026-10-14T\u0661\u0669:32:53Z" to null,
            "1700000000.5" to null,
            "1.7e9" to null,
            "+1700000000" to null,
            "\u0663\u0664\u0665" to "1970-01-01T00:05:45Z",
        )
    }
}
