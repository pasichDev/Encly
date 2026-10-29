package com.pasich.encly.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** A stored note time as an [Instant]; 0 means "never saved" and reads as now. */
fun noteInstant(millis: Long): Instant = if (millis == 0L) Clock.System.now() else Instant.fromEpochMilliseconds(millis)

/** A short date-and-time in the in-app language (e.g. "5/14/26, 3:04 PM"). */
@Composable
fun formatDateTime(date: Instant): String =
    formatLocalized(date, LocalizedDate.DATE_TIME_SHORT, currentLanguageTag(), TimeZone.currentSystemDefault())

/** "Today 15:04", "Yesterday 15:04", or a localized medium date with time. */
@Composable
fun formatNoteDate(date: Instant = Clock.System.now()): String {
    val tag = currentLanguageTag()
    val zone = TimeZone.currentSystemDefault()
    return when (relativeDay(date, zone = zone)) {
        RelativeDay.TODAY ->
            stringResource(R.string.date_today_at, formatLocalized(date, LocalizedDate.TIME, tag, zone))

        RelativeDay.YESTERDAY ->
            stringResource(R.string.date_yesterday_at, formatLocalized(date, LocalizedDate.TIME, tag, zone))

        RelativeDay.OTHER -> formatLocalized(date, LocalizedDate.DATE_TIME_MEDIUM, tag, zone)
    }
}

/**
 * The date on a note card: "Today", "Yesterday", then day and month ("18 Sep"), with the year
 * only when it is not this year (design spec §4.3).
 */
@Composable
fun formatCardDate(date: Instant): String {
    val zone = TimeZone.currentSystemDefault()
    return when (relativeDay(date, zone = zone)) {
        RelativeDay.TODAY -> stringResource(R.string.date_today)

        RelativeDay.YESTERDAY -> stringResource(R.string.date_yesterday)

        RelativeDay.OTHER -> {
            val style = if (isSameYear(date, zone = zone)) LocalizedDate.DAY_MONTH else LocalizedDate.DAY_MONTH_YEAR
            formatLocalized(date, style, currentLanguageTag(), zone)
        }
    }
}
