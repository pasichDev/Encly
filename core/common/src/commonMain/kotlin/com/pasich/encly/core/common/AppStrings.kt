package com.pasich.encly.core.common

/**
 * Resolves text that is stored rather than shown (for example a duplicated note's title), in
 * the in-app language. [id] is the platform's string resource id. Text that is only shown is
 * [UiText]. The Android implementation is [AndroidAppStrings].
 */
public interface AppStrings {
    public fun get(id: Int, vararg args: Any): String
}
