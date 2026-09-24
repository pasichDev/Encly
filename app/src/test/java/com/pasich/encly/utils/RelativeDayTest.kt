package com.pasich.encly.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class RelativeDayTest {

    private val now: Date = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, 10, 12, 0, 0)
    }.time

    private fun at(day: Int, hour: Int): Date = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, day, hour, 30, 0)
    }.time

    @Test
    fun sameDayIsToday() {
        assertEquals(RelativeDay.TODAY, relativeDay(at(10, 0), now))
        assertEquals(RelativeDay.TODAY, relativeDay(at(10, 23), now))
    }

    @Test
    fun previousDayIsYesterday() {
        assertEquals(RelativeDay.YESTERDAY, relativeDay(at(9, 8), now))
    }

    /** Regression: the old formatter compared "now" with itself and called every date "today". */
    @Test
    fun olderDatesAreNotToday() {
        assertEquals(RelativeDay.OTHER, relativeDay(at(1, 8), now))
        assertEquals(RelativeDay.OTHER, relativeDay(at(11, 8), now))
    }

    @Test
    fun yesterdayAcrossTheYearBoundary() {
        val newYear = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1, 9, 0, 0) }.time
        val newYearsEve = Calendar.getInstance().apply { set(2025, Calendar.DECEMBER, 31, 22, 0, 0) }.time
        assertEquals(RelativeDay.YESTERDAY, relativeDay(newYearsEve, newYear))
    }

    @Test
    fun sameYearOnlyWithinTheCalendarYear() {
        val newYearsEve = Calendar.getInstance().apply { set(2025, Calendar.DECEMBER, 31, 22, 0, 0) }.time
        assertTrue(isSameYear(at(1, 8), now))
        assertFalse(isSameYear(newYearsEve, now))
    }
}
