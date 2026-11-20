package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import com.pasich.encly.core.security.ScreenshotAllowance
import com.pasich.encly.platform.LocalScreenshotAllowance

/**
 * Keeps the window protected from screenshots and recordings while this is in the composition, even when the
 * user allowed screenshots until the next lock: the lock screen, PIN entry and the recovery words call it.
 *
 * The hold starts as the composition is built, not after it: a dialog made in the same pass reads the window's
 * flag when it is created. Without an allowance (a host that never allows screenshots) it does nothing.
 */
@Composable
fun ProtectFromCapture() {
    val allowance = LocalScreenshotAllowance.current
    remember(allowance) { CaptureHold(allowance) }
}

private class CaptureHold(allowance: ScreenshotAllowance?) : RememberObserver {
    private var release: (() -> Unit)? = allowance?.protect()

    override fun onRemembered() = Unit

    override fun onForgotten() = end()

    override fun onAbandoned() = end()

    private fun end() {
        release?.invoke()
        release = null
    }
}
