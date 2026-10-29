package com.pasich.encly.core.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** The text in the composition's configuration, so it follows the in-app language. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id)
}

/** The text in [context]'s configuration; pass an Activity, not the application context. */
fun UiText.asString(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(id)
}
