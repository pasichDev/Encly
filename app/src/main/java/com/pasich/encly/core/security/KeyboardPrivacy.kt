package com.pasich.encly.core.security

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Strict keyboard privacy" (Settings → Security, off by default). When on, every text field
 * presents itself to the keyboard as a visible-password field without suggestions (see
 * SecureTextInputBoundary): compliant keyboards then offer no suggestions or cloud
 * prediction and get no surrounding text. The cost is no autocorrect, and on some keyboards
 * no emoji or voice input.
 */
@Singleton
class KeyboardPrivacy @Inject constructor(private val flags: SharedPreferences) {
    private val _strict = MutableStateFlow(flags.getBoolean(STRICT_KEY, false))
    val strict: StateFlow<Boolean> = _strict.asStateFlow()

    fun setStrict(enabled: Boolean) {
        flags.edit().putBoolean(STRICT_KEY, enabled).apply()
        _strict.value = enabled
    }

    private companion object {
        const val STRICT_KEY = "keyboard_strict_privacy"
    }
}
