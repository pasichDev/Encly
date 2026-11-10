package com.pasich.encly.core.security

import com.pasich.encly.core.platform.KeyValueStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Strict keyboard privacy" (Settings → Security, off by default). When on, every text field
 * presents itself to the keyboard as a visible-password field without suggestions (see
 * SecureTextInputBoundary): compliant keyboards then offer no suggestions or cloud
 * prediction and get no surrounding text. The cost is no autocorrect, and on some keyboards
 * no emoji or voice input.
 */
@SingleIn(AppScope::class)
@Inject
public class KeyboardPrivacy(private val flags: KeyValueStore) {
    private val _strict = MutableStateFlow(flags.getBoolean(STRICT_KEY, false))
    public val strict: StateFlow<Boolean> = _strict.asStateFlow()

    public fun setStrict(enabled: Boolean) {
        flags.edit { putBoolean(STRICT_KEY, enabled) }
        _strict.value = enabled
    }

    private companion object {
        const val STRICT_KEY = "keyboard_strict_privacy"
    }
}
