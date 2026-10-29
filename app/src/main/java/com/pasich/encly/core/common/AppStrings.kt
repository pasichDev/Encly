package com.pasich.encly.core.common

import androidx.annotation.StringRes

/**
 * Resolves text that is stored rather than shown (for example a duplicated note's title), in
 * the in-app language. Text that is only shown is [UiText]. The Android implementation is
 * [AndroidAppStrings].
 */
interface AppStrings {
    fun get(@StringRes id: Int, vararg args: Any): String
}
