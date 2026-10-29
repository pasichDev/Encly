package com.pasich.encly.utils

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Where a timestamp falls relative to the current day, in the device time zone. */
enum class RelativeDay { TODAY, YESTERDAY, OTHER }

fun relativeDay(
    date: Instant,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): RelativeDay {
    val target = date.toLocalDateTime(zone).date
    val today = now.toLocalDateTime(zone).date
    return when (target) {
        today -> RelativeDay.TODAY
        today.minus(DatePeriod(days = 1)) -> RelativeDay.YESTERDAY
        else -> RelativeDay.OTHER
    }
}

/** Whether [date] falls in the same calendar year as [now]; a card date then leaves the year out. */
fun isSameYear(
    date: Instant,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean = date.toLocalDateTime(zone).year == now.toLocalDateTime(zone).year

/** Today's date as "2026-10-25" in the device time zone, for a file name. */
fun todayStamp(now: Instant = Clock.System.now(), zone: TimeZone = TimeZone.currentSystemDefault()): String =
    now.toLocalDateTime(zone).date.toString()
