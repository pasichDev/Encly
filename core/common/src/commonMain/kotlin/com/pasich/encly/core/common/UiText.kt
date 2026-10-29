package com.pasich.encly.core.common

/**
 * User-visible text produced outside the UI layer (ViewModels, managers, validators).
 *
 * It carries a resource id instead of resolved text, so the UI resolves it with its own
 * configuration and the string follows the in-app language, including after a language
 * switch recreates the activity while the ViewModel survives. Never resolve it with the
 * application context: on Android 12 and lower that context ignores the in-app language.
 * The id is the platform's string resource id (an `R.string` constant on Android), kept as a plain Int so
 * that this layer needs no platform annotation. The resolvers are `asString` in AndroidUiText.kt.
 */
public sealed interface UiText {

    public data class Resource(val id: Int) : UiText

    public companion object {
        public fun of(id: Int): UiText = Resource(id)
    }
}
