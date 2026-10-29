package com.pasich.encly.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaZoneId
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

/**
 * The one platform-specific place of date formatting: localized month names, order and 12/24 hour
 * clock come from java.text and android.text.format, which kotlinx-datetime has none of. Everything
 * else works on kotlinx-datetime types and passes a [LocalizedDate] and a language tag in here.
 */
internal enum class LocalizedDate {
    /** "3:04 PM" */
    TIME,

    /** "5/14/26, 3:04 PM" */
    DATE_TIME_SHORT,

    /** "May 14, 2026, 3:04 PM" */
    DATE_TIME_MEDIUM,

    /** "14 May", in the locale's own order. */
    DAY_MONTH,

    /** "14 May 2025", in the locale's own order. */
    DAY_MONTH_YEAR,
}

/**
 * The language tag of the current (per-app) configuration. Unlike the default locale it always
 * follows the language picked in Settings, on every API level.
 */
@Composable
internal fun currentLanguageTag(): String =
    (LocalResources.current.configuration.locales[0] ?: Locale.ENGLISH).toLanguageTag()

/** [instant] in [zone], written as [style] in the language [languageTag]. */
internal fun formatLocalized(instant: Instant, style: LocalizedDate, languageTag: String, zone: TimeZone): String {
    val format = formats.getOrPut(style to languageTag) { newFormat(style, Locale.forLanguageTag(languageTag)) }
    // A DateFormat is not thread-safe, and the cached one may last longer than the zone it was made in.
    return synchronized(format) {
        format.timeZone = java.util.TimeZone.getTimeZone(zone.toJavaZoneId())
        format.format(Date(instant.toEpochMilliseconds()))
    }
}

private val formats = ConcurrentHashMap<Pair<LocalizedDate, String>, DateFormat>()

private fun newFormat(style: LocalizedDate, locale: Locale): DateFormat = when (style) {
    LocalizedDate.TIME -> DateFormat.getTimeInstance(DateFormat.SHORT, locale)
    LocalizedDate.DATE_TIME_SHORT -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
    LocalizedDate.DATE_TIME_MEDIUM -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    LocalizedDate.DAY_MONTH -> SimpleDateFormat(bestPattern(locale, "dMMM"), locale)
    LocalizedDate.DAY_MONTH_YEAR -> SimpleDateFormat(bestPattern(locale, "dMMMy"), locale)
}

/** The locale's own order for a date [skeleton] ("18 Sep" in English, "Sep 18" in some locales). */
private fun bestPattern(locale: Locale, skeleton: String): String =
    android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
