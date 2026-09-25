package com.pasich.encly.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The locale of the current (per-app) configuration. Unlike [Locale.getDefault] it always
 * follows the language picked in Settings, on every API level.
 */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** A short date-and-time format in the in-app language (e.g. "5/14/26, 3:04 PM"). */
@Composable
fun rememberDateTimeFormat(): DateFormat {
    val locale = currentLocale()
    return remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
    }
}

/** "Today 15:04", "Yesterday 15:04", or a localized medium date with time. */
@Composable
fun formatNoteDate(date: Date = Date()): String {
    val locale = currentLocale()
    val timeFormat = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    val dateTimeFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }
    return when (relativeDay(date)) {
        RelativeDay.TODAY -> stringResource(R.string.date_today_at, timeFormat.format(date))
        RelativeDay.YESTERDAY -> stringResource(R.string.date_yesterday_at, timeFormat.format(date))
        RelativeDay.OTHER -> dateTimeFormat.format(date)
    }
}

/**
 * The date on a note card: "Today", "Yesterday", then day and month ("18 Sep"), with the year
 * only when it is not this year (design spec §4.3).
 */
@Composable
fun formatCardDate(date: Date): String {
    val locale = currentLocale()
    val dayMonth = remember(locale) { SimpleDateFormat(bestPattern(locale, "dMMM"), locale) }
    val dayMonthYear = remember(locale) { SimpleDateFormat(bestPattern(locale, "dMMMy"), locale) }
    return when (relativeDay(date)) {
        RelativeDay.TODAY -> stringResource(R.string.date_today)
        RelativeDay.YESTERDAY -> stringResource(R.string.date_yesterday)
        RelativeDay.OTHER -> if (isSameYear(date)) dayMonth.format(date) else dayMonthYear.format(date)
    }
}

/** The locale's own order for a date [skeleton] ("18 Sep" in English, "Sep 18" in some locales). */
private fun bestPattern(locale: Locale, skeleton: String): String =
    android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
