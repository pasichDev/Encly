package com.pasich.encly.core.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * User-visible text produced outside the UI layer (ViewModels, managers, validators).
 *
 * It carries a resource id instead of resolved text, so the UI resolves it with its own
 * configuration and the string follows the in-app language, including after a language
 * switch recreates the activity while the ViewModel survives. Never resolve it with the
 * application context: on Android 12 and lower that context ignores the in-app language.
 */
sealed interface UiText {

    data class Resource(@param:StringRes val id: Int) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(id)
    }

    fun asString(context: Context): String = when (this) {
        is Resource -> context.getString(id)
    }

    companion object {
        fun of(@StringRes id: Int): UiText = Resource(id)
    }
}
