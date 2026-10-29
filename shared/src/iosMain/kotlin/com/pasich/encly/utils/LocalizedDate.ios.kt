package com.pasich.encly.utils

import androidx.compose.runtime.Composable
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.datetime.TimeZone
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.defaultTimeZone
import platform.Foundation.preferredLanguages
import platform.Foundation.timeZoneWithName
import kotlin.time.Instant

private const val MILLIS_PER_SECOND = 1000.0

/** The first of the user's preferred languages, which is the in-app one after it was applied and the app restarted. */
@Composable
internal actual fun currentLanguageTag(): String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"

internal actual fun formatLocalized(
    instant: Instant,
    style: LocalizedDate,
    languageTag: String,
    zone: TimeZone,
): String = synchronized(lock) {
    val formatter = formatters.getOrPut(style to languageTag) { newFormatter(style, languageTag) }
    // The cached formatter may outlive the zone it was made in.
    formatter.timeZone = NSTimeZone.timeZoneWithName(zone.id) ?: NSTimeZone.defaultTimeZone
    formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(instant.toEpochMilliseconds() / MILLIS_PER_SECOND))
}

private val lock = SynchronizedObject()
private val formatters = HashMap<Pair<LocalizedDate, String>, NSDateFormatter>()

private fun newFormatter(style: LocalizedDate, languageTag: String): NSDateFormatter = NSDateFormatter().apply {
    locale = NSLocale(localeIdentifier = languageTag)
    when (style) {
        LocalizedDate.TIME -> {
            dateStyle = NSDateFormatterNoStyle
            timeStyle = NSDateFormatterShortStyle
        }

        LocalizedDate.DATE_TIME_SHORT -> {
            dateStyle = NSDateFormatterShortStyle
            timeStyle = NSDateFormatterShortStyle
        }

        LocalizedDate.DATE_TIME_MEDIUM -> {
            dateStyle = NSDateFormatterMediumStyle
            timeStyle = NSDateFormatterShortStyle
        }

        // The locale's own order of day and month comes from the template.
        LocalizedDate.DAY_MONTH -> setLocalizedDateFormatFromTemplate("dMMM")

        LocalizedDate.DAY_MONTH_YEAR -> setLocalizedDateFormatFromTemplate("dMMMy")
    }
}
