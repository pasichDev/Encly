package com.pasich.encly.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.utils.currentLanguageTag

/**
 * The layout direction of the app's language, on every host: Android, desktop and iOS do not all derive it
 * from the locale the same way. A language the app has no translation for keeps the host's own direction.
 */
@Composable
internal fun appLayoutDirection(): LayoutDirection {
    val host = LocalLayoutDirection.current
    val language = AppLanguage.fromTag(currentLanguageTag())
    return when {
        language == AppLanguage.SYSTEM -> host
        language.rightToLeft -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
}
