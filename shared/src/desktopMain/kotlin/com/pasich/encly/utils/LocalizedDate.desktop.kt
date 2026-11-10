package com.pasich.encly.utils

import androidx.compose.runtime.Composable
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.time.Instant

@Composable
internal actual fun currentLanguageTag(): String = Locale.getDefault().toLanguageTag()

internal actual fun formatLocalized(
    instant: Instant,
    style: LocalizedDate,
    languageTag: String,
    zone: TimeZone,
): String = // The JDK has no skeleton lookup: day and month in the order of the pattern "d MMM[ y]".
    formatLocalizedJvm(instant, style, languageTag, zone) { _, skeleton ->
        if (skeleton == "dMMM") "d MMM" else "d MMM y"
    }
