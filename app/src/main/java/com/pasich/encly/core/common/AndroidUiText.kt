package com.pasich.encly.core.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The text in the composition's locale, so it follows the in-app language. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id)
}

/** The text in the process's current locale, which is the in-app language (see AppLocales). */
suspend fun UiText.resolve(): String = when (this) {
    is UiText.Resource -> getString(id)
}
