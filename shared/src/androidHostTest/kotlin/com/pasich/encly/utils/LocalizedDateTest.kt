package com.pasich.encly.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Instant

/**
 * The platform date formats give exactly what the java.text expressions they replaced gave: the
 * locale picks the month names and the clock, the zone picks the day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class LocalizedDateTest {
    private val savedZone = java.util.TimeZone.getDefault()
    private val kyiv = TimeZone.of("Europe/Kyiv")
    private val instant = Instant.parse("2026-10-14T19:32:53Z")
    private val tags = listOf("en-US", "uk", "de", "fr", "ja")

    @Before
    fun useKyiv() = java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Kyiv"))

    @After
    fun restoreZone() = java.util.TimeZone.setDefault(savedZone)

    private fun reference(style: LocalizedDate, locale: Locale): String {
        val format = when (style) {
            LocalizedDate.TIME -> DateFormat.getTimeInstance(DateFormat.SHORT, locale)

            LocalizedDate.DATE_TIME_SHORT -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)

            LocalizedDate.DATE_TIME_MEDIUM ->
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)

            LocalizedDate.DAY_MONTH ->
                SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)

            LocalizedDate.DAY_MONTH_YEAR ->
                SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMy"), locale)
        }
        return format.format(Date(instant.toEpochMilliseconds()))
    }

    @Test
    fun everyStyleReadsAsItDidBeforeInEveryLanguage() {
        for (style in LocalizedDate.entries) {
            for (tag in tags) {
                assertEquals(
                    "$style in $tag",
                    reference(style, Locale.forLanguageTag(tag)),
                    formatLocalized(instant, style, tag, kyiv),
                )
            }
        }
    }

    @Test
    fun theTimeFollowsTheZoneAndTheLocaleClock() {
        assertEquals("22:32", formatLocalized(instant, LocalizedDate.TIME, "uk", kyiv))
        assertEquals("19:32", formatLocalized(instant, LocalizedDate.TIME, "uk", TimeZone.UTC))
        assertEquals("22:32", formatLocalized(instant, LocalizedDate.TIME, "uk", kyiv))
    }

    @Test
    fun aFixedOffsetZoneIsHonouredNotReadAsGmt() {
        val plusThree = UtcOffset(hours = 3).asTimeZone()
        assertEquals("22:32", formatLocalized(instant, LocalizedDate.TIME, "de", plusThree))
        assertNotEquals(
            formatLocalized(instant, LocalizedDate.TIME, "de", TimeZone.UTC),
            formatLocalized(instant, LocalizedDate.TIME, "de", plusThree),
        )
    }

    @Test
    fun theYearIsLeftOutOfTheDayAndMonthOnly() {
        val dayMonth = formatLocalized(instant, LocalizedDate.DAY_MONTH, "en-US", kyiv)
        assertEquals(false, dayMonth.contains("2026"))
        assertEquals(true, formatLocalized(instant, LocalizedDate.DAY_MONTH_YEAR, "en-US", kyiv).contains("2026"))
    }
}
