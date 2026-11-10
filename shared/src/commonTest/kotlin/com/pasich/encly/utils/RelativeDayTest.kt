package com.pasich.encly.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RelativeDayTest {

    private val zone = TimeZone.of("Europe/Kyiv")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 30): Instant =
        LocalDateTime(year, month, day, hour, minute, 0).toInstant(zone)

    private val now = at(2026, 3, 10, 12, 0)

    private fun march(day: Int, hour: Int) = at(2026, 3, day, hour)

    @Test
    fun sameDayIsToday() {
        assertEquals(RelativeDay.TODAY, relativeDay(march(10, 0), now, zone))
        assertEquals(RelativeDay.TODAY, relativeDay(march(10, 23), now, zone))
    }

    @Test
    fun previousDayIsYesterday() {
        assertEquals(RelativeDay.YESTERDAY, relativeDay(march(9, 8), now, zone))
    }

    /** Regression: the old formatter compared "now" with itself and called every date "today". */
    @Test
    fun olderDatesAreNotToday() {
        assertEquals(RelativeDay.OTHER, relativeDay(march(1, 8), now, zone))
        assertEquals(RelativeDay.OTHER, relativeDay(march(11, 8), now, zone))
    }

    @Test
    fun yesterdayAcrossTheYearBoundary() {
        val newYear = at(2026, 1, 1, 9, 0)
        val newYearsEve = at(2025, 12, 31, 22, 0)
        assertEquals(RelativeDay.YESTERDAY, relativeDay(newYearsEve, newYear, zone))
    }

    @Test
    fun sameYearOnlyWithinTheCalendarYear() {
        val newYearsEve = at(2025, 12, 31, 22, 0)
        assertTrue(isSameYear(march(1, 8), now, zone))
        assertFalse(isSameYear(newYearsEve, now, zone))
    }

    @Test
    fun theDayIsTheOneOfTheZoneNotOfUtc() {
        val lateEvening = at(2026, 3, 10, 23, 30) // 21:30 UTC, still the 10th in Kyiv
        val earlyMorning = at(2026, 3, 11, 0, 30) // 22:30 UTC on the 10th, already the 11th in Kyiv
        assertEquals(RelativeDay.TODAY, relativeDay(lateEvening, now, zone))
        assertEquals(RelativeDay.OTHER, relativeDay(earlyMorning, now, zone))
    }

    @Test
    fun yesterdayAcrossADaylightSavingChange() {
        val afterTheChange = at(2026, 3, 30, 9, 0)
        assertEquals(RelativeDay.YESTERDAY, relativeDay(at(2026, 3, 29, 1, 0), afterTheChange, zone))
    }

    @Test
    fun theDateStampIsTheZonesDateInIsoForm() {
        assertEquals("2026-03-10", todayStamp(now, zone))
        assertEquals("2026-03-09", todayStamp(at(2026, 3, 10, 0, 30), TimeZone.UTC))
    }
}
