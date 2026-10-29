package com.pasich.encly.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaZoneId
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

/*
 * Date formatting on the JVM (Android and desktop), through java.text. The locale's own order for the
 * day-and-month styles comes from the platform ([bestPattern]): Android has it in android.text.format.
 */

/** [instant] in [zone], written as [style] in the language [languageTag]. */
internal fun formatLocalizedJvm(
    instant: Instant,
    style: LocalizedDate,
    languageTag: String,
    zone: TimeZone,
    bestPattern: (Locale, String) -> String,
): String {
    val format = formats.getOrPut(style to languageTag) {
        newFormat(style, Locale.forLanguageTag(languageTag), bestPattern)
    }
    // A DateFormat is not thread-safe, and the cached one may last longer than the zone it was made in.
    return synchronized(format) {
        format.timeZone = java.util.TimeZone.getTimeZone(zone.toJavaZoneId())
        format.format(Date(instant.toEpochMilliseconds()))
    }
}

private val formats = ConcurrentHashMap<Pair<LocalizedDate, String>, DateFormat>()

private fun newFormat(style: LocalizedDate, locale: Locale, bestPattern: (Locale, String) -> String): DateFormat =
    when (style) {
        LocalizedDate.TIME -> DateFormat.getTimeInstance(DateFormat.SHORT, locale)
        LocalizedDate.DATE_TIME_SHORT -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        LocalizedDate.DATE_TIME_MEDIUM -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        LocalizedDate.DAY_MONTH -> SimpleDateFormat(bestPattern(locale, "dMMM"), locale)
        LocalizedDate.DAY_MONTH_YEAR -> SimpleDateFormat(bestPattern(locale, "dMMMy"), locale)
    }
