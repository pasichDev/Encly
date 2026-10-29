package com.pasich.encly.core.common

import org.jetbrains.compose.resources.StringResource

/**
 * User-visible text produced outside the UI layer (ViewModels, managers, validators).
 *
 * It carries a string resource instead of resolved text, so the UI resolves it when it is
 * shown and the string follows the in-app language, including after a language switch recreates
 * the activity while the ViewModel survives. The resolvers are `asString` and `resolve` in
 * AndroidUiText.kt.
 */
public sealed interface UiText {

    public data class Resource(val id: StringResource) : UiText

    public companion object {
        public fun of(id: StringResource): UiText = Resource(id)
    }
}
