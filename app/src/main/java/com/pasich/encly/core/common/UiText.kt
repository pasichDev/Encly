package com.pasich.encly.core.common

import androidx.annotation.StringRes

/**
 * User-visible text produced outside the UI layer (ViewModels, managers, validators).
 *
 * It carries a resource id instead of resolved text, so the UI resolves it with its own
 * configuration and the string follows the in-app language, including after a language
 * switch recreates the activity while the ViewModel survives. Never resolve it with the
 * application context: on Android 12 and lower that context ignores the in-app language.
 * The resolvers are `asString` in AndroidUiText.kt.
 */
sealed interface UiText {

    data class Resource(@param:StringRes val id: Int) : UiText

    companion object {
        fun of(@StringRes id: Int): UiText = Resource(id)
    }
}
