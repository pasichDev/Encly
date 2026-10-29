package com.pasich.encly.core.common

import org.jetbrains.compose.resources.StringResource

/**
 * Resolves text that is stored rather than shown (for example a duplicated note's title), in
 * the in-app language. Text that is only shown is [UiText]. The implementation is
 * [ComposeAppStrings].
 */
public interface AppStrings {
    public suspend fun get(id: StringResource, vararg args: Any): String
}
