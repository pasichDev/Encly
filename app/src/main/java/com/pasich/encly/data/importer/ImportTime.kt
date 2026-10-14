package com.pasich.encly.data.importer

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Timestamps of other apps as Encly milliseconds; anything missing, zero, negative, absurd,
 * unparseable or ambiguous is null (a wrong date is worse than none). A date without an offset is
 * taken in the phone's time zone: it is a local time, and the export is imported on the same phone
 * as a rule.
 *
 * Known forms: ISO-8601 (with or without offset), `2026-10-14 19:32:53Z`, epoch seconds or
 * milliseconds, and the user-selectable formats of Notesnook: `dd-MM-yyyy hh:mm a`,
 * `MM-dd-yyyy hh:mm a`, `yyyy-MM-dd hh:mm a` and their 24-hour forms, with `-`, `/` or `.`. When day
 * and month could be swapped (both 12 or less and different), the date is ambiguous.
 */
internal object ImportTime {
    /** Year 3000 in milliseconds: anything later is a corrupt timestamp. */
    private const val MAX_MILLIS = 32_503_680_000_000L
    private const val MICROS_PER_MILLI = 1000L

    /** Epoch values with at least this many digits are milliseconds, shorter ones seconds. */
    private const val MILLIS_DIGITS = 12
    private const val MILLIS_PER_SECOND = 1000L
    private const val MONTHS = 12
    private const val HALF_DAY = 12

    /** `2026-10-14 19:32:53Z`, how Joplin writes dates in front matter. */
    private val SPACE_SEPARATED = Regex("""^(\d{4}-\d{2}-\d{2}) (\d.*)$""")

    private const val CLOCK = """(?:[ ,T]+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([AaPp][Mm])?)"""
    private val DAY_FIRST = Regex("""^(\d{1,2})[-./](\d{1,2})[-./](\d{4})$CLOCK?$""")
    private val YEAR_FIRST = Regex("""^(\d{4})[-./](\d{1,2})[-./](\d{1,2})$CLOCK$""")

    private val ISO_FORMS: List<(String) -> Long> = listOf(
        { Instant.parse(it).toEpochMilli() },
        { OffsetDateTime.parse(it).toInstant().toEpochMilli() },
        { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() },
        { LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() },
    )

    /** Microseconds since the epoch as milliseconds; null if missing, not positive or absurdly large. */
    fun microsToMillis(micros: Long?): Long? = micros?.div(MICROS_PER_MILLI)?.takeIf { it in 1..MAX_MILLIS }

    fun parse(raw: String?): Long? {
        val text = raw?.trim().orEmpty().replace(SPACE_SEPARATED, "$1T$2")
        val millis = when {
            text.isEmpty() -> null

            text.all { it.isDigit() } ->
                text.toLongOrNull()?.let { if (text.length >= MILLIS_DIGITS) it else it * MILLIS_PER_SECOND }

            else -> localForm(text) ?: iso(text)
        }
        return millis?.takeIf { it in 1..MAX_MILLIS }
    }

    private fun iso(text: String): Long? = ISO_FORMS.firstNotNullOfOrNull { form ->
        try {
            form(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** `14-10-2026 10:48 PM`, `10/14/2026 22:48`, `2026-10-14 10:48 PM`: null if absent or ambiguous. */
    private fun localForm(text: String): Long? {
        val dayFirst = DAY_FIRST.matchEntire(text)?.groupValues
        val yearFirst = YEAR_FIRST.matchEntire(text)?.groupValues
        return when {
            dayFirst != null -> dayAndMonth(dayFirst[A].toInt(), dayFirst[B].toInt())
                ?.let { (day, month) -> millis(dayFirst[C].toInt(), month, day, dayFirst) }

            yearFirst != null -> millis(yearFirst[A].toInt(), yearFirst[B].toInt(), yearFirst[C].toInt(), yearFirst)

            else -> null
        }
    }

    /** (day, month) of two numbers in unknown order, or null if either order is possible. */
    private fun dayAndMonth(first: Int, second: Int): Pair<Int, Int>? = when {
        first > MONTHS && second <= MONTHS -> first to second
        second > MONTHS && first <= MONTHS -> second to first
        first == second -> first to second
        else -> null
    }

    /** [clock]: regex groups 4..7 are hour, minute, second and am/pm; any may be empty. */
    private fun millis(year: Int, month: Int, day: Int, clock: List<String>): Long? = try {
        val hour = clock[HOUR].toIntOrNull() ?: 0
        val hour24 = when (clock[MERIDIEM].lowercase()) {
            "am" -> hour % HALF_DAY
            "pm" -> hour % HALF_DAY + HALF_DAY
            else -> hour
        }
        LocalDateTime.of(year, month, day, hour24, clock[MINUTE].toIntOrNull() ?: 0, clock[SECOND].toIntOrNull() ?: 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: DateTimeException) {
        null
    }

    // Regex groups: the date parts in the order the pattern has them, then the clock.
    private const val A = 1
    private const val B = 2
    private const val C = 3
    private const val HOUR = 4
    private const val MINUTE = 5
    private const val SECOND = 6
    private const val MERIDIEM = 7
}
