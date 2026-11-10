package com.pasich.encly.utils

import androidx.compose.runtime.Composable
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The one platform-specific place of date formatting: localized month names, order and 12/24 hour
 * clock come from the platform (java.text, android.text.format), which kotlinx-datetime has none of. Everything
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
internal expect fun currentLanguageTag(): String

/** [instant] in [zone], written as [style] in the language [languageTag]. */
internal expect fun formatLocalized(instant: Instant, style: LocalizedDate, languageTag: String, zone: TimeZone): String
