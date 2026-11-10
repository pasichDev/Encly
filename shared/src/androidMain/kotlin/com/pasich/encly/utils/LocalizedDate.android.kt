package com.pasich.encly.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.time.Instant

@Composable
internal actual fun currentLanguageTag(): String =
    (LocalResources.current.configuration.locales[0] ?: Locale.ENGLISH).toLanguageTag()

internal actual fun formatLocalized(
    instant: Instant,
    style: LocalizedDate,
    languageTag: String,
    zone: TimeZone,
): String = formatLocalizedJvm(instant, style, languageTag, zone) { locale, skeleton ->
    android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
}
