package com.pasich.encly.utils

import java.util.Calendar
import java.util.Date

/** Where a timestamp falls relative to the current day, in the device time zone. */
enum class RelativeDay { TODAY, YESTERDAY, OTHER }

fun relativeDay(date: Date, now: Date = Date()): RelativeDay {
    val target = Calendar.getInstance().apply { time = date }
    val day = Calendar.getInstance().apply { time = now }
    if (target.isSameDay(day)) return RelativeDay.TODAY
    day.add(Calendar.DAY_OF_YEAR, -1)
    return if (target.isSameDay(day)) RelativeDay.YESTERDAY else RelativeDay.OTHER
}

private fun Calendar.isSameDay(other: Calendar): Boolean = get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
    get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

/** Whether [date] falls in the same calendar year as [now]; a card date then leaves the year out. */
fun isSameYear(date: Date, now: Date = Date()): Boolean {
    val target = Calendar.getInstance().apply { time = date }
    val current = Calendar.getInstance().apply { time = now }
    return target.get(Calendar.YEAR) == current.get(Calendar.YEAR)
}
